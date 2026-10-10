
package com.bookly.backendcf.booking.application.service;

import com.bookly.backendcf.booking.domain.events.BookingCancelledEvent;
import com.bookly.backendcf.booking.domain.model.Booking;
import com.bookly.backendcf.booking.domain.model.BookingStatus;
import com.bookly.backendcf.booking.infrastructure.event.BookingEventPublisher;
import com.bookly.backendcf.booking.infrastructure.persistence.BookingRepository;
import com.bookly.backendcf.shared.security.OwnershipGuard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CancelBookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingEventPublisher eventPublisher;

    @Mock
    private OwnershipGuard ownershipGuard;

    private CancelBookingService service;

    private UUID customerId;
    private UUID bookingId;
    private Booking booking;
    private Authentication authentication;

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-10-15T12:00:00Z"),
            ZoneId.of("UTC")
    );

    @BeforeEach
    void setUp() {
        service = new CancelBookingService(
                bookingRepository,
                eventPublisher,
                ownershipGuard,
                120,
                FIXED_CLOCK
        );

        customerId = UUID.randomUUID();
        bookingId = UUID.randomUUID();

        authentication = new UsernamePasswordAuthenticationToken(
                customerId.toString(),
                null,
                List.of(
                        new SimpleGrantedAuthority("ROLE_CUSTOMER")
                )
        );

        LocalDateTime now = LocalDateTime.now(FIXED_CLOCK);

        booking = new Booking(
                customerId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                now.plusHours(3),
                now.plusHours(4)
        );

        ReflectionTestUtils.setField(
                booking,
                "id",
                bookingId
        );
    }

    @Test
    void shouldCancelBookingWithThreeHoursNotice() {
        prepareSuccessfulCancellation();

        Booking result = service.cancelBooking(
                bookingId,
                authentication,
                "Cambio de planes"
        );

        assertEquals(BookingStatus.CANCELADA, result.getStatus());
        assertEquals(
                LocalDateTime.now(FIXED_CLOCK),
                result.getCancelledAt()
        );
        assertEquals(
                "Cambio de planes",
                result.getCancellationReason()
        );

        verify(ownershipGuard)
                .check(customerId, authentication);

        verify(bookingRepository)
                .saveAndFlush(booking);

        ArgumentCaptor<BookingCancelledEvent> eventCaptor =
                ArgumentCaptor.forClass(BookingCancelledEvent.class);

        verify(eventPublisher, times(1))
                .publish(eventCaptor.capture());

        BookingCancelledEvent event = eventCaptor.getValue();

        assertEquals(bookingId, event.getBookingId());
        assertEquals(customerId, event.getCustomerId());
        assertEquals(booking.getProfessionalId(), event.getProfessionalId());
        assertEquals(booking.getServiceId(), event.getServiceId());
        assertEquals(result.getCancelledAt(), event.getCancelledAt());
        assertEquals("Cambio de planes", event.getCancellationReason());
    }

    @Test
    void shouldRejectCancellationWithFortyFiveMinutesNotice() {
        LocalDateTime now = LocalDateTime.now(FIXED_CLOCK);

        booking.setStartTime(now.plusMinutes(45));

        when(bookingRepository.findByIdForUpdate(bookingId))
                .thenReturn(Optional.of(booking));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.cancelBooking(
                        bookingId,
                        authentication,
                        null
                )
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals(BookingStatus.CONFIRMADA, booking.getStatus());

        verify(bookingRepository, never()).saveAndFlush(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void shouldRejectCancellationByAnotherCustomer() {
        UUID anotherCustomer = UUID.randomUUID();

        Authentication otherAuthentication =
                new UsernamePasswordAuthenticationToken(
                        anotherCustomer.toString(),
                        null,
                        List.of(
                                new SimpleGrantedAuthority("ROLE_CUSTOMER")
                        )
                );

        when(bookingRepository.findByIdForUpdate(bookingId))
                .thenReturn(Optional.of(booking));

        assertThrows(
                AccessDeniedException.class,
                () -> service.cancelBooking(
                        bookingId,
                        otherAuthentication,
                        null
                )
        );

        assertEquals(BookingStatus.CONFIRMADA, booking.getStatus());

        verify(bookingRepository, never()).saveAndFlush(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void shouldRejectAlreadyCancelledBooking() {
        booking.setStatus(BookingStatus.CANCELADA);

        when(bookingRepository.findByIdForUpdate(bookingId))
                .thenReturn(Optional.of(booking));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.cancelBooking(
                        bookingId,
                        authentication,
                        null
                )
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());

        verify(bookingRepository, never()).saveAndFlush(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void shouldRejectMissingBooking() {
        when(bookingRepository.findByIdForUpdate(bookingId))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.cancelBooking(
                        bookingId,
                        authentication,
                        null
                )
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void shouldAllowCancellationAt121Minutes() {
        assertCancellationAllowedAtMinutes(121);
    }

    @Test
    void shouldAllowCancellationAtExactly120Minutes() {
        assertCancellationAllowedAtMinutes(120);
    }

    @Test
    void shouldRejectCancellationAt119Minutes() {
        LocalDateTime now = LocalDateTime.now(FIXED_CLOCK);

        booking.setStartTime(now.plusMinutes(119));
        booking.setEndTime(now.plusMinutes(179));

        when(bookingRepository.findByIdForUpdate(bookingId))
                .thenReturn(Optional.of(booking));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.cancelBooking(
                        bookingId,
                        authentication,
                        null
                )
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertTrue(exception.getReason().contains("120 minutos"));
        assertEquals(BookingStatus.CONFIRMADA, booking.getStatus());

        verify(bookingRepository, never()).saveAndFlush(any());
        verifyNoInteractions(eventPublisher);
    }

    private void assertCancellationAllowedAtMinutes(int minutes) {
        LocalDateTime now = LocalDateTime.now(FIXED_CLOCK);

        booking.setStartTime(now.plusMinutes(minutes));
        booking.setEndTime(now.plusMinutes(minutes + 60));

        prepareSuccessfulCancellation();

        Booking result = service.cancelBooking(
                bookingId,
                authentication,
                "Cancelacion dentro del plazo"
        );

        assertEquals(BookingStatus.CANCELADA, result.getStatus());
        assertEquals(now, result.getCancelledAt());

        verify(bookingRepository).saveAndFlush(booking);

        verify(eventPublisher, times(1))
                .publish(isA(BookingCancelledEvent.class));
    }

    private void prepareSuccessfulCancellation() {
        when(bookingRepository.findByIdForUpdate(bookingId))
                .thenReturn(Optional.of(booking));

        when(bookingRepository.saveAndFlush(booking))
                .thenReturn(booking);
    }
}
