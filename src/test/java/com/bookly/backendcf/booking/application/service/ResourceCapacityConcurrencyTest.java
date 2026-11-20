
package com.bookly.backendcf.booking.application.service;

import com.bookly.backendcf.booking.application.exception.BookingConflictException;
import com.bookly.backendcf.booking.domain.model.Booking;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:55432/bookly_test",
        "spring.datasource.username=bookly_test",
        "spring.datasource.password=bookly_test_local_2026",
        "spring.flyway.enabled=true",
        "spring.flyway.out-of-order=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "security.jwt.secret=BooklyIntegrationTestOnlySecretWithMoreThanSixtyFourCharacters123456789"
})
class ResourceCapacityConcurrencyTest {

    @Autowired
    private CreateBookingService bookingService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void twoProfessionalsCannotOverbookOnePhysicalResource()
            throws Exception {

        Scenario scenario = new Scenario(1);

        try {
            List<Object> results = reserveSimultaneously(scenario, 2);

            assertResults(results, 1, 1);

            assertEquals(
                    1L,
                    confirmedBookings(scenario.serviceId)
            );

        } finally {
            scenario.cleanup();
        }
    }

    @Test
    void threeProfessionalsCannotOverbookTwoPhysicalResources()
            throws Exception {

        Scenario scenario = new Scenario(2);

        try {
            List<Object> results = reserveSimultaneously(scenario, 3);

            assertResults(results, 2, 1);

            assertEquals(
                    2L,
                    confirmedBookings(scenario.serviceId)
            );

        } finally {
            scenario.cleanup();
        }
    }

    @Test
    void cancelledBookingReleasesResourceCapacity() {

        Scenario scenario = new Scenario(1);

        try {
            Booking first = bookingService.createBooking(
                    scenario.customers.get(0),
                    scenario.professionals.get(0),
                    scenario.serviceId,
                    scenario.start,
                    scenario.end
            );

            assertEquals(
                    1L,
                    confirmedBookings(scenario.serviceId)
            );

            // Simula el estado persistido que deja
            // CancelBookingService.
            // Esta prueba valida capacidad, no el flujo
            // completo de cancelacion.

            assertEquals(
                    1,
                    jdbc.update(
                            "UPDATE bookings SET status='CANCELADA', updated_at=? WHERE id=?",
                            LocalDateTime.now(),
                            first.getId()
                    )
            );

            Booking second = bookingService.createBooking(
                    scenario.customers.get(1),
                    scenario.professionals.get(1),
                    scenario.serviceId,
                    scenario.start,
                    scenario.end
            );

            assertNotNull(second.getId());

            assertEquals(
                    1L,
                    confirmedBookings(scenario.serviceId)
            );

            assertEquals(
                    1L,
                    jdbc.queryForObject(
                            """
                            SELECT COUNT(*)
                            FROM bookings
                            WHERE service_id=?
                              AND status='CANCELADA'
                            """,
                            Long.class,
                            scenario.serviceId
                    )
            );

        } finally {
            scenario.cleanup();
        }
    }

    @Test
    void consecutiveBookingsCanReuseTheSamePhysicalResource() {

        Scenario scenario = new Scenario(1);

        try {
            Booking first = bookingService.createBooking(
                    scenario.customers.get(0),
                    scenario.professionals.get(0),
                    scenario.serviceId,
                    scenario.start,
                    scenario.end
            );

            Booking second = bookingService.createBooking(
                    scenario.customers.get(1),
                    scenario.professionals.get(1),
                    scenario.serviceId,
                    scenario.end,
                    scenario.end.plusHours(1)
            );

            assertNotNull(first.getId());
            assertNotNull(second.getId());

            assertEquals(
                    2L,
                    confirmedBookings(scenario.serviceId)
            );

        } finally {
            scenario.cleanup();
        }
    }

    private List<Object> reserveSimultaneously(
            Scenario scenario,
            int requests
    ) throws Exception {

        ExecutorService executor =
                Executors.newFixedThreadPool(requests);

        CountDownLatch ready = new CountDownLatch(requests);
        CountDownLatch go = new CountDownLatch(1);

        try {
            List<Future<Object>> futures = new ArrayList<>();

            for (int i = 0; i < requests; i++) {

                final int index = i;

                futures.add(executor.submit(() -> {

                    ready.countDown();

                    try {
                        if (!go.await(10, TimeUnit.SECONDS)) {
                            return new IllegalStateException(
                                    "Start barrier timeout"
                            );
                        }

                        return bookingService.createBooking(
                                scenario.customers.get(index),
                                scenario.professionals.get(index),
                                scenario.serviceId,
                                scenario.start,
                                scenario.end
                        );

                    } catch (Exception exception) {
                        return exception;
                    }
                }));
            }

            assertTrue(
                    ready.await(10, TimeUnit.SECONDS),
                    "Las solicitudes no llegaron a la barrera de inicio"
            );

            go.countDown();

            List<Object> results = new ArrayList<>();

            for (Future<Object> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }

            return results;

        } finally {
            go.countDown();
            executor.shutdownNow();

            assertTrue(
                    executor.awaitTermination(10, TimeUnit.SECONDS),
                    "Los hilos deben terminar antes de limpiar la base de datos"
            );
        }
    }

