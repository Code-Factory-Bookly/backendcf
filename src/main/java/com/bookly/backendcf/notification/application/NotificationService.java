package com.bookly.backendcf.notification.application;

import com.bookly.backendcf.booking.domain.events.BookingCancelledEvent;
import com.bookly.backendcf.booking.domain.events.BookingCreatedEvent;
import com.bookly.backendcf.booking.domain.events.BookingEvent;
import com.bookly.backendcf.booking.domain.events.BookingRescheduledEvent;
import com.bookly.backendcf.notification.domain.model.NotificationDelivery;
import com.bookly.backendcf.notification.domain.model.NotificationEventType;
import com.bookly.backendcf.notification.infrastructure.persistence.NotificationDeliveryRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger(NotificationService.class);

    private static final String PROFESSIONAL_CANCELLATION_SUBJECT = "Cancelación de cita en tu agenda";

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ROOT);

    private final NotificationDeliveryRepository deliveryRepository;
    private final BookingNotificationDataProvider dataProvider;
    private final EmailNotificationPort emailNotificationPort;
    private final NotificationTemplateRenderer templateRenderer;

    public NotificationService(NotificationDeliveryRepository deliveryRepository,
                               BookingNotificationDataProvider dataProvider,
                               EmailNotificationPort emailNotificationPort) {
        this(deliveryRepository, dataProvider, emailNotificationPort,
                new com.bookly.backendcf.notification.infrastructure.email.ClasspathNotificationTemplateRenderer());
    }

    @Autowired
    public NotificationService(NotificationDeliveryRepository deliveryRepository,
                               BookingNotificationDataProvider dataProvider,
                               EmailNotificationPort emailNotificationPort,
                               NotificationTemplateRenderer templateRenderer) {
        this.deliveryRepository = deliveryRepository;
        this.dataProvider = dataProvider;
        this.emailNotificationPort = emailNotificationPort;
        this.templateRenderer = templateRenderer;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(BookingEvent event) {
        Objects.requireNonNull(event, "event es obligatorio");

        if (deliveryRepository.findByEventId(event.getEventId()).isPresent()) {
            return;
        }

        NotificationContent content = contentFor(event);
        NotificationDelivery delivery = new NotificationDelivery(
                event.getEventId(),
                event.getBookingId(),
                content.eventType(),
                content.data().customerEmail(),
                content.subject(),
                OffsetDateTime.now(ZoneOffset.UTC));

        try {
            delivery = deliveryRepository.saveAndFlush(delivery);
        } catch (DataIntegrityViolationException duplicateEvent) {
            return;
        }

        delivery.registerAttempt(OffsetDateTime.now(ZoneOffset.UTC));
        try {
            emailNotificationPort.send(new EmailNotificationPort.EmailMessage(
                    delivery.getRecipientEmail(), delivery.getSubject(), content.body()));
            delivery.markSent(OffsetDateTime.now(ZoneOffset.UTC));
        } catch (RuntimeException exception) {
            delivery.markFailed(safeErrorMessage(exception));
        }

        deliveryRepository.saveAndFlush(delivery);

        notifyProfessionalOfCancellation(event, content.data());
    }

    /**
     * HU-10 / HU-14: al cancelarse una reserva, el profesional recibe un aviso porque la cita
     * desaparece de su agenda. No se registra como entrega: el listado de notificaciones de una
     * reserva es visible para el cliente y expondria el correo del profesional. Un fallo aqui
     * no debe afectar el correo del cliente ni la cancelacion, por eso solo se deja en el log.
     */
    private void notifyProfessionalOfCancellation(
            BookingEvent event, BookingNotificationDataProvider.BookingNotificationData data) {
        if (!(event instanceof BookingCancelledEvent cancelled)) {
            return;
        }
        String professionalEmail = data.professionalEmail();
        if (professionalEmail == null || professionalEmail.isBlank()) {
            return;
        }
        try {
            String body = templateRenderer.professionalCancellation(variables(data, Map.of(
                    "dateTime", format(cancelled.getStartTime()),
                    "cancellationReason", valueOrDefault(cancelled.getCancellationReason(), "No especificado"))));
            emailNotificationPort.send(new EmailNotificationPort.EmailMessage(
                    professionalEmail, PROFESSIONAL_CANCELLATION_SUBJECT, body));
        } catch (RuntimeException exception) {
            LOGGER.warn("No se pudo avisar al profesional de la cancelación de la reserva {}: {}",
                    cancelled.getBookingId(), safeErrorMessage(exception));
        }
    }

    private NotificationContent contentFor(BookingEvent event) {
        if (event instanceof BookingCreatedEvent created) {
            BookingNotificationDataProvider.BookingNotificationData data = dataProvider.get(
                    created.getCustomerId(), created.getProfessionalId(), created.getServiceId());
            String subject = "Confirmación de reserva";
            return new NotificationContent(NotificationEventType.BOOKING_CREATED, data, subject,
                    templateRenderer.confirmation(variables(data, Map.of(
                            "dateTime", format(created.getStartTime())))));
        }

        if (event instanceof BookingCancelledEvent cancelled) {
            BookingNotificationDataProvider.BookingNotificationData data = dataProvider.get(
                    cancelled.getCustomerId(), cancelled.getProfessionalId(), cancelled.getServiceId());
            String subject = "Cancelación de reserva";
            return new NotificationContent(NotificationEventType.BOOKING_CANCELLED, data, subject,
                    templateRenderer.cancellation(variables(data, Map.of(
                            "dateTime", format(cancelled.getStartTime()),
                            "cancellationReason", valueOrDefault(cancelled.getCancellationReason(), "No especificado")))));
        }

        if (event instanceof BookingRescheduledEvent rescheduled) {
            BookingNotificationDataProvider.BookingNotificationData data = dataProvider.get(
                    rescheduled.getCustomerId(), rescheduled.getProfessionalId(), rescheduled.getServiceId());
            String subject = "Reprogramación de reserva";
            return new NotificationContent(NotificationEventType.BOOKING_RESCHEDULED, data, subject,
                    templateRenderer.rescheduling(variables(data, Map.of(
                            "previousDateTime", format(rescheduled.getPreviousStartTime()),
                            "dateTime", format(rescheduled.getStartTime())))));
        }

        throw new IllegalArgumentException("Evento de reserva no soportado: "
                + event.getClass().getSimpleName());
    }

    private Map<String, String> variables(BookingNotificationDataProvider.BookingNotificationData data,
                                          Map<String, String> eventVariables) {
        Map<String, String> variables = new java.util.HashMap<>();
        variables.put("customerName", data.customerName());
        variables.put("serviceName", data.serviceName());
        variables.put("professionalName", data.professionalName());
        variables.putAll(eventVariables);
        return variables;
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String format(java.time.LocalDateTime dateTime) {
        return dateTime.atOffset(ZoneOffset.UTC).format(DATE_TIME_FORMATTER);
    }

    private String safeErrorMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }

    private record NotificationContent(
            NotificationEventType eventType,
            BookingNotificationDataProvider.BookingNotificationData data,
            String subject,
            String body) {
    }
}
