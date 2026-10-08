package com.bookly.backendcf.booking.application.service;

import com.bookly.backendcf.booking.domain.events.BookingRescheduledEvent;
import com.bookly.backendcf.booking.domain.model.Booking;
import com.bookly.backendcf.booking.infrastructure.event.BookingEventPublisher;
import com.bookly.backendcf.booking.infrastructure.persistence.BookingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class RescheduleBookingService {

    private final BookingRepository bookingRepository;
    private final BookingEventPublisher eventPublisher;

    public RescheduleBookingService(BookingRepository bookingRepository,
                                    BookingEventPublisher eventPublisher) {
        this.bookingRepository = bookingRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Booking rescheduleBooking(UUID bookingId, LocalDateTime newStartTime, LocalDateTime newEndTime) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking no encontrado"));

        //validar nuevos tiempos :)
        if (newStartTime.isAfter(newEndTime)) {
            throw new IllegalArgumentException("startTime debe ser antes que endTime");
        }

        if (newStartTime.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("No se puede reprogramar a tiempos pasados");
        }

        //verificar disponibilidad en nuevo horario :)
        var overlapping = bookingRepository.findOverlappingBookings(
                booking.getProfessionalId(), newStartTime, newEndTime);

        if (overlapping.stream().anyMatch(b -> !b.getId().equals(bookingId))) {
            throw new IllegalArgumentException("Slot no disponible en el nuevo horario");
        }

        //guardar tiempos antiguos antes de actualizar :)
        LocalDateTime oldStartTime = booking.getStartTime();
        LocalDateTime oldEndTime = booking.getEndTime();

        //actualizar times :)
        booking.setStartTime(newStartTime);
        booking.setEndTime(newEndTime);
        booking.setUpdatedAt(LocalDateTime.now());

        Booking saved = bookingRepository.save(booking);

        //publicar el evento :)
        BookingRescheduledEvent event = new BookingRescheduledEvent(
                saved.getId(),
                oldStartTime,
                oldEndTime,
                newStartTime,
                newEndTime
        );
        eventPublisher.publish(event);

        return saved;
    }
}