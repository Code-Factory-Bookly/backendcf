package com.bookly.backendcf.notification.infrastructure.email;

import com.bookly.backendcf.notification.application.EmailNotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "notification.email.provider", havingValue = "logging", matchIfMissing = true)
public class LoggingEmailNotificationAdapter implements EmailNotificationPort {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingEmailNotificationAdapter.class);

    @Override
    public void send(EmailMessage message) {
        LOGGER.info("HU-10 email notification recipient={} subject={} body={}",
                message.recipient(), message.subject(), message.body());
    }
}
