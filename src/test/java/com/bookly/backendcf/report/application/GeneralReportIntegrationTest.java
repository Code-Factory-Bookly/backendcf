package com.bookly.backendcf.report.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookly.backendcf.report.domain.model.ReportPeriod;
import com.bookly.backendcf.report.presentation.dto.GeneralReportResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verifica contra PostgreSQL real que las consultas del reporte general (HU-16) devuelven los
 * números correctos: servicio, adaptador, repositorio JPA, SQL nativo y migraciones Flyway.
 *
 * <p><b>Cómo se aíslan los datos.</b> La base de pruebas es compartida y puede tener reservas de
 * otras pruebas, así que no se asume que esté vacía. Cada caso consulta el reporte antes de
 * insertar y después, y compara la <i>diferencia</i>. Los recursos sí se verifican con valores
 * absolutos, porque los crea la propia prueba con nombre único. Todo lo que se inserta se elimina
 * al final.
 *
 * <p>Las fechas son relativas a hoy para que los casos no caduquen. Cada reserva del mismo
 * profesional va en un día distinto para no chocar con la restricción de solape de la base.
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
class GeneralReportIntegrationTest {

    @Autowired
    private GeneralReportService reportService;

    @Autowired
    private JdbcTemplate jdbc;

    private final UUID professionalId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID serviceId = UUID.randomUUID();
    private final UUID otherServiceId = UUID.randomUUID();
    private final UUID resourceId = UUID.randomUUID();
    private final UUID idleResourceId = UUID.randomUUID();
    private final OffsetDateTime now = OffsetDateTime.now();
    private final String suffix = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        insertUser(professionalId, "professional-" + suffix + "@example.test", "PROFESSIONAL");
        insertUser(customerId, "customer-" + suffix + "@example.test", "CUSTOMER");
        jdbc.update("INSERT INTO profesionales (id, rol, especialidad, created_at, updated_at) "
                + "VALUES (?, 'PROFESSIONAL', 'General', ?, ?)", professionalId, now, now);
        insertService(serviceId, "General IT " + suffix, "100.00");
        insertService(otherServiceId, "General IT otro " + suffix, "40.00");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM bookings WHERE professional_id = ?", professionalId);
        jdbc.update("DELETE FROM servicio_recurso WHERE servicio_id IN (?, ?)", serviceId, otherServiceId);
        jdbc.update("DELETE FROM recursos_fisicos WHERE id IN (?, ?)", resourceId, idleResourceId);
        jdbc.update("DELETE FROM servicios WHERE id IN (?, ?)", serviceId, otherServiceId);
        jdbc.update("DELETE FROM profesionales WHERE id = ?", professionalId);
        jdbc.update("DELETE FROM app_user WHERE id IN (?, ?)", professionalId, customerId);
    }

    // ------------------------------------------------------------------------------------------
    // Totales: qué reservas entran en el periodo y cuáles cuentan como canceladas
    // ------------------------------------------------------------------------------------------

    @Test
    void cuentaSoloLasReservasDelPeriodoYSeparaLasCanceladas() {
        GeneralReportResponse antes = reportService.generate(ReportPeriod.MES);

        insertBooking(hace(1), "CONFIRMADA", "100.00");
        insertBooking(hace(2), "CANCELADA", "100.00");
        insertBooking(hace(3), "COMPLETED", "80.00");
        insertBooking(hace(40), "CONFIRMADA", "500.00"); // fuera de los 30 días

        GeneralReportResponse despues = reportService.generate(ReportPeriod.MES);

        assertThat(despues.totalReservations() - antes.totalReservations())
                .as("Entran las 3 reservas del periodo (la de hace 40 días no)").isEqualTo(3L);
        assertThat(despues.cancelledReservations() - antes.cancelledReservations())
                .as("Solo una está cancelada").isEqualTo(1L);
    }

    @Test
    void elPeriodoSemanaExcluyeLasReservasDeHaceMasDeSieteDias() {
        GeneralReportResponse mesAntes = reportService.generate(ReportPeriod.MES);
        GeneralReportResponse semanaAntes = reportService.generate(ReportPeriod.SEMANA);

        insertBooking(hace(10), "CONFIRMADA", "100.00");

        assertThat(reportService.generate(ReportPeriod.MES).totalReservations()
                - mesAntes.totalReservations()).isEqualTo(1L);
        assertThat(reportService.generate(ReportPeriod.SEMANA).totalReservations()
                - semanaAntes.totalReservations()).isZero();
    }

    // ------------------------------------------------------------------------------------------
    // Ingresos reconocidos
    // ------------------------------------------------------------------------------------------

    @Test
    void losIngresosSumanSoloLasReservasConfirmadasYAtendidasDelPeriodo() {
        BigDecimal antes = reportService.generate(ReportPeriod.MES).recognizedRevenue();

        insertBooking(hace(1), "CONFIRMADA", "100.00");
        insertBooking(hace(2), "CANCELADA", "999.00");  // cancelada: no reconoce ingreso
        insertBooking(hace(3), "COMPLETED", "80.00");
        insertBooking(hace(40), "CONFIRMADA", "500.00"); // fuera del periodo

        BigDecimal despues = reportService.generate(ReportPeriod.MES).recognizedRevenue();

        assertThat(despues.subtract(antes)).isEqualByComparingTo("180.00");
    }

    @Test
    void losIngresosUsanElPrecioGuardadoAunqueElServicioCambieDePrecio() {
        BigDecimal antes = reportService.generate(ReportPeriod.MES).recognizedRevenue();
        insertBooking(hace(1), "CONFIRMADA", "100.00");

        // El servicio sube de 100 a 150 después de la reserva: el ingreso ya reconocido no cambia.
        jdbc.update("UPDATE servicios SET precio = ? WHERE id = ?", new BigDecimal("150.00"), serviceId);

        BigDecimal despues = reportService.generate(ReportPeriod.MES).recognizedRevenue();
        assertThat(despues.subtract(antes)).isEqualByComparingTo("100.00");
    }

    @Test
    void unaReservaSinPrecioGuardadoUsaElPrecioActualDelServicio() {
        BigDecimal antes = reportService.generate(ReportPeriod.MES).recognizedRevenue();
        insertBooking(hace(1), "CONFIRMADA", null); // reserva anterior a la migración del precio

        BigDecimal despues = reportService.generate(ReportPeriod.MES).recognizedRevenue();

        assertThat(despues.subtract(antes)).isEqualByComparingTo("100.00");
    }

    // ------------------------------------------------------------------------------------------
    // Uso de recursos
    // ------------------------------------------------------------------------------------------

    @Test
    void elUsoDelRecursoCuentaLasReservasConfirmadasYAtendidasDeLosServiciosQueLoUsan() {
        insertResource(resourceId, "Camilla IT " + suffix, 3);
        insertResource(idleResourceId, "Sala libre IT " + suffix, 1);
        jdbc.update("INSERT INTO servicio_recurso (servicio_id, recurso_id) VALUES (?, ?)",
                serviceId, resourceId);

        insertBooking(hace(1), "CONFIRMADA", "100.00");
        insertBooking(hace(2), "COMPLETED", "100.00");
        insertBooking(hace(3), "CANCELADA", "100.00");  // cancelada: no usa el recurso
        insertBooking(hace(40), "CONFIRMADA", "100.00"); // fuera del periodo
        insertBookingDe(otherServiceId, hace(4), "CONFIRMADA", "40.00"); // servicio sin ese recurso

        GeneralReportResponse reporte = reportService.generate(ReportPeriod.MES);

        GeneralReportResponse.ResourceUsage camilla = usoDe(reporte, resourceId);
        assertThat(camilla.totalQuantity()).isEqualTo(3);
        assertThat(camilla.bookingsUsingResource()).isEqualTo(2L);
    }

    @Test
    void unRecursoSinReservasSigueApareciendoConCeroUsos() {
        insertResource(idleResourceId, "Sala libre IT " + suffix, 1);

        GeneralReportResponse.ResourceUsage sala = usoDe(reportService.generate(ReportPeriod.MES), idleResourceId);

        assertThat(sala.totalQuantity()).isEqualTo(1);
        assertThat(sala.bookingsUsingResource()).isZero();
    }

    // ------------------------------------------------------------------------------------------
    // Ayudantes
    // ------------------------------------------------------------------------------------------

    private static GeneralReportResponse.ResourceUsage usoDe(GeneralReportResponse reporte, UUID recurso) {
        return reporte.resourceUsage().stream()
                .filter(uso -> uso.resourceId().equals(recurso))
                .findFirst()
                .orElseThrow(() -> new AssertionError("El recurso " + recurso + " no aparece en el reporte"));
    }

    /** Un día de hace {@code dias} días a las 10:00, para que el rango sea estable. */
    private static LocalDateTime hace(int dias) {
        return LocalDate.now().minusDays(dias).atTime(10, 0);
    }

    private void insertUser(UUID id, String email, String role) {
        jdbc.update("INSERT INTO app_user (id, email, password_hash, full_name, role, enabled, created_at, "
                        + "updated_at) VALUES (?, ?, 'test-not-a-real-password', 'Usuario de Prueba', ?, true, ?, ?)",
                id, email, role, now, now);
    }

    private void insertService(UUID id, String name, String price) {
        jdbc.update("INSERT INTO servicios (id, nombre, categoria, duracion_minutos, precio, estado, "
                        + "created_at, updated_at) VALUES (?, ?, 'General', 60, ?, 'ACTIVO', ?, ?)",
                id, name, new BigDecimal(price), now, now);
    }

    private void insertResource(UUID id, String name, int quantity) {
        jdbc.update("INSERT INTO recursos_fisicos (id, nombre, cantidad_total, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?)", id, name, quantity, now, now);
    }

    private void insertBooking(LocalDateTime start, String status, String priceAtBooking) {
        insertBookingDe(serviceId, start, status, priceAtBooking);
    }

    private void insertBookingDe(UUID service, LocalDateTime start, String status, String priceAtBooking) {
        jdbc.update("INSERT INTO bookings (id, customer_id, professional_id, service_id, start_time, end_time, "
                        + "status, price_at_booking) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), customerId, professionalId, service, start, start.plusMinutes(60), status,
                priceAtBooking == null ? null : new BigDecimal(priceAtBooking));
    }
}
