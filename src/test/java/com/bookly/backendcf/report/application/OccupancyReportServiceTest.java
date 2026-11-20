package com.bookly.backendcf.report.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookly.backendcf.report.domain.model.BookedPeriod;
import com.bookly.backendcf.report.domain.model.ReportPeriod;
import com.bookly.backendcf.report.domain.model.WeeklyWindow;
import com.bookly.backendcf.report.presentation.dto.OccupancyReportResponse;
import com.bookly.backendcf.report.presentation.dto.OccupancyReportResponse.DayOccupancy;
import com.bookly.backendcf.report.presentation.dto.OccupancyReportResponse.ProfessionalOccupancy;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Pruebas del caso de uso de HU-15 (reporte de ocupación).
 *
 * <p>Las fechas son relativas a hoy para que los casos nunca expiren: el servicio siempre termina
 * el rango en la fecha actual.
 */
@Tag("unit")
class OccupancyReportServiceTest {

    private final UUID professionalId = UUID.randomUUID();
    private final UUID otroProfessionalId = UUID.randomUUID();
    private final LocalDate hoy = LocalDate.now();

    private OccupancyQuery occupancyQuery;
    private OccupancyReportService service;

    @BeforeEach
    void setUp() {
        occupancyQuery = mock(OccupancyQuery.class);
        when(occupancyQuery.findProfessionalIds()).thenReturn(List.of());
        when(occupancyQuery.findWeeklyWindows()).thenReturn(List.of());
        when(occupancyQuery.findOccupyingBookings(any(), any())).thenReturn(List.of());
        when(occupancyQuery.professionalNames(any())).thenReturn(Map.of());
        service = new OccupancyReportService(occupancyQuery);
    }

    // ------------------------------------------------------------------------------------------
    // Periodo y rango de fechas
    // ------------------------------------------------------------------------------------------

    @Test
    void usaElPeriodoMesPorDefectoCuandoNoSeSolicitaNinguno() {
        // Arrange
        ReportPeriod requested = null;

        // Act
        OccupancyReportResponse response = service.generate(requested);

        // Assert
        assertThat(response.period()).isEqualTo(ReportPeriod.MES);
        assertThat(response.to()).isEqualTo(hoy);
        assertThat(response.from()).isEqualTo(hoy.minusDays(29));
    }

    @Test
    void elRangoDelPeriodoMesCubreTreintaDiasTerminandoHoy() {
        // Arrange
        ReportPeriod period = ReportPeriod.MES;

        // Act
        OccupancyReportResponse response = service.generate(period);

        // Assert
        assertThat(response.from()).isEqualTo(hoy.minusDays(29));
        assertThat(response.to()).isEqualTo(hoy);
        assertThat(ChronoUnit.DAYS.between(response.from(), response.to())).isEqualTo(29);
    }

    @Test
    void elRangoDelPeriodoSemanaCubreSieteDiasTerminandoHoy() {
        // Arrange
        ReportPeriod period = ReportPeriod.SEMANA;

        // Act
        OccupancyReportResponse response = service.generate(period);

        // Assert
        assertThat(response.from()).isEqualTo(hoy.minusDays(6));
        assertThat(response.to()).isEqualTo(hoy);
    }

    @Test
    void consultaLasReservasDelRangoCompletoIncluyendoElDiaFinalHastaLas23_59() {
        // Arrange
        ReportPeriod period = ReportPeriod.SEMANA;

        // Act
        service.generate(period);

        // Assert
        verify(occupancyQuery).findOccupyingBookings(
                hoy.minusDays(6).atStartOfDay(),
                hoy.plusDays(1).atStartOfDay());
    }

    // ------------------------------------------------------------------------------------------
    // Agregación por día de la semana
    // ------------------------------------------------------------------------------------------

    @Test
    void elReporteSiempreTraeLosSieteDiasDeLaSemanaEnOrden() {
        // Arrange
        when(occupancyQuery.findWeeklyWindows()).thenReturn(List.of(
                new WeeklyWindow(professionalId, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0))));

