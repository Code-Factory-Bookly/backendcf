package com.bookly.backendcf.booking.application.service;

import com.bookly.backendcf.booking.domain.model.Booking;
import com.bookly.backendcf.booking.infrastructure.persistence.BookingRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;

@Service
public class GetBookingService {

    private final BookingRepository bookingRepository;

    public GetBookingService(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    public Booking getBookingById(UUID id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Booking no encontrado"));
    }

    public List<Booking> getByCustomerId(UUID customerId) {
        return bookingRepository.findByCustomerId(customerId);
    }

    public List<Booking> getByProfessionalId(UUID professionalId) {
        return bookingRepository.findByProfessionalId(professionalId);
    }
}