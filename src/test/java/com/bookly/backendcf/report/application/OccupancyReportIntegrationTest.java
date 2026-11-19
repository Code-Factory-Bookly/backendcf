package com.bookly.backendcf.report.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.bookly.backendcf.report.domain.model.ReportPeriod;
import com.bookly.backendcf.report.presentation.dto.OccupancyReportResponse;
import com.bookly.backendcf.report.presentation.dto.OccupancyReportResponse.DayOccupancy;
import com.bookly.backendcf.report.presentation.dto.OccupancyReportResponse.ProfessionalOccupancy;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verifica contra PostgreSQL real que el reporte de ocupación (HU-15) funciona de punta a punta:
 * servicio, adaptador, repositorios JPA y migraciones Flyway.
 *
 * <p>Las fechas son relativas a hoy para que los casos nunca expiren. Todos los datos que inserta
 * la prueba se eliminan al final, de modo que la base de pruebas queda como estaba.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:55432/bookly_test",
        "spring.datasource.username=bookly_test",
        "spring.datasource.password=bookly_test_local_2026",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "security.jwt.secret=BooklyIntegrationTestOnlySecretWithMoreThanSixtyFourCharacters123456789"
})
@Tag("integration")
class OccupancyReportIntegrationTest {

    @Autowired
    private OccupancyReportService occupancyReportService;

    @Autowired
    private JdbcTemplate jdbc;

