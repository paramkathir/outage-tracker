package io.github.paramkathir.outagetracker.core;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Decides what to do with a freshly submitted outage report.
 *
 * <p>This class is deliberately free of Spring and JPA: it takes plain snapshots of the
 * relevant state and returns a {@link Decision}. The service layer is responsible for
 * loading candidates and applying the decision inside a transaction. Keeping the rules
 * pure makes them trivial to unit test and reason about.</p>
 *
 * <p>Algorithm (a streaming, density-based approach in the spirit of DBSCAN):</p>
 * <ol>
 *   <li>If the report is within {@code joinRadiusKm} of an open incident's centroid,
 *       attach it to the <em>nearest</em> such incident.</li>
 *   <li>Otherwise, look at recent unclustered reports within {@code joinRadiusKm}.
 *       If those plus the new report reach {@code minReportsToOpen}, open a new incident
 *       containing all of them.</li>
 *   <li>Otherwise, leave the report unclustered; it may seed an incident later.</li>
 * </ol>
 */
public final class IncidentClusterer {

    private final ClusterConfig config;

    public IncidentClusterer(ClusterConfig config) {
        this.config = Objects.requireNonNull(config);
    }

    public ClusterConfig config() {
        return config;
    }

    /**
     * @param location           where the new report was made
     * @param openIncidents      open incidents for the same service type
     * @param unclusteredReports recent unclustered reports for the same service type
     *                           (may include reports outside the radius; they are filtered here)
     */
    public Decision decide(GeoPoint location,
                           List<OpenIncident> openIncidents,
                           List<PendingReport> unclusteredReports) {
        Objects.requireNonNull(location, "location");

        OpenIncident nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (OpenIncident incident : openIncidents) {
            double d = Geo.distanceKm(location, incident.centroid());
            if (d <= config.joinRadiusKm() && d < nearestDistance) {
                nearest = incident;
                nearestDistance = d;
            }
        }
        if (nearest != null) {
            return new AttachToIncident(nearest.id(), nearestDistance);
        }

        List<PendingReport> neighbours = new ArrayList<>();
        for (PendingReport report : unclusteredReports) {
            if (Geo.distanceKm(location, report.location()) <= config.joinRadiusKm()) {
                neighbours.add(report);
            }
        }
        if (neighbours.size() + 1 < config.minReportsToOpen()) {
            return new LeaveUnclustered();
        }

        neighbours.sort(Comparator.comparingLong(PendingReport::id));
        List<GeoPoint> points = new ArrayList<>(neighbours.size() + 1);
        neighbours.forEach(r -> points.add(r.location()));
        points.add(location);
        GeoPoint centroid = Geo.centroid(points);
        double radius = 0;
        for (GeoPoint p : points) {
            radius = Math.max(radius, Geo.distanceKm(centroid, p));
        }
        List<Long> ids = neighbours.stream().map(PendingReport::id).toList();
        return new CreateIncident(ids, centroid, radius);
    }

    /** The status an open incident should have once it holds {@code reportCount} reports. */
    public IncidentStatus statusFor(IncidentStatus current, int reportCount) {
        if (current == IncidentStatus.RESOLVED) {
            return current;
        }
        return reportCount >= config.confirmThreshold() ? IncidentStatus.CONFIRMED : IncidentStatus.SUSPECTED;
    }

    /** Whether an open incident whose last report was at {@code lastReportedAt} should auto-resolve. */
    public boolean isStale(Instant lastReportedAt, Instant now) {
        return Duration.between(lastReportedAt, now).compareTo(config.quietPeriod()) >= 0;
    }

    // ----- Input snapshots -----

    public record OpenIncident(long id, GeoPoint centroid) {
    }

    public record PendingReport(long id, GeoPoint location) {
    }

    // ----- Decisions -----

    public sealed interface Decision permits AttachToIncident, CreateIncident, LeaveUnclustered {
    }

    /** Join an existing incident. */
    public record AttachToIncident(long incidentId, double distanceKm) implements Decision {
    }

    /**
     * Open a new incident containing the new report plus the listed existing reports.
     *
     * @param existingReportIds ids of previously unclustered reports to pull into the incident
     */
    public record CreateIncident(List<Long> existingReportIds, GeoPoint centroid, double radiusKm)
            implements Decision {

        public CreateIncident {
            existingReportIds = List.copyOf(existingReportIds);
        }

        public int totalReports() {
            return existingReportIds.size() + 1;
        }
    }

    /** Not enough nearby activity yet. */
    public record LeaveUnclustered() implements Decision {
    }
}
