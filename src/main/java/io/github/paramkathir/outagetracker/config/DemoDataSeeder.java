package io.github.paramkathir.outagetracker.config;

import io.github.paramkathir.outagetracker.core.ServiceType;
import io.github.paramkathir.outagetracker.repository.OutageReportRepository;
import io.github.paramkathir.outagetracker.service.IncidentService;
import io.github.paramkathir.outagetracker.service.ReportService;
import io.github.paramkathir.outagetracker.service.SubscriptionService;
import io.github.paramkathir.outagetracker.web.dto.Requests;
import io.github.paramkathir.outagetracker.web.dto.Responses.ReportResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Fills an empty database with a realistic day of activity around Dallas–Fort Worth: a
 * trickle of background reports over the last 24 hours, then four outages building up over
 * the last 40 minutes. Everything is replayed in time order through {@link ReportService},
 * so the real clustering pipeline produces the incidents and their timelines.
 */
@Component
@ConditionalOnProperty(name = "tracker.demo.seed-enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private record Hotspot(ServiceType type, double lat, double lng, String zip, int reports,
                           int startMinutesAgo, int endMinutesAgo, double spreadKm, List<String> notes) {
    }

    private record SeedReport(Instant at, ServiceType type, double lat, double lng, String zip, String note,
                              boolean plano) {
    }

    private static final List<Hotspot> HOTSPOTS = List.of(
            new Hotspot(ServiceType.INTERNET, 33.0198, -96.6989, "75074", 14, 40, 2, 1.0,
                    List.of("Modem light blinking orange", "Wi-Fi connected but nothing loads", "Whole street is out")),
            new Hotspot(ServiceType.TV, 32.9483, -96.7299, "75080", 6, 34, 7, 1.2,
                    List.of("No signal on any channel", "Guide won't load")),
            new Hotspot(ServiceType.MOBILE, 33.1507, -96.8236, "75034", 4, 30, 7, 0.8,
                    List.of("No bars near the stadium", "Calls dropping")),
            new Hotspot(ServiceType.INTERNET, 32.7555, -97.3308, "76102", 5, 31, 7, 1.0,
                    List.of("Wi-Fi up but no internet")),
            new Hotspot(ServiceType.PHONE, 32.7767, -96.7970, "75201", 2, 22, 10, 0.5,
                    List.of("No dial tone")));

    private final ReportService reportService;
    private final IncidentService incidentService;
    private final SubscriptionService subscriptionService;
    private final OutageReportRepository reports;
    private final Clock clock;
    private final Random random = new Random(42);

    public DemoDataSeeder(ReportService reportService, IncidentService incidentService,
                          SubscriptionService subscriptionService, OutageReportRepository reports, Clock clock) {
        this.reportService = reportService;
        this.incidentService = incidentService;
        this.subscriptionService = subscriptionService;
        this.reports = reports;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (reports.count() > 0) {
            log.info("Database already has data; skipping demo seed");
            return;
        }
        Instant now = clock.instant();

        // A subscriber in Plano, so you can watch an alert fire in the logs as the seed runs.
        subscriptionService.subscribe(new Requests.CreateSubscription(
                "demo.user@example.com", 33.0250, -96.7050, 5.0, null, null));

        List<SeedReport> plan = new ArrayList<>();
        plan.addAll(background(now));
        for (Hotspot h : HOTSPOTS) {
            plan.addAll(outage(h, now));
        }
        plan.sort(Comparator.comparing(SeedReport::at));

        Long planoIncident = null;
        int client = 0;
        for (SeedReport r : plan) {
            ReportResult result = reportService.submitAt(
                    new Requests.CreateReport(r.type(), r.lat(), r.lng(), r.zip(), r.note()), "demo-" + client++, r.at());
            if (r.plano() && result.incident() != null) {
                planoIncident = result.incident().id();
            }
        }

        if (planoIncident != null) {
            incidentService.postUpdate(planoIncident, "Technician on the way",
                    "We found a damaged line feeding the area. A technician has been dispatched to repair it.");
        }
        log.info("Demo data seeded: {} reports", reports.count());
    }

    /**
     * Isolated reports scattered over the metro area and over the last 24 hours (ending well
     * before the outages start, so they never fall inside an outage's 30-minute window).
     */
    private List<SeedReport> background(Instant now) {
        List<SeedReport> out = new ArrayList<>();
        ServiceType[] types = ServiceType.values();
        for (int i = 0; i < 40; i++) {
            Instant at = now.minus(Duration.ofMinutes(60 + random.nextInt(23 * 60)));
            double lat = 32.65 + random.nextDouble() * 0.6;
            double lng = -97.40 + random.nextDouble() * 0.85;
            out.add(new SeedReport(at, types[random.nextInt(types.length)], lat, lng, null, "Spotty service", false));
        }
        // A slow trickle near each hotspot so the "reports near you" chart has a realistic baseline.
        for (Hotspot h : HOTSPOTS) {
            for (int i = 0; i < 5; i++) {
                Instant at = now.minus(Duration.ofMinutes(70 + i * 250L + random.nextInt(120)));
                double[] p = jitter(h.lat(), h.lng(), 1.5);
                out.add(new SeedReport(at, types[random.nextInt(types.length)], p[0], p[1], h.zip(), null, false));
            }
        }
        return out;
    }

    /** Reports building up at one hotspot between {@code startMinutesAgo} and {@code endMinutesAgo}. */
    private List<SeedReport> outage(Hotspot h, Instant now) {
        List<SeedReport> out = new ArrayList<>();
        double span = h.startMinutesAgo() - h.endMinutesAgo();
        boolean plano = "75074".equals(h.zip());
        for (int i = 0; i < h.reports(); i++) {
            // Reports accelerate as more people notice, like a real outage.
            double fraction = h.reports() == 1 ? 1 : Math.sqrt((double) i / (h.reports() - 1));
            long secondsAgo = Math.round((h.startMinutesAgo() - span * fraction) * 60);
            double[] p = jitter(h.lat(), h.lng(), h.spreadKm());
            String note = i % 2 == 0 ? h.notes().get((i / 2) % h.notes().size()) : null;
            out.add(new SeedReport(now.minusSeconds(secondsAgo), h.type(), p[0], p[1], h.zip(), note, plano));
        }
        return out;
    }

    private double[] jitter(double lat, double lng, double spreadKm) {
        double degPerKm = 1 / 111.195;
        double dLat = random.nextGaussian() * spreadKm * 0.4 * degPerKm;
        double dLng = random.nextGaussian() * spreadKm * 0.4 * degPerKm / Math.cos(Math.toRadians(lat));
        return new double[]{lat + dLat, lng + dLng};
    }
}
