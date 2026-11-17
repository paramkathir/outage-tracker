package io.github.paramkathir.outagetracker.alert;

import io.github.paramkathir.outagetracker.domain.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Stand-in for an email provider: logs the message that would be sent.
 * Swap for an SMTP / Amazon SES implementation in production.
 */
@Component
public class EmailLogAlertChannel implements AlertChannel {

    private static final Logger log = LoggerFactory.getLogger(EmailLogAlertChannel.class);

    @Override
    public String name() {
        return "email";
    }

    @Override
    public boolean supports(Subscription subscription) {
        return true;
    }

    @Override
    public void send(Subscription subscription, Alert alert) {
        log.info("[email -> {}] {}", mask(subscription.getEmail()), alert.message());
    }

    /** j***@example.com, so logs don't leak full addresses. */
    static String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + (at >= 0 ? email.substring(at) : "");
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
