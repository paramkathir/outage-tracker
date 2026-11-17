package io.github.paramkathir.outagetracker.support;

import io.github.paramkathir.outagetracker.core.ServiceType;
import io.github.paramkathir.outagetracker.service.ReportService;
import io.github.paramkathir.outagetracker.web.dto.Requests;
import io.github.paramkathir.outagetracker.web.dto.Responses.ReportResult;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Boots the full application against an in-memory H2 database (Flyway-migrated, PostgreSQL mode)
 * with a controllable clock. Tables are wiped before each test.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestBase.ClockConfig.class)
public abstract class IntegrationTestBase {

    public static final Instant T0 = Instant.parse("2026-06-01T15:00:00Z");

    /** Degrees of latitude per km, for placing points at known distances. */
    protected static final double KM = 1 / 111.195;

    private static final AtomicInteger CLIENTS = new AtomicInteger();

    @Autowired
    protected ReportService reportService;

    @Autowired
    protected MutableClock clock;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetState() {
        jdbc.update("DELETE FROM incident_update");
        jdbc.update("DELETE FROM outage_report");
        jdbc.update("DELETE FROM incident");
        jdbc.update("DELETE FROM subscription");
        clock.set(T0);
    }

    /** Submits a report from a fresh client id (so duplicate suppression never interferes). */
    protected ReportResult report(ServiceType type, double lat, double lng) {
        return reportService.submit(
                new Requests.CreateReport(type, lat, lng, null, null),
                "client-" + CLIENTS.incrementAndGet());
    }

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(T0);
        }
    }
}
