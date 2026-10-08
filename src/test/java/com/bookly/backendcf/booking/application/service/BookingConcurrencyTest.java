package com.bookly.backendcf.booking.application.service;

import com.bookly.backendcf.booking.application.exception.BookingConflictException;
import com.bookly.backendcf.booking.domain.model.Booking;
import com.bookly.backendcf.booking.infrastructure.persistence.BookingRepository;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test de concurrencia para validar que el UNIQUE constraint previene dobles reservas
 * REQUIERE: Postgres real, no H2
 *
 * Escenario: Dos clientes intentan reservar la misma franja simultaneamente
 * Esperado: Solo UNO recibe confirmación, el otro recibe CONFLICT (409)
 */
@SpringBootTest
class BookingConcurrencyTest {

    @Autowired
    private CreateBookingService createBookingService;

    @Autowired
    private BookingRepository bookingRepository;

    @Test
    @Disabled("Requiere Postgres real, no H2. Validar manualmente contra BD de producción")
    void testRaceConditionTwoClientsReserveSameFranja() throws InterruptedException {
        // Limpiar datos previos
        bookingRepository.deleteAll();

        UUID professionalId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        LocalDateTime startTime = LocalDateTime.now().plusHours(2);
        LocalDateTime endTime = startTime.plusMinutes(45);

        UUID customerId1 = UUID.randomUUID();
        UUID customerId2 = UUID.randomUUID();

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(2);

        // Cliente 1 intenta reservar
        executor.submit(() -> {
            try {
                Booking booking = createBookingService.createBooking(
                        customerId1, professionalId, serviceId, startTime, endTime);
                assertNotNull(booking);
                successCount.incrementAndGet();
            } catch (BookingConflictException e) {
                assertEquals("SLOT_OCUPADO", e.getErrorCode());
                conflictCount.incrementAndGet();
            } catch (Exception e) {
                System.err.println("Error Cliente 1: " + e.getMessage());
                errorCount.incrementAndGet();
            } finally {
                latch.countDown();
            }
        });

        // Cliente 2 intenta reservar SIMULTANEAMENTE (sin delay)
        executor.submit(() -> {
            try {
                Booking booking = createBookingService.createBooking(
                        customerId2, professionalId, serviceId, startTime, endTime);
                assertNotNull(booking);
                successCount.incrementAndGet();
            } catch (BookingConflictException e) {
                assertEquals("SLOT_OCUPADO", e.getErrorCode());
                conflictCount.incrementAndGet();
            } catch (Exception e) {
                System.err.println("Error Cliente 2: " + e.getMessage());
                errorCount.incrementAndGet();
            } finally {
                latch.countDown();
            }
        });

        latch.await();
        executor.shutdown();

        System.out.println("\n=== RESULTADOS DE CONCURRENCIA ===");
        System.out.println("Exitosas: " + successCount.get());
        System.out.println("Conflictos: " + conflictCount.get());
        System.out.println("Errores: " + errorCount.get());

        // Validación principal
        assertEquals(1, successCount.get(),
                "Solo UN cliente debe confirmar exitosamente la reserva");
        assertEquals(1, conflictCount.get(),
                "El OTRO cliente debe recibir BookingConflictException");
        assertEquals(0, errorCount.get(),
                "No debe haber errores inesperados");

        // Validación de datos: solo UNA reserva en BD
        long bookingCount = bookingRepository.count();
        assertEquals(1, bookingCount,
                "Debe haber exactamente 1 reserva total en la BD");

        Booking saved = bookingRepository.findAll().get(0);
        assertEquals(professionalId, saved.getProfessionalId());
        assertEquals(startTime, saved.getStartTime());
        assertEquals(endTime, saved.getEndTime());
    }
}