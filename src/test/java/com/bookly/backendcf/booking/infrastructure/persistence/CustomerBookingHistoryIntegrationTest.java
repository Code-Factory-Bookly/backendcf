package com.bookly.backendcf.booking.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookly.backendcf.booking.application.CustomerBookingHistoryService;
import com.bookly.backendcf.booking.domain.model.BookingStatus;
import com.bookly.backendcf.booking.presentation.dto.BookingHistoryPageResponse;
import com.bookly.backendcf.booking.presentation.dto.BookingResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
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
 * Verifica contra PostgreSQL real que la consulta de historial del cliente (HU-13) filtra de
 * forma correcta por cliente, rango de fechas y estado, y que nunca expone reservas de
 * otras cuentas.
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
class CustomerBookingHistoryIntegrationTest {

    @Autowired
    private CustomerBookingHistoryService customerBookingHistoryService;

    @Autowired
    private JdbcTemplate jdbc;

    private final UUID customerId = UUID.randomUUID();
    private final UUID otherCustomerId = UUID.randomUUID();
    private final UUID professionalId = UUID.randomUUID();
    private final UUID serviceId = UUID.randomUUID();
    private final OffsetDateTime now = OffsetDateTime.now();
    private final String suffix = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        insertUser(customerId, "customer-" + suffix + "@example.test", "CUSTOMER");
        insertUser(otherCustomerId, "other-customer-" + suffix + "@example.test", "CUSTOMER");
        insertUser(professionalId, "professional-" + suffix + "@example.test", "PROFESSIONAL");
        jdbc.update("INSERT INTO profesionales (id, rol, especialidad, created_at, updated_at) "
                + "VALUES (?, 'PROFESSIONAL', 'General', ?, ?)", professionalId, now, now);
        jdbc.update("INSERT INTO servicios (id, nombre, categoria, duracion_minutos, precio, estado, "
                        + "created_at, updated_at) VALUES (?, ?, 'General', 60, ?, 'ACTIVO', ?, ?)",
                serviceId, "Historial IT " + suffix, new BigDecimal("100.00"), now, now);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM bookings WHERE customer_id IN (?, ?)", customerId, otherCustomerId);
        jdbc.update("DELETE FROM profesionales WHERE id = ?", professionalId);
        jdbc.update("DELETE FROM servicios WHERE id = ?", serviceId);
        jdbc.update("DELETE FROM app_user WHERE id IN (?, ?, ?)",
                customerId, otherCustomerId, professionalId);
    }

    @Test
    // Escenario Gherkin HU-13: Consultar historial de reservas
    // El listado del cliente incluye sus reservas confirmadas, canceladas e historicas.
    void devuelveElHistorialDelClienteConSusReservas() {
        // Arrange
        LocalDateTime past = LocalDateTime.now().minusDays(20).withSecond(0).withNano(0);
        LocalDateTime future = LocalDateTime.now().plusDays(20).withSecond(0).withNano(0);

        insertBooking(customerId, past, "COMPLETED");
        insertBooking(customerId, future, "CONFIRMADA");
        insertBooking(customerId, future.plusDays(1), "CANCELADA");

        // Act
        BookingHistoryPageResponse response = customerBookingHistoryService.find(
                customerId, null, null, null, 0, 50, "asc");

        // Assert
        assertThat(response.content()).hasSize(3);
        assertThat(response.content())
                .extracting(BookingResponse::status)
                .containsExactly("COMPLETED", "CONFIRMADA", "CANCELADA");
        assertThat(response.content())
                .allSatisfy(item ->
                        assertThat(item.customerId()).isEqualTo(customerId));
        assertThat(response.totalElements()).isEqualTo(3);
    }

    @Test
    // Escenario Gherkin HU-13: Consultar historial de reservas
    // CRITICO: la consulta NUNCA devuelve reservas de otros clientes.
    void noDevuelveReservasDeOtrosClientes() {
        // Arrange
        LocalDateTime mine = LocalDateTime.now().plusDays(3).withSecond(0).withNano(0);
        LocalDateTime theirs = LocalDateTime.now().plusDays(4).withSecond(0).withNano(0);

        insertBooking(customerId, mine, "CONFIRMADA");
        insertBooking(otherCustomerId, theirs, "CONFIRMADA");

        // Act
        BookingHistoryPageResponse response = customerBookingHistoryService.find(
                customerId, null, null, null, 0, 50, "asc");

        // Assert
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).customerId()).isEqualTo(customerId);
        assertThat(response.content().get(0).startTime()).isEqualTo(mine);
        assertThat(response.content().get(0).id()).isNotEqualTo(
                jdbc.queryForObject(
                        "SELECT id FROM bookings WHERE customer_id = ?",
                        UUID.class,
                        otherCustomerId));
    }

    @Test
    // Escenario Gherkin HU-13: Consultar historial de reservas
    // El filtro por rango de fechas es INCLUSIVO sobre startTime: una reserva cuya
    // fecha de inicio cae exactamente en el limite "from" o exactamente en el limite
    // "to" debe aparecer en el historial. Los limites son fijos (no now()) para que
    // el limite derecho sea verificable de forma determinista.
    void filtraElHistorialPorRangoDeFechas() {
        // Arrange
        LocalDateTime from = LocalDateTime.of(2026, 3, 10, 8, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 3, 20, 18, 0, 0);

        LocalDateTime before = from.minusDays(1);
        LocalDateTime exactlyFrom = from;
        LocalDateTime inside = from.plusDays(5);
        LocalDateTime exactlyTo = to;
        LocalDateTime after = to.plusDays(1);

        insertBooking(customerId, before, "COMPLETED");
        insertBooking(customerId, exactlyFrom, "COMPLETED");
        insertBooking(customerId, inside, "COMPLETED");
        insertBooking(customerId, exactlyTo, "CONFIRMADA");
        insertBooking(customerId, after, "CONFIRMADA");

        // Act
        BookingHistoryPageResponse response = customerBookingHistoryService.find(
                customerId, from, to, null, 0, 50, "asc");

        // Assert: el rango es inclusivo en ambos extremos.
        assertThat(response.content()).hasSize(3);
        assertThat(response.content())
                .extracting(BookingResponse::startTime)
                .containsExactly(exactlyFrom, inside, exactlyTo);
        assertThat(response.totalElements()).isEqualTo(3);
    }

    @Test
    // Escenario Gherkin HU-13: Consultar historial de reservas
    // Los nombres de servicio, profesional y cliente se resuelven contra la base real
    // (no contra datos simulados): el servicio se llama "Historial IT ..." y tanto el
    // cliente como el profesional tienen el nombre completo "HU13 Test".
    void resuelveLosNombresDeServicioClienteYProfesionalDesdeLaBase() {
        // Arrange
        LocalDateTime start = LocalDateTime.of(2026, 5, 4, 10, 0, 0);
        insertBooking(customerId, start, "CONFIRMADA");

        // Act
        BookingHistoryPageResponse response = customerBookingHistoryService.find(
                customerId, null, null, null, 0, 50, "asc");

        // Assert
        assertThat(response.content()).hasSize(1);
        BookingResponse first = response.content().get(0);
        assertThat(first.serviceName())
                .isNotNull()
                .startsWith("Historial IT");
        assertThat(first.professionalName()).isEqualTo("HU13 Test");
        assertThat(first.customerName()).isEqualTo("HU13 Test");
    }

    @Test
    // Escenario Gherkin HU-13: Consultar historial de reservas
    // El filtro por estado restringe el historial a las reservas con ese estado.
    void filtraElHistorialPorEstado() {
        // Arrange
        LocalDateTime confirmed = LocalDateTime.now().plusDays(2).withSecond(0).withNano(0);
        LocalDateTime cancelled = LocalDateTime.now().plusDays(3).withSecond(0).withNano(0);
        LocalDateTime rescheduled = LocalDateTime.now().plusDays(4).withSecond(0).withNano(0);

        insertBooking(customerId, confirmed, "CONFIRMADA");
        insertBooking(customerId, cancelled, "CANCELADA");
        insertBooking(customerId, rescheduled, "RESCHEDULED");

        // Act
        BookingHistoryPageResponse response = customerBookingHistoryService.find(
                customerId, null, null, BookingStatus.CANCELADA, 0, 50, "asc");

        // Assert
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).status()).isEqualTo("CANCELADA");
        assertThat(response.content().get(0).startTime()).isEqualTo(cancelled);
    }

    @Test
    // Escenario Gherkin HU-13: Consultar historial de reservas
    // El filtro por estado tambien reconoce las reservas reprogramadas.
    void filtraElHistorialPorEstadoRescheduled() {
        // Arrange
        LocalDateTime confirmed = LocalDateTime.now().plusDays(2).withSecond(0).withNano(0);
        LocalDateTime rescheduled = LocalDateTime.now().plusDays(3).withSecond(0).withNano(0);

        insertBooking(customerId, confirmed, "CONFIRMADA");
        insertBooking(customerId, rescheduled, "RESCHEDULED");

        // Act
        BookingHistoryPageResponse response = customerBookingHistoryService.find(
                customerId, null, null, BookingStatus.RESCHEDULED, 0, 50, "asc");

        // Assert
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).status()).isEqualTo("RESCHEDULED");
        assertThat(response.content().get(0).startTime()).isEqualTo(rescheduled);
    }

    @Test
    // Escenario Gherkin HU-13: Consultar historial de reservas
    // El orden descendente invierte la presentacion cronologica.
    void ordenaElHistorialDeFormaEstable() {
        // Arrange
        LocalDateTime base = LocalDateTime.now().plusDays(5).withSecond(0).withNano(0);
        LocalDateTime earliest = base;
        LocalDateTime middle = base.plusDays(1);
        LocalDateTime latest = base.plusDays(2);

        insertBooking(customerId, earliest, "CONFIRMADA");
        insertBooking(customerId, middle, "CONFIRMADA");
        insertBooking(customerId, latest, "CONFIRMADA");

        // Act
        BookingHistoryPageResponse ascending = customerBookingHistoryService.find(
                customerId, null, null, null, 0, 50, "asc");
        BookingHistoryPageResponse descending = customerBookingHistoryService.find(
                customerId, null, null, null, 0, 50, "desc");

        // Assert
        assertThat(ascending.content())
                .extracting(BookingResponse::startTime)
                .containsExactly(earliest, middle, latest);
        assertThat(descending.content())
                .extracting(BookingResponse::startTime)
                .containsExactly(latest, middle, earliest);
    }

    @Test
    // Escenario Gherkin HU-13: Consultar historial de reservas
    // El orden es estable: cuando varias reservas comparten EXACTAMENTE la misma
    // fecha de inicio, el desempate es determinista por identificador, tanto en
    // orden ascendente como descendente.
    void ordenaElHistorialDeFormaEstableConFechasIdenticas() {
        // Arrange
        LocalDateTime sameMoment = LocalDateTime.of(2026, 6, 15, 9, 30, 0);

        // El indice unico "unique_booking_per_professional_time" solo aplica a las
        // reservas CONFIRMADA, asi que las reservas con la misma hora de inicio se
        // insertan en estado CANCELADA para poder coexistir en la misma franja.
        insertBookingWithId(customerId, sameMoment, "CANCELADA");
        insertBookingWithId(customerId, sameMoment, "CANCELADA");
        insertBookingWithId(customerId, sameMoment, "CANCELADA");

        // El criterio de desempate es el identificador, con el mismo orden que usa
        // la base de datos (ORDER BY id), de modo que la asercion no dependa de una
        // suposicion sobre la colacion de uuid.
        List<UUID> idsAscending = idsOrderedById("ASC");

        // Act
        BookingHistoryPageResponse ascending = customerBookingHistoryService.find(
                customerId, null, null, null, 0, 50, "asc");
        BookingHistoryPageResponse descending = customerBookingHistoryService.find(
                customerId, null, null, null, 0, 50, "desc");

        // Assert: en ascendente el desempate por id va de menor a mayor...
        assertThat(ascending.content())
                .extracting(BookingResponse::startTime)
                .containsExactly(sameMoment, sameMoment, sameMoment);
        assertThat(ascending.content())
                .extracting(BookingResponse::id)
                .containsExactlyElementsOf(idsAscending);

        // ...y en descendente se invierte exactamente.
        assertThat(descending.content())
                .extracting(BookingResponse::startTime)
                .containsExactly(sameMoment, sameMoment, sameMoment);
        assertThat(descending.content())
                .extracting(BookingResponse::id)
                .containsExactlyElementsOf(
                        idsOrderedById("DESC"));
    }

    /**
     * Identificadores de las reservas del cliente en el orden indicado por el
     * identificador, usando el mismo criterio de orden que usa PostgreSQL.
     */
    private List<UUID> idsOrderedById(String direction) {
        return jdbc.queryForList(
                "SELECT id FROM bookings WHERE customer_id = ? ORDER BY id " + direction,
                UUID.class,
                customerId);
    }

    @Test
    // Escenario Gherkin HU-13: Consultar historial de reservas
    // La paginacion respeta page y size sobre el historial del cliente.
    void paginaElHistorialDelCliente() {
        // Arrange
        LocalDateTime base = LocalDateTime.now().plusDays(5).withSecond(0).withNano(0);
        insertBooking(customerId, base, "CONFIRMADA");
        insertBooking(customerId, base.plusDays(1), "CONFIRMADA");
        insertBooking(customerId, base.plusDays(2), "CONFIRMADA");

        // Act
        BookingHistoryPageResponse firstPage = customerBookingHistoryService.find(
                customerId, null, null, null, 0, 2, "asc");
        BookingHistoryPageResponse secondPage = customerBookingHistoryService.find(
                customerId, null, null, null, 1, 2, "asc");

        // Assert
        assertThat(firstPage.content()).hasSize(2);
        assertThat(firstPage.totalElements()).isEqualTo(3);
        assertThat(firstPage.totalPages()).isEqualTo(2);
        assertThat(secondPage.content()).hasSize(1);
        assertThat(secondPage.page()).isEqualTo(1);
    }

    @Test
    // QA/Exception: Sin reservas registradas
    void informaQueNoHayReservasCuandoElClienteNoTieneNinguna() {
        // Arrange
        insertBooking(otherCustomerId, LocalDateTime.now().plusDays(6), "CONFIRMADA");

        // Act
        BookingHistoryPageResponse response = customerBookingHistoryService.find(
                customerId, null, null, null, 0, 50, "asc");

        // Assert
        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
        assertThat(response.message())
                .isEqualTo(CustomerBookingHistoryService.MESSAGE_SIN_RESERVAS);
    }

    private void insertUser(UUID id, String email, String role) {
        jdbc.update("INSERT INTO app_user (id, email, password_hash, full_name, role, enabled, created_at, "
                        + "updated_at) VALUES (?, ?, 'test-not-a-real-password', 'HU13 Test', ?, true, ?, ?)",
                id, email, role, now, now);
    }

    private void insertBooking(UUID ownerCustomerId, LocalDateTime start, String status) {
        insertBookingWithId(ownerCustomerId, start, status);
    }

    private UUID insertBookingWithId(UUID ownerCustomerId, LocalDateTime start, String status) {
        UUID bookingId = UUID.randomUUID();
        jdbc.update("INSERT INTO bookings (id, customer_id, professional_id, service_id, start_time, "
                        + "end_time, status) VALUES (?, ?, ?, ?, ?, ?, ?)",
                bookingId, ownerCustomerId, professionalId, serviceId,
                start, start.plusHours(1), status);
        return bookingId;
    }
}
