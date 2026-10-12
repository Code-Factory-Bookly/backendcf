package com.bookly.backendcf.availability.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bookly.backendcf.availability.domain.model.BusyPeriod;
import com.bookly.backendcf.availability.domain.model.WorkingWindow;
import com.bookly.backendcf.booking.domain.model.Booking;
import com.bookly.backendcf.booking.infrastructure.persistence.BookingRepository;
import com.bookly.backendcf.catalog.domain.model.ServiceOffering;
import com.bookly.backendcf.catalog.domain.model.ServiceStatus;
import com.bookly.backendcf.catalog.infrastructure.persistence.ServiceOfferingRepository;
import com.bookly.backendcf.professional.infrastructure.persistence.ProfessionalRepository;
import com.bookly.backendcf.schedule.domain.model.WeeklySlot;
import com.bookly.backendcf.schedule.infrastructure.persistence.WeeklySlotRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AvailabilityQueryAdapterTest {

    private final UUID professionalId = UUID.randomUUID();
    private final UUID serviceId = UUID.randomUUID();
    private ProfessionalRepository professionalRepository;
    private ServiceOfferingRepository serviceRepository;
    private WeeklySlotRepository weeklySlotRepository;
    private BookingRepository bookingRepository;
    private AvailabilityQueryAdapter adapter;

    @BeforeEach
    void setUp() {
        professionalRepository = mock(ProfessionalRepository.class);
        serviceRepository = mock(ServiceOfferingRepository.class);
        weeklySlotRepository = mock(WeeklySlotRepository.class);
        bookingRepository = mock(BookingRepository.class);
        adapter = new AvailabilityQueryAdapter(
                professionalRepository, serviceRepository, weeklySlotRepository, bookingRepository);
    }

    @Test
    void delegaLaExistenciaDelProfesionalAlRepositorio() {
        when(professionalRepository.existsById(professionalId)).thenReturn(true);

        assertThat(adapter.professionalExists(professionalId)).isTrue();
        assertThat(adapter.professionalExists(UUID.randomUUID())).isFalse();
    }

    @Test
    void devuelveLaDuracionDeUnServicioActivo() {
        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(offering()));

        assertThat(adapter.findActiveServiceDuration(serviceId)).contains(45);
    }

    @Test
    void unServicioInactivoOInexistenteNoTieneDuracion() {
        ServiceOffering inactive = offering();
        inactive.update("Consulta", null, "Salud", 45, new BigDecimal("50000"), ServiceStatus.INACTIVO);
        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(inactive));

        assertThat(adapter.findActiveServiceDuration(serviceId)).isEmpty();
        assertThat(adapter.findActiveServiceDuration(UUID.randomUUID())).isEmpty();
    }

    @Test
    void convierteLasFranjasSemanalesEnVentanasDeTrabajo() {
        when(weeklySlotRepository.findByProfessionalIdOrderByDayOfWeekAscStartTimeAsc(professionalId))
                .thenReturn(List.of(new WeeklySlot(
                        professionalId, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0))));

        assertThat(adapter.findWorkingWindows(professionalId)).containsExactly(
                new WorkingWindow(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0)));
    }

    @Test
    void convierteLasReservasConfirmadasEnPeriodosOcupados() {
        LocalDateTime from = LocalDateTime.of(2026, 10, 12, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 10, 13, 0, 0);
        Booking booking = new Booking(UUID.randomUUID(), professionalId, serviceId,
                LocalDateTime.of(2026, 10, 12, 9, 0), LocalDateTime.of(2026, 10, 12, 10, 0));
        when(bookingRepository.findOverlappingBookings(professionalId, from, to)).thenReturn(List.of(booking));

        assertThat(adapter.findBusyPeriods(professionalId, from, to)).containsExactly(
                new BusyPeriod(LocalDateTime.of(2026, 10, 12, 9, 0), LocalDateTime.of(2026, 10, 12, 10, 0)));
    }

    private ServiceOffering offering() {
        return new ServiceOffering("Consulta", null, "Salud", 45, new BigDecimal("50000"));
    }
}