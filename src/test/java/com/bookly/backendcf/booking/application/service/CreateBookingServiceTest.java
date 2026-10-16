
package com.bookly.backendcf.booking.application.service;

import com.bookly.backendcf.booking.application.exception.BookingConflictException;
import com.bookly.backendcf.booking.domain.model.Booking;
import com.bookly.backendcf.booking.domain.model.BookingStatus;
import com.bookly.backendcf.booking.domain.events.BookingCreatedEvent;
import com.bookly.backendcf.booking.infrastructure.event.BookingEventPublisher;
import com.bookly.backendcf.booking.infrastructure.persistence.BookingRepository;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateBookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingEventPublisher eventPublisher;

    private CreateBookingService service;
    private UUID customerId;
    private UUID professionalId;
    private UUID serviceId;

    @BeforeEach
    void setUp() {
        service = new CreateBookingService(
                bookingRepository,
                eventPublisher
        );

        customerId = UUID.randomUUID();
        professionalId = UUID.randomUUID();
        serviceId = UUID.randomUUID();
    }

    @Test
    void shouldCreateBookingSuccessfully() {
        LocalDateTime startTime =
                LocalDateTime.now().plusDays(1);
        LocalDateTime endTime =
                startTime.plusMinutes(45);

        when(bookingRepository.findOverlappingBookings(
                professionalId, startTime, endTime
        )).thenReturn(Collections.emptyList());

        Booking mockBooking = new Booking(
                customerId,
                professionalId,
                serviceId,
                startTime,
                endTime
        );

        when(bookingRepository.saveAndFlush(
                any(Booking.class)
        )).thenReturn(mockBooking);

        Booking result = service.createBooking(
                customerId,
                professionalId,
                serviceId,
                startTime,
                endTime
        );

        assertNotNull(result);
        assertEquals(customerId, result.getCustomerId());
        assertEquals(
                BookingStatus.CONFIRMADA,
                result.getStatus()
        );

        verify(bookingRepository, times(1))
                .saveAndFlush(any(Booking.class));

        verify(eventPublisher, times(1))
                .publish(any(BookingCreatedEvent.class));
    }

    @Test
    void shouldRejectIfStartTimeAfterEndTime() {
        LocalDateTime startTime =
                LocalDateTime.now().plusDays(2);
        LocalDateTime endTime =
                startTime.minusMinutes(30);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.createBooking(
                        customerId,
                        professionalId,
                        serviceId,
                        startTime,
                        endTime
                )
        );

        verifyNoInteractions(bookingRepository);
    }

    @Test
    void shouldRejectIfStartTimeIsNull() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.createBooking(
                        customerId,
                        professionalId,
                        serviceId,
                        null,
                        LocalDateTime.now().plusDays(1)
                )
        );

        verifyNoInteractions(bookingRepository);
    }

    @Test
    void shouldRejectIfEndTimeIsNull() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.createBooking(
                        customerId,
                        professionalId,
                        serviceId,
                        LocalDateTime.now().plusDays(1),
                        null
                )
        );

        verifyNoInteractions(bookingRepository);
    }

    @Test
    void shouldRejectIfStartTimeInPast() {
        LocalDateTime startTime =
                LocalDateTime.now().minusDays(1);
        LocalDateTime endTime =
                startTime.plusMinutes(45);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.createBooking(
                        customerId,
                        professionalId,
                        serviceId,
                        startTime,
                        endTime
                )
        );

        verifyNoInteractions(bookingRepository);
    }

    @Test
    void shouldRejectIfSlotOccupied() {
        LocalDateTime startTime =
                LocalDateTime.now().plusDays(1);
        LocalDateTime endTime =
                startTime.plusMinutes(45);

        Booking existingBooking = new Booking(
                customerId,
                professionalId,
                serviceId,
                startTime,
                endTime
        );

        when(bookingRepository.findOverlappingBookings(
                professionalId, startTime, endTime
        )).thenReturn(
                Collections.singletonList(existingBooking)
        );

        assertThrows(
                BookingConflictException.class,
                () -> service.createBooking(
                        customerId,
                        professionalId,
                        serviceId,
                        startTime,
                        endTime
                )
        );

        verify(bookingRepository, never())
                .saveAndFlush(any(Booking.class));

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void shouldTranslateOverlapConstraintViolationToSlotOccupied() {
        LocalDateTime startTime = LocalDateTime.now().plusDays(1);
        LocalDateTime endTime = startTime.plusMinutes(45);

        when(bookingRepository.findOverlappingBookings(
                professionalId, startTime, endTime
        )).thenReturn(Collections.emptyList());
        doThrow(new DataIntegrityViolationException(
                "booking_no_overlapping_confirmed"
        )).when(bookingRepository).saveAndFlush(any(Booking.class));

        BookingConflictException exception = assertThrows(
                BookingConflictException.class,
                () -> service.createBooking(
                        customerId, professionalId, serviceId, startTime, endTime
                )
        );

        assertEquals("SLOT_OCUPADO", exception.getErrorCode());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void shouldTranslateUniqueConstraintViolationToSlotOccupied() {
        LocalDateTime startTime = LocalDateTime.now().plusDays(1);
        LocalDateTime endTime = startTime.plusMinutes(45);

        when(bookingRepository.findOverlappingBookings(
                professionalId, startTime, endTime
        )).thenReturn(Collections.emptyList());
        doThrow(new DataIntegrityViolationException(
                "duplicate booking",
                new ConstraintViolationException(
                        "duplicate booking",
                        null,
                        "unique_booking_per_professional_time"
                )
        )).when(bookingRepository).saveAndFlush(any(Booking.class));

        BookingConflictException exception = assertThrows(
                BookingConflictException.class,
                () -> service.createBooking(
                        customerId, professionalId, serviceId, startTime, endTime
                )
        );

        assertEquals("SLOT_OCUPADO", exception.getErrorCode());
    }

    @Test
    void shouldTranslateDeadlockToSlotOccupied() {
        LocalDateTime startTime = LocalDateTime.now().plusDays(1);
        LocalDateTime endTime = startTime.plusMinutes(45);

        when(bookingRepository.findOverlappingBookings(
                professionalId, startTime, endTime
        )).thenReturn(Collections.emptyList());
        doThrow(new CannotAcquireLockException("deadlock detected"))
                .when(bookingRepository).saveAndFlush(any(Booking.class));

        BookingConflictException exception = assertThrows(
                BookingConflictException.class,
                () -> service.createBooking(
                        customerId, professionalId, serviceId, startTime, endTime
                )
        );

        assertEquals("SLOT_OCUPADO", exception.getErrorCode());
    }

    @Test
    void shouldPropagateUnrelatedIntegrityViolation() {
        LocalDateTime startTime = LocalDateTime.now().plusDays(1);
        LocalDateTime endTime = startTime.plusMinutes(45);
        DataIntegrityViolationException original =
                new DataIntegrityViolationException("foreign key violation");

        when(bookingRepository.findOverlappingBookings(
                professionalId, startTime, endTime
        )).thenReturn(Collections.emptyList());
        doThrow(original).when(bookingRepository)
                .saveAndFlush(any(Booking.class));

        assertSame(
                original,
                assertThrows(
                        DataIntegrityViolationException.class,
                        () -> service.createBooking(
                                customerId, professionalId, serviceId, startTime, endTime
                        )
                )
        );
    }

    @Test
    void shouldPropagateViolationFromAnotherConstraint() {
        LocalDateTime startTime = LocalDateTime.now().plusDays(1);
        LocalDateTime endTime = startTime.plusMinutes(45);
        DataIntegrityViolationException original =
                new DataIntegrityViolationException(
                        "other constraint",
                        new ConstraintViolationException(
                                "other constraint",
                                null,
                                "other_constraint"
                        )
                );

        when(bookingRepository.findOverlappingBookings(
                professionalId, startTime, endTime
        )).thenReturn(Collections.emptyList());
        doThrow(original).when(bookingRepository)
                .saveAndFlush(any(Booking.class));

        assertSame(
                original,
                assertThrows(
                        DataIntegrityViolationException.class,
                        () -> service.createBooking(
                                customerId, professionalId, serviceId, startTime, endTime
                        )
                )
        );
    }
}
