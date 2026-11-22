package com.bookly.backendcf.availability.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookly.backendcf.availability.domain.model.BusyPeriod;
import com.bookly.backendcf.booking.domain.model.Booking;
import com.bookly.backendcf.booking.infrastructure.persistence.BookingRepository;
import com.bookly.backendcf.catalog.infrastructure.persistence.ServiceOfferingRepository;
import com.bookly.backendcf.professional.infrastructure.persistence.ProfessionalRepository;
import com.bookly.backendcf.schedule.application.AgendaBlocks;
import com.bookly.backendcf.schedule.infrastructure.persistence.WeeklySlotRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * HU-05: el bloqueo de agenda ocupa el tiempo igual que una reserva confirmada.
 *
 * <p>Referencia a los criterios de aceptacion (Gherkin, HU-05 refinada Sprint 2):
 * <ul>
 *   <li>Escenario "Bloquear una franja sin reservas previas": el sistema deja de ofrecer esas
 *       franjas a los clientes de forma inmediata (el bloqueo se trata como periodo ocupado).</li>
 * </ul>
 */
@Tag("unit")
class AvailabilityQueryAdapterAgendaBlockTest {

    private final UUID professionalId = UUID.randomUUID();
    private final UUID serviceId = UUID.randomUUID();

    /**
     * Rango relativo al momento de ejecucion: una fecha fija acabaria expirando la suite.
     */
    private LocalDateTime from;
    private LocalDateTime to;

    private BookingRepository bookingRepository;
    private AgendaBlocks agendaBlocks;
    private AvailabilityQueryAdapter adapter;

    @BeforeEach
    void setUp() {
        from = LocalDateTime.now().plusDays(30).withHour(0).withMinute(0).withSecond(0).withNano(0);
        to = from.plusDays(1);
        bookingRepository = mock(BookingRepository.class);
        agendaBlocks = mock(AgendaBlocks.class);
        adapter = new AvailabilityQueryAdapter(
                mock(ProfessionalRepository.class),
                mock(ServiceOfferingRepository.class),
                mock(WeeklySlotRepository.class),
                bookingRepository,
                agendaBlocks);
    }

    // Escenario Gherkin HU-05: Bloquear una franja sin reservas previas.
    // Un bloqueo se traduce en un periodo ocupado.
    @Test
    void convierteElBloqueoEnUnPeriodoOcupado() {
        // Arrange
        LocalDateTime inicio = from.plusHours(9);
        LocalDateTime fin = from.plusHours(10);
        when(bookingRepository.findOverlappingBookings(professionalId, from, to)).thenReturn(List.of());
        when(agendaBlocks.findOverlapping(professionalId, from, to)).thenReturn(List.of(
                new AgendaBlocks.BlockedPeriod(inicio, fin)));

        // Act
        List<BusyPeriod> periods = adapter.findBusyPeriods(professionalId, from, to);

        // Assert
        assertThat(periods).containsExactly(new BusyPeriod(inicio, fin));
    }

    // Escenario Gherkin HU-05: Bloquear una franja sin reservas previas.
    // Los bloqueos se suman a las reservas confirmadas.
    @Test
    void sumaLosBloqueosALasReservasConfirmadas() {
        // Arrange
        LocalDateTime reservaInicio = from.plusHours(9);
        LocalDateTime reservaFin = from.plusHours(10);
        LocalDateTime bloqueoInicio = from.plusHours(14);
        LocalDateTime bloqueoFin = from.plusHours(15);
        when(bookingRepository.findOverlappingBookings(professionalId, from, to)).thenReturn(List.of(
                new Booking(UUID.randomUUID(), professionalId, serviceId, reservaInicio, reservaFin)));
        when(agendaBlocks.findOverlapping(professionalId, from, to)).thenReturn(List.of(
                new AgendaBlocks.BlockedPeriod(bloqueoInicio, bloqueoFin)));

        // Act
        List<BusyPeriod> periods = adapter.findBusyPeriods(professionalId, from, to);

        // Assert
        assertThat(periods).containsExactly(
                new BusyPeriod(reservaInicio, reservaFin),
                new BusyPeriod(bloqueoInicio, bloqueoFin));
    }

    // Escenario Gherkin HU-05: Bloquear una franja sin reservas previas.
    // Sin reservas ni bloqueos no hay periodos ocupados.
    @Test
    void noHayPeriodosOcupadosSinReservasNiBloqueos() {
        // Arrange
        when(bookingRepository.findOverlappingBookings(professionalId, from, to)).thenReturn(List.of());
        when(agendaBlocks.findOverlapping(professionalId, from, to)).thenReturn(List.of());

        // Act
        List<BusyPeriod> periods = adapter.findBusyPeriods(professionalId, from, to);

        // Assert
        assertThat(periods).isEmpty();
    }

    // Escenario Gherkin HU-05: Bloquear una franja sin reservas previas.
    // Los bloqueos se consultan en el mismo rango que las reservas.
    @Test
    void consultaLosBloqueosEnElMismoRangoQueLasReservas() {
        // Arrange
        when(bookingRepository.findOverlappingBookings(professionalId, from, to)).thenReturn(List.of());
        when(agendaBlocks.findOverlapping(professionalId, from, to)).thenReturn(List.of());

        // Act
        adapter.findBusyPeriods(professionalId, from, to);

        // Assert
        verify(agendaBlocks).findOverlapping(professionalId, from, to);
        verify(bookingRepository).findOverlappingBookings(professionalId, from, to);
    }

    // QA/Exception: el constructor sin puerto de bloqueos ignora los bloqueos y devuelve solo
    // las reservas (compatibilidad del constructor anterior, HU-05 "Decisiones de diseño").
    @Test
    void elConstructorSinPuertoDeBloqueosIgnoraLosBloqueosYDevuelveSoloLasReservas() {
        // Arrange
        AvailabilityQueryAdapter adapterSinBloqueos = new AvailabilityQueryAdapter(
                mock(ProfessionalRepository.class),
                mock(ServiceOfferingRepository.class),
                mock(WeeklySlotRepository.class),
                bookingRepository);
        LocalDateTime inicio = from.plusHours(9);
        LocalDateTime fin = from.plusHours(10);
        when(bookingRepository.findOverlappingBookings(professionalId, from, to)).thenReturn(List.of(
                new Booking(UUID.randomUUID(), professionalId, serviceId, inicio, fin)));

        // Act
        List<BusyPeriod> periods = adapterSinBloqueos.findBusyPeriods(professionalId, from, to);

        // Assert
        assertThat(periods).containsExactly(new BusyPeriod(inicio, fin));
    }
}
