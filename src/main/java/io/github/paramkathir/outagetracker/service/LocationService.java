package io.github.paramkathir.outagetracker.service;

import io.github.paramkathir.outagetracker.core.Buckets;
import io.github.paramkathir.outagetracker.core.Geo;
import io.github.paramkathir.outagetracker.core.GeoPoint;
import io.github.paramkathir.outagetracker.core.IncidentStatus;
import io.github.paramkathir.outagetracker.core.ServiceType;
import io.github.paramkathir.outagetracker.domain.Incident;
import io.github.paramkathir.outagetracker.repository.IncidentRepository;
import io.github.paramkathir.outagetracker.repository.OutageReportRepository;
import io.github.paramkathir.outagetracker.service.Exceptions.NotFoundException;
import io.github.paramkathir.outagetracker.web.dto.IncidentView;
import io.github.paramkathir.outagetracker.web.dto.Responses.HealthState;
import io.github.paramkathir.outagetracker.web.dto.Responses.IncidentDetail;
import io.github.paramkathir.outagetracker.web.dto.Responses.LocationStatus;
import io.github.paramkathir.outagetracker.web.dto.Responses.ServiceAtLocation;
import io.github.paramkathir.outagetracker.web.dto.Responses.ZipView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Answers "is anything down where I am?" for the public status page.
 */
@Service
public class LocationService {

    /**
     * An incident covers a point if the point is within its radius plus this buffer. Incident
     * radii are measured from where reports were made, so neighbours just outside that circle
     * are very likely on the same node.
     */
    static final double COVERAGE_BUFFER_KM = 1.0;

    static final double NEARBY_REPORTS_RADIUS_KM = 3.0;

    private static final Comparator<Incident> MOST_SEVERE_FIRST = Comparator
            .comparing((Incident i) -> i.getStatus() == IncidentStatus.CONFIRMED ? 0 : 1)
            .thenComparing(Incident::getReportCount, Comparator.reverseOrder());

    private final IncidentRepository incidents;
    private final OutageReportRepository reports;
    private final IncidentService incidentService;
    private final ZipDirectory zips;
    private final Clock clock;

    public LocationService(IncidentRepository incidents, OutageReportRepository reports,
                           IncidentService incidentService, ZipDirectory zips, Clock clock) {
        this.incidents = incidents;
        this.reports = reports;
        this.incidentService = incidentService;
        this.zips = zips;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public LocationStatus forZip(String zip) {
        ZipDirectory.ZipEntry entry = zips.find(zip)
                .orElseThrow(() -> new NotFoundException(
                        "We don't have ZIP " + zip + " in the demo lookup yet. Try another, or use your location."));
        return statusAt(entry.location(), entry.zip(), entry.place());
    }

    @Transactional(readOnly = true)
    public LocationStatus forPoint(double latitude, double longitude) {
        return statusAt(GeoPoint.of(latitude, longitude), null, null);
    }

    public List<ZipView> supportedZips() {
        return zips.all().stream()
                .map(z -> new ZipView(z.zip(), z.place(), z.location().latitude(), z.location().longitude()))
                .toList();
    }

    private LocationStatus statusAt(GeoPoint point, String zip, String place) {
        Instant now = clock.instant();

        List<Incident> covering = incidents.findByStatusInOrderByLastReportedAtDesc(IncidentStatus.OPEN).stream()
                .filter(i -> Geo.distanceKm(point, i.centroid()) <= i.getRadiusKm() + COVERAGE_BUFFER_KM)
                .sorted(MOST_SEVERE_FIRST)
                .toList();

        List<ServiceAtLocation> services = new ArrayList<>();
        HealthState overall = HealthState.OPERATIONAL;
        for (ServiceType type : ServiceType.values()) {
            Incident worst = covering.stream().filter(i -> i.getServiceType() == type).findFirst().orElse(null);
            HealthState state = worst == null ? HealthState.OPERATIONAL
                    : worst.getStatus() == IncidentStatus.CONFIRMED ? HealthState.OUTAGE
                    : HealthState.DEGRADED;
            if (state.ordinal() > overall.ordinal()) {
                overall = state;
            }
            services.add(new ServiceAtLocation(type, state, worst == null ? null : worst.getId()));
        }

        IncidentDetail primary = covering.isEmpty() ? null : incidentService.get(covering.get(0).getId(), 10);

        Geo.BoundingBox box = Geo.boundingBox(point, NEARBY_REPORTS_RADIUS_KM);
        List<Instant> times = reports.findReportTimesInBoxSince(
                now.minus(Duration.ofHours(24)), box.minLat(), box.maxLat(), box.minLng(), box.maxLng());
        List<Integer> hourly = Arrays.stream(Buckets.count(times, now, Duration.ofHours(1), 24)).boxed().toList();

        return new LocationStatus(zip, place, point.latitude(), point.longitude(), overall, services,
                covering.stream().map(IncidentView::from).toList(), primary, hourly, now);
    }
}
