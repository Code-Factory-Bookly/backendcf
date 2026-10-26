package com.bookly.backendcf.notification.infrastructure.persistence;

import com.bookly.backendcf.booking.infrastructure.persistence.BookingRepository;
import com.bookly.backendcf.notification.application.BookingParticipantsLookup;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BookingParticipantsLookupAdapter implements BookingParticipantsLookup {

    private final BookingRepository bookingRepository;

    public BookingParticipantsLookupAdapter(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Participants> find(UUID bookingId) {
        return bookingRepository.findById(bookingId)
                .map(booking -> new Participants(booking.getCustomerId(), booking.getProfessionalId()));
    }
}
