package com.bookly.backendcf.platform.infrastructure.notification;

import com.bookly.backendcf.platform.application.WelcomeNotificationPort;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingWelcomeNotificationAdapter implements WelcomeNotificationPort {

    private static final Logger logger = LoggerFactory.getLogger(LoggingWelcomeNotificationAdapter.class);

    @Override
    public void sendPlatformWelcome(UUID platformId, String platformName, String adminEmail) {
        logger.info(
                "Bienvenida enviada: plataforma '{}' (id={}) configurada, destinatario={}",
                platformName,
                platformId,
                adminEmail);
    }
}
