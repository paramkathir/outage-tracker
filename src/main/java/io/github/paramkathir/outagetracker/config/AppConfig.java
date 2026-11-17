package io.github.paramkathir.outagetracker.config;

import io.github.paramkathir.outagetracker.core.IncidentClusterer;
import io.github.paramkathir.outagetracker.core.TokenBucketRateLimiter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(TrackerProperties.class)
public class AppConfig {

    /** Injected everywhere "now" is needed so tests can control time. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public IncidentClusterer incidentClusterer(TrackerProperties properties) {
        return new IncidentClusterer(properties.clustering().toClusterConfig());
    }

    @Bean
    public TokenBucketRateLimiter rateLimiter(TrackerProperties properties) {
        TrackerProperties.RateLimit rl = properties.rateLimit();
        return new TokenBucketRateLimiter(rl.capacity(), rl.refillPerSecond());
    }
}
