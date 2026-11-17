package io.github.paramkathir.outagetracker.core;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle of an incident.
 *
 * <pre>
 *   SUSPECTED --(report count reaches confirm threshold)--> CONFIRMED
 *   SUSPECTED | CONFIRMED --(quiet period elapses, or admin resolves)--> RESOLVED
 * </pre>
 */
public enum IncidentStatus {
    SUSPECTED,
    CONFIRMED,
    RESOLVED;

    public static final Set<IncidentStatus> OPEN = EnumSet.of(SUSPECTED, CONFIRMED);

    public boolean isOpen() {
        return this != RESOLVED;
    }
}
