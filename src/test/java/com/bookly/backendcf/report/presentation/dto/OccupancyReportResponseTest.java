package com.bookly.backendcf.report.presentation.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookly.backendcf.report.domain.model.ReportPeriod;
import com.bookly.backendcf.report.presentation.dto.OccupancyReportResponse.DayOccupancy;
import com.bookly.backendcf.report.presentation.dto.OccupancyReportResponse.ProfessionalOccupancy;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Pruebas del contrato de respuesta del reporte de ocupación (HU-15). */
@Tag("unit")
class OccupancyReportResponseTest {

    private final UUID professionalId = UUID.randomUUID();

    @Test
    void laRespuestaExponeElPeriodoYElRangoDeFechas() {
        // Arrange
        LocalDate from = LocalDate.now().minusDays(29);
        LocalDate to = LocalDate.now();

        // Act
        OccupancyReportResponse response = new OccupancyReportResponse(
                ReportPeriod.MES, from, to, 42.5, List.of(), List.of());

        // Assert
        assertThat(response.period()).isEqualTo(ReportPeriod.MES);
        assertThat(response.from()).isEqualTo(from);
        assertThat(response.to()).isEqualTo(to);
        assertThat(response.overallOccupancyPercent()).isEqualTo(42.5);
    }

    @Test
    void laRespuestaExponeLasListasDeOcupacionPorDiaYPorProfesional() {
        // Arrange
        DayOccupancy day = new DayOccupancy(DayOfWeek.MONDAY, 600L, 1200L, 50.0);
        ProfessionalOccupancy professional =
                new ProfessionalOccupancy(professionalId, "Ana Lopez", 300L, 800L, 37.5);

        // Act
        OccupancyReportResponse response = new OccupancyReportResponse(
                ReportPeriod.SEMANA, LocalDate.now().minusDays(6), LocalDate.now(),
                50.0, List.of(day), List.of(professional));

        // Assert
        assertThat(response.byDayOfWeek()).containsExactly(day);
        assertThat(response.byProfessional()).containsExactly(professional);
    }

    @Test
    void elDetallePorDiaExponeDiaMinutosReservadosCapacidadYPorcentaje() {
        // Arrange & Act
        DayOccupancy day = new DayOccupancy(DayOfWeek.TUESDAY, 90L, 240L, 37.5);

        // Assert
        assertThat(day.dayOfWeek()).isEqualTo(DayOfWeek.TUESDAY);
        assertThat(day.bookedMinutes()).isEqualTo(90L);
        assertThat(day.capacityMinutes()).isEqualTo(240L);
        assertThat(day.occupancyPercent()).isEqualTo(37.5);
    }

    @Test
    void elDetallePorProfesionalExponeIdNombreMinutosYPorcentaje() {
        // Arrange & Act
        ProfessionalOccupancy professional =
                new ProfessionalOccupancy(professionalId, "Ana Lopez", 0L, 0L, 0.0);

        // Assert
        assertThat(professional.professionalId()).isEqualTo(professionalId);
        assertThat(professional.professionalName()).isEqualTo("Ana Lopez");
        assertThat(professional.bookedMinutes()).isZero();
        assertThat(professional.capacityMinutes()).isZero();
        assertThat(professional.occupancyPercent()).isZero();
    }

    @Test
    void elDetallePorDiaEsIgualSiCoincidenTodosSusValores() {
        // Arrange
        DayOccupancy primero = new DayOccupancy(DayOfWeek.MONDAY, 600L, 1200L, 50.0);
        DayOccupancy segundo = new DayOccupancy(DayOfWeek.MONDAY, 600L, 1200L, 50.0);

        // Act & Assert
        assertThat(primero).isEqualTo(segundo);
        assertThat(primero).hasSameHashCodeAs(segundo);
    }

    @Test
    void elDetallePorProfesionalEsIgualSiCoincidenTodosSusValores() {
        // Arrange
        ProfessionalOccupancy primero =
                new ProfessionalOccupancy(professionalId, "Ana Lopez", 300L, 800L, 37.5);
        ProfessionalOccupancy segundo =
                new ProfessionalOccupancy(professionalId, "Ana Lopez", 300L, 800L, 37.5);

        // Act & Assert
        assertThat(primero).isEqualTo(segundo);
        assertThat(primero).hasSameHashCodeAs(segundo);
    }

    @Test
    void unaRespuestaSinDatosTransaccionalesConservaListasVaciasYPorcentajeEnCero() {
        // Arrange & Act
        OccupancyReportResponse response = new OccupancyReportResponse(
                ReportPeriod.MES, LocalDate.now().minusDays(29), LocalDate.now(),
                0.0, List.of(), List.of());

        // Assert
        assertThat(response.overallOccupancyPercent()).isZero();
        assertThat(response.byDayOfWeek()).isEmpty();
        assertThat(response.byProfessional()).isEmpty();
    }
}
