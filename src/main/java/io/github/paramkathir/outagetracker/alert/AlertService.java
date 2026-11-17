package io.github.paramkathir.outagetracker.alert;

import io.github.paramkathir.outagetracker.core.Geo;
import io.github.paramkathir.outagetracker.core.GeoPoint;
import io.github.paramkathir.outagetracker.domain.Subscription;
import io.github.paramkathir.outagetracker.repository.SubscriptionRepository;
import io.github.paramkathir.outagetracker.service.IncidentEvent;
import io.github.paramkathir.outagetracker.web.dto.IncidentView;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * Fans incident CONFIRMED / RESOLVED events out to nearby subscribers.
 *
 * <p>Runs asynchronously after the transaction commits, so a slow webhook can never hold up
 * report submission, and subscribers are never told about an incident that was rolled back.</p>
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final SubscriptionRepository subscriptions;
    private final List<AlertChannel> channels;
    private final MeterRegistry meters;

    public AlertService(SubscriptionRepository subscriptions, List<AlertChannel> channels, MeterRegistry meters) {
        this.subscriptions = subscriptions;
        this.channels = channels;
        this.meters = meters;
    }

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onIncidentEvent(IncidentEvent event) {
        dispatch(event);
    }

    /**
     * Sends alerts for one event.
     *
     * @return number of alerts successfully delivered
     */
    public int dispatch(IncidentEvent event) {
        if (event.kind() != IncidentEvent.Kind.CONFIRMED && event.kind() != IncidentEvent.Kind.RESOLVED) {
            return 0;
        }
        IncidentView incident = event.incident();
        GeoPoint centre = GeoPoint.of(incident.latitude(), incident.longitude());

        int delivered = 0;
        for (Subscription subscription : subscriptions.findCovering(incident.serviceType())) {
            double distance = Geo.distanceKm(subscription.location(), centre);
            // Alert if the subscriber's circle overlaps the incident's footprint.
            if (distance > subscription.getRadiusKm() + incident.radiusKm()) {
                continue;
            }
            Alert alert = Alert.of(event.kind(), incident, distance);
            for (AlertChannel channel : channels) {
                if (!channel.supports(subscription)) {
                    continue;
                }
                try {
                    channel.send(subscription, alert);
                    delivered++;
                    meters.counter("outage.alerts.sent", "channel", channel.name()).increment();
                } catch (Exception e) {
                    meters.counter("outage.alerts.failed", "channel", channel.name()).increment();
                    log.warn("Alert via {} to subscription {} failed: {}",
                            channel.name(), subscription.getId(), e.getMessage());
                }
            }
        }
        return delivered;
    }
}
