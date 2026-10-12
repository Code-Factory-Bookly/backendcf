package com.bookly.backendcf.notification.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "notification_delivery", uniqueConstraints = {
        @UniqueConstraint(name = "uk_notification_delivery_event", columnNames = "event_id")
})
public class NotificationDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private NotificationEventType eventType;

    @Column(name = "recipient_email", nullable = false, length = 320)
    private String recipientEmail;

    @Column(nullable = false, length = 200)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationDeliveryStatus status = NotificationDeliveryStatus.PENDING;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Column(name = "last_attempt_at")
    private OffsetDateTime lastAttemptAt;

    @Column(name = "error_message")
    private String errorMessage;

    protected NotificationDelivery() {
    }

    public NotificationDelivery(UUID eventId, UUID bookingId, NotificationEventType eventType,
                                String recipientEmail, String subject, OffsetDateTime createdAt) {
        this.eventId = eventId;
        this.bookingId = bookingId;
        this.eventType = eventType;
        this.recipientEmail = recipientEmail;
        this.subject = subject;
        this.createdAt = createdAt;
        this.status = NotificationDeliveryStatus.PENDING;
        this.attempts = 0;
    }

    public void registerAttempt(OffsetDateTime attemptedAt) {
        attempts++;
        lastAttemptAt = attemptedAt;
    }

    public void markSent(OffsetDateTime deliveredAt) {
        status = NotificationDeliveryStatus.SENT;
        sentAt = deliveredAt;
        errorMessage = null;
    }

    public void markFailed(String message) {
        status = NotificationDeliveryStatus.FAILED;
        errorMessage = message;
    }

    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public UUID getBookingId() { return bookingId; }
    public NotificationEventType getEventType() { return eventType; }
    public String getRecipientEmail() { return recipientEmail; }
    public String getSubject() { return subject; }
    public NotificationDeliveryStatus getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getSentAt() { return sentAt; }
    public OffsetDateTime getLastAttemptAt() { return lastAttemptAt; }
    public String getErrorMessage() { return errorMessage; }
}
