package io.github.paramkathir.outagetracker.alert;

import io.github.paramkathir.outagetracker.domain.Subscription;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

/**
 * POSTs a JSON payload to the subscriber's https webhook (e.g. a Slack/Discord/Teams relay).
 */
@Component
public class WebhookAlertChannel implements AlertChannel {

    private final RestClient http;

    public WebhookAlertChannel(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.http = builder.requestFactory(factory).build();
    }

    @Override
    public String name() {
        return "webhook";
    }

    @Override
    public boolean supports(Subscription subscription) {
        return subscription.getWebhookUrl() != null;
    }

    @Override
    public void send(Subscription subscription, Alert alert) {
        Map<String, Object> body = Map.of(
                "event", alert.kind().name(),
                "message", alert.message(),
                "distanceKm", Math.round(alert.distanceKm() * 10) / 10.0,
                "incident", alert.incident());
        http.post()
                .uri(subscription.getWebhookUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }
}
