package io.github.paramkathir.outagetracker.core;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Thread-safe, per-key token bucket rate limiter.
 *
 * <p>Each key (e.g. a client IP) gets a bucket holding up to {@code capacity} tokens that
 * refills continuously at {@code refillPerSecond}. A request consumes one token; when the
 * bucket is empty the request is rejected. This allows short bursts while bounding the
 * sustained rate.</p>
 *
 * <p>The clock is injectable so behaviour is deterministic in tests.</p>
 */
public final class TokenBucketRateLimiter {

    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private final double capacity;
    private final double refillPerNano;
    private final LongSupplier nanoClock;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public TokenBucketRateLimiter(int capacity, double refillPerSecond) {
        this(capacity, refillPerSecond, System::nanoTime);
    }

    public TokenBucketRateLimiter(int capacity, double refillPerSecond, LongSupplier nanoClock) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1");
        }
        if (refillPerSecond <= 0) {
            throw new IllegalArgumentException("refillPerSecond must be > 0");
        }
        this.capacity = capacity;
        this.refillPerNano = refillPerSecond / NANOS_PER_SECOND;
        this.nanoClock = Objects.requireNonNull(nanoClock);
    }

    /** Attempts to take one token for {@code key}. */
    public Result tryAcquire(String key) {
        Objects.requireNonNull(key, "key");
        long now = nanoClock.getAsLong();
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(capacity, now));
        return bucket.tryConsume(now);
    }

    /** Drops buckets that have been idle long enough to be full again; bounds memory use. */
    public int evictIdle(Duration idleFor) {
        long now = nanoClock.getAsLong();
        long idleNanos = idleFor.toNanos();
        int before = buckets.size();
        buckets.entrySet().removeIf(e -> e.getValue().idleSince(now) >= idleNanos);
        return before - buckets.size();
    }

    public int trackedKeys() {
        return buckets.size();
    }

    /**
     * @param allowed           whether the request may proceed
     * @param remainingTokens   whole tokens left after this call
     * @param retryAfterSeconds when rejected, seconds until one token is available (rounded up); else 0
     */
    public record Result(boolean allowed, long remainingTokens, long retryAfterSeconds) {
    }

    private final class Bucket {
        private double tokens;
        private long lastRefillNanos;

        Bucket(double tokens, long now) {
            this.tokens = tokens;
            this.lastRefillNanos = now;
        }

        synchronized Result tryConsume(long now) {
            refill(now);
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return new Result(true, (long) Math.floor(tokens), 0);
            }
            double nanosUntilToken = (1.0 - tokens) / refillPerNano;
            long retryAfter = Math.max(1, (long) Math.ceil(nanosUntilToken / NANOS_PER_SECOND));
            return new Result(false, 0, retryAfter);
        }

        synchronized long idleSince(long now) {
            return now - lastRefillNanos;
        }

        private void refill(long now) {
            long elapsed = now - lastRefillNanos;
            if (elapsed > 0) {
                tokens = Math.min(capacity, tokens + elapsed * refillPerNano);
                lastRefillNanos = now;
            }
        }
    }
}