    private final UUID professionalId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID serviceId = UUID.randomUUID();
    private final LocalDate hoy = LocalDate.now();
    private final OffsetDateTime now = OffsetDateTime.now();
    private final String suffix = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        insertUser(professionalId, "professional-" + suffix + "@example.test", "PROFESSIONAL");
        insertUser(customerId, "customer-" + suffix + "@example.test", "CUSTOMER");
        jdbc.update("INSERT INTO profesionales (id, rol, especialidad, created_at, updated_at) "
                + "VALUES (?, 'PROFESSIONAL', 'General', ?, ?)", professionalId, now, now);
        jdbc.update("INSERT INTO servicios (id, nombre, categoria, duracion_minutos, precio, estado, "
                        + "created_at, updated_at) VALUES (?, ?, 'General', 60, ?, 'ACTIVO', ?, ?)",
                serviceId, "Ocupacion IT " + suffix, new BigDecimal("100.00"), now, now);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM bookings WHERE professional_id = ?", professionalId);
        jdbc.update("DELETE FROM horario_semanal WHERE profesional_id = ?", professionalId);
        jdbc.update("DELETE FROM profesionales WHERE id = ?", professionalId);
        jdbc.update("DELETE FROM servicios WHERE id = ?", serviceId);
        jdbc.update("DELETE FROM app_user WHERE id IN (?, ?)", professionalId, customerId);
    }

    // ------------------------------------------------------------------------------------------
    // Escenario 1: con reservas agendadas y atendidas en el último mes
    // ------------------------------------------------------------------------------------------

    @Test
    void calculaLaOcupacionPorDiaDeLaSemanaConReservasReales() {
        // Arrange
        LocalDate monday = lunesDeLaSemanaActual();
        insertWeeklyWindow(monday.getDayOfWeek(), LocalTime.of(8, 0), LocalTime.of(12, 0));
        insertBooking(monday.atTime(9, 0), monday.atTime(10, 0), "CONFIRMADA");

        // Act
        OccupancyReportResponse response = occupancyReportService.generate(ReportPeriod.SEMANA);

        // Assert: 60 minutos reservados sobre 240 de capacidad del lunes
        DayOccupancy lunes = dayOf(response, DayOfWeek.MONDAY);
        assertThat(lunes.bookedMinutes()).isEqualTo(60L);
        assertThat(lunes.capacityMinutes()).isEqualTo(240L);
        assertThat(lunes.occupancyPercent()).isEqualTo(25.0);
    }

    @Test
    void calculaLaOcupacionPorProfesionalConReservasReales() {
        // Arrange
        LocalDate monday = lunesDeLaSemanaActual();
        insertWeeklyWindow(monday.getDayOfWeek(), LocalTime.of(8, 0), LocalTime.of(12, 0));
        insertBooking(monday.atTime(9, 0), monday.atTime(11, 0), "CONFIRMADA");

        // Act
        OccupancyReportResponse response = occupancyReportService.generate(ReportPeriod.SEMANA);

        // Assert
        assertThat(response.byProfessional()).hasSize(1);
        ProfessionalOccupancy professional = response.byProfessional().get(0);
        assertThat(professional.professionalId()).isEqualTo(professionalId);
        assertThat(professional.professionalName()).isEqualTo("Profesional de Prueba");
        assertThat(professional.bookedMinutes()).isEqualTo(120L);
        assertThat(professional.capacityMinutes()).isEqualTo(240L);
        assertThat(professional.occupancyPercent()).isEqualTo(50.0);
    }

    @Test
    void unaReservaAtendidaTambienOcupaAgenda() {
        // Arrange
        LocalDate monday = lunesDeLaSemanaActual();
        insertWeeklyWindow(monday.getDayOfWeek(), LocalTime.of(8, 0), LocalTime.of(12, 0));
        insertBooking(monday.atTime(9, 0), monday.atTime(10, 0), "COMPLETED");

        // Act
        OccupancyReportResponse response = occupancyReportService.generate(ReportPeriod.SEMANA);

        // Assert
        assertThat(dayOf(response, DayOfWeek.MONDAY).bookedMinutes()).isEqualTo(60L);
    }

    @Test
    void elPorcentajeGlobalAcumulaLaCapacidadDeTodosLosDiasDelRango() {
        // Arrange: hay agenda y reservas en dos dias distintos de la semana.
        // El rango semanal contiene exactamente un lunes y un martes.
        insertWeeklyWindow(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0));
        insertWeeklyWindow(DayOfWeek.TUESDAY, LocalTime.of(8, 0), LocalTime.of(10, 0));
        insertBooking(lunesDeLaSemanaActual().atTime(9, 0), lunesDeLaSemanaActual().atTime(11, 0), "CONFIRMADA");
        insertBooking(martesDeLaSemanaActual().atTime(9, 0), martesDeLaSemanaActual().atTime(10, 0), "CONFIRMADA");

        // Act
        OccupancyReportResponse response = occupancyReportService.generate(ReportPeriod.SEMANA);

        // Assert: 180 minutos reservados sobre 360 de capacidad acumulada (240 + 120).
        // Si solo se sumara la capacidad del dia mas cargado (240) el resultado seria 75%.
        assertThat(dayOf(response, DayOfWeek.MONDAY).capacityMinutes()).isEqualTo(240L);
        assertThat(dayOf(response, DayOfWeek.TUESDAY).capacityMinutes()).isEqualTo(120L);
        assertThat(response.overallOccupancyPercent()).isEqualTo(50.0);
    }

    @Test
    void unaReservaCanceladaNoOcupaAgenda() {
        // Arrange
        LocalDate monday = lunesDeLaSemanaActual();
        insertWeeklyWindow(monday.getDayOfWeek(), LocalTime.of(8, 0), LocalTime.of(12, 0));
        insertBooking(monday.atTime(9, 0), monday.atTime(10, 0), "CANCELADA");

        // Act
        OccupancyReportResponse response = occupancyReportService.generate(ReportPeriod.SEMANA);

        // Assert
        assertThat(dayOf(response, DayOfWeek.MONDAY).bookedMinutes()).isZero();
        assertThat(dayOf(response, DayOfWeek.MONDAY).occupancyPercent()).isZero();
    }

    @Test
    void elReporteTraeSiempreLosSieteDiasDeLaSemana() {
        // Arrange
        insertWeeklyWindow(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0));

        // Act
        OccupancyReportResponse response = occupancyReportService.generate(ReportPeriod.MES);

        // Assert
        assertThat(response.byDayOfWeek()).hasSize(7);
        assertThat(response.byDayOfWeek())
                .extracting(DayOccupancy::dayOfWeek)
                .containsExactly(DayOfWeek.values());
    }

    @Test
    void elRangoDelReporteTerminaHoyYEmpiezaEnElDiaDelPeriodo() {
        // Arrange & Act
        OccupancyReportResponse response = occupancyReportService.generate(ReportPeriod.SEMANA);

        // Assert
        assertThat(response.period()).isEqualTo(ReportPeriod.SEMANA);
        assertThat(response.to()).isEqualTo(hoy);
        assertThat(response.from()).isEqualTo(hoy.minusDays(6));
    }

    // ------------------------------------------------------------------------------------------
    // QA/Exception: sin datos transaccionales en el periodo
    // ------------------------------------------------------------------------------------------

    @Test
    void sinReservasEnLaBaseGeneraElReporteEnCerosSinNingunaExcepcion() {
        // Arrange: el profesional existe y tiene agenda, pero no hay ninguna reserva
        insertWeeklyWindow(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0));

        // Act
        OccupancyReportResponse report = occupancyReportService.generate(ReportPeriod.MES);

        // Assert: no revienta y ademas...
        assertThatCode(() -> occupancyReportService.generate(ReportPeriod.MES))
                .doesNotThrowAnyException();

        // Assert: el nombre del caso promete "en ceros": los minutos reservados y los
        // porcentajes quedan en cero y los siete dias siguen presentes.
        assertThat(report.byDayOfWeek()).hasSize(7);
        assertThat(report.byDayOfWeek())
                .allSatisfy(day -> {
                    assertThat(day.bookedMinutes()).isZero();
                    assertThat(day.occupancyPercent()).isZero();
                });
        assertThat(report.overallOccupancyPercent()).isZero();
        assertThat(report.byProfessional()).hasSize(1);
        assertThat(report.byProfessional().get(0).bookedMinutes()).isZero();
        assertThat(report.byProfessional().get(0).occupancyPercent()).isZero();
    }

    @Test
    void sinReservasLosPorcentajesQuedanEnCeroYLosSieteDiasSiguenPresentes() {
        // Arrange: no hay franjas ni reservas en la base de datos

        // Act
        OccupancyReportResponse response = occupancyReportService.generate(ReportPeriod.MES);

        // Assert
        assertThat(response.byDayOfWeek()).hasSize(7);
        assertThat(response.byDayOfWeek())
                .allSatisfy(day -> {
                    assertThat(day.bookedMinutes()).isZero();
                    assertThat(day.capacityMinutes()).isZero();
                    assertThat(day.occupancyPercent()).isZero();
                });
        assertThat(response.overallOccupancyPercent()).isZero();
    }

    @Test
    void sinReservasLosMinutosReservadosDeCadaProfesionalQuedanEnCero() {
        // Arrange
        insertWeeklyWindow(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0));

        // Act
        OccupancyReportResponse response = occupancyReportService.generate(ReportPeriod.MES);

        // Assert: la capacidad depende de cuantos lunes caen en los 30 dias del rango,
        // pero los minutos reservados y los porcentajes siempre son cero.
        ProfessionalOccupancy professional = response.byProfessional().get(0);
        assertThat(response.byProfessional()).hasSize(1);
        assertThat(professional.bookedMinutes()).isZero();
        assertThat(professional.capacityMinutes()).isPositive();
        assertThat(professional.occupancyPercent()).isZero();
        assertThat(response.overallOccupancyPercent()).isZero();
    }

    @Test
    void sinReservasElPorcentajeGlobalNoEsNullNiNaN() {
        // Arrange & Act
        OccupancyReportResponse response = occupancyReportService.generate(ReportPeriod.MES);

        // Assert
        assertThat(response.overallOccupancyPercent()).isNotNull().isNotNaN().isZero();
    }

    // ------------------------------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------------------------------

    /** Lunes de la semana en curso: siempre cae dentro del rango de los últimos siete días. */
    private LocalDate lunesDeLaSemanaActual() {
        return hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /**
     * Martes que cae dentro del rango semanal del reporte (hoy - 6 .. hoy).
     *
     * <p>Si la semana en curso ya pasó (por ejemplo, hoy es lunes), el martes pertenece a la
     * semana anterior, que también cae dentro del rango de siete días.
     */
    private LocalDate martesDeLaSemanaActual() {
        LocalDate martes = lunesDeLaSemanaActual().plusDays(1);
        if (martes.isAfter(hoy)) {
            martes = martes.minusWeeks(1);
        }
        return martes;
    }

    private DayOccupancy dayOf(OccupancyReportResponse response, DayOfWeek day) {
        List<DayOccupancy> days = response.byDayOfWeek().stream()
                .filter(item -> item.dayOfWeek() == day)
                .toList();
        assertThat(days).hasSize(1);
        return days.get(0);
    }

    private void insertUser(UUID id, String email, String role) {
        jdbc.update("INSERT INTO app_user (id, email, password_hash, full_name, role, enabled, created_at, "
                        + "updated_at) VALUES (?, ?, 'test-not-a-real-password', ?, ?, true, ?, ?)",
                id, email, "Profesional de Prueba", role, now, now);
    }

    private void insertWeeklyWindow(DayOfWeek day, LocalTime start, LocalTime end) {
        jdbc.update("INSERT INTO horario_semanal (id, profesional_id, dia_semana, hora_inicio, hora_fin, "
                        + "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), professionalId, day.name(), start, end, now, now);
    }

    private void insertBooking(LocalDateTime start, LocalDateTime end, String status) {
        jdbc.update("INSERT INTO bookings (id, customer_id, professional_id, service_id, start_time, end_time, "
                        + "status) VALUES (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), customerId, professionalId, serviceId, start, end, status);
    }
}
