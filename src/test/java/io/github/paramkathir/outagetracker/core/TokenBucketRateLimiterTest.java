package io.github.paramkathir.outagetracker.core;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenBucketRateLimiterTest {

    private final AtomicLong nanos = new AtomicLong();

    private void advanceSeconds(double seconds) {
        nanos.addAndGet((long) (seconds * 1_000_000_000L));
    }

    @Test
    void allowsBurstUpToCapacityThenRejects() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(5, 1.0, nanos::get);
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire("a").allowed(), "request " + i);
        }
        TokenBucketRateLimiter.Result rejected = limiter.tryAcquire("a");
        assertFalse(rejected.allowed());
        assertEquals(1, rejected.retryAfterSeconds());
    }

    @Test
    void refillsOverTime() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(2, 0.5, nanos::get); // 1 token / 2 s
        assertTrue(limiter.tryAcquire("a").allowed());
        assertTrue(limiter.tryAcquire("a").allowed());
        assertFalse(limiter.tryAcquire("a").allowed());

        advanceSeconds(1.0);
        TokenBucketRateLimiter.Result stillEmpty = limiter.tryAcquire("a");
        assertFalse(stillEmpty.allowed());
        assertEquals(1, stillEmpty.retryAfterSeconds());

        advanceSeconds(1.0);
        assertTrue(limiter.tryAcquire("a").allowed());
    }

    @Test
    void neverExceedsCapacityAfterLongIdle() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(3, 10, nanos::get);
        limiter.tryAcquire("a");
        advanceSeconds(3600);
        int allowed = 0;
        while (limiter.tryAcquire("a").allowed()) {
            allowed++;
        }
        assertEquals(3, allowed);
    }

    @Test
    void keysAreIndependent() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(1, 0.01, nanos::get);
        assertTrue(limiter.tryAcquire("alice").allowed());
        assertFalse(limiter.tryAcquire("alice").allowed());
        assertTrue(limiter.tryAcquire("bob").allowed());
    }

    @Test
    void evictsIdleBuckets() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(1, 1, nanos::get);
        limiter.tryAcquire("old");
        advanceSeconds(600);
        limiter.tryAcquire("fresh");
        assertEquals(1, limiter.evictIdle(Duration.ofMinutes(5)));
        assertEquals(1, limiter.trackedKeys());
    }

    @Test
    void exactlyCapacityRequestsSucceedUnderContention() throws InterruptedException {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(100, 0.0001, nanos::get);
        int threads = 16;
        int perThread = 50; // 800 attempts for 100 tokens
        AtomicInteger allowed = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                start.await();
                for (int i = 0; i < perThread; i++) {
                    if (limiter.tryAcquire("shared").allowed()) {
                        allowed.incrementAndGet();
                    }
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        assertEquals(100, allowed.get());
    }
}
