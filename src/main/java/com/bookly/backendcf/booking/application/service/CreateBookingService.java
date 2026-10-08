package com.bookly.backendcf.booking.application.service;

import com.bookly.backendcf.booking.application.exception.BookingConflictException;
import com.bookly.backendcf.booking.domain.events.BookingCreatedEvent;
import com.bookly.backendcf.booking.domain.model.Booking;
import com.bookly.backendcf.booking.domain.model.BookingStatus;
import com.bookly.backendcf.booking.infrastructure.persistence.BookingRepository;
import com.bookly.backendcf.booking.infrastructure.event.BookingEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class CreateBookingService {

    private final BookingRepository bookingRepository;
    private final BookingEventPublisher eventPublisher;

    public CreateBookingService(BookingRepository bookingRepository, BookingEventPublisher eventPublisher) {
        this.bookingRepository = bookingRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Booking createBooking(UUID customerId, UUID professionalId, UUID serviceId,
                                 LocalDateTime startTime, LocalDateTime endTime) {

        // Validaciones de negocio
        validateTimeRange(startTime, endTime);
        validateFutureTime(startTime);

        // Verificar solapamientos ANTES de intentar guardar
        // La BD es la fuente de verdad con el UNIQUE constraint
        List<Booking> overlapping = bookingRepository.findOverlappingBookings(professionalId, startTime, endTime);
        if (!overlapping.isEmpty()) {
            throw new BookingConflictException("SLOT_OCUPADO", "El profesional ya tiene una reserva en ese horario");
        }

        try {
            // Crear entidad
            Booking booking = new Booking(customerId, professionalId, serviceId, startTime, endTime);
            booking.setStatus(BookingStatus.CONFIRMADA);

            // Guardar en BD - aquí es donde el UNIQUE constraint previene race conditions
            // Si otro thread intenta la misma franja simultáneamente, la BD rechazará una de las dos
            Booking saved = bookingRepository.save(booking);

            // Publicar evento
            BookingCreatedEvent event = new BookingCreatedEvent(
                    saved.getId(),
                    saved.getCustomerId(),
                    saved.getProfessionalId(),
                    saved.getServiceId(),
                    saved.getStartTime(),
                    saved.getEndTime()
            );
            eventPublisher.publish(event);

            return saved;

        } catch (Exception e) {
            // El UNIQUE constraint de la BD lanza una excepción cuando hay duplicado
            if (e.getMessage() != null && e.getMessage().contains("unique_booking_per_professional_time")) {
                throw new BookingConflictException("SLOT_OCUPADO", "El profesional ya tiene una reserva en ese horario");
            }
            throw e;
        }
    }

    private void validateTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime.isAfter(endTime) || startTime.equals(endTime)) {
            throw new IllegalArgumentException("startTime debe ser antes que endTime");
        }
    }

    private void validateFutureTime(LocalDateTime startTime) {
        if (startTime.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("No puedes agendar en el pasado");
        }
    }
}