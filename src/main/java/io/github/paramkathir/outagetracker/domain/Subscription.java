package io.github.paramkathir.outagetracker.domain;

import io.github.paramkathir.outagetracker.core.GeoPoint;
import io.github.paramkathir.outagetracker.core.ServiceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A request to be alerted when an incident is confirmed or resolved near a location.
 */
@Entity
@Table(name = "subscription")
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(name = "radius_km", nullable = false)
    private double radiusKm;

    /** Null means "all service types". */
    @Enumerated(EnumType.STRING)
    @Column(name = "service_type", length = 16)
    private ServiceType serviceType;

    @Column(name = "webhook_url", length = 500)
    private String webhookUrl;

    @Column(name = "unsubscribe_token", nullable = false, unique = true, length = 36)
    private String unsubscribeToken;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Subscription() {
        // for JPA
    }

    public Subscription(String email, GeoPoint location, double radiusKm, ServiceType serviceType,
                        String webhookUrl, Instant createdAt) {
        this.email = email;
        this.latitude = location.latitude();
        this.longitude = location.longitude();
        this.radiusKm = radiusKm;
        this.serviceType = serviceType;
        this.webhookUrl = webhookUrl;
        this.createdAt = createdAt;
        this.unsubscribeToken = UUID.randomUUID().toString();
    }

    public GeoPoint location() {
        return new GeoPoint(latitude, longitude);
    }

    public boolean coversService(ServiceType type) {
        return serviceType == null || serviceType == type;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public double getRadiusKm() {
        return radiusKm;
    }

    public ServiceType getServiceType() {
        return serviceType;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public String getUnsubscribeToken() {
        return unsubscribeToken;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
