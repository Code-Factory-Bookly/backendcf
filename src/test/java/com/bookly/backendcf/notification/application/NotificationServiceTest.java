package com.bookly.backendcf.notification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookly.backendcf.booking.domain.events.BookingCreatedEvent;
import com.bookly.backendcf.notification.domain.model.NotificationDelivery;
import com.bookly.backendcf.notification.domain.model.NotificationDeliveryStatus;
import com.bookly.backendcf.notification.infrastructure.persistence.NotificationDeliveryRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationDeliveryRepository deliveryRepository;

    @Mock
    private BookingNotificationDataProvider dataProvider;

    @Mock
    private EmailNotificationPort emailNotificationPort;

    private NotificationService service;
    private UUID customerId;
    private UUID professionalId;
    private UUID serviceId;

    @BeforeEach
    void setUp() {
        service = new NotificationService(deliveryRepository, dataProvider, emailNotificationPort);
        customerId = UUID.randomUUID();
        professionalId = UUID.randomUUID();
        serviceId = UUID.randomUUID();
    }

    @Test
    void procesaCreacionYMarcaElCorreoComoEnviado() {
        BookingCreatedEvent event = new BookingCreatedEvent(
                UUID.randomUUID(), customerId, professionalId, serviceId,
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(1));
        when(deliveryRepository.findByEventId(event.getEventId())).thenReturn(Optional.empty());
        when(dataProvider.get(customerId, professionalId, serviceId))
                .thenReturn(new BookingNotificationDataProvider.BookingNotificationData(
                        "cliente@example.com", "Cliente Uno", "Profesional Uno", "Consulta"));
        when(deliveryRepository.saveAndFlush(any(NotificationDelivery.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.process(event);

        ArgumentCaptor<NotificationDelivery> captor = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(deliveryRepository, org.mockito.Mockito.times(2)).saveAndFlush(captor.capture());
        NotificationDelivery delivery = captor.getAllValues().get(1);
        assertEquals(event.getEventId(), delivery.getEventId());
        assertEquals(NotificationDeliveryStatus.SENT, delivery.getStatus());
        assertEquals(1, delivery.getAttempts());
        assertNotNull(delivery.getSentAt());
        verify(emailNotificationPort).send(any(EmailNotificationPort.EmailMessage.class));
    }

    @Test
    void registraFalloDelProveedorSinPropagarLaExcepcion() {
        BookingCreatedEvent event = new BookingCreatedEvent(
                UUID.randomUUID(), customerId, professionalId, serviceId,
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(1));
        when(deliveryRepository.findByEventId(event.getEventId())).thenReturn(Optional.empty());
        when(dataProvider.get(customerId, professionalId, serviceId))
                .thenReturn(new BookingNotificationDataProvider.BookingNotificationData(
                        "cliente@example.com", "Cliente Uno", "Profesional Uno", "Consulta"));
        when(deliveryRepository.saveAndFlush(any(NotificationDelivery.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new IllegalStateException("SMTP no disponible"))
                .when(emailNotificationPort).send(any(EmailNotificationPort.EmailMessage.class));

        service.process(event);

        ArgumentCaptor<NotificationDelivery> captor = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(deliveryRepository, org.mockito.Mockito.times(2)).saveAndFlush(captor.capture());
        NotificationDelivery delivery = captor.getAllValues().get(1);
        assertEquals(NotificationDeliveryStatus.FAILED, delivery.getStatus());
        assertEquals("SMTP no disponible", delivery.getErrorMessage());
    }

    @Test
    void eventoYaProcesadoNoVuelveAEnviarCorreo() {
        BookingCreatedEvent event = new BookingCreatedEvent(
                UUID.randomUUID(), customerId, professionalId, serviceId,
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(1));
        when(deliveryRepository.findByEventId(event.getEventId()))
                .thenReturn(Optional.of(org.mockito.Mockito.mock(NotificationDelivery.class)));

        service.process(event);

        verify(dataProvider, never()).get(any(), any(), any());
        verify(emailNotificationPort, never()).send(any());
        verify(deliveryRepository, never()).saveAndFlush(any());
    }
}
