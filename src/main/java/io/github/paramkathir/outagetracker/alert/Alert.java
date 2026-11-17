package io.github.paramkathir.outagetracker.alert;

import io.github.paramkathir.outagetracker.service.IncidentEvent;
import io.github.paramkathir.outagetracker.web.dto.IncidentView;

/** A notification about an incident, addressed to one subscriber. */
public record Alert(IncidentEvent.Kind kind, IncidentView incident, double distanceKm, String message) {

    public static Alert of(IncidentEvent.Kind kind, IncidentView incident, double distanceKm) {
        String what = incident.serviceType().name().toLowerCase();
        String message = switch (kind) {
            case CONFIRMED -> String.format(
                    "Confirmed %s outage about %.1f km from you (%d reports). We're on it.",
                    what, distanceKm, incident.reportCount());
            case RESOLVED -> String.format(
                    "The %s outage about %.1f km from you has been resolved.", what, distanceKm);
            default -> String.format("Update on %s incident #%d.", what, incident.id());
        };
        return new Alert(kind, incident, distanceKm, message);
    }
}
