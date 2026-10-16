
package com.bookly.backendcf.booking.application.service;

import com.bookly.backendcf.booking.application.exception.BookingConflictException;
import com.bookly.backendcf.booking.domain.model.Booking;
import com.bookly.backendcf.booking.domain.model.BookingStatus;
import com.bookly.backendcf.booking.domain.events.BookingCreatedEvent;
import com.bookly.backendcf.booking.infrastructure.event.BookingEventPublisher;
import com.bookly.backendcf.booking.infrastructure.persistence.BookingRepository;
import com.bookly.backendcf.schedule.domain.model.WeeklySlot;
import com.bookly.backendcf.schedule.infrastructure.persistence.WeeklySlotRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
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

    @Mock
    private WeeklySlotRepository weeklySlotRepository;

    private CreateBookingService service;
    private UUID customerId;
    private UUID professionalId;
    private UUID serviceId;

    @BeforeEach
    void setUp() {
        service = new CreateBookingService(
                bookingRepository,
                eventPublisher,
                weeklySlotRepository
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
        stubScheduleContaining(startTime, endTime);

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
        stubScheduleContaining(startTime, endTime);

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
    void shouldRejectBookingOutsideProfessionalSchedule() {
        LocalDateTime startTime = LocalDateTime.now()
                .plusDays(1)
                .withHour(15)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);
        LocalDateTime endTime = startTime.plusHours(1);

        when(weeklySlotRepository
                .findByProfessionalIdOrderByDayOfWeekAscStartTimeAsc(
                        professionalId
                ))
                .thenReturn(List.of(new WeeklySlot(
                        professionalId,
                        startTime.getDayOfWeek(),
                        LocalTime.of(9, 0),
                        LocalTime.of(12, 0)
                )));

        BookingConflictException exception = assertThrows(
                BookingConflictException.class,
                () -> service.createBooking(
                        customerId,
                        professionalId,
                        serviceId,
                        startTime,
                        endTime
                )
        );

        assertEquals("SLOT_OUTSIDE_SCHEDULE", exception.getErrorCode());
        verifyNoInteractions(bookingRepository, eventPublisher);
    }

    @Test
    void shouldAcceptBookingContainedInProfessionalSchedule() {
        LocalDateTime startTime = LocalDateTime.now()
                .plusDays(1)
                .withHour(10)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);
        LocalDateTime endTime = startTime.plusHours(1);

        stubScheduleContaining(startTime, endTime);
        when(bookingRepository.findOverlappingBookings(
                professionalId, startTime, endTime
        )).thenReturn(Collections.emptyList());

        Booking savedBooking = new Booking(
                customerId,
                professionalId,
                serviceId,
                startTime,
                endTime
        );
        when(bookingRepository.saveAndFlush(any(Booking.class)))
                .thenReturn(savedBooking);

        Booking result = service.createBooking(
                customerId,
                professionalId,
                serviceId,
                startTime,
                endTime
        );

        assertEquals(BookingStatus.CONFIRMADA, result.getStatus());
        verify(bookingRepository).saveAndFlush(any(Booking.class));
    }

    private void stubScheduleContaining(
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        when(weeklySlotRepository
                .findByProfessionalIdOrderByDayOfWeekAscStartTimeAsc(
                        professionalId
                ))
                .thenReturn(List.of(new WeeklySlot(
                        professionalId,
                        startTime.getDayOfWeek(),
                        startTime.toLocalTime().minusHours(1),
                        endTime.toLocalTime().plusHours(1)
                )));
    }
}
