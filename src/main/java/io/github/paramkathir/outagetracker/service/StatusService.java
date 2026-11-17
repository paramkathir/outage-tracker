package io.github.paramkathir.outagetracker.service;

import io.github.paramkathir.outagetracker.core.IncidentStatus;
import io.github.paramkathir.outagetracker.core.ServiceType;
import io.github.paramkathir.outagetracker.repository.IncidentRepository;
import io.github.paramkathir.outagetracker.repository.OutageReportRepository;
import io.github.paramkathir.outagetracker.web.dto.Responses.HealthState;
import io.github.paramkathir.outagetracker.web.dto.Responses.ServiceStatus;
import io.github.paramkathir.outagetracker.web.dto.Responses.StatusSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Builds the at-a-glance status board. */
@Service
public class StatusService {

    private final IncidentRepository incidents;
    private final OutageReportRepository reports;
    private final Clock clock;

    public StatusService(IncidentRepository incidents, OutageReportRepository reports, Clock clock) {
        this.incidents = incidents;
        this.reports = reports;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public StatusSummary summary() {
        Instant now = clock.instant();
        Instant hourAgo = now.minus(Duration.ofHours(1));
        List<ServiceStatus> services = new ArrayList<>();
        HealthState overall = HealthState.OPERATIONAL;

        for (ServiceType type : ServiceType.values()) {
            long suspected = incidents.countByServiceTypeAndStatus(type, IncidentStatus.SUSPECTED);
            long confirmed = incidents.countByServiceTypeAndStatus(type, IncidentStatus.CONFIRMED);
            long lastHour = reports.countByServiceTypeAndReportedAtAfter(type, hourAgo);
            HealthState state = confirmed > 0 ? HealthState.OUTAGE
                    : suspected > 0 ? HealthState.DEGRADED
                    : HealthState.OPERATIONAL;
            if (state.ordinal() > overall.ordinal()) {
                overall = state;
            }
            services.add(new ServiceStatus(type, state, suspected, confirmed, lastHour));
        }
        return new StatusSummary(overall, services, now);
    }
}
