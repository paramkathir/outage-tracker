package io.github.paramkathir.outagetracker.core;

import io.github.paramkathir.outagetracker.core.IncidentClusterer.AttachToIncident;
import io.github.paramkathir.outagetracker.core.IncidentClusterer.CreateIncident;
import io.github.paramkathir.outagetracker.core.IncidentClusterer.Decision;
import io.github.paramkathir.outagetracker.core.IncidentClusterer.LeaveUnclustered;
import io.github.paramkathir.outagetracker.core.IncidentClusterer.OpenIncident;
import io.github.paramkathir.outagetracker.core.IncidentClusterer.PendingReport;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncidentClustererTest {

    private static final double KM_LAT = 1 / 111.32; // degrees of latitude per km

    private final IncidentClusterer clusterer = new IncidentClusterer(
            new ClusterConfig(3.0, 3, 10, Duration.ofMinutes(30), Duration.ofMinutes(45)));

    private static GeoPoint north(GeoPoint p, double km) {
        return GeoPoint.of(p.latitude() + km * KM_LAT, p.longitude());
    }

    private static final GeoPoint PLANO = GeoPoint.of(33.0198, -96.6989);

    @Test
    void lonelyReportStaysUnclustered() {
        Decision d = clusterer.decide(PLANO, List.of(), List.of());
        assertInstanceOf(LeaveUnclustered.class, d);
    }

    @Test
    void oneNeighbourIsNotEnoughToOpenAnIncident() {
        Decision d = clusterer.decide(PLANO, List.of(), List.of(new PendingReport(1, north(PLANO, 0.5))));
        assertInstanceOf(LeaveUnclustered.class, d);
    }

    @Test
    void reachingMinReportsOpensIncidentWithAllNeighbours() {
        List<PendingReport> pending = List.of(
                new PendingReport(7, north(PLANO, 1.0)),
                new PendingReport(3, north(PLANO, -1.0)));

        Decision d = clusterer.decide(PLANO, List.of(), pending);

        CreateIncident create = assertInstanceOf(CreateIncident.class, d);
        assertEquals(List.of(3L, 7L), create.existingReportIds(), "ids are sorted for determinism");
        assertEquals(3, create.totalReports());
        assertEquals(PLANO.latitude(), create.centroid().latitude(), 1e-9);
        assertEquals(1.0, create.radiusKm(), 0.01);
    }

    @Test
    void neighboursOutsideRadiusAreIgnored() {
        List<PendingReport> pending = List.of(
                new PendingReport(1, north(PLANO, 1.0)),
                new PendingReport(2, north(PLANO, 3.5)),   // just outside 3 km
                new PendingReport(3, north(PLANO, 25)));   // other side of town

        assertInstanceOf(LeaveUnclustered.class, clusterer.decide(PLANO, List.of(), pending));
    }

    @Test
    void attachesToOpenIncidentWithinRadius() {
        OpenIncident incident = new OpenIncident(42, north(PLANO, 2.0));
        Decision d = clusterer.decide(PLANO, List.of(incident), List.of());

        AttachToIncident attach = assertInstanceOf(AttachToIncident.class, d);
        assertEquals(42, attach.incidentId());
        assertEquals(2.0, attach.distanceKm(), 0.01);
    }

    @Test
    void attachesToNearestWhenSeveralIncidentsAreInRange() {
        List<OpenIncident> open = List.of(
                new OpenIncident(1, north(PLANO, 2.5)),
                new OpenIncident(2, north(PLANO, -0.8)),
                new OpenIncident(3, north(PLANO, 1.9)));

        AttachToIncident attach = assertInstanceOf(AttachToIncident.class, clusterer.decide(PLANO, open, List.of()));
        assertEquals(2, attach.incidentId());
    }

    @Test
    void joiningAnIncidentTakesPriorityOverOpeningANewOne() {
        List<OpenIncident> open = List.of(new OpenIncident(9, north(PLANO, 2.9)));
        List<PendingReport> pending = List.of(
                new PendingReport(1, north(PLANO, -0.2)),
                new PendingReport(2, north(PLANO, -0.3)));

        assertInstanceOf(AttachToIncident.class, clusterer.decide(PLANO, open, pending));
    }

    @Test
    void farAwayIncidentIsNotJoined() {
        List<OpenIncident> open = List.of(new OpenIncident(1, north(PLANO, 10)));
        assertInstanceOf(LeaveUnclustered.class, clusterer.decide(PLANO, open, List.of()));
    }

    @Test
    void statusPromotesAtConfirmThresholdAndResolvedIsFinal() {
        assertEquals(IncidentStatus.SUSPECTED, clusterer.statusFor(IncidentStatus.SUSPECTED, 9));
        assertEquals(IncidentStatus.CONFIRMED, clusterer.statusFor(IncidentStatus.SUSPECTED, 10));
        assertEquals(IncidentStatus.CONFIRMED, clusterer.statusFor(IncidentStatus.CONFIRMED, 50));
        assertEquals(IncidentStatus.RESOLVED, clusterer.statusFor(IncidentStatus.RESOLVED, 50));
    }

    @Test
    void staleAfterQuietPeriod() {
        Instant last = Instant.parse("2026-01-01T12:00:00Z");
        assertFalse(clusterer.isStale(last, last.plus(Duration.ofMinutes(44))));
        assertTrue(clusterer.isStale(last, last.plus(Duration.ofMinutes(45))));
    }

    @Test
    void configRejectsNonsense() {
        assertThrows(IllegalArgumentException.class,
                () -> new ClusterConfig(0, 3, 10, Duration.ofMinutes(1), Duration.ofMinutes(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new ClusterConfig(3, 1, 10, Duration.ofMinutes(1), Duration.ofMinutes(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new ClusterConfig(3, 5, 4, Duration.ofMinutes(1), Duration.ofMinutes(1)));
    }
}
