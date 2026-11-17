package io.github.paramkathir.outagetracker.service;

import io.github.paramkathir.outagetracker.core.GeoPoint;
import io.github.paramkathir.outagetracker.domain.Subscription;
import io.github.paramkathir.outagetracker.repository.SubscriptionRepository;
import io.github.paramkathir.outagetracker.service.Exceptions.NotFoundException;
import io.github.paramkathir.outagetracker.web.dto.Requests;
import io.github.paramkathir.outagetracker.web.dto.Responses.SubscriptionCreated;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class SubscriptionService {

    private final SubscriptionRepository subscriptions;
    private final Clock clock;

    public SubscriptionService(SubscriptionRepository subscriptions, Clock clock) {
        this.subscriptions = subscriptions;
        this.clock = clock;
    }

    @Transactional
    public SubscriptionCreated subscribe(Requests.CreateSubscription request) {
        String webhook = request.webhookUrl() == null || request.webhookUrl().isBlank()
                ? null : request.webhookUrl().strip();
        Subscription saved = subscriptions.save(new Subscription(
                request.email().strip().toLowerCase(),
                GeoPoint.of(request.latitude(), request.longitude()),
                request.radiusKm(),
                request.serviceType(),
                webhook,
                clock.instant()));
        return new SubscriptionCreated(saved.getId(), saved.getEmail(), saved.getRadiusKm(),
                saved.getServiceType(), saved.getUnsubscribeToken());
    }

    @Transactional
    public void unsubscribe(String token) {
        Subscription subscription = subscriptions.findByUnsubscribeToken(token)
                .orElseThrow(() -> new NotFoundException("No subscription for that token"));
        subscriptions.delete(subscription);
    }
}
