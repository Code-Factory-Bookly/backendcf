
package com.bookly.backendcf.booking.application.service;

import com.bookly.backendcf.booking.domain.model.Booking;
import com.bookly.backendcf.booking.domain.model.BookingStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:55432/bookly_test",
        "spring.datasource.username=bookly_test",
        "spring.datasource.password=bookly_test_local_2026",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "security.jwt.secret=BooklyIntegrationTestOnlySecretWithMoreThanSixtyFourCharacters123456789",
        "booking.cancellation.min-advance-minutes=120"
})
class BookingCancellationConcurrencyTest {

    @Autowired
    private CreateBookingService createBookingService;

    @Autowired
    private CancelBookingService cancelBookingService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void twoSimultaneousCancellationsOnlyOneSucceeds() throws Exception {

        UUID customerId = UUID.randomUUID();
        UUID professionalId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();

        OffsetDateTime now = OffsetDateTime.now();
        String suffix = UUID.randomUUID().toString();

        LocalDateTime start = LocalDateTime.now()
                .plusDays(7)
                .withHour(9)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);

        LocalDateTime end = start.plusHours(1);

        try {
            jdbc.update(
                    "INSERT INTO app_user " +
                            "(id, email, password_hash, full_name, role, " +
                            "enabled, created_at, updated_at) " +
                            "VALUES (?, ?, 'test-password', 'HU09 Test', " +
                            "'CUSTOMER', true, ?, ?)",
                    customerId,
                    "hu09-cancel-" + suffix + "@example.test",
                    now,
                    now
            );

            jdbc.update(
                    "INSERT INTO app_user " +
                            "(id, email, password_hash, full_name, role, " +
                            "enabled, created_at, updated_at) " +
                            "VALUES (?, ?, 'test-password', 'HU09 Professional', " +
                            "'PROFESSIONAL', true, ?, ?)",
                    professionalId,
                    "hu09-prof-" + suffix + "@example.test",
                    now,
                    now
            );

            jdbc.update(
                    "INSERT INTO profesionales " +
                            "(id, rol, especialidad, created_at, updated_at) " +
                            "VALUES (?, 'PROFESSIONAL', 'General', ?, ?)",
                    professionalId,
                    now,
                    now
            );

            jdbc.update(
                    "INSERT INTO servicios " +
                            "(id, nombre, categoria, duracion_minutos, " +
                            "precio, estado, created_at, updated_at) " +
                            "VALUES (?, ?, 'General', 60, ?, 'ACTIVO', ?, ?)",
                    serviceId,
                    "HU09 Concurrency " + suffix,
                    new BigDecimal("100.00"),
                    now,
                    now
            );

            Booking created = createBookingService.createBooking(
                    customerId,
                    professionalId,
                    serviceId,
                    start,
                    end
            );

            assertNotNull(created.getId());

            Authentication authentication =
                    new UsernamePasswordAuthenticationToken(
                            customerId.toString(),
                            null,
                            List.of(
                                    new SimpleGrantedAuthority(
                                            "ROLE_CUSTOMER"
                                    )
                            )
                    );

            ExecutorService executor =
                    Executors.newFixedThreadPool(2);

            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);

            try {
                Future<Object> first = executor.submit(() ->
                        attemptCancellation(
                                created.getId(),
                                authentication,
                                ready,
                                go
                        )
                );

                Future<Object> second = executor.submit(() ->
                        attemptCancellation(
                                created.getId(),
                                authentication,
                                ready,
                                go
                        )
                );

                assertTrue(
                        ready.await(10, TimeUnit.SECONDS),
                        "Ambas solicitudes deben estar preparadas"
                );

                go.countDown();

                Object resultA = first.get(30, TimeUnit.SECONDS);
                Object resultB = second.get(30, TimeUnit.SECONDS);

                long successes = Stream.of(resultA, resultB)
                        .filter(Booking.class::isInstance)
                        .count();

                long conflicts = Stream.of(resultA, resultB)
                        .filter(result ->
                                result instanceof ResponseStatusException ex
                                        && ex.getStatusCode()
                                        .equals(HttpStatus.CONFLICT))
                        .count();

                assertEquals(
                        1,
                        successes,
                        "Solo una cancelacion debe tener exito: "
                                + resultA + " / " + resultB
                );

                assertEquals(
                        1,
                        conflicts,
                        "La otra cancelacion debe recibir 409: "
                                + resultA + " / " + resultB
                );

                Long cancelledCount = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM bookings " +
                                "WHERE id = ? AND status = 'CANCELADA' " +
                                "AND cancelled_at IS NOT NULL",
                        Long.class,
                        created.getId()
                );

                assertEquals(1L, cancelledCount);

                Long totalCount = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM bookings WHERE id = ?",
                        Long.class,
                        created.getId()
                );

                assertEquals(1L, totalCount);


                // HU-09 / HU-19:
                //2 intentos simultaneos deben producir
                //exactamente un registro de auditoria de cancelacion

                Long cancellationAuditCount = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM audit_log " +
                                "WHERE resource_id = ? " +
                                "AND action_type = 'BOOKING_CANCELLED'",
                        Long.class,
                        created.getId()
                );

                assertEquals(
                        1L,
                        cancellationAuditCount,
                        "Debe existir un solo registro BOOKING_CANCELLED"
                );

                //el actor debe ser el propietario de la reserva
                Long correctActorCount = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM audit_log " +
                                "WHERE resource_id = ? " +
                                "AND action_type = 'BOOKING_CANCELLED' " +
                                "AND actor_user_id = ?",
                        Long.class,
                        created.getId(),
                        customerId
                );

                assertEquals(
                        1L,
                        correctActorCount,
                        "La cancelacion debe auditarse a nombre del cliente propietario"
                );


            } finally {
                go.countDown();
                executor.shutdownNow();
            }

        } finally {
            //base de pruebas (solo para)
            jdbc.update(
                    "DELETE FROM bookings WHERE professional_id = ?",
                    professionalId
            );

            jdbc.update(
                    "DELETE FROM profesionales WHERE id = ?",
                    professionalId
            );

            jdbc.update(
                    "DELETE FROM servicios WHERE id = ?",
                    serviceId
            );

            //los usuarios se conservan porque audit_log
            //puede referenciarlos mediante fk_audit_log_actor
            //no se borra la bitacora inmutable de HU-19
        }
    }

    private Object attemptCancellation(
            UUID bookingId,
            Authentication authentication,
            CountDownLatch ready,
            CountDownLatch go
    ) {
        ready.countDown();

        try {
            if (!go.await(10, TimeUnit.SECONDS)) {
                return new IllegalStateException(
                        "Tiempo agotado esperando el inicio"
                );
            }

            return cancelBookingService.cancelBooking(
                    bookingId,
                    authentication,
                    "Cancelacion simultanea"
            );

        } catch (Exception exception) {
            return exception;
        }
    }
}
