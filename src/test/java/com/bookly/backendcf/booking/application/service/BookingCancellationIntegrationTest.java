
package com.bookly.backendcf.booking.application.service;

import com.bookly.backendcf.booking.domain.model.Booking;
import com.bookly.backendcf.booking.domain.model.BookingStatus;
import com.bookly.backendcf.booking.infrastructure.persistence.BookingRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

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
class BookingCancellationIntegrationTest {

    @Autowired
    private CreateBookingService createBookingService;

    @Autowired
    private CancelBookingService cancelBookingService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void cancelledSlotCanBeBookedByAnotherCustomer() {

        UUID customerA = UUID.randomUUID();
        UUID customerB = UUID.randomUUID();
        UUID professional = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();

        String suffix = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now();

        LocalDateTime start = LocalDateTime.now()
                .plusDays(7)
                .withHour(9)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);

        LocalDateTime end = start.plusHours(1);

        try {
            insertUser(
                    customerA,
                    "hu09-a-" + suffix + "@example.test",
                    "CUSTOMER",
                    now
            );

            insertUser(
                    customerB,
                    "hu09-b-" + suffix + "@example.test",
                    "CUSTOMER",
                    now
            );

            insertUser(
                    professional,
                    "hu09-prof-" + suffix + "@example.test",
                    "PROFESSIONAL",
                    now
            );

            jdbc.update(
                    "INSERT INTO profesionales " +
                            "(id, rol, especialidad, created_at, updated_at) " +
                            "VALUES (?, 'PROFESSIONAL', 'General', ?, ?)",
                    professional, now, now
            );

            jdbc.update(
                    "INSERT INTO servicios " +
                            "(id, nombre, categoria, duracion_minutos, precio, " +
                            "estado, created_at, updated_at) " +
                            "VALUES (?, ?, 'General', 60, ?, 'ACTIVO', ?, ?)",
                    serviceId,
                    "HU09 test " + suffix,
                    new BigDecimal("100.00"),
                    now,
                    now
            );

            // 1. Cliente A crea una reserva.
            Booking first = createBookingService.createBooking(
                    customerA,
                    professional,
                    serviceId,
                    start,
                    end
            );

            assertNotNull(first.getId());
            assertEquals(
                    BookingStatus.CONFIRMADA,
                    first.getStatus()
            );

            // 2. Cliente A cancela su propia reserva.
            Authentication customerAuthentication =
                    new UsernamePasswordAuthenticationToken(
                            customerA.toString(),
                            null,
                            List.of(
                                    new SimpleGrantedAuthority(
                                            "ROLE_CUSTOMER"
                                    )
                            )
                    );

            Booking cancelled = cancelBookingService.cancelBooking(
                    first.getId(),
                    customerAuthentication,
                    "Cambio de planes"
            );

            assertEquals(
                    BookingStatus.CANCELADA,
                    cancelled.getStatus()
            );

            assertNotNull(cancelled.getCancelledAt());

            // 3. Cliente B reserva exactamente el mismo horario.
            Booking second = createBookingService.createBooking(
                    customerB,
                    professional,
                    serviceId,
                    start,
                    end
            );

            assertNotNull(second.getId());
            assertNotEquals(first.getId(), second.getId());

            // 4. Verificar los estados persistidos.
            Booking firstFromDb = bookingRepository
                    .findById(first.getId())
                    .orElseThrow();

            Booking secondFromDb = bookingRepository
                    .findById(second.getId())
                    .orElseThrow();

            assertEquals(
                    BookingStatus.CANCELADA,
                    firstFromDb.getStatus()
            );

            assertEquals(
                    BookingStatus.CONFIRMADA,
                    secondFromDb.getStatus()
            );

            // 5. Solo una reserva CONFIRMADA ocupa la franja.
            Long activeBookings = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM bookings " +
                            "WHERE professional_id = ? " +
                            "AND start_time = ? " +
                            "AND end_time = ? " +
                            "AND status = 'CONFIRMADA'",
                    Long.class,
                    professional,
                    start,
                    end
            );

            assertEquals(1L, activeBookings);

            // 6. La cancelada permanece en el historial.
            Long totalBookings = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM bookings " +
                            "WHERE professional_id = ? " +
                            "AND start_time = ? " +
                            "AND end_time = ?",
                    Long.class,
                    professional,
                    start,
                    end
            );

            assertEquals(2L, totalBookings);

        } finally {

            // Limpiar exclusivamente los datos de esta prueba.
            jdbc.update(
                    "DELETE FROM bookings WHERE professional_id = ?",
                    professional
            );

            jdbc.update(
                    "DELETE FROM profesionales WHERE id = ?",
                    professional
            );

            jdbc.update(
                    "DELETE FROM servicios WHERE id = ?",
                    serviceId
            );

        }
    }

    private void insertUser(
            UUID id,
            String email,
            String role,
            OffsetDateTime now
    ) {

        jdbc.update(
                "INSERT INTO app_user " +
                        "(id, email, password_hash, full_name, role, " +
                        "enabled, created_at, updated_at) " +
                        "VALUES (?, ?, 'test-not-a-real-password', " +
                        "'HU09 Integration Test', ?, true, ?, ?)",
                id,
                email,
                role,
                now,
                now
        );
    }
}
