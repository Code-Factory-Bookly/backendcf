package com.bookly.backendcf.notification.infrastructure.email;

import com.bookly.backendcf.notification.application.EmailNotificationPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Adaptador SMTP; las credenciales se obtienen exclusivamente de configuracion externa.
 *
 * <p>Se instancia desde {@link EmailNotificationConfiguration}, que decide
 * cuando es el adaptador activo segun {@code notification.email.provider}.</p>
 */
public class SmtpEmailNotificationAdapter implements EmailNotificationPort {

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpEmailNotificationAdapter(JavaMailSender mailSender,
                                        @Value("${notification.email.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void send(EmailMessage message) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(from);
        mail.setTo(message.recipient());
        mail.setSubject(message.subject());
        mail.setText(message.body());
        mailSender.send(mail);
    }
}
