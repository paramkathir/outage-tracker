package io.github.paramkathir.outagetracker.config;

import io.github.paramkathir.outagetracker.core.ClusterConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Strongly typed view of the {@code tracker.*} settings in application.yml.
 */
@ConfigurationProperties(prefix = "tracker")
public record TrackerProperties(
        Clustering clustering,
        RateLimit rateLimit,
        Reports reports,
        Admin admin,
        Demo demo) {

    public record Clustering(
            double joinRadiusKm,
            int minReportsToOpen,
            int confirmThreshold,
            Duration pendingWindow,
            Duration quietPeriod) {

        public ClusterConfig toClusterConfig() {
            return new ClusterConfig(joinRadiusKm, minReportsToOpen, confirmThreshold, pendingWindow, quietPeriod);
        }
    }

    /** Token bucket applied per client IP to write endpoints. */
    public record RateLimit(boolean enabled, int capacity, double refillPerSecond) {
    }

    /** A client may report the same service at most once per {@code duplicateWindow}; zero disables. */
    public record Reports(Duration duplicateWindow) {
    }

    public record Admin(String apiKey) {
    }

    public record Demo(boolean seedEnabled) {
    }
}
