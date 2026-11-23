package com.bookly.backendcf.booking.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookly.backendcf.booking.application.ProfessionalAgendaService;
import com.bookly.backendcf.booking.presentation.dto.ProfessionalAgendaResponse;
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
 * Verifica contra PostgreSQL real la agenda del profesional (HU-14): orden cronologico, filtro
 * por dia y rango, exclusion de canceladas y, sobre todo, aislamiento entre profesionales.
 */
@Tag("integration")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:55432/bookly_test",
        "spring.datasource.username=bookly_test",
        "spring.datasource.password=bookly_test_local_2026",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "security.jwt.secret=BooklyIntegrationTestOnlySecretWithMoreThanSixtyFourCharacters123456789"
})
class ProfessionalAgendaIntegrationTest {

    // Lejos de cualquier dato de otras pruebas; no depende del dia en que se ejecute.
    private static final LocalDate DAY = LocalDate.of(2031, 3, 10);

    @Autowired
    private ProfessionalAgendaService agendaService;

    @Autowired
    private JdbcTemplate jdbc;

    private final UUID professionalId = UUID.randomUUID();
    private final UUID otherProfessionalId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID serviceId = UUID.randomUUID();
    private final OffsetDateTime now = OffsetDateTime.now();
    private final String suffix = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        insertUser(customerId, "cliente-" + suffix + "@example.test", "CUSTOMER", "Clienta HU14");
        insertUser(professionalId, "prof-" + suffix + "@example.test", "PROFESSIONAL", "Prof Uno");
        insertUser(otherProfessionalId, "otro-" + suffix + "@example.test", "PROFESSIONAL", "Prof Dos");
        for (UUID id : new UUID[] {professionalId, otherProfessionalId}) {
            jdbc.update("INSERT INTO profesionales (id, rol, especialidad, created_at, updated_at) "
                    + "VALUES (?, 'PROFESSIONAL', 'General', ?, ?)", id, now, now);
        }
        jdbc.update("INSERT INTO servicios (id, nombre, categoria, duracion_minutos, precio, estado, "
                        + "created_at, updated_at) VALUES (?, ?, 'General', 60, ?, 'ACTIVO', ?, ?)",
                serviceId, "Agenda IT " + suffix, new BigDecimal("100.00"), now, now);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM bookings WHERE customer_id = ?", customerId);
        jdbc.update("DELETE FROM profesionales WHERE id IN (?, ?)", professionalId, otherProfessionalId);
        jdbc.update("DELETE FROM servicios WHERE id = ?", serviceId);
        jdbc.update("DELETE FROM app_user WHERE id IN (?, ?, ?)",
                customerId, professionalId, otherProfessionalId);
    }

    @Test
    // Escenario Gherkin HU-14: Consultar mi agenda de atencion diaria
    // Lista ordenada cronologicamente con hora, nombre del cliente y tipo de servicio.
    void devuelveLaAgendaDelDiaOrdenadaCronologicamente() {
        // Arrange: se insertan desordenadas a proposito
        insertBooking(professionalId, DAY.atTime(15, 0), "CONFIRMADA");
        insertBooking(professionalId, DAY.atTime(8, 0), "CONFIRMADA");
        insertBooking(professionalId, DAY.atTime(11, 30), "CONFIRMADA");

        // Act
        ProfessionalAgendaResponse response = agendaService.find(professionalId, DAY, DAY);

        // Assert
        assertThat(response.content())
                .extracting(ProfessionalAgendaResponse.Item::startTime)
                .containsExactly(DAY.atTime(8, 0), DAY.atTime(11, 30), DAY.atTime(15, 0));
        assertThat(response.content()).allSatisfy(item -> {
            assertThat(item.customerName()).isEqualTo("Clienta HU14");
            assertThat(item.serviceName()).isEqualTo("Agenda IT " + suffix);
            assertThat(item.status()).isEqualTo("CONFIRMADA");
        });
        assertThat(response.message()).isNull();
    }

    @Test
    // Escenario Gherkin HU-14: Consultar mi agenda de atencion diaria
    // CRITICO: nunca devuelve datos de otros profesionales.
    void noDevuelveReservasDeOtrosProfesionales() {
        // Arrange
        insertBooking(professionalId, DAY.atTime(9, 0), "CONFIRMADA");
        insertBooking(otherProfessionalId, DAY.atTime(9, 0), "CONFIRMADA");
        insertBooking(otherProfessionalId, DAY.atTime(10, 0), "CONFIRMADA");

        // Act
        ProfessionalAgendaResponse mine = agendaService.find(professionalId, DAY, DAY);
        ProfessionalAgendaResponse theirs = agendaService.find(otherProfessionalId, DAY, DAY);

        // Assert
        assertThat(mine.content()).hasSize(1);
        assertThat(theirs.content()).hasSize(2);
        assertThat(mine.content()).extracting(ProfessionalAgendaResponse.Item::id)
                .doesNotContainAnyElementsOf(theirs.content().stream()
                        .map(ProfessionalAgendaResponse.Item::id).toList());
    }

    @Test
    // Escenario Gherkin HU-14: Consultar mi agenda de atencion diaria
    void noIncluyeReservasDeOtrosDias() {
        // Arrange
        insertBooking(professionalId, DAY.minusDays(1).atTime(23, 30), "CONFIRMADA");
        insertBooking(professionalId, DAY.atTime(0, 0), "CONFIRMADA");
        insertBooking(professionalId, DAY.atTime(23, 30), "CONFIRMADA");
        insertBooking(professionalId, DAY.plusDays(1).atTime(0, 0), "CONFIRMADA");

        // Act
        ProfessionalAgendaResponse response = agendaService.find(professionalId, DAY, DAY);

        // Assert: el limite inferior es inclusivo y el superior exclusivo
        assertThat(response.content())
                .extracting(ProfessionalAgendaResponse.Item::startTime)
                .containsExactly(DAY.atTime(0, 0), DAY.atTime(23, 30));
    }

    @Test
    // Decision de negocio: las reservas canceladas no se muestran en la agenda.
    void noIncluyeReservasCanceladas() {
        // Arrange
        insertBooking(professionalId, DAY.atTime(9, 0), "CONFIRMADA");
        insertBooking(professionalId, DAY.atTime(10, 0), "CANCELADA");

        // Act
        ProfessionalAgendaResponse response = agendaService.find(professionalId, DAY, DAY);

        // Assert
        assertThat(response.content())
                .extracting(ProfessionalAgendaResponse.Item::startTime)
                .containsExactly(DAY.atTime(9, 0));
    }

    @Test
    // Al cancelar se libera la franja: si otro cliente la reserva, la nueva reserva si aparece.
    void laFranjaLiberadaAparecePorLaNuevaReservaYNoPorLaCancelada() {
        // Arrange
        insertBooking(professionalId, DAY.atTime(9, 0), "CANCELADA");
        insertBooking(professionalId, DAY.atTime(9, 0), "CONFIRMADA");

        // Act
        ProfessionalAgendaResponse response = agendaService.find(professionalId, DAY, DAY);

        // Assert
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).status()).isEqualTo("CONFIRMADA");
    }

    @Test
    // Las reservas ya atendidas del dia siguen en la agenda de ese dia.
    void incluyeLasReservasCompletadasDelDia() {
        // Arrange
        insertBooking(professionalId, DAY.atTime(8, 0), "COMPLETED");
        insertBooking(professionalId, DAY.atTime(9, 0), "CONFIRMADA");

        // Act
        ProfessionalAgendaResponse response = agendaService.find(professionalId, DAY, DAY);

        // Assert
        assertThat(response.content())
                .extracting(ProfessionalAgendaResponse.Item::status)
                .containsExactly("COMPLETED", "CONFIRMADA");
    }

    @Test
    // Escenario Gherkin HU-14: Consultar agenda en un rango de fechas ampliado
    // Lunes a viernes: listado cronologico consolidado, solo del profesional.
    void devuelveLaSemanaConsolidadaYOrdenadaSoloDelProfesional() {
        // Arrange: 10/03/2031 es lunes
        LocalDate monday = DAY;
        LocalDate friday = DAY.plusDays(4);
        insertBooking(professionalId, friday.atTime(18, 0), "CONFIRMADA");
        insertBooking(professionalId, monday.atTime(8, 0), "CONFIRMADA");
        insertBooking(professionalId, monday.plusDays(2).atTime(12, 0), "CONFIRMADA");
        insertBooking(professionalId, monday.minusDays(1).atTime(12, 0), "CONFIRMADA");
        insertBooking(professionalId, friday.plusDays(1).atTime(8, 0), "CONFIRMADA");
        insertBooking(otherProfessionalId, monday.plusDays(1).atTime(8, 0), "CONFIRMADA");

        // Act
        ProfessionalAgendaResponse response = agendaService.find(professionalId, monday, friday);

        // Assert
        assertThat(response.content())
                .extracting(ProfessionalAgendaResponse.Item::startTime)
                .containsExactly(monday.atTime(8, 0), monday.plusDays(2).atTime(12, 0),
                        friday.atTime(18, 0));
    }

    @Test
    // Escenario Gherkin HU-14: Consultar mi agenda sin clientes programados
    void sinReservasDevuelveListaVaciaYMensaje() {
        // Act
        ProfessionalAgendaResponse response = agendaService.find(professionalId, DAY, DAY);

        // Assert
        assertThat(response.content()).isEmpty();
        assertThat(response.message()).isNotBlank();
    }

    @Test
    // Escenario Gherkin HU-14: Consultar mi agenda sin clientes programados
    // Un profesional sin citas no ve las de otro: su agenda esta libre aunque haya mas reservas.
    void laAgendaEstaLibreAunqueOtroProfesionalTengaReservas() {
        // Arrange
        insertBooking(otherProfessionalId, DAY.atTime(9, 0), "CONFIRMADA");

        // Act
        ProfessionalAgendaResponse response = agendaService.find(professionalId, DAY, DAY);

        // Assert
        assertThat(response.content()).isEmpty();
        assertThat(response.message()).isNotBlank();
    }

    @Test
    // Escenario Gherkin HU-14: Consultar mi agenda de atencion diaria
    // Sin parametros se consulta "hoy" en Colombia: una reserva de hoy debe aparecer.
    void sinParametrosIncluyeLasReservasDeHoyEnColombia() {
        // Arrange
        LocalDate todayInColombia = LocalDate.now(ProfessionalAgendaService.AGENDA_ZONE);
        LocalDateTime noonToday = todayInColombia.atTime(12, 0);
        insertBooking(professionalId, noonToday, "CONFIRMADA");

        // Act
        ProfessionalAgendaResponse response = agendaService.find(professionalId, null, null);

        // Assert
        assertThat(response.content())
                .extracting(ProfessionalAgendaResponse.Item::startTime)
                .contains(noonToday);
        assertThat(response.from()).isEqualTo(todayInColombia);
    }

    private void insertUser(UUID id, String email, String role, String fullName) {
        jdbc.update("INSERT INTO app_user (id, email, password_hash, full_name, role, enabled, created_at, "
                        + "updated_at) VALUES (?, ?, 'test-not-a-real-password', ?, ?, true, ?, ?)",
                id, email, fullName, role, now, now);
    }

    private void insertBooking(UUID ownerProfessionalId, LocalDateTime start, String status) {
        jdbc.update("INSERT INTO bookings (id, customer_id, professional_id, service_id, start_time, "
                        + "end_time, status) VALUES (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), customerId, ownerProfessionalId, serviceId,
                start, start.plusMinutes(30), status);
    }
}
