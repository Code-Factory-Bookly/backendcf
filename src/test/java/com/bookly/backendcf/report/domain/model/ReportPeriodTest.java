package com.bookly.backendcf.report.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Pruebas del enum de periodos soportados por el reporte de ocupación (HU-15). */
@Tag("unit")
class ReportPeriodTest {

    @Test
    void elPeriodoSemanaCubreSieteDias() {
        // Arrange
        ReportPeriod period = ReportPeriod.SEMANA;

        // Act
        int days = period.getDays();

        // Assert
        assertThat(days).isEqualTo(7);
    }

    @Test
    void elPeriodoMesCubreTreintaDias() {
        // Arrange
        ReportPeriod period = ReportPeriod.MES;

        // Act
        int days = period.getDays();

        // Assert
        assertThat(days).isEqualTo(30);
    }

    @Test
    void soloExistenLosPeriodosSemanaYMes() {
        // Arrange & Act
        ReportPeriod[] periods = ReportPeriod.values();

        // Assert
        assertThat(periods).containsExactly(ReportPeriod.SEMANA, ReportPeriod.MES);
    }

    @Test
    void elNombreDelPeriodoEsElValorEsperadoEnLaApi() {
        // Arrange & Act & Assert
        assertThat(ReportPeriod.SEMANA.name()).isEqualTo("SEMANA");
        assertThat(ReportPeriod.MES.name()).isEqualTo("MES");
    }

    @Test
    void elValorDeUnPeriodoSeObtienePorSuNombre() {
        // Arrange & Act
        ReportPeriod period = ReportPeriod.valueOf("MES");

        // Assert
        assertThat(period).isEqualTo(ReportPeriod.MES);
        assertThat(period.getDays()).isEqualTo(30);
    }
}
