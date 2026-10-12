package com.bookly.backendcf.availability.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookly.backendcf.availability.presentation.dto.AvailabilityResponse;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verifica contra PostgreSQL real que la consulta de horarios y reservas (HU-07) funciona de punta a punta:
 * servicio, adaptador, repositorios JPA y migraciones Flyway.
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
class AvailabilityIntegrationTest {

    @Autowired
    private AvailabilityService availabilityService;

    @Autowired
    private JdbcTemplate jdbc;

    private final UUID professionalId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID serviceId = UUID.randomUUID();
    private final LocalDate monday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
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
                serviceId, "Disponibilidad IT " + suffix, new BigDecimal("100.00"), now, now);
        jdbc.update("INSERT INTO horario_semanal (id, profesional_id, dia_semana, hora_inicio, hora_fin, "
                        + "created_at, updated_at) VALUES (?, ?, 'MONDAY', ?, ?, ?, ?)",
                UUID.randomUUID(), professionalId, LocalTime.of(8, 0), LocalTime.of(12, 0), now, now);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM bookings WHERE professional_id = ?", professionalId);
        jdbc.update("DELETE FROM horario_semanal WHERE profesional_id = ?", professionalId);
        jdbc.update("DELETE FROM profesionales WHERE id = ?", professionalId);
        jdbc.update("DELETE FROM servicios WHERE id = ?", serviceId);
        jdbc.update("DELETE FROM app_user WHERE id IN (?, ?)", professionalId, customerId);
    }

    @Test
    void consultaLasFranjasRealesYExcluyeLasReservasConfirmadas() {
        insertBooking(monday.atTime(9, 0), monday.atTime(10, 0), "CONFIRMADA");

        AvailabilityResponse response = availabilityService.find(professionalId, serviceId, monday, monday);

        assertThat(response.available()).isTrue();
        assertThat(response.days()).hasSize(1);
        assertThat(response.days().get(0).slots())
                .extracting(AvailabilityResponse.Block::startTime)
                .containsExactly(monday.atTime(8, 0), monday.atTime(10, 0), monday.atTime(11, 0));
    }

    @Test
    void unaReservaCanceladaNoOcupaElBloque() {
        insertBooking(monday.atTime(9, 0), monday.atTime(10, 0), "CANCELADA");

        AvailabilityResponse response = availabilityService.find(professionalId, serviceId, monday, monday);

        assertThat(response.days().get(0).slots()).hasSize(4);
    }

    @Test
    void sinHorariosEnElRangoInformaQueNoHayDisponibilidad() {
        LocalDate tuesday = monday.plusDays(1);

        AvailabilityResponse response = availabilityService.find(professionalId, serviceId, tuesday, tuesday);

        assertThat(response.available()).isFalse();
        assertThat(response.days()).isEmpty();
        assertThat(response.message()).contains("otro profesional");
    }

    @Test
    void profesionalTotalmenteReservadoInformaQueNoHayDisponibilidad() {
        for (int hour = 8; hour < 12; hour++) {
            insertBooking(monday.atTime(hour, 0), monday.atTime(hour + 1, 0), "CONFIRMADA");
        }

        AvailabilityResponse response = availabilityService.find(professionalId, serviceId, monday, monday);

        assertThat(response.available()).isFalse();
        assertThat(response.days()).isEmpty();
    }

    private void insertUser(UUID id, String email, String role) {
        jdbc.update("INSERT INTO app_user (id, email, password_hash, full_name, role, enabled, created_at, "
                        + "updated_at) VALUES (?, ?, 'test-not-a-real-password', 'Availability Test', ?, true, ?, ?)",
                id, email, role, now, now);
    }

    private void insertBooking(LocalDateTime start, LocalDateTime end, String status) {
        jdbc.update("INSERT INTO bookings (id, customer_id, professional_id, service_id, start_time, end_time, "
                        + "status) VALUES (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), customerId, professionalId, serviceId, start, end, status);
    }
}