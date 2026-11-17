package io.github.paramkathir.outagetracker.core;

import java.time.Duration;
import java.util.Objects;

/**
 * Tuning knobs for the clustering algorithm.
 *
 * @param joinRadiusKm      a report within this distance of an open incident's centroid joins it;
 *                          unclustered reports within this distance of each other can form a new incident
 * @param minReportsToOpen  how many nearby unclustered reports it takes to open a SUSPECTED incident
 * @param confirmThreshold  report count at which an incident is promoted to CONFIRMED
 * @param pendingWindow     how far back unclustered reports are considered when opening an incident
 * @param quietPeriod       an open incident with no new reports for this long is auto-resolved
 */
public record ClusterConfig(
        double joinRadiusKm,
        int minReportsToOpen,
        int confirmThreshold,
        Duration pendingWindow,
        Duration quietPeriod) {

    public ClusterConfig {
        if (joinRadiusKm <= 0) {
            throw new IllegalArgumentException("joinRadiusKm must be > 0");
        }
        if (minReportsToOpen < 2) {
            throw new IllegalArgumentException("minReportsToOpen must be >= 2");
        }
        if (confirmThreshold < minReportsToOpen) {
            throw new IllegalArgumentException("confirmThreshold must be >= minReportsToOpen");
        }
        Objects.requireNonNull(pendingWindow, "pendingWindow");
        Objects.requireNonNull(quietPeriod, "quietPeriod");
        if (pendingWindow.isNegative() || pendingWindow.isZero()) {
            throw new IllegalArgumentException("pendingWindow must be positive");
        }
        if (quietPeriod.isNegative() || quietPeriod.isZero()) {
            throw new IllegalArgumentException("quietPeriod must be positive");
        }
    }

    public static ClusterConfig defaults() {
        return new ClusterConfig(3.0, 3, 10, Duration.ofMinutes(30), Duration.ofMinutes(45));
    }
}
