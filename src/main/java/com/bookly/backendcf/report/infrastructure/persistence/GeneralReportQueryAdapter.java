package com.bookly.backendcf.report.infrastructure.persistence;

import com.bookly.backendcf.booking.infrastructure.persistence.BookingRepository;
import com.bookly.backendcf.report.application.GeneralReportQuery;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Adaptador de las agregaciones PostgreSQL de HU-16. */
@Component
public class GeneralReportQueryAdapter implements GeneralReportQuery {

    private final BookingRepository bookingRepository;
    private final EntityManager entityManager;

    public GeneralReportQueryAdapter(BookingRepository bookingRepository, EntityManager entityManager) {
        this.bookingRepository = bookingRepository;
        this.entityManager = entityManager;
    }

    @Override
    public long countReservations(LocalDateTime from, LocalDateTime to) {
        return bookingRepository.countReservationsInRange(from, to);
    }

    @Override
    public long countCancelledReservations(LocalDateTime from, LocalDateTime to) {
        return bookingRepository.countCancelledReservationsInRange(from, to);
    }

    @Override
    public BigDecimal sumRecognizedRevenue(LocalDateTime from, LocalDateTime to) {
        return bookingRepository.sumRecognizedRevenueInRange(from, to);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ResourceUsage> findResourceUsage(LocalDateTime from, LocalDateTime to) {
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT r.id, r.nombre, r.cantidad_total, COUNT(DISTINCT b.id)
                FROM recursos_fisicos r
                LEFT JOIN servicio_recurso sr ON sr.recurso_id = r.id
                LEFT JOIN bookings b
                       ON b.service_id = sr.servicio_id
                      AND b.status IN ('CONFIRMADA', 'COMPLETED')
                      AND b.start_time >= :from
                      AND b.start_time < :to
                GROUP BY r.id, r.nombre, r.cantidad_total
                ORDER BY r.nombre
                """)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();

        return rows.stream()
                .map(row -> new ResourceUsage(
                        toUuid(row[0]),
                        (String) row[1],
                        ((Number) row[2]).intValue(),
                        ((Number) row[3]).longValue()))
                .toList();
    }

    private UUID toUuid(Object value) {
        return value instanceof UUID uuid ? uuid : UUID.fromString(value.toString());
    }
}
