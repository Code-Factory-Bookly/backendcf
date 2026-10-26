package com.bookly.backendcf.notification.presentation.dto;

import com.bookly.backendcf.notification.domain.model.NotificationDelivery;
import com.bookly.backendcf.notification.domain.model.NotificationDeliveryStatus;
import com.bookly.backendcf.notification.domain.model.NotificationEventType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * deliveryMode dice si el correo sale por SMTP real ("smtp") o solo se registra en el log
 * ("logging"), para que la interfaz no presente como entregado algo que no llego a un buzon.
 */
public record BookingNotificationsResponse(String deliveryMode, List<Item> deliveries) {

    public record Item(
            UUID id,
            NotificationEventType eventType,
            String recipientEmail,
            String subject,
            NotificationDeliveryStatus status,
            int attempts,
            OffsetDateTime createdAt,
            OffsetDateTime sentAt,
            String errorMessage) {

        public static Item from(NotificationDelivery delivery) {
            return new Item(
                    delivery.getId(),
                    delivery.getEventType(),
                    delivery.getRecipientEmail(),
                    delivery.getSubject(),
                    delivery.getStatus(),
                    delivery.getAttempts(),
                    delivery.getCreatedAt(),
                    delivery.getSentAt(),
                    delivery.getErrorMessage());
        }
    }
}