    private void assertResults(
            List<Object> results,
            long expectedSuccesses,
            long expectedConflicts
    ) {

        long successes = results.stream()
                .filter(Booking.class::isInstance)
                .count();

        long conflicts = results.stream()
                .filter(result ->
                        result instanceof BookingConflictException exception
                                && "RESOURCE_UNAVAILABLE"
                                .equals(exception.getErrorCode())
                )
                .count();

        assertEquals(
                expectedSuccesses,
                successes,
                "Reservas confirmadas: " + results
        );

        assertEquals(
                expectedConflicts,
                conflicts,
                "Conflictos de recursos: " + results
        );

        assertEquals(
                results.size(),
                successes + conflicts,
                "No deben existir errores inesperados: " + results
        );
    }

    private long confirmedBookings(UUID serviceId) {

        return jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM bookings
                WHERE service_id=?
                  AND status='CONFIRMADA'
                """,
                Long.class,
                serviceId
        );
    }

    private final class Scenario {

        private final UUID serviceId = UUID.randomUUID();
        private final UUID resourceId = UUID.randomUUID();

        private final List<UUID> customers = new ArrayList<>();
        private final List<UUID> professionals = new ArrayList<>();

        private final LocalDateTime start =
                LocalDateTime.now()
                        .plusDays(7)
                        .withHour(10)
                        .withMinute(0)
                        .withSecond(0)
                        .withNano(0);

        private final LocalDateTime end = start.plusHours(1);

        private Scenario(int totalQuantity) {

            OffsetDateTime now = OffsetDateTime.now();
            String suffix = UUID.randomUUID().toString();

            for (int i = 0; i < 3; i++) {

                UUID customer = UUID.randomUUID();
                UUID professional = UUID.randomUUID();

                customers.add(customer);
                professionals.add(professional);

                insertUser(
                        customer,
                        "hu12-customer-" + i + "-" + suffix,
                        "CUSTOMER",
                        now
                );

                insertUser(
                        professional,
                        "hu12-prof-" + i + "-" + suffix,
                        "PROFESSIONAL",
                        now
                );

                jdbc.update(
                        """
                        INSERT INTO profesionales
                        (id, rol, especialidad, created_at, updated_at)
                        VALUES (?, 'PROFESSIONAL', 'General', ?, ?)
                        """,
                        professional,
                        now,
                        now
                );

                jdbc.update(
                        """
                        INSERT INTO horario_semanal
                        (id, profesional_id, dia_semana,
                         hora_inicio, hora_fin, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                        UUID.randomUUID(),
                        professional,
                        start.getDayOfWeek().name(),
                        LocalTime.of(8, 0),
                        LocalTime.of(18, 0),
                        now,
                        now
                );
            }

            jdbc.update(
                    """
                    INSERT INTO servicios
                    (id, nombre, categoria, duracion_minutos,
                     precio, estado, created_at, updated_at)
                    VALUES (?, ?, 'General', 60, ?, 'ACTIVO', ?, ?)
                    """,
                    serviceId,
                    "HU12 Service " + suffix,
                    new BigDecimal("100.00"),
                    now,
                    now
            );

            jdbc.update(
                    """
                    INSERT INTO recursos_fisicos
                    (id, nombre, cantidad_total,
                     created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?)
                    """,
                    resourceId,
                    "HU12 Resource " + suffix,
                    totalQuantity,
                    now,
                    now
            );

            jdbc.update(
                    """
                    INSERT INTO servicio_recurso
                    (servicio_id, recurso_id)
                    VALUES (?, ?)
                    """,
                    serviceId,
                    resourceId
            );
        }

        private void cleanup() {

            jdbc.update(
                    "DELETE FROM bookings WHERE service_id=?",
                    serviceId
            );

            jdbc.update(
                    "DELETE FROM servicio_recurso WHERE servicio_id=?",
                    serviceId
            );

            jdbc.update(
                    "DELETE FROM recursos_fisicos WHERE id=?",
                    resourceId
            );

            for (UUID professional : professionals) {

                jdbc.update(
                        "DELETE FROM horario_semanal WHERE profesional_id=?",
                        professional
                );

                jdbc.update(
                        "DELETE FROM profesionales WHERE id=?",
                        professional
                );
            }

            jdbc.update(
                    "DELETE FROM servicios WHERE id=?",
                    serviceId
            );

            // audit_log conserva referencias a app_user.
            // No eliminar usuarios de prueba.
        }
    }

    private void insertUser(
            UUID id,
            String email,
            String role,
            OffsetDateTime now
    ) {

        jdbc.update(
                """
                INSERT INTO app_user
                (id, email, password_hash, full_name,
                 role, enabled, created_at, updated_at)
                VALUES (?, ?, 'test-not-a-real-password',
                        'HU12 Concurrency Test', ?, true, ?, ?)
                """,
                id,
                email + "@example.test",
                role,
                now,
                now
        );
    }
}
