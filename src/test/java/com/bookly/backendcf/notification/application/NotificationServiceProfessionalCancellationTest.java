package com.bookly.backendcf.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookly.backendcf.booking.domain.events.BookingCancelledEvent;
import com.bookly.backendcf.booking.domain.events.BookingCreatedEvent;
import com.bookly.backendcf.notification.domain.model.NotificationDelivery;
import com.bookly.backendcf.notification.domain.model.NotificationDeliveryStatus;
import com.bookly.backendcf.notification.infrastructure.persistence.NotificationDeliveryRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Aviso de cancelacion al profesional (HU-10 / HU-14).
 *
 * <p>Cuando se cancela una reserva, ademas del correo al cliente, el profesional recibe un aviso
 * porque la reserva desaparece de su agenda. Los demas eventos siguen notificando solo al
 * cliente, y un fallo en uno de los dos correos no debe afectar al otro.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class NotificationServiceProfessionalCancellationTest {

    private static final String CUSTOMER_EMAIL = "cliente@example.com";
    private static final String PROFESSIONAL_EMAIL = "profesional@example.com";

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
    void laCancelacionNotificaAlClienteYAlProfesional() {
        // Arrange
        BookingCancelledEvent event = cancelledEvent("Cambio de planes");
        stubProcessable(event, PROFESSIONAL_EMAIL);

        // Act
        service.process(event);

        // Assert
        List<EmailNotificationPort.EmailMessage> sent = sentEmails(2);
        EmailNotificationPort.EmailMessage toCustomer = byRecipient(sent, CUSTOMER_EMAIL);
        EmailNotificationPort.EmailMessage toProfessional = byRecipient(sent, PROFESSIONAL_EMAIL);
        assertThat(toCustomer.subject()).isEqualTo("Cancelación de reserva");
        assertThat(toProfessional.subject()).isEqualTo("Cancelación de cita en tu agenda");
    }

    @Test
    void elCorreoDelProfesionalDiceQueClienteYServicioFueronCancelados() {
        // Arrange
        BookingCancelledEvent event = cancelledEvent("Cambio de planes");
        stubProcessable(event, PROFESSIONAL_EMAIL);

        // Act
        service.process(event);

        // Assert
        String body = byRecipient(sentEmails(2), PROFESSIONAL_EMAIL).body();
        assertThat(body)
                .contains("Profesional Uno")
                .contains("Cliente Uno")
                .contains("Consulta general")
                .contains("Cambio de planes")
                .contains("25/11/2026 09:00")
                .doesNotContain("{{");
    }

    @Test
    void sinMotivoElCorreoDelProfesionalIndicaNoEspecificado() {
        // Arrange
        BookingCancelledEvent event = cancelledEvent(null);
        stubProcessable(event, PROFESSIONAL_EMAIL);

        // Act
        service.process(event);

        // Assert
        assertThat(byRecipient(sentEmails(2), PROFESSIONAL_EMAIL).body())
                .contains("No especificado");
    }

    @Test
    void elCorreoDelClienteNoCambiaYNoRevelaElCorreoDelProfesional() {
        // Arrange
        BookingCancelledEvent event = cancelledEvent("Cambio de planes");
        stubProcessable(event, PROFESSIONAL_EMAIL);

        // Act
        service.process(event);

        // Assert
        String body = byRecipient(sentEmails(2), CUSTOMER_EMAIL).body();
        assertThat(body).contains("Hola Cliente Uno").contains("Tu reserva fue cancelada.");
        assertThat(body).doesNotContain(PROFESSIONAL_EMAIL);
    }

    @Test
    void soloElCorreoDelClienteQuedaRegistradoComoEntrega() {
        // Arrange
        BookingCancelledEvent event = cancelledEvent("Cambio de planes");
        stubProcessable(event, PROFESSIONAL_EMAIL);

        // Act
        service.process(event);

        // Assert: el listado de notificaciones de una reserva (visible para cliente y
        // profesional) no debe exponer el correo del otro participante.
        ArgumentCaptor<NotificationDelivery> captor = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(deliveryRepository, times(2)).saveAndFlush(captor.capture());
        assertThat(captor.getAllValues())
                .allSatisfy(delivery -> assertThat(delivery.getRecipientEmail())
                        .isEqualTo(CUSTOMER_EMAIL));
    }

    @Test
    void siFallaElCorreoDelProfesionalNoSePropagaYElDelClienteQuedaEnviado() {
        // Arrange
        BookingCancelledEvent event = cancelledEvent("Cambio de planes");
        stubProcessable(event, PROFESSIONAL_EMAIL);
        failSendingTo(PROFESSIONAL_EMAIL);

        // Act
        service.process(event);

        // Assert
        ArgumentCaptor<NotificationDelivery> captor = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(deliveryRepository, times(2)).saveAndFlush(captor.capture());
        assertThat(captor.getAllValues().get(1).getStatus()).isEqualTo(NotificationDeliveryStatus.SENT);
    }

    @Test
    void siFallaElCorreoDelClienteElProfesionalIgualRecibeElAviso() {
        // Arrange
        BookingCancelledEvent event = cancelledEvent("Cambio de planes");
        stubProcessable(event, PROFESSIONAL_EMAIL);
        failSendingTo(CUSTOMER_EMAIL);

        // Act
        service.process(event);

        // Assert
        verify(emailNotificationPort).send(
                org.mockito.ArgumentMatchers.argThat(message ->
                        PROFESSIONAL_EMAIL.equals(message.recipient())));
        ArgumentCaptor<NotificationDelivery> captor = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(deliveryRepository, times(2)).saveAndFlush(captor.capture());
        assertThat(captor.getAllValues().get(1).getStatus()).isEqualTo(NotificationDeliveryStatus.FAILED);
    }

    @Test
    void sinCorreoDeProfesionalSoloNotificaAlCliente() {
        // Arrange
        BookingCancelledEvent event = cancelledEvent("Cambio de planes");
        stubProcessable(event, null);

        // Act
        service.process(event);

        // Assert
        List<EmailNotificationPort.EmailMessage> sent = sentEmails(1);
        assertThat(sent.get(0).recipient()).isEqualTo(CUSTOMER_EMAIL);
    }

    @Test
    void laCreacionDeUnaReservaNoNotificaAlProfesional() {
        // Arrange
        BookingCreatedEvent event = new BookingCreatedEvent(
                UUID.randomUUID(), customerId, professionalId, serviceId,
                LocalDateTime.of(2026, 11, 25, 9, 0), LocalDateTime.of(2026, 11, 25, 10, 0));
        when(deliveryRepository.findByEventId(event.getEventId())).thenReturn(Optional.empty());
        when(dataProvider.get(customerId, professionalId, serviceId)).thenReturn(data(PROFESSIONAL_EMAIL));
        when(deliveryRepository.saveAndFlush(any(NotificationDelivery.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        service.process(event);

        // Assert
        List<EmailNotificationPort.EmailMessage> sent = sentEmails(1);
        assertThat(sent.get(0).recipient()).isEqualTo(CUSTOMER_EMAIL);
    }

    @Test
    void unEventoYaProcesadoNoVuelveAAvisarAlProfesional() {
        // Arrange
        BookingCancelledEvent event = cancelledEvent("Cambio de planes");
        when(deliveryRepository.findByEventId(event.getEventId()))
                .thenReturn(Optional.of(org.mockito.Mockito.mock(NotificationDelivery.class)));

        // Act
        service.process(event);

        // Assert
        verify(emailNotificationPort, never()).send(any());
        verify(dataProvider, never()).get(any(), any(), any());
    }

    // ---- ayudas ----

    private BookingCancelledEvent cancelledEvent(String reason) {
        return new BookingCancelledEvent(
                UUID.randomUUID(), customerId, professionalId, serviceId,
                LocalDateTime.of(2026, 11, 25, 9, 0), LocalDateTime.of(2026, 11, 25, 10, 0),
                LocalDateTime.of(2026, 11, 22, 18, 0), reason);
    }

    private BookingNotificationDataProvider.BookingNotificationData data(String professionalEmail) {
        return new BookingNotificationDataProvider.BookingNotificationData(
                CUSTOMER_EMAIL, "Cliente Uno", "Profesional Uno", "Consulta general",
                professionalEmail);
    }

    private void stubProcessable(BookingCancelledEvent event, String professionalEmail) {
        when(deliveryRepository.findByEventId(event.getEventId())).thenReturn(Optional.empty());
        when(dataProvider.get(customerId, professionalId, serviceId)).thenReturn(data(professionalEmail));
        when(deliveryRepository.saveAndFlush(any(NotificationDelivery.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    // Hace fallar solo el envio al destinatario indicado; los demas envios funcionan.
    // doAnswer evita el "strict stubbing argument mismatch" de Mockito con argumentos distintos.
    private void failSendingTo(String recipient) {
        doAnswer(invocation -> {
            EmailNotificationPort.EmailMessage message = invocation.getArgument(0);
            if (recipient.equals(message.recipient())) {
                throw new IllegalStateException("SMTP no disponible");
            }
            return null;
        }).when(emailNotificationPort).send(any(EmailNotificationPort.EmailMessage.class));
    }

    private List<EmailNotificationPort.EmailMessage> sentEmails(int expected) {
        ArgumentCaptor<EmailNotificationPort.EmailMessage> captor =
                ArgumentCaptor.forClass(EmailNotificationPort.EmailMessage.class);
        verify(emailNotificationPort, times(expected)).send(captor.capture());
        return captor.getAllValues();
    }

    private EmailNotificationPort.EmailMessage byRecipient(
            List<EmailNotificationPort.EmailMessage> sent, String recipient) {
        return sent.stream()
                .filter(message -> recipient.equals(message.recipient()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No se envió correo a " + recipient));
    }
}
