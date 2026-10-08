package com.bookly.backendcf.booking.application.service;

import com.bookly.backendcf.booking.application.exception.BookingConflictException;
import com.bookly.backendcf.booking.domain.model.Booking;
import com.bookly.backendcf.booking.infrastructure.persistence.BookingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:55432/bookly_test",
        "spring.datasource.username=bookly_test",
        "spring.datasource.password=bookly_test_local_2026",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "security.jwt.secret=BooklyIntegrationTestOnlySecretWithMoreThanSixtyFourCharacters123456789"
})
class BookingConcurrencyTest {

    @Autowired private CreateBookingService bookingService;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void twoSimultaneousCustomersOnlyOneBookingIsConfirmed() throws Exception {
        UUID customerX = UUID.randomUUID();
        UUID customerY = UUID.randomUUID();
        UUID professional = UUID.randomUUID();
        UUID service = UUID.randomUUID();
        LocalDateTime start = LocalDateTime.now().plusDays(7).withHour(9).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusHours(1);
        OffsetDateTime now = OffsetDateTime.now();
        String suffix = UUID.randomUUID().toString();

        try {
            insertUser(customerX, "customer-x-" + suffix + "@example.test", "CUSTOMER", now);
            insertUser(customerY, "customer-y-" + suffix + "@example.test", "CUSTOMER", now);
            insertUser(professional, "professional-" + suffix + "@example.test", "PROFESSIONAL", now);
            jdbc.update("INSERT INTO profesionales (id, rol, especialidad, created_at, updated_at) VALUES (?, 'PROFESSIONAL', 'General', ?, ?)", professional, now, now);
            jdbc.update("INSERT INTO servicios (id, nombre, categoria, duracion_minutos, precio, estado, created_at, updated_at) VALUES (?, ?, 'General', 60, ?, 'ACTIVO', ?, ?)", service, "Test concurrency " + suffix, new BigDecimal("100.00"), now, now);

            ExecutorService executor = Executors.newFixedThreadPool(2);
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);
            try {
                Future<Object> first = executor.submit(() -> attempt(customerX, professional, service, start, end, ready, go));
                Future<Object> second = executor.submit(() -> attempt(customerY, professional, service, start, end, ready, go));
                assertTrue(ready.await(10, TimeUnit.SECONDS), "Both requests must be ready");
                go.countDown();
                Object resultX = first.get(30, TimeUnit.SECONDS);
                Object resultY = second.get(30, TimeUnit.SECONDS);

                long successes = java.util.stream.Stream.of(resultX, resultY).filter(Booking.class::isInstance).count();
                long conflicts = java.util.stream.Stream.of(resultX, resultY).filter(BookingConflictException.class::isInstance).count();
                assertEquals(1, successes, "Exactly one request must succeed: " + resultX + " / " + resultY);
                assertEquals(1, conflicts, "Exactly one request must fail with BookingConflictException: " + resultX + " / " + resultY);
                assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM bookings WHERE professional_id=? AND start_time=? AND end_time=? AND status='CONFIRMADA'", Long.class, professional, start, end));
            } finally {
                go.countDown();
                executor.shutdownNow();
            }
        } finally {
            jdbc.update("DELETE FROM bookings WHERE professional_id=?", professional);
            jdbc.update("DELETE FROM profesionales WHERE id=?", professional);
            jdbc.update("DELETE FROM servicios WHERE id=?", service);
            jdbc.update("DELETE FROM app_user WHERE id IN (?, ?, ?)", customerX, customerY, professional);
        }
    }

    private Object attempt(UUID customer, UUID professional, UUID service, LocalDateTime start,
                           LocalDateTime end, CountDownLatch ready, CountDownLatch go) {
        ready.countDown();
        try {
            if (!go.await(10, TimeUnit.SECONDS)) return new IllegalStateException("Start barrier timeout");
            return bookingService.createBooking(customer, professional, service, start, end);
        } catch (Exception e) {
            return e;
        }
    }

    private void insertUser(UUID id, String email, String role, OffsetDateTime now) {
        jdbc.update("INSERT INTO app_user (id, email, password_hash, full_name, role, enabled, created_at, updated_at) VALUES (?, ?, 'test-not-a-real-password', 'Concurrency Test', ?, true, ?, ?)", id, email, role, now, now);
    }
}
