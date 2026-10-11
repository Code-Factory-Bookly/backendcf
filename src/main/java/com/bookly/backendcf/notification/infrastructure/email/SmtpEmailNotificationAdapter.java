package com.bookly.backendcf.notification.infrastructure.email;

import com.bookly.backendcf.notification.application.EmailNotificationPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Adaptador SMTP; las credenciales se obtienen exclusivamente de configuración externa. */
@Component
@ConditionalOnProperty(name = "notification.email.provider", havingValue = "smtp")
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
