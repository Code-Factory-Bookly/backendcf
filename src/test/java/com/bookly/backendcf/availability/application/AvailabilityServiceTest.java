package com.bookly.backendcf.availability.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookly.backendcf.availability.domain.model.BusyPeriod;
import com.bookly.backendcf.availability.domain.model.WorkingWindow;
import com.bookly.backendcf.availability.presentation.dto.AvailabilityResponse;
import com.bookly.backendcf.catalog.application.ServiceOfferingNotFoundException;
import com.bookly.backendcf.schedule.application.ProfessionalNotFoundException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AvailabilityServiceTest {

    private final UUID professionalId = UUID.randomUUID();
    private final UUID serviceId = UUID.randomUUID();
    private final LocalDate monday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    private AvailabilityQuery availabilityQuery;
    private AvailabilityService service;

    @BeforeEach
    void setUp() {
        availabilityQuery = mock(AvailabilityQuery.class);
        when(availabilityQuery.professionalExists(professionalId)).thenReturn(true);
        when(availabilityQuery.findActiveServiceDuration(serviceId)).thenReturn(Optional.of(60));
        when(availabilityQuery.findWorkingWindows(professionalId)).thenReturn(List.of(
                new WorkingWindow(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0))));
        when(availabilityQuery.findBusyPeriods(eq(professionalId), any(), any())).thenReturn(List.of());
        service = new AvailabilityService(availabilityQuery);
    }

    @Test
    void devuelveLosBloquesLibresSegunLaDuracionDelServicio() {
        AvailabilityResponse response = service.find(professionalId, serviceId, monday, monday);

        assertThat(response.available()).isTrue();
        assertThat(response.message()).isNull();
        assertThat(response.durationMinutes()).isEqualTo(60);
        assertThat(response.days()).hasSize(1);
        assertThat(response.days().get(0).date()).isEqualTo(monday);
        assertThat(response.days().get(0).slots()).hasSize(4);
    }

    @Test
    void noMuestraLosBloquesYaOcupados() {
        when(availabilityQuery.findBusyPeriods(eq(professionalId), any(), any())).thenReturn(List.of(
                new BusyPeriod(monday.atTime(9, 0), monday.atTime(10, 0))));

        AvailabilityResponse response = service.find(professionalId, serviceId, monday, monday);

        assertThat(response.days().get(0).slots()).hasSize(3);
        assertThat(response.days().get(0).slots())
                .noneMatch(block -> block.startTime().equals(monday.atTime(9, 0)));
    }

    @Test
    void sinFranjasLibresInformaYSugiereAmpliarElFiltro() {
        LocalDate tuesday = monday.plusDays(1);

        AvailabilityResponse response = service.find(professionalId, serviceId, tuesday, tuesday);

        assertThat(response.available()).isFalse();
        assertThat(response.days()).isEmpty();
        assertThat(response.message()).contains("Amplía el filtro de fechas", "otro profesional");
    }

    @Test
    void consultaLasReservasDelRangoCompleto() {
        service.find(professionalId, serviceId, monday, monday.plusDays(6));

        verify(availabilityQuery).findBusyPeriods(
                professionalId, monday.atStartOfDay(), monday.plusDays(7).atStartOfDay());
    }

    @Test
    void rechazaParametrosFaltantes() {
        assertThatThrownBy(() -> service.find(null, null, null, null))
                .isInstanceOfSatisfying(InvalidAvailabilityQueryException.class, exception ->
                        assertThat(exception.getDetails())
                                .containsKeys("profesionalId", "servicioId", "desde", "hasta"));
    }

    @Test
    void rechazaUnRangoInvertido() {
        assertThatThrownBy(() -> service.find(professionalId, serviceId, monday.plusDays(3), monday))
                .isInstanceOfSatisfying(InvalidAvailabilityQueryException.class, exception ->
                        assertThat(exception.getDetails()).containsKey("hasta"));
    }

    @Test
    void rechazaUnRangoMayorAlMaximo() {
        assertThatThrownBy(() -> service.find(professionalId, serviceId, monday, monday.plusDays(31)))
                .isInstanceOfSatisfying(InvalidAvailabilityQueryException.class, exception ->
                        assertThat(exception.getDetails().get("hasta")).contains("31"));
    }

    @Test
    void rechazaUnRangoCompletamenteEnElPasado() {
        LocalDate past = LocalDate.now().minusDays(3);

        assertThatThrownBy(() -> service.find(professionalId, serviceId, past.minusDays(2), past))
                .isInstanceOf(InvalidAvailabilityQueryException.class);
    }

    @Test
    void profesionalInexistenteDevuelveNotFound() {
        UUID unknown = UUID.randomUUID();
        when(availabilityQuery.professionalExists(unknown)).thenReturn(false);

        assertThatThrownBy(() -> service.find(unknown, serviceId, monday, monday))
                .isInstanceOf(ProfessionalNotFoundException.class);

        verify(availabilityQuery, never()).findBusyPeriods(any(), any(), any());
    }

    @Test
    void servicioInexistenteOInactivoDevuelveNotFound() {
        UUID unknown = UUID.randomUUID();
        when(availabilityQuery.findActiveServiceDuration(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.find(professionalId, unknown, monday, monday))
                .isInstanceOf(ServiceOfferingNotFoundException.class);
    }
}