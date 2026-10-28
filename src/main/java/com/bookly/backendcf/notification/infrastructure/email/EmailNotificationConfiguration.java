package com.bookly.backendcf.notification.infrastructure.email;

import com.bookly.backendcf.notification.application.EmailNotificationPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Registro unico del adaptador de email (HU-10).
 *
 * <p>Antes cada adaptador era un {@code @Component} y ambos implementaban
 * {@link EmailNotificationPort}. Spring debia resolver la inyeccion en
 * {@code NotificationService} entre dos candidatos, lo que Sonar reportaba
 * como java:S9352 ("Multiple beans match this dependency").
 *
 * <p>Aqui la eleccion se hace en un unico punto: el adaptador de logging es el
 * predeterminado y el de SMTP solo se registra cuando
 * {@code notification.email.provider=smtp}. En cualquier escenario queda
 * exactamente un bean {@link EmailNotificationPort} en el contexto.</p>
 */
@Configuration
public class EmailNotificationConfiguration {

    /**
     * Adaptador por defecto: registra el envio en el log.
     *
     * <p>Se activa cuando {@code notification.email.provider} no esta definido
     * o vale {@code logging}.</p>
     */
    @Bean
    @ConditionalOnProperty(
            name = "notification.email.provider",
            havingValue = "logging",
            matchIfMissing = true)
    public EmailNotificationPort loggingEmailNotificationPort() {
        return new LoggingEmailNotificationAdapter();
    }

    /**
     * Adaptador SMTP: usado cuando {@code notification.email.provider=smtp}.
     *
     * <p>Las credenciales se obtienen exclusivamente de configuracion externa
     * ({@code spring.mail.*}), nunca del codigo.</p>
     */
    @Bean
    @ConditionalOnProperty(name = "notification.email.provider", havingValue = "smtp")
    public EmailNotificationPort smtpEmailNotificationPort(
            JavaMailSender mailSender,
            @Value("${notification.email.from}") String from) {
        return new SmtpEmailNotificationAdapter(mailSender, from);
    }
}
