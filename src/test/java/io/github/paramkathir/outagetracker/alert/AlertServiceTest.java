package io.github.paramkathir.outagetracker.alert;

import io.github.paramkathir.outagetracker.core.GeoPoint;
import io.github.paramkathir.outagetracker.core.IncidentStatus;
import io.github.paramkathir.outagetracker.core.ServiceType;
import io.github.paramkathir.outagetracker.domain.Subscription;
import io.github.paramkathir.outagetracker.repository.SubscriptionRepository;
import io.github.paramkathir.outagetracker.service.IncidentEvent;
import io.github.paramkathir.outagetracker.web.dto.IncidentView;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Pure unit test: no Spring context, collaborators mocked with Mockito. */
@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-01T15:00:00Z");
    private static final GeoPoint PLANO = GeoPoint.of(33.0198, -96.6989);

    @Mock
    SubscriptionRepository subscriptions;

    @Mock
    AlertChannel email;

    @Mock
    AlertChannel webhook;

    AlertService service;

    @BeforeEach
    void setUp() {
        lenient().when(email.name()).thenReturn("email");
        lenient().when(webhook.name()).thenReturn("webhook");
        service = new AlertService(subscriptions, List.of(email, webhook), new SimpleMeterRegistry());
    }

    private static IncidentView incident(double radiusKm) {
        return new IncidentView(1, ServiceType.INTERNET, IncidentStatus.CONFIRMED,
                PLANO.latitude(), PLANO.longitude(), radiusKm, 12, NOW, NOW, NOW, null, null);
    }

    private static Subscription subscriberAt(double lat, double lng, double radiusKm) {
        return new Subscription("x@example.com", GeoPoint.of(lat, lng), radiusKm, null, null, NOW);
    }

    @Test
    void nearbySubscriberIsAlertedOnConfirmation() throws Exception {
        Subscription near = subscriberAt(33.03, -96.70, 5);
        when(subscriptions.findCovering(ServiceType.INTERNET)).thenReturn(List.of(near));
        when(email.supports(near)).thenReturn(true);
        when(webhook.supports(near)).thenReturn(false);

        int sent = service.dispatch(new IncidentEvent(IncidentEvent.Kind.CONFIRMED, incident(1.0)));

        assertThat(sent).isEqualTo(1);
        ArgumentCaptor<Alert> alert = ArgumentCaptor.forClass(Alert.class);
        verify(email).send(eq(near), alert.capture());
        verify(webhook, never()).send(any(), any());
        assertThat(alert.getValue().message()).contains("Confirmed internet outage");
        assertThat(alert.getValue().distanceKm()).isLessThan(2.0);
    }

    @Test
    void farSubscriberIsNotAlerted() throws Exception {
        // Fort Worth, ~60 km away, watching a 5 km radius.
        Subscription far = subscriberAt(32.7555, -97.3308, 5);
        when(subscriptions.findCovering(ServiceType.INTERNET)).thenReturn(List.of(far));

        assertThat(service.dispatch(new IncidentEvent(IncidentEvent.Kind.CONFIRMED, incident(1.0)))).isZero();
        verify(email, never()).send(any(), any());
    }

    @Test
    void incidentFootprintCountsTowardOverlap() throws Exception {
        // Subscriber 6 km away with a 3 km radius: only reached because the incident spans 4 km.
        Subscription edge = subscriberAt(PLANO.latitude() + 6 / 111.195, PLANO.longitude(), 3);
        when(subscriptions.findCovering(ServiceType.INTERNET)).thenReturn(List.of(edge));
        when(email.supports(edge)).thenReturn(true);

        assertThat(service.dispatch(new IncidentEvent(IncidentEvent.Kind.CONFIRMED, incident(1.0)))).isZero();
        assertThat(service.dispatch(new IncidentEvent(IncidentEvent.Kind.CONFIRMED, incident(4.0)))).isEqualTo(1);
    }

    @Test
    void routineUpdatesDoNotAlert() {
        assertThat(service.dispatch(new IncidentEvent(IncidentEvent.Kind.UPDATED, incident(1.0)))).isZero();
        assertThat(service.dispatch(new IncidentEvent(IncidentEvent.Kind.OPENED, incident(1.0)))).isZero();
        verifyNoInteractions(subscriptions);
    }

    @Test
    void oneFailingChannelDoesNotBlockOthers() throws Exception {
        Subscription near = subscriberAt(33.02, -96.70, 5);
        when(subscriptions.findCovering(ServiceType.INTERNET)).thenReturn(List.of(near));
        when(email.supports(near)).thenReturn(true);
        when(webhook.supports(near)).thenReturn(true);
        doThrow(new RuntimeException("connection refused")).when(webhook).send(any(), any());

        int sent = service.dispatch(new IncidentEvent(IncidentEvent.Kind.RESOLVED, incident(1.0)));

        assertThat(sent).isEqualTo(1);
        verify(email).send(eq(near), any());
    }
}
