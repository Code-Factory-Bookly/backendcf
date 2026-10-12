package com.bookly.backendcf.notification.infrastructure.event;

import com.bookly.backendcf.booking.domain.events.BookingEvent;
import com.bookly.backendcf.notification.application.NotificationService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class BookingNotificationListener {

    private final NotificationService notificationService;

    public BookingNotificationListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingEvent(BookingEvent event) {
        notificationService.process(event);
    }
}
