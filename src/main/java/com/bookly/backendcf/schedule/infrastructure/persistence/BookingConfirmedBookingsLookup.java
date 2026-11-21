package com.bookly.backendcf.schedule.infrastructure.persistence;

import com.bookly.backendcf.booking.application.BookingNameDirectory;
import com.bookly.backendcf.booking.domain.model.Booking;
import com.bookly.backendcf.booking.infrastructure.persistence.BookingRepository;
import com.bookly.backendcf.schedule.application.ConfirmedBookingsLookup;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BookingConfirmedBookingsLookup implements ConfirmedBookingsLookup {

    private final BookingRepository bookingRepository;
    private final BookingNameDirectory nameDirectory;

    public BookingConfirmedBookingsLookup(BookingRepository bookingRepository, BookingNameDirectory nameDirectory) {
        this.bookingRepository = bookingRepository;
        this.nameDirectory = nameDirectory;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AffectedBooking> findConfirmed(UUID professionalId, LocalDateTime from, LocalDateTime to) {
        List<Booking> bookings = bookingRepository.findOverlappingBookings(professionalId, from, to);
        if (bookings.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> customers = nameDirectory.accountNames(
                bookings.stream().map(Booking::getCustomerId).distinct().toList());
        Map<UUID, String> services = nameDirectory.serviceNames(
                bookings.stream().map(Booking::getServiceId).distinct().toList());
        return bookings.stream()
                .map(b -> new AffectedBooking(
                        b.getId(), b.getStartTime(), b.getEndTime(),
                        customers.get(b.getCustomerId()), services.get(b.getServiceId())))
                .toList();
    }
}
