package io.github.paramkathir.outagetracker.service;

import io.github.paramkathir.outagetracker.core.Buckets;
import io.github.paramkathir.outagetracker.core.IncidentClusterer;
import io.github.paramkathir.outagetracker.core.IncidentStatus;
import io.github.paramkathir.outagetracker.core.ServiceType;
import io.github.paramkathir.outagetracker.domain.Incident;
import io.github.paramkathir.outagetracker.domain.IncidentUpdate;
import io.github.paramkathir.outagetracker.repository.IncidentRepository;
import io.github.paramkathir.outagetracker.repository.IncidentUpdateRepository;
import io.github.paramkathir.outagetracker.repository.OutageReportRepository;
import io.github.paramkathir.outagetracker.service.Exceptions.InvalidStateException;
import io.github.paramkathir.outagetracker.service.Exceptions.NotFoundException;
import io.github.paramkathir.outagetracker.web.dto.IncidentView;
import io.github.paramkathir.outagetracker.web.dto.ReportView;
import io.github.paramkathir.outagetracker.web.dto.Responses.IncidentDetail;
import io.github.paramkathir.outagetracker.web.dto.UpdateView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Queries and lifecycle management for incidents.
 */
@Service
public class IncidentService {

    private static final Logger log = LoggerFactory.getLogger(IncidentService.class);

    private static final Duration ACTIVITY_BUCKET = Duration.ofMinutes(10);
    private static final int ACTIVITY_BUCKETS = 6;

    public enum Scope { ACTIVE, RESOLVED, ALL }

