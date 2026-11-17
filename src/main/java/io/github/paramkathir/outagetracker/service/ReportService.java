package io.github.paramkathir.outagetracker.service;

import io.github.paramkathir.outagetracker.config.TrackerProperties;
import io.github.paramkathir.outagetracker.core.ClusterConfig;
import io.github.paramkathir.outagetracker.core.Geo;
import io.github.paramkathir.outagetracker.core.GeoPoint;
import io.github.paramkathir.outagetracker.core.IncidentClusterer;
import io.github.paramkathir.outagetracker.core.IncidentClusterer.AttachToIncident;
import io.github.paramkathir.outagetracker.core.IncidentClusterer.CreateIncident;
import io.github.paramkathir.outagetracker.core.IncidentClusterer.Decision;
import io.github.paramkathir.outagetracker.core.IncidentClusterer.LeaveUnclustered;
import io.github.paramkathir.outagetracker.core.IncidentStatus;
import io.github.paramkathir.outagetracker.core.ServiceType;
import io.github.paramkathir.outagetracker.domain.Incident;
import io.github.paramkathir.outagetracker.domain.OutageReport;
import io.github.paramkathir.outagetracker.repository.IncidentRepository;
import io.github.paramkathir.outagetracker.repository.OutageReportRepository;
import io.github.paramkathir.outagetracker.service.Exceptions.DuplicateReportException;
import io.github.paramkathir.outagetracker.web.dto.IncidentView;
import io.github.paramkathir.outagetracker.web.dto.ReportView;
import io.github.paramkathir.outagetracker.web.dto.Requests;
import io.github.paramkathir.outagetracker.web.dto.Responses.ReportResult;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Accepts outage reports and runs them through the clustering pipeline.
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final OutageReportRepository reports;
    private final IncidentRepository incidents;
    private final IncidentClusterer clusterer;
    private final IncidentTimeline timeline;
    private final ServiceTypeLocks locks;
    private final TransactionTemplate tx;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final TrackerProperties properties;
    private final MeterRegistry meters;
    private final Timer submitTimer;

    public ReportService(OutageReportRepository reports,
                         IncidentRepository incidents,
                         IncidentClusterer clusterer,
                         IncidentTimeline timeline,
                         ServiceTypeLocks locks,
                         PlatformTransactionManager transactionManager,
                         ApplicationEventPublisher events,
                         Clock clock,
                         TrackerProperties properties,
                         MeterRegistry meters) {
        this.reports = reports;
        this.incidents = incidents;
        this.clusterer = clusterer;
        this.timeline = timeline;
        this.locks = locks;
        this.tx = new TransactionTemplate(transactionManager);
        this.events = events;
        this.clock = clock;
        this.properties = properties;
        this.meters = meters;
        this.submitTimer = Timer.builder("outage.report.submit")
                .description("Time to persist and cluster one report")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meters);
    }

    /**
     * Persists a report and clusters it.
     *
     * @param clientId an identifier for the submitter (the client IP); only its hash is stored
     */
    public ReportResult submit(Requests.CreateReport request, String clientId) {
        return submitAt(request, clientId, clock.instant());
    }

    /**
     * Like {@link #submit} but with an explicit report time. Used to replay history (the demo
     * seed backfills the last 24 hours); reports must be replayed in chronological order.
     */
    public ReportResult submitAt(Requests.CreateReport request, String clientId, Instant at) {
        ServiceType type = request.serviceType();
        Timer.Sample sample = Timer.start(meters);
        try {
            ReportResult result = locks.withLock(type, () -> tx.execute(status -> doSubmit(request, clientId, at)));
            meters.counter("outage.reports.submitted", "service", type.name()).increment();
            return result;
        } finally {
            sample.stop(submitTimer);
        }
    }

    @Transactional(readOnly = true)
    public List<ReportView> recent(int limit) {
        int size = Math.max(1, Math.min(limit, 200));
        return reports.findAllByOrderByReportedAtDesc(PageRequest.of(0, size))
                .map(ReportView::from)
                .getContent();
    }

    private ReportResult doSubmit(Requests.CreateReport request, String clientId, Instant now) {
        ServiceType type = request.serviceType();
        String clientHash = sha256(clientId);
        rejectIfDuplicate(clientHash, type, now);

        GeoPoint location = GeoPoint.of(request.latitude(), request.longitude());
        ClusterConfig cfg = clusterer.config();

        // Gather candidates *before* saving so the new report is not its own neighbour.
        List<Incident> open = incidents.findByServiceTypeAndStatusIn(type, IncidentStatus.OPEN);
        Geo.BoundingBox box = Geo.boundingBox(location, cfg.joinRadiusKm());
        List<OutageReport> pending = reports.findUnclusteredInBox(
                type, now.minus(cfg.pendingWindow()),
                box.minLat(), box.maxLat(), box.minLng(), box.maxLng());

        Decision decision = clusterer.decide(
                location,
                open.stream().map(i -> new IncidentClusterer.OpenIncident(i.getId(), i.centroid())).toList(),
                pending.stream().map(r -> new IncidentClusterer.PendingReport(r.getId(), r.location())).toList());

        OutageReport report = reports.save(new OutageReport(
                type, location, blankToNull(request.zipCode()), blankToNull(request.description()), now, clientHash));

        Incident incident = switch (decision) {
            case AttachToIncident attach -> attachToIncident(attach, open, report, now);
            case CreateIncident create -> openIncident(create, pending, report, type, now);
            case LeaveUnclustered ignored -> null;
        };

        return new ReportResult(ReportView.from(report), incident == null ? null : IncidentView.from(incident));
    }

    private Incident attachToIncident(AttachToIncident decision, List<Incident> open, OutageReport report, Instant now) {
        Incident incident = open.stream()
                .filter(i -> i.getId() == decision.incidentId())
                .findFirst()
                .orElseThrow();
        incident.absorb(report.location(), now);
        report.assignTo(incident);

        boolean confirmed = applyStatus(incident, now);
        events.publishEvent(new IncidentEvent(
                confirmed ? IncidentEvent.Kind.CONFIRMED : IncidentEvent.Kind.UPDATED, IncidentView.from(incident)));
        return incident;
    }

    private Incident openIncident(CreateIncident decision, List<OutageReport> pending, OutageReport report,
                          ServiceType type, Instant now) {
        Map<Long, OutageReport> pendingById = pending.stream()
                .collect(Collectors.toMap(OutageReport::getId, Function.identity()));
        List<OutageReport> members = decision.existingReportIds().stream().map(pendingById::get).toList();
        Instant first = members.stream().map(OutageReport::getReportedAt).min(Instant::compareTo).orElse(now);

        Incident incident = incidents.save(new Incident(
                type, decision.centroid(), decision.radiusKm(), decision.totalReports(), first, now));
        members.forEach(r -> r.assignTo(incident));
        report.assignTo(incident);
        timeline.opened(incident, first);
        meters.counter("outage.incidents.opened", "service", type.name()).increment();
        log.info("Opened incident {} for {} with {} reports near {}",
                incident.getId(), type, decision.totalReports(), decision.centroid());

        events.publishEvent(new IncidentEvent(IncidentEvent.Kind.OPENED, IncidentView.from(incident)));
        if (applyStatus(incident, now)) {
            events.publishEvent(new IncidentEvent(IncidentEvent.Kind.CONFIRMED, IncidentView.from(incident)));
        }
        return incident;
    }

    /** @return true if this call promoted the incident to CONFIRMED */
    private boolean applyStatus(Incident incident, Instant now) {
        IncidentStatus next = clusterer.statusFor(incident.getStatus(), incident.getReportCount());
        boolean changed = incident.transitionTo(next, now);
        if (changed && next == IncidentStatus.CONFIRMED) {
            timeline.confirmed(incident, now);
            log.info("Incident {} CONFIRMED ({} reports)", incident.getId(), incident.getReportCount());
            return true;
        }
        return false;
    }

    private void rejectIfDuplicate(String clientHash, ServiceType type, Instant now) {
        Duration window = properties.reports().duplicateWindow();
        if (window == null || window.isZero() || window.isNegative()) {
            return;
        }
        if (reports.existsByClientHashAndServiceTypeAndReportedAtAfter(clientHash, type, now.minus(window))) {
            throw new DuplicateReportException(
                    "You already reported a " + type + " outage in the last " + window.toMinutes()
                            + " minutes. Thanks, it's been counted.");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
