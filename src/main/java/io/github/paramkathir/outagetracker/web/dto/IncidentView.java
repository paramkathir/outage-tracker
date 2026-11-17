package io.github.paramkathir.outagetracker.web.dto;

import io.github.paramkathir.outagetracker.core.IncidentStatus;
import io.github.paramkathir.outagetracker.core.ServiceType;
import io.github.paramkathir.outagetracker.domain.Incident;

import java.time.Instant;

public record IncidentView(
        long id,
        ServiceType serviceType,
        IncidentStatus status,
        double latitude,
        double longitude,
        double radiusKm,
        int reportCount,
        Instant firstReportedAt,
        Instant lastReportedAt,
        Instant confirmedAt,
        Instant resolvedAt,
        String resolutionNote) {

    public static IncidentView from(Incident i) {
        return new IncidentView(
                i.getId(),
                i.getServiceType(),
                i.getStatus(),
                i.getCentroidLat(),
                i.getCentroidLng(),
                Math.round(i.getRadiusKm() * 100) / 100.0,
                i.getReportCount(),
                i.getFirstReportedAt(),
                i.getLastReportedAt(),
                i.getConfirmedAt(),
                i.getResolvedAt(),
                i.getResolutionNote());
    }
}