    private final IncidentRepository incidents;
    private final OutageReportRepository reports;
    private final IncidentUpdateRepository updates;
    private final IncidentTimeline timeline;
    private final IncidentClusterer clusterer;
    private final ServiceTypeLocks locks;
    private final TransactionTemplate tx;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public IncidentService(IncidentRepository incidents,
                           OutageReportRepository reports,
                           IncidentUpdateRepository updates,
                           IncidentTimeline timeline,
                           IncidentClusterer clusterer,
                           ServiceTypeLocks locks,
                           PlatformTransactionManager transactionManager,
                           ApplicationEventPublisher events,
                           Clock clock) {
        this.incidents = incidents;
        this.reports = reports;
        this.updates = updates;
        this.timeline = timeline;
        this.clusterer = clusterer;
        this.locks = locks;
        this.tx = new TransactionTemplate(transactionManager);
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<IncidentView> list(Scope scope, ServiceType serviceType) {
        Set<IncidentStatus> statuses = switch (scope) {
            case ACTIVE -> IncidentStatus.OPEN;
            case RESOLVED -> EnumSet.of(IncidentStatus.RESOLVED);
            case ALL -> EnumSet.allOf(IncidentStatus.class);
        };
        List<Incident> found = serviceType == null
                ? incidents.findByStatusInOrderByLastReportedAtDesc(statuses)
                : incidents.findByStatusInAndServiceTypeOrderByLastReportedAtDesc(statuses, serviceType);
        return found.stream().map(IncidentView::from).toList();
    }

    @Transactional(readOnly = true)
    public IncidentDetail get(long id, int reportLimit) {
        Incident incident = incidents.findById(id)
                .orElseThrow(() -> new NotFoundException("Incident " + id + " not found"));
        int size = Math.max(1, Math.min(reportLimit, 100));
        List<ReportView> recent = reports.findByIncidentIdOrderByReportedAtDesc(id, PageRequest.of(0, size))
                .map(ReportView::from)
                .getContent();
        List<UpdateView> timelineEntries = updates.findByIncidentIdOrderByCreatedAtDescIdDesc(id).stream()
                .map(UpdateView::from)
                .toList();

        Instant now = clock.instant();
        List<Instant> times = reports.findReportTimesForIncidentSince(
                id, now.minus(ACTIVITY_BUCKET.multipliedBy(ACTIVITY_BUCKETS)));
        List<Integer> activity = Arrays.stream(Buckets.count(times, now, ACTIVITY_BUCKET, ACTIVITY_BUCKETS))
                .boxed()
                .toList();

        return new IncidentDetail(IncidentView.from(incident), recent, timelineEntries, activity);
    }

    /** Manually resolves an incident (e.g. a technician fixed the node). */
    public IncidentView resolve(long id, String note) {
        ServiceType type = serviceTypeOf(id);
        return locks.withLock(type, () -> tx.execute(status -> {
            Incident incident = incidents.findById(id).orElseThrow();
            if (!incident.getStatus().isOpen()) {
                throw new InvalidStateException("Incident " + id + " is already resolved");
            }
            String text = note == null || note.isBlank() ? "Resolved by our network team." : note.strip();
            Instant now = clock.instant();
            incident.resolve(now, text);
            timeline.resolved(incident, now, IncidentUpdate.Kind.OPERATOR, text);
            IncidentView view = IncidentView.from(incident);
            events.publishEvent(new IncidentEvent(IncidentEvent.Kind.RESOLVED, view));
            log.info("Incident {} resolved: {}", id, text);
            return view;
        }));
    }

    /** Posts an operator update to an open incident's public timeline. */
    public UpdateView postUpdate(long id, String title, String body) {
        ServiceType type = serviceTypeOf(id);
        return locks.withLock(type, () -> tx.execute(status -> {
            Incident incident = incidents.findById(id).orElseThrow();
            if (!incident.getStatus().isOpen()) {
                throw new InvalidStateException("Incident " + id + " is resolved; updates are closed");
            }
            IncidentUpdate update = timeline.operatorUpdate(incident, title, body, clock.instant());
            events.publishEvent(new IncidentEvent(IncidentEvent.Kind.UPDATED, IncidentView.from(incident)));
            return UpdateView.from(update);
        }));
    }

    /**
     * Auto-resolves incidents that have gone quiet. Runs periodically; each incident is
     * re-checked under the service-type lock in its own transaction so a report arriving
     * mid-sweep is never lost.
     *
     * @return how many incidents were resolved
     */
    @Scheduled(fixedDelayString = "${tracker.clustering.sweep-interval:PT1M}",
            initialDelayString = "${tracker.clustering.sweep-initial-delay:PT30S}")
    public int resolveStaleIncidents() {
        Instant now = clock.instant();
        Instant cutoff = now.minus(clusterer.config().quietPeriod());
        List<Incident> candidates = incidents.findByStatusInAndLastReportedAtLessThanEqual(IncidentStatus.OPEN, cutoff);

        int resolved = 0;
        for (Incident candidate : candidates) {
            long id = candidate.getId();
            Boolean done = locks.withLock(candidate.getServiceType(), () -> tx.execute(status -> {
                Incident incident = incidents.findById(id).orElse(null);
                Instant at = clock.instant();
                if (incident == null || !incident.getStatus().isOpen()
                        || !clusterer.isStale(incident.getLastReportedAt(), at)) {
                    return false;
                }
                String note = "No new reports for " + clusterer.config().quietPeriod().toMinutes()
                        + " minutes, so we've marked this resolved.";
                incident.resolve(at, "Auto-resolved: " + note);
                timeline.resolved(incident, at, IncidentUpdate.Kind.AUTOMATIC, note);
                events.publishEvent(new IncidentEvent(IncidentEvent.Kind.RESOLVED, IncidentView.from(incident)));
                return true;
            }));
            if (Boolean.TRUE.equals(done)) {
                resolved++;
            }
        }
        if (resolved > 0) {
            log.info("Auto-resolved {} quiet incident(s)", resolved);
        }
        return resolved;
    }

    private ServiceType serviceTypeOf(long id) {
        return incidents.findById(id)
                .map(Incident::getServiceType)
                .orElseThrow(() -> new NotFoundException("Incident " + id + " not found"));
    }
}
