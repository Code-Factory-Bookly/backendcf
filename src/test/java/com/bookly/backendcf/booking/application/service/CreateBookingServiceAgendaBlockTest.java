package com.bookly.backendcf.booking.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookly.backendcf.booking.application.exception.BookingConflictException;
import com.bookly.backendcf.booking.domain.model.Booking;
import com.bookly.backendcf.booking.domain.model.BookingStatus;
import com.bookly.backendcf.booking.infrastructure.event.BookingEventPublisher;
import com.bookly.backendcf.booking.infrastructure.persistence.BookingRepository;
import com.bookly.backendcf.schedule.application.AgendaBlocks;
import com.bookly.backendcf.schedule.domain.model.WeeklySlot;
import com.bookly.backendcf.schedule.infrastructure.persistence.WeeklySlotRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * HU-05: el efecto de los bloqueos de agenda en la creacion de reservas.
 * Escenario Gherkin HU-05: Bloquear franja sin reservas previas (el bloqueo impide reservar).
 * No duplica los casos ya cubiertos por CreateBookingServiceTest.
 */
@Tag("unit")
class CreateBookingServiceAgendaBlockTest {

    private final UUID customerId = UUID.randomUUID();
    private final UUID professionalId = UUID.randomUUID();
    private final UUID serviceId = UUID.randomUUID();

    /**
     * Rango relativo al momento de ejecucion: el servicio valida contra LocalDateTime.now(),
     * asi que una fecha fija acabaria expirando la suite.
     */
    private LocalDateTime inicio;
    private LocalDateTime fin;

    private BookingRepository bookingRepository;
    private BookingEventPublisher eventPublisher;
    private WeeklySlotRepository weeklySlotRepository;
    private AgendaBlocks agendaBlocks;
    private CreateBookingService service;

    @BeforeEach
    void setUp() {
        inicio = LocalDateTime.now().plusDays(30).withHour(9).withMinute(0).withSecond(0).withNano(0);
        fin = inicio.plusHours(1);
        bookingRepository = mock(BookingRepository.class);
        eventPublisher = mock(BookingEventPublisher.class);
        weeklySlotRepository = mock(WeeklySlotRepository.class);
        agendaBlocks = mock(AgendaBlocks.class);
        service = new CreateBookingService(
                bookingRepository, eventPublisher, weeklySlotRepository, agendaBlocks);
    }

    // Escenario Gherkin HU-05: Bloquear franja sin reservas previas (el bloqueo impide reservar).
    @Test
    void rechazaLaReservaCuandoElRangoEstaBloqueado() {
        // Arrange
        when(agendaBlocks.findOverlapping(professionalId, inicio, fin)).thenReturn(List.of(
                new AgendaBlocks.BlockedPeriod(inicio, fin)));
        stubScheduleContaining();

        // Act
        assertThatThrownBy(() -> service.createBooking(
                customerId, professionalId, serviceId, inicio, fin))
                .isInstanceOfSatisfying(BookingConflictException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo("SLOT_BLOCKED");
                    assertThat(exception.getMessage()).contains("bloqueó");
                });

