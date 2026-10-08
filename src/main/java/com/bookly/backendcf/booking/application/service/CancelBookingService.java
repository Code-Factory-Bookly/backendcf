package com.bookly.backendcf.booking.application.service;

import com.bookly.backendcf.booking.domain.events.BookingCancelledEvent;
import com.bookly.backendcf.booking.domain.model.Booking;
import com.bookly.backendcf.booking.domain.model.BookingStatus;
import com.bookly.backendcf.booking.infrastructure.event.BookingEventPublisher;
import com.bookly.backendcf.booking.infrastructure.persistence.BookingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class CancelBookingService {

    private final BookingRepository bookingRepository;
    private final BookingEventPublisher eventPublisher;

    public CancelBookingService(BookingRepository bookingRepository,
                                BookingEventPublisher eventPublisher) {
        this.bookingRepository = bookingRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Booking cancelBooking(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking no encontrado"));

        if (booking.getStatus() == BookingStatus.CANCELADA) {
            throw new IllegalArgumentException("Booking ya fue cancelado");
        }

        booking.setStatus(BookingStatus.CANCELADA);
        booking.setCancelledAt(LocalDateTime.now());
        booking.setUpdatedAt(LocalDateTime.now());

        Booking saved = bookingRepository.save(booking);

        //publicar evento :)
        BookingCancelledEvent event = new BookingCancelledEvent(
                saved.getId(),
                "Cancelado por el cliente"
        );
        eventPublisher.publish(event);

        return saved;
    }
}