package io.github.paramkathir.outagetracker.domain;

import io.github.paramkathir.outagetracker.core.Geo;
import io.github.paramkathir.outagetracker.core.GeoPoint;
import io.github.paramkathir.outagetracker.core.IncidentStatus;
import io.github.paramkathir.outagetracker.core.ServiceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * A cluster of outage reports for one service type in one area.
 */
@Entity
@Table(name = "incident")
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_type", nullable = false, length = 16)
    private ServiceType serviceType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private IncidentStatus status;

    @Column(name = "centroid_lat", nullable = false)
    private double centroidLat;

    @Column(name = "centroid_lng", nullable = false)
    private double centroidLng;

    @Column(name = "radius_km", nullable = false)
    private double radiusKm;

    @Column(name = "report_count", nullable = false)
    private int reportCount;

    @Column(name = "first_reported_at", nullable = false)
    private Instant firstReportedAt;

    @Column(name = "last_reported_at", nullable = false)
    private Instant lastReportedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolution_note", length = 500)
    private String resolutionNote;

    /** Optimistic locking guards against lost updates if two writers ever race on the same row. */
    @Version
    private long version;

    protected Incident() {
        // for JPA
    }

    public Incident(ServiceType serviceType, GeoPoint centroid, double radiusKm, int reportCount,
                    Instant firstReportedAt, Instant lastReportedAt) {
        this.serviceType = serviceType;
        this.status = IncidentStatus.SUSPECTED;
        this.centroidLat = centroid.latitude();
        this.centroidLng = centroid.longitude();
        this.radiusKm = radiusKm;
        this.reportCount = reportCount;
        this.firstReportedAt = firstReportedAt;
        this.lastReportedAt = lastReportedAt;
    }

    /** Folds one more report into this incident's running centroid, radius and count. */
    public void absorb(GeoPoint location, Instant reportedAt) {
        if (!status.isOpen()) {
            throw new IllegalStateException("cannot add reports to a resolved incident");
        }
        GeoPoint newCentroid = Geo.addToCentroid(centroid(), reportCount, location);
        this.centroidLat = newCentroid.latitude();
        this.centroidLng = newCentroid.longitude();
        this.reportCount++;
        this.radiusKm = Math.max(radiusKm, Geo.distanceKm(newCentroid, location));
        if (reportedAt.isAfter(lastReportedAt)) {
            this.lastReportedAt = reportedAt;
        }
    }

    /**
     * Moves to {@code next} if it differs from the current status.
     *
     * @return true if the status changed
     */
    public boolean transitionTo(IncidentStatus next, Instant at) {
        if (next == status) {
            return false;
        }
        if (status == IncidentStatus.RESOLVED) {
            throw new IllegalStateException("resolved incidents are final");
        }
        if (next == IncidentStatus.CONFIRMED && confirmedAt == null) {
            confirmedAt = at;
        }
        status = next;
        return true;
    }

    public void resolve(Instant at, String note) {
        transitionTo(IncidentStatus.RESOLVED, at);
        this.resolvedAt = at;
        this.resolutionNote = note;
    }

    public GeoPoint centroid() {
        return new GeoPoint(centroidLat, centroidLng);
    }

    public Long getId() {
        return id;
    }

    public ServiceType getServiceType() {
        return serviceType;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public double getCentroidLat() {
        return centroidLat;
    }

    public double getCentroidLng() {
        return centroidLng;
    }

    public double getRadiusKm() {
        return radiusKm;
    }

    public int getReportCount() {
        return reportCount;
    }

    public Instant getFirstReportedAt() {
        return firstReportedAt;
    }

    public Instant getLastReportedAt() {
        return lastReportedAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public String getResolutionNote() {
        return resolutionNote;
    }

    public long getVersion() {
        return version;
    }
}
