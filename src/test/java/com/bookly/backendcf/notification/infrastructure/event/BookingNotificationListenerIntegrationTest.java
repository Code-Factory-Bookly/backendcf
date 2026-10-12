package com.bookly.backendcf.notification.infrastructure.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.bookly.backendcf.booking.domain.events.BookingCreatedEvent;
import com.bookly.backendcf.notification.application.NotificationService;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class BookingNotificationListenerIntegrationTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    void procesaElEventoSoloDespuesDelCommit() {
        BookingCreatedEvent event = event();

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(event);
            verifyNoInteractions(notificationService);
        });

        verify(notificationService).process(event);
    }

    @Test
    void noProcesaElEventoCuandoLaTransaccionHaceRollback() {
        BookingCreatedEvent event = event();

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(event);
            status.setRollbackOnly();
        });

        verify(notificationService, never()).process(any());
    }

    private BookingCreatedEvent event() {
        return new BookingCreatedEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(1));
    }
}
