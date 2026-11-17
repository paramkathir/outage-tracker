package io.github.paramkathir.outagetracker.alert;

import io.github.paramkathir.outagetracker.domain.Subscription;

/**
 * A way of delivering an alert (email, webhook, SMS...). New channels are picked up
 * automatically: just add another {@code @Component} implementing this interface.
 */
public interface AlertChannel {

    String name();

    boolean supports(Subscription subscription);

    void send(Subscription subscription, Alert alert) throws Exception;
}
