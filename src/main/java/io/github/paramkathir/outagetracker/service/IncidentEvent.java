package io.github.paramkathir.outagetracker.service;

import io.github.paramkathir.outagetracker.web.dto.IncidentView;

/**
 * Published inside the transaction that changed an incident; listeners use
 * {@code @TransactionalEventListener} so they only see committed state.
 */
public record IncidentEvent(Kind kind, IncidentView incident) {

    public enum Kind {
        OPENED,
        UPDATED,
        CONFIRMED,
        RESOLVED
    }
}
