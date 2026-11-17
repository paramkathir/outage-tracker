package io.github.paramkathir.outagetracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One entry on an incident's public timeline.
 */
@Entity
@Table(name = "incident_update")
public class IncidentUpdate {

    public enum Kind {
        /** Written by the system on lifecycle changes. */
        AUTOMATIC,
        /** Posted by an operator from the dashboard. */
        OPERATOR
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "incident_id", nullable = false)
    private Long incidentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Kind kind;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(length = 1000)
    private String body;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IncidentUpdate() {
        // for JPA
    }

    public IncidentUpdate(Long incidentId, Kind kind, String title, String body, Instant createdAt) {
        this.incidentId = incidentId;
        this.kind = kind;
        this.title = title;
        this.body = body;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getIncidentId() {
        return incidentId;
    }

    public Kind getKind() {
        return kind;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
