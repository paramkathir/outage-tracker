package io.github.paramkathir.outagetracker.web;

import io.github.paramkathir.outagetracker.core.ServiceType;
import io.github.paramkathir.outagetracker.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "tracker.rate-limit.enabled=true",
        "tracker.rate-limit.capacity=3",
        "tracker.rate-limit.refill-per-second=0.001",
        "tracker.reports.duplicate-window=15m"
})
class ApiTest extends IntegrationTestBase {

    @Autowired
    MockMvc mvc;

    private static RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private static MockHttpServletRequestBuilder postJson(String path, String json) {
        return post(path).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private static String reportJson(String service) {
        return """
                {"serviceType":"%s","latitude":32.95,"longitude":-96.73,"zipCode":"75080"}
                """.formatted(service);
    }

    @Test
    void submitReportReturns201WithLocation() throws Exception {
        mvc.perform(postJson("/api/reports", reportJson("INTERNET")).with(from("10.0.0.1")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.report.serviceType").value("INTERNET"))
                .andExpect(jsonPath("$.report.zipCode").value("75080"))
                .andExpect(jsonPath("$.incident").doesNotExist());
    }

    @Test
    void invalidReportReturnsFieldErrors() throws Exception {
        mvc.perform(postJson("/api/reports",
                        """
                        {"serviceType":"INTERNET","latitude":120,"longitude":-96.7,"zipCode":"abc"}
                        """).with(from("10.0.0.2")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.latitude").exists())
                .andExpect(jsonPath("$.errors.zipCode").value("must be a 5-digit ZIP code"));
    }

    @Test
    void unknownServiceTypeIsBadRequest() throws Exception {
        mvc.perform(postJson("/api/reports", reportJson("CARRIER_PIGEON")).with(from("10.0.0.3")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sameClientReportingSameServiceTwiceGets409() throws Exception {
        mvc.perform(postJson("/api/reports", reportJson("TV")).with(from("10.0.0.4")))
                .andExpect(status().isCreated());
        mvc.perform(postJson("/api/reports", reportJson("TV")).with(from("10.0.0.4")))
                .andExpect(status().isConflict());
    }

    @Test
    void exceedingRateLimitGets429WithRetryAfter() throws Exception {
        String ip = "10.0.0.5";
        for (String service : new String[]{"INTERNET", "TV", "PHONE"}) {
            mvc.perform(postJson("/api/reports", reportJson(service)).with(from(ip)))
                    .andExpect(status().isCreated());
        }
        mvc.perform(postJson("/api/reports", reportJson("MOBILE")).with(from(ip)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));

        // A different client is unaffected.
        mvc.perform(postJson("/api/reports", reportJson("MOBILE")).with(from("10.0.0.6")))
                .andExpect(status().isCreated());
    }

    @Test
    void adminEndpointRequiresKey() throws Exception {
        long id = openIncident();

        mvc.perform(post("/api/admin/incidents/{id}/resolve", id))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/incidents/{id}/resolve", id).header("X-Admin-Key", "wrong"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/admin/incidents/{id}/resolve", id)
                        .header("X-Admin-Key", "test-admin-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Node 14 rebooted\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolutionNote").value("Node 14 rebooted"));
    }

    @Test
    void incidentEndpointsListAndFetch() throws Exception {
        long id = openIncident();

        mvc.perform(get("/api/incidents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].status").value("SUSPECTED"));

        mvc.perform(get("/api/incidents/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incident.reportCount").value(3))
                .andExpect(jsonPath("$.recentReports.length()").value(3));

        mvc.perform(get("/api/incidents/{id}", 999_999))
                .andExpect(status().isNotFound());
    }

    @Test
    void statusReflectsActiveIncidents() throws Exception {
        mvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overall").value("OPERATIONAL"));

        openIncident();

        mvc.perform(get("/api/status"))
                .andExpect(jsonPath("$.overall").value("DEGRADED"))
                .andExpect(jsonPath("$.services[0].serviceType").value("INTERNET"))
                .andExpect(jsonPath("$.services[0].suspectedIncidents").value(1));
    }

    @Test
    void subscribeValidatesWebhookScheme() throws Exception {
        mvc.perform(postJson("/api/subscriptions", """
                        {"email":"a@b.com","latitude":32.9,"longitude":-96.7,"radiusKm":5,
                         "webhookUrl":"http://insecure.example.com"}
                        """).with(from("10.0.0.7")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.webhookUrl").exists());

        mvc.perform(postJson("/api/subscriptions", """
                        {"email":"A@B.com","latitude":32.9,"longitude":-96.7,"radiusKm":5}
                        """).with(from("10.0.0.8")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("a@b.com"))
                .andExpect(jsonPath("$.unsubscribeToken").exists());
    }

    @Test
    void locationStatusReflectsIncidentsCoveringThatZip() throws Exception {
        long id = openIncident(); // at 32.95, -96.73, a few hundred metres from ZIP 75080's point

        mvc.perform(get("/api/locations/75080"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.place").value("Richardson, TX"))
                .andExpect(jsonPath("$.overall").value("DEGRADED"))
                .andExpect(jsonPath("$.services[0].serviceType").value("INTERNET"))
                .andExpect(jsonPath("$.services[0].state").value("DEGRADED"))
                .andExpect(jsonPath("$.services[0].incidentId").value(id))
                .andExpect(jsonPath("$.services[1].state").value("OPERATIONAL"))
                .andExpect(jsonPath("$.primary.incident.id").value(id))
                .andExpect(jsonPath("$.primary.updates[0].title").value("First reports"))
                .andExpect(jsonPath("$.hourlyReports.length()").value(24))
                .andExpect(jsonPath("$.hourlyReports[23]").value(3));

        // Fort Worth is ~40 km away: unaffected.
        mvc.perform(get("/api/locations/76102"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overall").value("OPERATIONAL"))
                .andExpect(jsonPath("$.primary").doesNotExist());

        mvc.perform(get("/api/locations").param("lat", "32.95").param("lng", "-96.73"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overall").value("DEGRADED"));
    }

    @Test
    void unknownOrMalformedZipIsRejected() throws Exception {
        mvc.perform(get("/api/locations/00000")).andExpect(status().isNotFound());
        mvc.perform(get("/api/locations/abc")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/zips"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].zip").exists());
    }

    @Test
    void operatorCanPostTimelineUpdate() throws Exception {
        long id = openIncident();

        mvc.perform(postJson("/api/admin/incidents/" + id + "/updates", "{\"body\":\"Crew dispatched\"}"))
                .andExpect(status().isUnauthorized());

        mvc.perform(postJson("/api/admin/incidents/" + id + "/updates", "{\"body\":\"\"}")
                        .header("X-Admin-Key", "test-admin-key"))
                .andExpect(status().isBadRequest());

        mvc.perform(postJson("/api/admin/incidents/" + id + "/updates",
                        "{\"title\":\"Technician on the way\",\"body\":\"Crew dispatched\"}")
                        .header("X-Admin-Key", "test-admin-key"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("OPERATOR"));

        mvc.perform(get("/api/incidents/{id}", id))
                .andExpect(jsonPath("$.updates[0].title").value("Technician on the way"))
                .andExpect(jsonPath("$.updates[0].body").value("Crew dispatched"))
                .andExpect(jsonPath("$.activity.length()").value(6));
    }

    /** Opens a SUSPECTED INTERNET incident via the service layer (bypassing rate limits). */
    private long openIncident() {
        report(ServiceType.INTERNET, 32.95, -96.73);
        report(ServiceType.INTERNET, 32.95, -96.73);
        return report(ServiceType.INTERNET, 32.95, -96.73).incident().id();
    }
}
