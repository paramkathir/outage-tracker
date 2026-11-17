package io.github.paramkathir.outagetracker.service;

import io.github.paramkathir.outagetracker.core.ServiceType;
import io.github.paramkathir.outagetracker.domain.Incident;
import io.github.paramkathir.outagetracker.domain.IncidentUpdate;
import io.github.paramkathir.outagetracker.repository.IncidentUpdateRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Writes the entries customers see on an incident's timeline. Always called from inside the
 * transaction that changed the incident, so the timeline and the incident never disagree.
 */
@Service
public class IncidentTimeline {

    static final String DEFAULT_OPERATOR_TITLE = "Update from our team";

    private final IncidentUpdateRepository updates;

    public IncidentTimeline(IncidentUpdateRepository updates) {
        this.updates = updates;
    }

    public void opened(Incident incident, Instant firstReportAt) {
        save(incident, IncidentUpdate.Kind.AUTOMATIC, "First reports",
                "Customers nearby started reporting " + noun(incident.getServiceType()) + " problems.",
                firstReportAt);
    }

    public void confirmed(Incident incident, Instant at) {
        save(incident, IncidentUpdate.Kind.AUTOMATIC, "Outage confirmed",
                incident.getReportCount() + " customers in the area reported " + noun(incident.getServiceType())
                        + " problems, so we confirmed the outage.",
                at);
    }

    public void resolved(Incident incident, Instant at, IncidentUpdate.Kind kind, String note) {
        save(incident, kind, "Resolved", note, at);
    }

    public IncidentUpdate operatorUpdate(Incident incident, String title, String body, Instant at) {
        String heading = title == null || title.isBlank() ? DEFAULT_OPERATOR_TITLE : title.strip();
        return save(incident, IncidentUpdate.Kind.OPERATOR, heading, body.strip(), at);
    }

    private IncidentUpdate save(Incident incident, IncidentUpdate.Kind kind, String title, String body, Instant at) {
        return updates.save(new IncidentUpdate(incident.getId(), kind, title, body, at));
    }

    /** How the service reads in a sentence: "internet problems", "home phone problems". */
    static String noun(ServiceType type) {
        return switch (type) {
            case INTERNET -> "internet";
            case TV -> "TV";
            case PHONE -> "home phone";
            case MOBILE -> "mobile";
        };
    }
}
