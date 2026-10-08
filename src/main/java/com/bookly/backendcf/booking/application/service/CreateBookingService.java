
package com.bookly.backendcf.booking.application.service;

import com.bookly.backendcf.booking.application.exception.BookingConflictException;
import com.bookly.backendcf.booking.domain.events.BookingCreatedEvent;
import com.bookly.backendcf.booking.domain.model.Booking;
import com.bookly.backendcf.booking.domain.model.BookingStatus;
import com.bookly.backendcf.booking.infrastructure.persistence.BookingRepository;
import com.bookly.backendcf.booking.infrastructure.event.BookingEventPublisher;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class CreateBookingService {

    private static final String SLOT_OCCUPIED = "SLOT_OCUPADO";
    private static final String SLOT_OCCUPIED_MESSAGE =
            "El horario seleccionado ya no está disponible";

    private static final String BOOKING_UNIQUE_CONSTRAINT =
            "unique_booking_per_professional_time";

    private final BookingRepository bookingRepository;
    private final BookingEventPublisher eventPublisher;

    public CreateBookingService(
            BookingRepository bookingRepository,
            BookingEventPublisher eventPublisher
    ) {
        this.bookingRepository = bookingRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Booking createBooking(
            UUID customerId,
            UUID professionalId,
            UUID serviceId,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {

        validateTimeRange(startTime, endTime);
        validateFutureTime(startTime);

        List<Booking> overlapping =
                bookingRepository.findOverlappingBookings(
                        professionalId,
                        startTime,
                        endTime
                );

        if (!overlapping.isEmpty()) {
            throw slotOccupied();
        }

        try {
            Booking booking = new Booking(
                    customerId,
                    professionalId,
                    serviceId,
                    startTime,
                    endTime
            );

            booking.setStatus(BookingStatus.CONFIRMADA);

            // Forzar INSERT y detectar errores de unicidad
            // dentro del bloque try/catch.
            Booking saved =
                    bookingRepository.saveAndFlush(booking);

            BookingCreatedEvent event =
                    new BookingCreatedEvent(
                            saved.getId(),
                            saved.getCustomerId(),
                            saved.getProfessionalId(),
                            saved.getServiceId(),
                            saved.getStartTime(),
                            saved.getEndTime()
                    );

            eventPublisher.publish(event);

            return saved;

        } catch (DataIntegrityViolationException e) {

            if (isBookingUniqueConstraintViolation(e)) {
                throw slotOccupied();
            }

            throw e;
        }
    }

    private BookingConflictException slotOccupied() {
        return new BookingConflictException(
                SLOT_OCCUPIED,
                SLOT_OCCUPIED_MESSAGE
        );
    }

    private boolean isBookingUniqueConstraintViolation(
            Throwable exception
    ) {
        Throwable current = exception;

        while (current != null) {

            if (current instanceof
                    org.hibernate.exception.ConstraintViolationException) {

                org.hibernate.exception.ConstraintViolationException
                        constraintException =
                        (org.hibernate.exception.ConstraintViolationException)
                                current;

                String constraintName =
                        constraintException.getConstraintName();

                if (constraintName != null
                        && constraintName.equalsIgnoreCase(
                        BOOKING_UNIQUE_CONSTRAINT)) {
                    return true;
                }
            }

            String message = current.getMessage();

            if (message != null
                    && message.toLowerCase(
                    java.util.Locale.ROOT
            ).contains(
                    BOOKING_UNIQUE_CONSTRAINT
            )) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }

    private void validateTimeRange(
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException(
                    "Las fechas de inicio y fin son obligatorias"
            );
        }

        if (!startTime.isBefore(endTime)) {
            throw new IllegalArgumentException(
                    "startTime debe ser antes que endTime"
            );
        }
    }

    private void validateFutureTime(
            LocalDateTime startTime
    ) {
        if (startTime.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException(
                    "No puedes agendar en el pasado"
            );
        }
    }
}
