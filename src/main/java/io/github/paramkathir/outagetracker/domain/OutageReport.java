package io.github.paramkathir.outagetracker.domain;

import io.github.paramkathir.outagetracker.core.GeoPoint;
import io.github.paramkathir.outagetracker.core.ServiceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A single customer's "my service is down" report.
 */
@Entity
@Table(name = "outage_report")
public class OutageReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_type", nullable = false, length = 16)
    private ServiceType serviceType;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(name = "zip_code", length = 10)
    private String zipCode;

    @Column(length = 500)
    private String description;

    @Column(name = "reported_at", nullable = false)
    private Instant reportedAt;

    /** SHA-256 of the client identifier; lets us de-duplicate without storing raw IPs. */
    @Column(name = "client_hash", nullable = false, length = 64)
    private String clientHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_id")
    private Incident incident;

    protected OutageReport() {
        // for JPA
    }

    public OutageReport(ServiceType serviceType, GeoPoint location, String zipCode, String description,
                        Instant reportedAt, String clientHash) {
        this.serviceType = serviceType;
        this.latitude = location.latitude();
        this.longitude = location.longitude();
        this.zipCode = zipCode;
        this.description = description;
        this.reportedAt = reportedAt;
        this.clientHash = clientHash;
    }

    public GeoPoint location() {
        return new GeoPoint(latitude, longitude);
    }

    public void assignTo(Incident incident) {
        this.incident = incident;
    }

    public Long getId() {
        return id;
    }

    public ServiceType getServiceType() {
        return serviceType;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public String getZipCode() {
        return zipCode;
    }

    public String getDescription() {
        return description;
    }

    public Instant getReportedAt() {
        return reportedAt;
    }

    public String getClientHash() {
        return clientHash;
    }

    public Incident getIncident() {
        return incident;
    }
}
