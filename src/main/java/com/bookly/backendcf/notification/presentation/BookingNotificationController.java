package com.bookly.backendcf.notification.presentation;

import com.bookly.backendcf.notification.application.BookingNotificationQueryService;
import com.bookly.backendcf.notification.presentation.dto.BookingNotificationsResponse;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reservas")
public class BookingNotificationController {

    private final BookingNotificationQueryService queryService;

    public BookingNotificationController(BookingNotificationQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/{bookingId}/notificaciones")
    public BookingNotificationsResponse notifications(@PathVariable UUID bookingId, Authentication authentication) {
        return queryService.find(bookingId, authentication);
    }
}
