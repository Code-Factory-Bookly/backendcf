package com.bookly.backendcf.report.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookly.backendcf.report.domain.model.ReportPeriod;
import com.bookly.backendcf.report.presentation.dto.GeneralReportResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Reglas del reporte general de uso (HU-16), escenario "estadísticas agregadas".
 *
 * <p>El puerto {@link GeneralReportQuery} se simula: aquí se prueba solo la lógica del servicio
 * (ventana de fechas, promedio, ingresos nulos, mensaje sin datos y mapeo de recursos). Que las
 * consultas SQL devuelvan los números correctos se comprueba en {@code GeneralReportIntegrationTest}.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class GeneralReportServiceTest {

    @Mock
    private GeneralReportQuery query;

    @InjectMocks
    private GeneralReportService service;

    private void consulta(long total, long canceladas, BigDecimal ingresos,
                          List<GeneralReportQuery.ResourceUsage> recursos) {
        when(query.countReservations(any(), any())).thenReturn(total);
        when(query.countCancelledReservations(any(), any())).thenReturn(canceladas);
        when(query.sumRecognizedRevenue(any(), any())).thenReturn(ingresos);
        when(query.findResourceUsage(any(), any())).thenReturn(recursos);
    }

    // ----------------------------------------------------------------------------------
    // Ventana del periodo
    // ----------------------------------------------------------------------------------

    @Test
    void sinPeriodoSeUsaElMesYLaVentanaDeTreintaDiasTerminaHoy() {
        consulta(1, 0, BigDecimal.TEN, List.of());

        GeneralReportResponse respuesta = service.generate(null);

        assertThat(respuesta.period()).isEqualTo(ReportPeriod.MES);
        assertThat(respuesta.to()).isEqualTo(LocalDate.now());
        assertThat(respuesta.from()).isEqualTo(LocalDate.now().minusDays(29));
    }

    @Test
    void elPeriodoSemanaCubreSieteDiasTerminadosHoy() {
        consulta(1, 0, BigDecimal.TEN, List.of());

        GeneralReportResponse respuesta = service.generate(ReportPeriod.SEMANA);

        assertThat(respuesta.period()).isEqualTo(ReportPeriod.SEMANA);
        assertThat(respuesta.from()).isEqualTo(LocalDate.now().minusDays(6));
        assertThat(respuesta.to()).isEqualTo(LocalDate.now());
    }

    @Test
    void lasConsultasRecibenDesdeElInicioDelPrimerDiaHastaElInicioDelDiaSiguienteAlUltimo() {
        consulta(1, 0, BigDecimal.TEN, List.of());

        GeneralReportResponse respuesta = service.generate(ReportPeriod.SEMANA);

        ArgumentCaptor<LocalDateTime> desde = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> hasta = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(query).countReservations(desde.capture(), hasta.capture());
        assertThat(desde.getValue()).isEqualTo(respuesta.from().atStartOfDay());
        // Límite superior exclusivo: así entra todo el último día y nada del siguiente.
        assertThat(hasta.getValue()).isEqualTo(respuesta.to().plusDays(1).atStartOfDay());
    }

    // ----------------------------------------------------------------------------------
    // Totales, promedio e ingresos
    // ----------------------------------------------------------------------------------

    @Test
    void elPromedioDeCancelacionesEsLaCantidadDivididaEntreLosDiasDelPeriodo() {
        consulta(40, 15, BigDecimal.TEN, List.of());
        assertThat(service.generate(ReportPeriod.MES).averageCancellationsPerDay())
                .isEqualTo(0.5);

        consulta(40, 7, BigDecimal.TEN, List.of());
        assertThat(service.generate(ReportPeriod.SEMANA).averageCancellationsPerDay())
                .isEqualTo(1.0);

        consulta(40, 10, BigDecimal.TEN, List.of());
        assertThat(service.generate(ReportPeriod.MES).averageCancellationsPerDay())
                .isCloseTo(1.0 / 3.0, within(1e-9));
    }

    @Test
    void elReporteDevuelveLosTotalesYLosIngresosDeLaConsulta() {
        consulta(12, 3, new BigDecimal("1234.50"), List.of());

        GeneralReportResponse respuesta = service.generate(ReportPeriod.MES);

        assertThat(respuesta.totalReservations()).isEqualTo(12L);
        assertThat(respuesta.cancelledReservations()).isEqualTo(3L);
        assertThat(respuesta.recognizedRevenue()).isEqualByComparingTo("1234.50");
    }

    @Test
    void unIngresoNuloSeConvierteEnCeroEnLugarDeFallar() {
        consulta(2, 0, null, List.of());

        GeneralReportResponse respuesta = service.generate(ReportPeriod.MES);

        assertThat(respuesta.recognizedRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ----------------------------------------------------------------------------------
    // Sin datos
    // ----------------------------------------------------------------------------------

    @Test
    void sinReservasElReporteVieneEnCerosYConUnMensajeInformativo() {
        consulta(0, 0, null, List.of());

        GeneralReportResponse respuesta = service.generate(ReportPeriod.MES);

        assertThat(respuesta.totalReservations()).isZero();
        assertThat(respuesta.cancelledReservations()).isZero();
        assertThat(respuesta.averageCancellationsPerDay()).isZero();
        assertThat(respuesta.recognizedRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(respuesta.resourceUsage()).isEmpty();
        assertThat(respuesta.message()).isEqualTo("No hay reservas registradas en el periodo consultado");
    }

    @Test
    void conReservasNoHayMensaje() {
        consulta(1, 0, BigDecimal.TEN, List.of());

        assertThat(service.generate(ReportPeriod.MES).message()).isNull();
    }

    // ----------------------------------------------------------------------------------
    // Uso de recursos
    // ----------------------------------------------------------------------------------

    @Test
    void elUsoDeRecursosConservaLaCantidadTotalYLasReservasQueLoUsan() {
        UUID camilla = UUID.randomUUID();
        UUID sala = UUID.randomUUID();
        consulta(5, 0, BigDecimal.TEN, List.of(
                new GeneralReportQuery.ResourceUsage(camilla, "Camilla", 2, 4L),
                new GeneralReportQuery.ResourceUsage(sala, "Sala", 1, 0L)));

        List<GeneralReportResponse.ResourceUsage> uso = service.generate(ReportPeriod.MES).resourceUsage();

        assertThat(uso).hasSize(2);
        assertThat(uso.get(0).resourceId()).isEqualTo(camilla);
        assertThat(uso.get(0).resourceName()).isEqualTo("Camilla");
        assertThat(uso.get(0).totalQuantity()).isEqualTo(2);
        assertThat(uso.get(0).bookingsUsingResource()).isEqualTo(4L);
        // Un recurso sin reservas sigue apareciendo, con cero usos.
        assertThat(uso.get(1).resourceName()).isEqualTo("Sala");
        assertThat(uso.get(1).bookingsUsingResource()).isZero();
    }
}