        // Assert
        verify(bookingRepository, never()).saveAndFlush(any(Booking.class));
        verifyNoEventPublished();
    }

    // Escenario Gherkin HU-05: Bloquear franja sin reservas previas (el bloqueo impide reservar).
    @Test
    void informaElCodigoDeErrorDelBloqueoAlCliente() {
        // Arrange
        when(agendaBlocks.findOverlapping(professionalId, inicio, fin)).thenReturn(List.of(
                new AgendaBlocks.BlockedPeriod(inicio.minusHours(1), fin.plusHours(1))));
        stubScheduleContaining();

        // Act
        assertThatThrownBy(() -> service.createBooking(
                customerId, professionalId, serviceId, inicio, fin))
                .isInstanceOfSatisfying(BookingConflictException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo("SLOT_BLOCKED"));

        // Assert
        verify(bookingRepository, never()).saveAndFlush(any(Booking.class));
        verifyNoEventPublished();
    }

    // Escenario Gherkin HU-05: Bloquear franja sin reservas previas (el bloqueo impide reservar).
    @Test
    void rechazaLaReservaQueSeSolapaAunqueSeaEnUnBorde() {
        // Arrange
        when(agendaBlocks.findOverlapping(professionalId, inicio, fin)).thenReturn(List.of(
                new AgendaBlocks.BlockedPeriod(inicio.minusMinutes(30), inicio.plusMinutes(30))));
        stubScheduleContaining();

        // Act
        assertThatThrownBy(() -> service.createBooking(
                customerId, professionalId, serviceId, inicio, fin))
                .isInstanceOfSatisfying(BookingConflictException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("SLOT_BLOCKED"));

        // Assert
        verify(bookingRepository, never()).saveAndFlush(any(Booking.class));
        verifyNoEventPublished();
    }

    // Escenario Gherkin HU-05: Bloquear franja sin reservas previas (el rango es [inicio, fin)).
    @Test
    void aceptaLaReservaContiguaAlBloqueo() {
        // Arrange
        when(agendaBlocks.findOverlapping(professionalId, inicio, fin)).thenReturn(List.of());
        stubScheduleContaining();
        when(bookingRepository.findOverlappingBookings(professionalId, inicio, fin)).thenReturn(List.of());
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Booking result = service.createBooking(customerId, professionalId, serviceId, inicio, fin);

        // Assert
        assertThat(result.getStatus()).isEqualTo(BookingStatus.CONFIRMADA);
        verify(bookingRepository).saveAndFlush(any(Booking.class));
    }

    // Escenario Gherkin HU-05: Bloquear franja sin reservas previas (sin bloqueos la reserva se crea).
    @Test
    void creaLaReservaCuandoNoHayBloqueos() {
        // Arrange
        when(agendaBlocks.findOverlapping(professionalId, inicio, fin)).thenReturn(List.of());
        stubScheduleContaining();
        when(bookingRepository.findOverlappingBookings(professionalId, inicio, fin)).thenReturn(List.of());
        Booking saved = new Booking(customerId, professionalId, serviceId, inicio, fin);
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenReturn(saved);

        // Act
        Booking result = service.createBooking(customerId, professionalId, serviceId, inicio, fin);

        // Assert
        assertThat(result).isSameAs(saved);
        assertThat(result.getStatus()).isEqualTo(BookingStatus.CONFIRMADA);
        verify(bookingRepository).saveAndFlush(any(Booking.class));
    }

    // Escenario Gherkin HU-05: Bloquear franja sin reservas previas (el bloqueo se consulta con el rango).
    @Test
    void consultaLosBloqueosConElProfesionalYElRangoDeLaReserva() {
        // Arrange
        when(agendaBlocks.findOverlapping(professionalId, inicio, fin)).thenReturn(List.of());
        stubScheduleContaining();
        when(bookingRepository.findOverlappingBookings(professionalId, inicio, fin)).thenReturn(List.of());
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        service.createBooking(customerId, professionalId, serviceId, inicio, fin);

        // Assert
        verify(agendaBlocks).findOverlapping(professionalId, inicio, fin);
    }

    // Escenario Gherkin HU-05: Bloquear franja sin reservas previas (el bloqueo se comprueba primero).
    @Test
    void compruebaElBloqueoAntesDeBuscarReservasSolapadas() {
        // Arrange
        when(agendaBlocks.findOverlapping(professionalId, inicio, fin)).thenReturn(List.of(
                new AgendaBlocks.BlockedPeriod(inicio, fin)));
        stubScheduleContaining();

        // Act
        assertThatThrownBy(() -> service.createBooking(
                customerId, professionalId, serviceId, inicio, fin))
                .isInstanceOf(BookingConflictException.class);

        // Assert
        verify(bookingRepository, never()).findOverlappingBookings(any(), any(), any());
    }

    // Escenario Gherkin HU-05: Bloquear franja sin reservas previas (SLOT_BLOCKED, no SLOT_OCUPADO).
    @Test
    void elConflictoDeBloqueoNoSeConfundeConElDeFranjaOcupada() {
        // Arrange
        when(agendaBlocks.findOverlapping(professionalId, inicio, fin)).thenReturn(List.of(
                new AgendaBlocks.BlockedPeriod(inicio, fin)));
        stubScheduleContaining();
        // La franja tambien esta ocupada por otra reserva: el bloqueo se reporta primero.
        when(bookingRepository.findOverlappingBookings(professionalId, inicio, fin)).thenReturn(List.of(
                new Booking(customerId, professionalId, serviceId, inicio, fin)));

        // Act
        assertThatThrownBy(() -> service.createBooking(
                customerId, professionalId, serviceId, inicio, fin))
                .isInstanceOfSatisfying(BookingConflictException.class, exception ->
                        assertThat(exception.getErrorCode()).isNotEqualTo("SLOT_OCUPADO"));

        // Assert
        verify(bookingRepository, never()).saveAndFlush(any(Booking.class));
    }

    private void stubScheduleContaining() {
        when(weeklySlotRepository.findByProfessionalIdOrderByDayOfWeekAscStartTimeAsc(professionalId))
                .thenReturn(List.of(new WeeklySlot(
                        professionalId,
                        inicio.getDayOfWeek(),
                        inicio.toLocalTime().minusHours(1),
                        fin.toLocalTime().plusHours(1))));
    }

    private void verifyNoEventPublished() {
        verify(eventPublisher, never()).publish(any());
    }
}
