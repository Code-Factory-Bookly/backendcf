
package com.bookly.backendcf.booking.application.service;

import com.bookly.backendcf.booking.domain.events.BookingCancelledEvent;
import com.bookly.backendcf.booking.domain.model.Booking;
import com.bookly.backendcf.booking.domain.model.BookingStatus;
import com.bookly.backendcf.booking.infrastructure.event.BookingEventPublisher;
import com.bookly.backendcf.booking.infrastructure.persistence.BookingRepository;
import com.bookly.backendcf.shared.security.OwnershipGuard;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class CancelBookingService {

    private final BookingRepository bookingRepository;
    private final BookingEventPublisher eventPublisher;
    private final OwnershipGuard ownershipGuard;
    private final int minAdvanceMinutes;
    private final Clock clock;

    //constructor utilizado por Spring Boot.
    @Autowired
    public CancelBookingService(
            BookingRepository bookingRepository,
            BookingEventPublisher eventPublisher,
            OwnershipGuard ownershipGuard,
            @Value("${booking.cancellation.min-advance-minutes:120}")
            int minAdvanceMinutes
    ) {
        this(
                bookingRepository,
                eventPublisher,
                ownershipGuard,
                minAdvanceMinutes,
                Clock.systemDefaultZone()
        );
    }

    // Constructor para pruebas con un reloj controlado.
    CancelBookingService(
            BookingRepository bookingRepository,
            BookingEventPublisher eventPublisher,
            OwnershipGuard ownershipGuard,
            int minAdvanceMinutes,
            Clock clock
    ) {
        if (minAdvanceMinutes < 0) {
            throw new IllegalArgumentException(
                    "El plazo minimo de cancelacion no puede ser negativo"
            );
        }

        this.bookingRepository = bookingRepository;
        this.eventPublisher = eventPublisher;
        this.ownershipGuard = ownershipGuard;
        this.minAdvanceMinutes = minAdvanceMinutes;
        this.clock = java.util.Objects.requireNonNull(clock);
    }

    @Transactional
    public Booking cancelBooking(
            UUID bookingId,
            Authentication authentication,
            String cancellationReason
    ) {
        if (bookingId == null) {
            throw new IllegalArgumentException(
                    "El identificador de reserva es obligatorio"
            );
        }

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException(
                    "Debe autenticarse para cancelar una reserva"
            );
        }

        UUID authenticatedCustomerId;

        try {
            authenticatedCustomerId =
                    UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException e) {
            throw new AccessDeniedException(
                    "Identidad de cliente invalida"
            );
        }

        Booking booking = bookingRepository
                .findByIdForUpdate(bookingId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Reserva no encontrada"
                        )
                );

        // Solo el cliente propietario puede cancelar.
        if (!authenticatedCustomerId.equals(booking.getCustomerId())) {
            throw new AccessDeniedException(
                    "Solo el cliente propietario puede cancelar la reserva"
            );
        }

        ownershipGuard.check(
                booking.getCustomerId(),
                authentication
        );

        if (booking.getStatus() != BookingStatus.CONFIRMADA) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Solo se pueden cancelar reservas confirmadas"
            );
        }

        LocalDateTime now = LocalDateTime.now(clock);

        // Exactamente 120 minutos: permitido.
        // Menos de 120 minutos: rechazado.
        if (now.plusMinutes(minAdvanceMinutes)
                .isAfter(booking.getStartTime())) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "La reserva debe cancelarse con al menos "
                            + minAdvanceMinutes
                            + " minutos de anticipacion"
            );
        }

        booking.setStatus(BookingStatus.CANCELADA);
        booking.setCancelledAt(now);
        booking.setUpdatedAt(now);
        booking.setCancellationReason(cancellationReason);

        Booking saved = bookingRepository.saveAndFlush(booking);

        BookingCancelledEvent event = new BookingCancelledEvent(
                saved.getId(),
                saved.getCustomerId(),
                saved.getProfessionalId(),
                saved.getServiceId(),
                saved.getStartTime(),
                saved.getEndTime(),
                saved.getCancelledAt(),
                saved.getCancellationReason()
        );

        //HU-09 / HU-19:
        //publicar dentro de la transaccion para que la auditoria
        //forme parte del mismo commit que la cancelacion
        eventPublisher.publish(event);

        return saved;
    }
}