        // Act
        OccupancyReportResponse response = service.generate(ReportPeriod.SEMANA);

        // Assert
        assertThat(response.byDayOfWeek()).hasSize(7);
        assertThat(response.byDayOfWeek())
                .extracting(DayOccupancy::dayOfWeek)
                .containsExactly(DayOfWeek.values());
    }

    @Test
    void agregaLosMinutosReservadosPorDiaDeLaSemana() {
        // Arrange: dos reservas el lunes
        when(occupancyQuery.findOccupyingBookings(any(), any())).thenReturn(List.of(
                new BookedPeriod(professionalId, lunesDelRangoSemanal().atTime(9, 0), lunesDelRangoSemanal().atTime(10, 0)),
                new BookedPeriod(professionalId, lunesDelRangoSemanal().atTime(11, 0), lunesDelRangoSemanal().atTime(12, 30))));
        when(occupancyQuery.findWeeklyWindows()).thenReturn(List.of(
                new WeeklyWindow(professionalId, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0))));

        // Act
        OccupancyReportResponse response = service.generate(ReportPeriod.SEMANA);

        // Assert
        DayOccupancy lunes = response.byDayOfWeek().get(0);
        assertThat(lunes.dayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(lunes.bookedMinutes()).isEqualTo(150L);
        assertThat(lunes.capacityMinutes()).isEqualTo(240L);
        assertThat(lunes.occupancyPercent()).isEqualTo(62.5);
    }

    @Test
    void elPorcentajeDeUnDiaSinCapacidadEsCero() {
        // Arrange: solo hay franja los lunes, pero el martes del rango hay una reserva.
        // El martes nunca tiene capacidad, asi que su porcentaje debe ser 0 y no una division por cero.
        LocalDate martes = martesDelRangoSemanal();
        when(occupancyQuery.findOccupyingBookings(any(), any())).thenReturn(List.of(
                new BookedPeriod(professionalId, martes.atTime(9, 0), martes.atTime(10, 0))));
        when(occupancyQuery.findWeeklyWindows()).thenReturn(List.of(
                new WeeklyWindow(professionalId, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0))));

        // Act
        OccupancyReportResponse response = service.generate(ReportPeriod.SEMANA);

        // Assert
        DayOccupancy diaMartes = response.byDayOfWeek().stream()
                .filter(day -> day.dayOfWeek().equals(DayOfWeek.TUESDAY))
                .findFirst()
                .orElseThrow();
        assertThat(diaMartes.bookedMinutes()).isEqualTo(60L);
        assertThat(diaMartes.capacityMinutes()).isZero();
        assertThat(diaMartes.occupancyPercent()).isZero();
    }

    // ------------------------------------------------------------------------------------------
    // Agregación por profesional
    // ------------------------------------------------------------------------------------------

    @Test
    void agregaLosMinutosPorProfesionalYOrdenaPorNombre() {
        // Arrange
        when(occupancyQuery.findProfessionalIds())
                .thenReturn(List.of(professionalId, otroProfessionalId));
        when(occupancyQuery.professionalNames(any())).thenReturn(Map.of(
                professionalId, "Zoraida Sanchez",
                otroProfessionalId, "Alberto Ruiz"));
        when(occupancyQuery.findOccupyingBookings(any(), any())).thenReturn(List.of(
                new BookedPeriod(professionalId, lunesDelRangoSemanal().atTime(9, 0), lunesDelRangoSemanal().atTime(10, 0)),
                new BookedPeriod(otroProfessionalId, lunesDelRangoSemanal().atTime(9, 0), lunesDelRangoSemanal().atTime(11, 0))));
        when(occupancyQuery.findWeeklyWindows()).thenReturn(List.of(
                new WeeklyWindow(professionalId, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0)),
                new WeeklyWindow(otroProfessionalId, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0))));

        // Act
        OccupancyReportResponse response = service.generate(ReportPeriod.SEMANA);

        // Assert
        assertThat(response.byProfessional()).hasSize(2);
        assertThat(response.byProfessional())
                .extracting(ProfessionalOccupancy::professionalName)
                .containsExactly("Alberto Ruiz", "Zoraida Sanchez");

        ProfessionalOccupancy alberto = response.byProfessional().get(0);
        assertThat(alberto.professionalId()).isEqualTo(otroProfessionalId);
        assertThat(alberto.bookedMinutes()).isEqualTo(120L);
        assertThat(alberto.capacityMinutes()).isEqualTo(240L);
        assertThat(alberto.occupancyPercent()).isEqualTo(50.0);
    }

    @Test
    void unProfesionalSinReservasApareceConCerosYNoConNull() {
        // Arrange
        when(occupancyQuery.findProfessionalIds()).thenReturn(List.of(professionalId));
        when(occupancyQuery.professionalNames(any())).thenReturn(Map.of(professionalId, "Ana Lopez"));

        // Act
        OccupancyReportResponse response = service.generate(ReportPeriod.SEMANA);

        // Assert
        ProfessionalOccupancy ana = response.byProfessional().get(0);
        assertThat(ana.bookedMinutes()).isZero();
        assertThat(ana.capacityMinutes()).isZero();
        assertThat(ana.occupancyPercent()).isZero();
    }

    @Test
    void unProfesionalSinNombreRegistradoApareceConNombreVacioYNoConNull() {
        // Arrange
        when(occupancyQuery.findProfessionalIds()).thenReturn(List.of(professionalId));
        when(occupancyQuery.professionalNames(any())).thenReturn(Map.of());

        // Act
        OccupancyReportResponse response = service.generate(ReportPeriod.SEMANA);

        // Assert
        assertThat(response.byProfessional().get(0).professionalName()).isEmpty();
    }

    @Test
    void losProfesionalesSinFranjasNoAparecenEnElReporte() {
        // Arrange
        when(occupancyQuery.findProfessionalIds()).thenReturn(List.of(professionalId));
        when(occupancyQuery.professionalNames(any())).thenReturn(Map.of(professionalId, "Ana Lopez"));
        when(occupancyQuery.findWeeklyWindows()).thenReturn(List.of(
                new WeeklyWindow(otroProfessionalId, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0))));

        // Act
        OccupancyReportResponse response = service.generate(ReportPeriod.SEMANA);

        // Assert
        assertThat(response.byProfessional())
                .extracting(ProfessionalOccupancy::professionalId)
                .containsExactly(professionalId);
    }

    // ------------------------------------------------------------------------------------------
    // Porcentaje global
    // ------------------------------------------------------------------------------------------

    @Test
    void calculaElPorcentajeGlobalSobreLaSumaDeReservadosYCapacidad() {
        // Arrange
        when(occupancyQuery.findProfessionalIds()).thenReturn(List.of(professionalId));
        when(occupancyQuery.professionalNames(any())).thenReturn(Map.of(professionalId, "Ana Lopez"));
        when(occupancyQuery.findWeeklyWindows()).thenReturn(List.of(
                new WeeklyWindow(professionalId, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0))));
        when(occupancyQuery.findOccupyingBookings(any(), any())).thenReturn(List.of(
                new BookedPeriod(professionalId, lunesDelRangoSemanal().atTime(9, 0), lunesDelRangoSemanal().atTime(10, 0))));

        // Act
        OccupancyReportResponse response = service.generate(ReportPeriod.SEMANA);

        // Assert: 60 minutos reservados sobre 240 de capacidad del lunes
        assertThat(response.overallOccupancyPercent()).isEqualTo(25.0);
    }

    @Test
    void elPorcentajeGlobalSumaLaCapacidadDeTodosLosDiasYNoSoloElMayor() {
        // Arrange: la capacidad se reparte entre el lunes (240 min) y el martes (120 min),
        // y hay reservas en ambos dias (120 + 60 = 180 min).
        // Si el servicio sumara solo la capacidad del dia mas cargado (240) el resultado
        // seria 75% en lugar de 50%.
        when(occupancyQuery.findProfessionalIds()).thenReturn(List.of(professionalId));
        when(occupancyQuery.professionalNames(any())).thenReturn(Map.of(professionalId, "Ana Lopez"));
        when(occupancyQuery.findWeeklyWindows()).thenReturn(List.of(
                new WeeklyWindow(professionalId, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0)),
                new WeeklyWindow(professionalId, DayOfWeek.TUESDAY, LocalTime.of(8, 0), LocalTime.of(10, 0))));
        when(occupancyQuery.findOccupyingBookings(any(), any())).thenReturn(List.of(
                new BookedPeriod(professionalId, lunesDelRangoSemanal().atTime(9, 0), lunesDelRangoSemanal().atTime(11, 0)),
                new BookedPeriod(professionalId, martesDelRangoSemanal().atTime(9, 0), martesDelRangoSemanal().atTime(10, 0))));

        // Act
        OccupancyReportResponse response = service.generate(ReportPeriod.SEMANA);

        // Assert: 180 minutos reservados sobre 360 de capacidad acumulada (240 + 120)
        assertThat(dayOf(response, DayOfWeek.MONDAY).capacityMinutes()).isEqualTo(240L);
        assertThat(dayOf(response, DayOfWeek.TUESDAY).capacityMinutes()).isEqualTo(120L);
        assertThat(response.overallOccupancyPercent()).isEqualTo(50.0);
    }

    @Test
    void elPorcentajeGlobalEsCeroCuandoNoHayCapacidad() {
        // Arrange: hay reservas pero ninguna franja configurada
        when(occupancyQuery.findOccupyingBookings(any(), any())).thenReturn(List.of(
                new BookedPeriod(professionalId, lunesDelRangoSemanal().atTime(9, 0), lunesDelRangoSemanal().atTime(10, 0))));
        when(occupancyQuery.findProfessionalIds()).thenReturn(List.of(professionalId));
        when(occupancyQuery.professionalNames(any())).thenReturn(Map.of(professionalId, "Ana Lopez"));

        // Act
        OccupancyReportResponse response = service.generate(ReportPeriod.MES);

        // Assert
        assertThat(response.overallOccupancyPercent()).isZero();
    }

    // ------------------------------------------------------------------------------------------
    // QA/Exception: sin datos transaccionales en el periodo
    // ------------------------------------------------------------------------------------------

    @Test
    void sinReservasEnElUltimoMesGeneraElReporteEnCerosSinNingunaExcepcion() {
        // Arrange: la BD no tiene reservas, no hay profesionales y no hay franjas
        when(occupancyQuery.findOccupyingBookings(any(), any())).thenReturn(List.of());
        when(occupancyQuery.findProfessionalIds()).thenReturn(List.of());
        when(occupancyQuery.findWeeklyWindows()).thenReturn(List.of());

        // Act
        OccupancyReportResponse report = service.generate(ReportPeriod.MES);

        // Assert: no revienta y ademas...
        assertThatCode(() -> service.generate(ReportPeriod.MES))
                .doesNotThrowAnyException();

        // Assert: el nombre del caso promete "en ceros": no basta con que no reviente.
        assertThat(report.overallOccupancyPercent()).isZero();
        assertThat(report.byDayOfWeek()).hasSize(7);
        assertThat(report.byDayOfWeek())
                .allSatisfy(day -> {
                    assertThat(day.bookedMinutes()).isZero();
                    assertThat(day.capacityMinutes()).isZero();
                    assertThat(day.occupancyPercent()).isZero();
                });
        assertThat(report.byProfessional()).isEmpty();
    }

    @Test
    void sinReservasTodosLosDiasQuedanEnCeroYLosSieteDiasSiguenPresentes() {
        // Arrange
        when(occupancyQuery.findOccupyingBookings(any(), any())).thenReturn(List.of());

        // Act
        OccupancyReportResponse response = service.generate(ReportPeriod.MES);

        // Assert
        assertThat(response.byDayOfWeek()).hasSize(7);
        assertThat(response.byDayOfWeek())
                .extracting(DayOccupancy::dayOfWeek)
                .containsExactly(DayOfWeek.values());
        assertThat(response.byDayOfWeek())
                .allSatisfy(day -> {
                    assertThat(day.bookedMinutes()).isZero();
                    assertThat(day.capacityMinutes()).isZero();
                    assertThat(day.occupancyPercent()).isZero();
                });
        assertThat(response.overallOccupancyPercent()).isZero();
        assertThat(response.byProfessional()).isEmpty();
    }

    @Test
    void sinReservasPeroConFranjasLaCapacidadSeReportaYLosPorcentajesQuedanEnCero() {
        // Arrange: hay agenda configurada pero ninguna reserva
        when(occupancyQuery.findOccupyingBookings(any(), any())).thenReturn(List.of());
        when(occupancyQuery.findProfessionalIds()).thenReturn(List.of(professionalId));
        when(occupancyQuery.professionalNames(any())).thenReturn(Map.of(professionalId, "Ana Lopez"));
        when(occupancyQuery.findWeeklyWindows()).thenReturn(List.of(
                new WeeklyWindow(professionalId, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0))));

        // Act
        OccupancyReportResponse response = service.generate(ReportPeriod.SEMANA);

        // Assert
        assertThat(response.byDayOfWeek().get(0).capacityMinutes()).isEqualTo(240L);
        assertThat(response.byDayOfWeek().get(0).bookedMinutes()).isZero();
        assertThat(response.byDayOfWeek().get(0).occupancyPercent()).isZero();
        assertThat(response.byProfessional().get(0).bookedMinutes()).isZero();
        assertThat(response.overallOccupancyPercent()).isZero();
    }

    @Test
    void sinProfesionalesRegistradosNoConsultaLosNombresYElReporteQuedaVacio() {
        // Arrange
        when(occupancyQuery.findProfessionalIds()).thenReturn(List.of());

        // Act
        OccupancyReportResponse response = service.generate(ReportPeriod.MES);

        // Assert
        assertThat(response.byProfessional()).isEmpty();
        assertThat(response.overallOccupancyPercent()).isZero();
    }

    // ------------------------------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------------------------------

    /**
     * Lunes que cae dentro del rango de siete días que usa el servicio (hoy - 6 .. hoy).
     * Cualquier semana de siete días consecutivos contiene exactamente un lunes, así que el
     * resultado es estable sin importar el día de la semana en que se ejecute la prueba.
     */
    private LocalDate lunesDelRangoSemanal() {
        LocalDate desde = hoy.minusDays(6);
        for (LocalDate date = desde; !date.isAfter(hoy); date = date.plusDays(1)) {
            if (date.getDayOfWeek().equals(DayOfWeek.MONDAY)) {
                return date;
            }
        }
        throw new IllegalStateException("El rango semanal siempre contiene un lunes");
    }

    /** Día de la semana concreto dentro del reporte generado. */
    private DayOccupancy dayOf(OccupancyReportResponse response, DayOfWeek day) {
        List<DayOccupancy> days = response.byDayOfWeek().stream()
                .filter(item -> item.dayOfWeek().equals(day))
                .toList();
        assertThat(days).hasSize(1);
        return days.get(0);
    }

    /**
     * Martes que cae dentro del rango de siete días que usa el servicio (hoy - 6 .. hoy).
     * Si el rango termina en domingo, el martes siguiente al lunes cae fuera y se toma el
     * martes anterior, que siempre está dentro del rango.
     */
    private LocalDate martesDelRangoSemanal() {
        LocalDate desde = hoy.minusDays(6);
        LocalDate martes = null;
        for (LocalDate date = desde; !date.isAfter(hoy); date = date.plusDays(1)) {
            if (date.getDayOfWeek().equals(DayOfWeek.TUESDAY)) {
                martes = date;
            }
        }
        if (martes == null) {
            throw new IllegalStateException("El rango semanal siempre contiene un martes");
        }
        return martes;
    }
}
