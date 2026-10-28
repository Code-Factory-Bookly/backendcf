package com.bookly.backendcf.notification.infrastructure.email;

import com.bookly.backendcf.notification.application.EmailNotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adaptador de email que registra el envio en el log.
 *
 * <p>Se instancia desde {@link EmailNotificationConfiguration}, que decide
 * cuando es el adaptador activo segun {@code notification.email.provider}.</p>
 */
public class LoggingEmailNotificationAdapter implements EmailNotificationPort {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingEmailNotificationAdapter.class);

    @Override
    public void send(EmailMessage message) {
        LOGGER.info("HU-10 email notification recipient={} subject={} body={}",
                message.recipient(), message.subject(), message.body());
    }
}
