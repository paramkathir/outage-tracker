package io.github.paramkathir.outagetracker.web;

import io.github.paramkathir.outagetracker.config.TrackerProperties;
import io.github.paramkathir.outagetracker.core.TokenBucketRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.time.Duration;

/**
 * Applies the per-IP token bucket to write requests (POST) on the routes it is registered for.
 * Responds 429 with a Retry-After header when a client is over its budget.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final TokenBucketRateLimiter limiter;
    private final boolean enabled;

    public RateLimitInterceptor(TokenBucketRateLimiter limiter, TrackerProperties properties) {
        this.limiter = limiter;
        this.enabled = properties.rateLimit().enabled();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (!enabled || !"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        TokenBucketRateLimiter.Result result = limiter.tryAcquire(request.getRemoteAddr());
        response.setHeader("X-RateLimit-Remaining", Long.toString(result.remainingTokens()));
        if (result.allowed()) {
            return true;
        }
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", Long.toString(result.retryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("""
                {"type":"about:blank","title":"Too Many Requests","status":429,\
                "detail":"Rate limit exceeded. Try again in %d second(s)."}"""
                .formatted(result.retryAfterSeconds()));
        return false;
    }

    /** Forget clients that have been idle for a while so the bucket map can't grow forever. */
    @Scheduled(fixedRate = 300_000)
    public void evictIdleBuckets() {
        limiter.evictIdle(Duration.ofMinutes(10));
    }
}
