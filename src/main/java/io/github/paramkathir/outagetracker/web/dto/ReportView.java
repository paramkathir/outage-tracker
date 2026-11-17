package io.github.paramkathir.outagetracker.web.dto;

import io.github.paramkathir.outagetracker.core.ServiceType;
import io.github.paramkathir.outagetracker.domain.OutageReport;

import java.time.Instant;

public record ReportView(
        long id,
        ServiceType serviceType,
        double latitude,
        double longitude,
        String zipCode,
        String description,
        Instant reportedAt,
        Long incidentId) {

    public static ReportView from(OutageReport r) {
        return new ReportView(
                r.getId(),
                r.getServiceType(),
                r.getLatitude(),
                r.getLongitude(),
                r.getZipCode(),
                r.getDescription(),
                r.getReportedAt(),
                // getId() on a lazy proxy does not trigger a fetch
                r.getIncident() == null ? null : r.getIncident().getId());
    }
}
