package com.bookly.backendcf.schedule.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookly.backendcf.booking.application.BookingNameDirectory;
import com.bookly.backendcf.booking.domain.model.Booking;
import com.bookly.backendcf.booking.infrastructure.persistence.BookingRepository;
import com.bookly.backendcf.schedule.application.ConfirmedBookingsLookup;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * HU-05: el adaptador que encuentra las reservas confirmadas que impiden un bloqueo.
 *
 * <p>Referencia a los criterios de aceptacion (Gherkin, HU-05 refinada Sprint 2):
 * <ul>
 *   <li>Escenario "Intento de bloquear rango con reservas confirmadas activas": el adaptador
 *       traduce cada reserva confirmada del rango en una cita afectada.</li>
 * </ul>
 */
@Tag("unit")
class BookingConfirmedBookingsLookupTest {

    private final UUID professionalId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID serviceId = UUID.randomUUID();

    /**
     * Rango relativo al momento de ejecucion: el servicio valida contra LocalDateTime.now(),
     * asi que una fecha fija acabaria expirando la suite.
     */
    private LocalDateTime from;
    private LocalDateTime to;

    private BookingRepository bookingRepository;
    private BookingNameDirectory nameDirectory;
    private BookingConfirmedBookingsLookup lookup;

    @BeforeEach
    void setUp() {
        from = LocalDateTime.now().plusDays(30).withHour(8).withMinute(0).withSecond(0).withNano(0);
        to = from.plusHours(4);
        bookingRepository = mock(BookingRepository.class);
        nameDirectory = mock(BookingNameDirectory.class);
        lookup = new BookingConfirmedBookingsLookup(bookingRepository, nameDirectory);
    }

    // Escenario Gherkin HU-05: Intento de bloquear rango con reservas confirmadas activas.
    // Sin reservas en el rango no hay citas afectadas.
    @Test
    void devuelveListaVaciaCuandoNoHayReservasEnElRango() {
        // Arrange
        when(bookingRepository.findOverlappingBookings(professionalId, from, to)).thenReturn(List.of());

        // Act
        List<ConfirmedBookingsLookup.AffectedBooking> affected =
                lookup.findConfirmed(professionalId, from, to);

        // Assert
        assertThat(affected).isEmpty();
        verify(nameDirectory, never()).accountNames(any());
        verify(nameDirectory, never()).serviceNames(any());
    }

    // Escenario Gherkin HU-05: Intento de bloquear rango con reservas confirmadas activas.
    // Cada reserva se traduce con id, rango, cliente y servicio.
    @Test
    void traduceCadaReservaConIdRangoClienteYServicio() {
        // Arrange
        UUID bookingId = UUID.randomUUID();
        LocalDateTime inicio = from.plusHours(1);
        LocalDateTime fin = from.plusHours(2);
        when(bookingRepository.findOverlappingBookings(professionalId, from, to))
                .thenReturn(List.of(booking(bookingId, inicio, fin)));
        when(nameDirectory.accountNames(any())).thenReturn(Map.of(customerId, "Ana Pérez"));
        when(nameDirectory.serviceNames(any())).thenReturn(Map.of(serviceId, "Consulta"));

        // Act
        List<ConfirmedBookingsLookup.AffectedBooking> affected =
                lookup.findConfirmed(professionalId, from, to);

        // Assert
        assertThat(affected).containsExactly(new ConfirmedBookingsLookup.AffectedBooking(
                bookingId, inicio, fin, "Ana Pérez", "Consulta"));
    }

    // QA/Exception: los nombres se resuelven una sola vez por cuenta y por servicio.
    @Test
    void resuelveLosNombresUnaSolaVezPorCuentaYPorServicio() {
        // Arrange
        UUID otroCustomerId = UUID.randomUUID();
        LocalDateTime inicio = from.plusHours(1);
        LocalDateTime fin = from.plusHours(2);
        when(bookingRepository.findOverlappingBookings(professionalId, from, to)).thenReturn(List.of(
                booking(UUID.randomUUID(), inicio, fin),
                bookingConCliente(UUID.randomUUID(), otroCustomerId, inicio, fin)));
        when(nameDirectory.accountNames(any())).thenReturn(Map.of());
        when(nameDirectory.serviceNames(any())).thenReturn(Map.of());

        // Act
        lookup.findConfirmed(professionalId, from, to);

        // Assert
        verify(nameDirectory).accountNames(List.of(customerId, otroCustomerId));
        verify(nameDirectory).serviceNames(List.of(serviceId));
    }

    // QA/Exception: una reserva sin nombre resuelto deja el nombre en null.
    @Test
    void dejaEnNullLosNombresQueNoSeResuelven() {
        // Arrange
        UUID bookingId = UUID.randomUUID();
        LocalDateTime inicio = from.plusHours(1);
        LocalDateTime fin = from.plusHours(2);
        when(bookingRepository.findOverlappingBookings(professionalId, from, to))
                .thenReturn(List.of(booking(bookingId, inicio, fin)));
        when(nameDirectory.accountNames(any())).thenReturn(Map.of());
        when(nameDirectory.serviceNames(any())).thenReturn(Map.of());

        // Act
        List<ConfirmedBookingsLookup.AffectedBooking> affected =
                lookup.findConfirmed(professionalId, from, to);

        // Assert
        assertThat(affected).hasSize(1);
        assertThat(affected.get(0).customerName()).isNull();
        assertThat(affected.get(0).serviceName()).isNull();
    }

    private Booking booking(UUID bookingId, LocalDateTime start, LocalDateTime end) {
        return bookingConCliente(bookingId, customerId, start, end);
    }

    private Booking bookingConCliente(
            UUID bookingId, UUID customer, LocalDateTime start, LocalDateTime end) {
        Booking booking = new Booking(customer, professionalId, serviceId, start, end);
        ReflectionTestUtils.setField(booking, "id", bookingId);
        return booking;
    }
}
