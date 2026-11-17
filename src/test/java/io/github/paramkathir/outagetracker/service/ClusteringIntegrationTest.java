package io.github.paramkathir.outagetracker.service;

import io.github.paramkathir.outagetracker.core.IncidentStatus;
import io.github.paramkathir.outagetracker.core.ServiceType;
import io.github.paramkathir.outagetracker.repository.IncidentRepository;
import io.github.paramkathir.outagetracker.repository.OutageReportRepository;
import io.github.paramkathir.outagetracker.support.IntegrationTestBase;
import io.github.paramkathir.outagetracker.web.dto.IncidentView;
import io.github.paramkathir.outagetracker.web.dto.Responses.ReportResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClusteringIntegrationTest extends IntegrationTestBase {

    private static final double LAT = 33.0198;
    private static final double LNG = -96.6989;

    @Autowired
    IncidentRepository incidents;

    @Autowired
    OutageReportRepository reports;

    @Autowired
    IncidentService incidentService;

    @Test
    void thirdNearbyReportOpensSuspectedIncidentContainingAllThree() {
        assertThat(report(ServiceType.INTERNET, LAT, LNG).incident()).isNull();
        assertThat(report(ServiceType.INTERNET, LAT + 0.5 * KM, LNG).incident()).isNull();

        IncidentView incident = report(ServiceType.INTERNET, LAT - 0.5 * KM, LNG).incident();

        assertThat(incident).isNotNull();
        assertThat(incident.status()).isEqualTo(IncidentStatus.SUSPECTED);
        assertThat(incident.reportCount()).isEqualTo(3);
        assertThat(reports.findAll()).allMatch(r -> r.getIncident() != null);
    }

    @Test
    void incidentIsConfirmedAtThreshold() {
        IncidentView last = null;
        for (int i = 0; i < 10; i++) {
            last = report(ServiceType.TV, LAT + (i % 3) * 0.2 * KM, LNG).incident();
        }
        assertThat(last).isNotNull();
        assertThat(last.reportCount()).isEqualTo(10);
        assertThat(last.status()).isEqualTo(IncidentStatus.CONFIRMED);
        assertThat(last.confirmedAt()).isEqualTo(T0);
        assertThat(incidents.count()).isEqualTo(1);
    }

    @Test
    void differentServiceTypesNeverShareAnIncident() {
        report(ServiceType.INTERNET, LAT, LNG);
        report(ServiceType.TV, LAT, LNG);
        ReportResult third = report(ServiceType.PHONE, LAT, LNG);

        assertThat(third.incident()).isNull();
        assertThat(incidents.count()).isZero();
    }

    @Test
    void distantReportsFormSeparateIncidents() {
        for (int i = 0; i < 3; i++) {
            report(ServiceType.INTERNET, LAT, LNG);
            report(ServiceType.INTERNET, LAT + 20 * KM, LNG);
        }
        assertThat(incidents.count()).isEqualTo(2);
    }

    @Test
    void oldUnclusteredReportsDoNotSeedNewIncidents() {
        report(ServiceType.INTERNET, LAT, LNG);
        report(ServiceType.INTERNET, LAT, LNG);
        clock.advance(Duration.ofMinutes(31)); // past the 30 min pending window

        assertThat(report(ServiceType.INTERNET, LAT, LNG).incident()).isNull();
    }

    @Test
    void quietIncidentAutoResolvesAndLaterReportsStartAFreshOne() {
        long firstId = 0;
        for (int i = 0; i < 3; i++) {
            IncidentView v = report(ServiceType.INTERNET, LAT, LNG).incident();
            if (v != null) {
                firstId = v.id();
            }
        }

        clock.advance(Duration.ofMinutes(44));
        assertThat(incidentService.resolveStaleIncidents()).isZero();

        clock.advance(Duration.ofMinutes(1));
        assertThat(incidentService.resolveStaleIncidents()).isEqualTo(1);
        assertThat(incidents.findById(firstId)).hasValueSatisfying(i -> {
            assertThat(i.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
            assertThat(i.getResolutionNote()).startsWith("Auto-resolved");
        });

        // New activity in the same spot must not reopen the resolved incident.
        report(ServiceType.INTERNET, LAT, LNG);
        report(ServiceType.INTERNET, LAT, LNG);
        IncidentView fresh = report(ServiceType.INTERNET, LAT, LNG).incident();
        assertThat(fresh).isNotNull();
        assertThat(fresh.id()).isNotEqualTo(firstId);
    }

    @Test
    void resolvingTwiceIsRejected() {
        IncidentView v = null;
        for (int i = 0; i < 3; i++) {
            v = report(ServiceType.MOBILE, LAT, LNG).incident();
        }
        long id = v.id();
        assertThat(incidentService.resolve(id, "Fiber splice repaired").status()).isEqualTo(IncidentStatus.RESOLVED);
        assertThatThrownBy(() -> incidentService.resolve(id, null))
                .isInstanceOf(Exceptions.InvalidStateException.class);
    }

    @Test
    void timelineRecordsLifecycleAndOperatorUpdates() {
        IncidentView v = null;
        for (int i = 0; i < 10; i++) {
            clock.advance(Duration.ofMinutes(1));
            v = report(ServiceType.INTERNET, LAT, LNG).incident();
        }
        long id = v.id();
        incidentService.postUpdate(id, null, "Crew is on site.");
        clock.advance(Duration.ofMinutes(1));
        incidentService.resolve(id, "Line spliced.");

        var detail = incidentService.get(id, 5);
        assertThat(detail.updates()).extracting(u -> u.title())
                .containsExactly("Resolved", "Update from our team", "Outage confirmed", "First reports");
        assertThat(detail.updates().get(0).body()).isEqualTo("Line spliced.");
        // The "first reports" entry is backdated to the first report, not to when the incident opened.
        assertThat(detail.updates().get(3).createdAt()).isEqualTo(T0.plus(Duration.ofMinutes(1)));
        assertThat(detail.activity()).hasSize(6);
        assertThat(detail.activity().stream().mapToInt(Integer::intValue).sum()).isEqualTo(10);
    }

    @Test
    void updatesAreRejectedOnResolvedIncidents() {
        IncidentView v = null;
        for (int i = 0; i < 3; i++) {
            v = report(ServiceType.TV, LAT, LNG).incident();
        }
        long id = v.id();
        incidentService.resolve(id, null);
        assertThatThrownBy(() -> incidentService.postUpdate(id, null, "too late"))
                .isInstanceOf(Exceptions.InvalidStateException.class);
    }

    /**
     * Fires many simultaneous reports at the same spot. Without the per-service-type lock held
     * around the transaction, several threads would each see "not enough neighbours" or each
     * open their own incident. With it, every report lands in exactly one incident.
     */
    @Test
    void concurrentReportsAtSameSpotProduceExactlyOneIncident() throws Exception {
        int threads = 40;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ReportResult>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return report(ServiceType.INTERNET, LAT, LNG);
            }));
        }
        start.countDown();
        for (Future<ReportResult> f : futures) {
            f.get();
        }
        pool.shutdown();

        assertThat(incidents.count()).isEqualTo(1);
        var incident = incidents.findAll().get(0);
        assertThat(incident.getReportCount()).isEqualTo(threads);
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.CONFIRMED);
        assertThat(reports.findAll()).hasSize(threads).allMatch(r -> r.getIncident() != null);
    }
}
