package com.bookly.backendcf.notification.infrastructure.email;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import com.bookly.backendcf.notification.application.EmailNotificationPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class SmtpEmailNotificationAdapterTest {

    @Mock
    private JavaMailSender mailSender;

    @Test
    void construyeYEnviaElMensajeConElRemitenteConfigurado() {
        SmtpEmailNotificationAdapter adapter =
                new SmtpEmailNotificationAdapter(mailSender, "no-reply@bookly.test");

        adapter.send(new EmailNotificationPort.EmailMessage(
                "cliente@example.com", "Confirmación", "Contenido"));

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertEquals("no-reply@bookly.test", message.getFrom());
        assertEquals("cliente@example.com", message.getTo()[0]);
        assertEquals("Confirmación", message.getSubject());
        assertEquals("Contenido", message.getText());
    }
}
