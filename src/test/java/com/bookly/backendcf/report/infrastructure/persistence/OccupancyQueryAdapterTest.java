package com.bookly.backendcf.report.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.domain.model.UserRole;
import com.bookly.backendcf.auth.infrastructure.persistence.UserAccountRepository;
import com.bookly.backendcf.booking.domain.model.Booking;
import com.bookly.backendcf.booking.domain.model.BookingStatus;
import com.bookly.backendcf.booking.infrastructure.persistence.BookingRepository;
import com.bookly.backendcf.professional.domain.model.Professional;
import com.bookly.backendcf.professional.infrastructure.persistence.ProfessionalRepository;
import com.bookly.backendcf.report.domain.model.BookedPeriod;
import com.bookly.backendcf.report.domain.model.WeeklyWindow;
import com.bookly.backendcf.schedule.domain.model.WeeklySlot;
import com.bookly.backendcf.schedule.infrastructure.persistence.WeeklySlotRepository;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Pruebas del adaptador que traduce el puerto del reporte de ocupación a los repositorios de los
 * demás módulos (HU-15).
 */
@Tag("unit")
class OccupancyQueryAdapterTest {

    private final UUID professionalId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID serviceId = UUID.randomUUID();

    private ProfessionalRepository professionalRepository;
    private WeeklySlotRepository weeklySlotRepository;
    private BookingRepository bookingRepository;
    private UserAccountRepository userAccountRepository;
    private OccupancyQueryAdapter adapter;

    @BeforeEach
    void setUp() {
        professionalRepository = mock(ProfessionalRepository.class);
        weeklySlotRepository = mock(WeeklySlotRepository.class);
        bookingRepository = mock(BookingRepository.class);
        userAccountRepository = mock(UserAccountRepository.class);
        adapter = new OccupancyQueryAdapter(
                professionalRepository, weeklySlotRepository, bookingRepository, userAccountRepository);
    }

    @Test
    void devuelveLosIdsDeTodosLosProfesionalesRegistrados() {
        // Arrange
        when(professionalRepository.findAll()).thenReturn(List.of(
                new Professional(professionalId, "General"),
                new Professional(UUID.randomUUID(), "Pediatria")));

        // Act
        List<UUID> ids = adapter.findProfessionalIds();

        // Assert
        assertThat(ids).hasSize(2);
        assertThat(ids).contains(professionalId);
    }

    @Test
    void sinProfesionalesRegistradosDevuelveUnaListaVacia() {
        // Arrange
        when(professionalRepository.findAll()).thenReturn(List.of());

        // Act
        List<UUID> ids = adapter.findProfessionalIds();

        // Assert
        assertThat(ids).isEmpty();
    }

    @Test
    void convierteLasFranjasSemanalesEnVentanasDeCapacidad() {
        // Arrange
        when(weeklySlotRepository.findAll()).thenReturn(List.of(
                new WeeklySlot(professionalId, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0)),
                new WeeklySlot(professionalId, DayOfWeek.TUESDAY, LocalTime.of(14, 0), LocalTime.of(16, 0))));

        // Act
        List<WeeklyWindow> windows = adapter.findWeeklyWindows();

        // Assert
        assertThat(windows).containsExactly(
                new WeeklyWindow(professionalId, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0)),
                new WeeklyWindow(professionalId, DayOfWeek.TUESDAY, LocalTime.of(14, 0), LocalTime.of(16, 0)));
    }

    @Test
    void sinFranjasSemanalesDevuelveUnaListaVacia() {
        // Arrange
        when(weeklySlotRepository.findAll()).thenReturn(List.of());

        // Act
        List<WeeklyWindow> windows = adapter.findWeeklyWindows();

        // Assert
        assertThat(windows).isEmpty();
    }

    @Test
    void consultaLasReservasSoloConLosEstadosQueOcupanAgenda() {
        // Arrange
        LocalDateTime from = LocalDateTime.now().minusDays(30).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime to = LocalDateTime.now().plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);

        // Act
        adapter.findOccupyingBookings(from, to);

        // Assert: CONFIRMADA, RESCHEDULED y COMPLETED ocupan agenda (#220); CANCELADA no cuenta
        verify(bookingRepository).findByStatusesInRange(
                eq(List.of(BookingStatus.CONFIRMADA, BookingStatus.RESCHEDULED, BookingStatus.COMPLETED)),
                eq(from), eq(to));
    }

    @Test
    void convierteLasReservasEnPeriodosReservados() {
        // Arrange
        LocalDateTime from = LocalDateTime.now().minusDays(30).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime to = LocalDateTime.now().plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime start = LocalDateTime.now().minusDays(3).withHour(9).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusHours(1);
        when(bookingRepository.findByStatusesInRange(anyCollection(), any(), any()))
                .thenReturn(List.of(new Booking(customerId, professionalId, serviceId, start, end)));

        // Act
        List<BookedPeriod> bookings = adapter.findOccupyingBookings(from, to);

        // Assert
        assertThat(bookings).containsExactly(new BookedPeriod(professionalId, start, end));
    }

    @Test
    void sinReservasEnElRangoDevuelveUnaListaVacia() {
        // Arrange
        LocalDateTime from = LocalDateTime.now().minusDays(30).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime to = LocalDateTime.now().plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        when(bookingRepository.findByStatusesInRange(anyCollection(), any(), any())).thenReturn(List.of());

        // Act
        List<BookedPeriod> bookings = adapter.findOccupyingBookings(from, to);

        // Assert
        assertThat(bookings).isEmpty();
    }

    @Test
    void devuelveElNombreCompletoDeCadaProfesional() {
        // Arrange: UserAccount genera su id al persistirse, asi que se simula una cuenta ya guardada
        UserAccount account = new UserAccount(
                "profesional-" + professionalId + "@example.com", "hash", "Ana Lopez", UserRole.PROFESSIONAL);
        assignId(account, professionalId);
        when(userAccountRepository.findAllById(List.of(professionalId))).thenReturn(List.of(account));

        // Act
        Map<UUID, String> names = adapter.professionalNames(List.of(professionalId));

        // Assert
        assertThat(names).containsEntry(professionalId, "Ana Lopez");
    }

    @Test
    void sinProfesionalesSolicitadosNoConsultaLaBaseDeDatos() {
        // Arrange
        List<UUID> ids = List.of();

        // Act
        Map<UUID, String> names = adapter.professionalNames(ids);

        // Assert
        assertThat(names).isEmpty();
        verify(userAccountRepository, never()).findAllById(any());
    }

    /** UserAccount delega su id a la generacion de Hibernate: se asigna por reflexion para simular una cuenta persistida. */
    private void assignId(UserAccount account, UUID id) {
        try {
            java.lang.reflect.Field field = UserAccount.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(account, id);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("No se pudo asignar el id de la cuenta de prueba", exception);
        }
    }
}
