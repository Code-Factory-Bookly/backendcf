package com.bookly.backendcf.notification.application;

import com.bookly.backendcf.notification.infrastructure.persistence.NotificationDeliveryRepository;
import com.bookly.backendcf.notification.presentation.dto.BookingNotificationsResponse;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BookingNotificationQueryService {

    private final BookingParticipantsLookup participantsLookup;
    private final NotificationDeliveryRepository deliveryRepository;
    private final String deliveryMode;

    public BookingNotificationQueryService(
            BookingParticipantsLookup participantsLookup,
            NotificationDeliveryRepository deliveryRepository,
            @Value("${notification.email.provider:logging}") String deliveryMode) {
        this.participantsLookup = participantsLookup;
        this.deliveryRepository = deliveryRepository;
        this.deliveryMode = deliveryMode;
    }

    @Transactional(readOnly = true)
    public BookingNotificationsResponse find(UUID bookingId, Authentication authentication) {
        BookingParticipantsLookup.Participants participants = participantsLookup.find(bookingId)
                .orElseThrow(BookingNotificationQueryService::notFound);

        // Igual que en la cancelacion (bug #188): una reserva ajena responde 404, no 403, para no
        // revelar que el id existe.
        if (!isAdmin(authentication) && !isParticipant(authentication, participants)) {
            throw notFound();
        }

        return new BookingNotificationsResponse(
                deliveryMode,
                deliveryRepository.findByBookingIdOrderByCreatedAtAsc(bookingId).stream()
                        .map(BookingNotificationsResponse.Item::from)
                        .toList());
    }

    private static boolean isParticipant(Authentication authentication,
                                         BookingParticipantsLookup.Participants participants) {
        String userId = authentication.getName();
        return userId.equals(String.valueOf(participants.customerId()))
                || userId.equals(String.valueOf(participants.professionalId()));
    }

    private static boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Reserva no encontrada");
    }
}
