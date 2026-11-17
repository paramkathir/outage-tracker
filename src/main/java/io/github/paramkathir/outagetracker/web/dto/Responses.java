package io.github.paramkathir.outagetracker.web.dto;

import io.github.paramkathir.outagetracker.core.ServiceType;

import java.time.Instant;
import java.util.List;

/** Outbound response bodies (other than the entity views). */
public final class Responses {

    private Responses() {
    }

    /** Result of submitting a report: the report, and the incident it landed in (if any). */
    public record ReportResult(ReportView report, IncidentView incident) {
    }

    /**
     * @param updates  timeline entries, newest first
     * @param activity reports per 10 minutes over the last hour, oldest first (6 values)
     */
    public record IncidentDetail(IncidentView incident, List<ReportView> recentReports, List<UpdateView> updates,
                                 List<Integer> activity) {
    }

    public record ServiceAtLocation(ServiceType serviceType, HealthState state, Long incidentId) {
    }

    /**
     * Everything the public status page needs for one place.
     *
     * @param zip           the ZIP looked up, or null when queried by coordinates
     * @param place         human-readable place name, or null
     * @param affecting     open incidents whose area covers this point, most severe first
     * @param primary       details of the most severe of those, or null when none
     * @param hourlyReports reports of any service within 3 km, per hour over the last 24 hours, oldest first
     */
    public record LocationStatus(
            String zip,
            String place,
            double latitude,
            double longitude,
            HealthState overall,
            List<ServiceAtLocation> services,
            List<IncidentView> affecting,
            IncidentDetail primary,
            List<Integer> hourlyReports,
            Instant generatedAt) {
    }

    public record ZipView(String zip, String place, double latitude, double longitude) {
    }

    public enum HealthState { OPERATIONAL, DEGRADED, OUTAGE }

    public record ServiceStatus(
            ServiceType serviceType,
            HealthState state,
            long suspectedIncidents,
            long confirmedIncidents,
            long reportsLastHour) {
    }

    public record StatusSummary(HealthState overall, List<ServiceStatus> services, Instant generatedAt) {
    }

    public record SubscriptionCreated(long id, String email, double radiusKm, ServiceType serviceType,
                                      String unsubscribeToken) {
    }
}
