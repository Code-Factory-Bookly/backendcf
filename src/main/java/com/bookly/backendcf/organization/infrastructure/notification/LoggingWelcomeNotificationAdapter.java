package com.bookly.backendcf.organization.infrastructure.notification;

import com.bookly.backendcf.organization.application.WelcomeNotificationPort;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingWelcomeNotificationAdapter implements WelcomeNotificationPort {

    private static final Logger logger = LoggerFactory.getLogger(LoggingWelcomeNotificationAdapter.class);

    @Override
    public void sendOrganizationWelcome(UUID organizationId, String organizationName, String adminEmail) {
        logger.info(
                "Bienvenida enviada: organizacion '{}' (id={}) activada, destinatario={}",
                organizationName,
                organizationId,
                adminEmail);
    }
}
