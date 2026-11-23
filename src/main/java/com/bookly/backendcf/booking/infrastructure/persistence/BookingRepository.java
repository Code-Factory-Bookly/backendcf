
package com.bookly.backendcf.booking.infrastructure.persistence;

import com.bookly.backendcf.booking.domain.model.Booking;
import com.bookly.backendcf.booking.domain.model.BookingStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.bookly.backendcf.booking.domain.model.BookingStatus;
import java.util.Collection;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {

    @Query("SELECT b FROM Booking b WHERE b.professionalId = :professionalId " +
            "AND b.status = 'CONFIRMADA' " +
            "AND ((b.startTime < :endTime) AND (b.endTime > :startTime))")
    List<Booking> findOverlappingBookings(
            @Param("professionalId") UUID professionalId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    List<Booking> findByCustomerId(UUID customerId);

    // HU-13: los filtros opcionales se comparan contra un parametro que puede ser null.
    // PostgreSQL no puede inferir el tipo de un parametro sin tipo en la expresion
    // ":parametro IS NULL" y responde "could not determine data type of parameter $n",
    // por lo que el cast explicito es obligatorio para que la consulta funcione
    // contra la base real (no solo contra H2 en las pruebas unitarias).
    // Sin el CAST, este endpoint devuelve 500 en produccion. Ver HU-13.
    // El enum se castea a String porque JPQL no admite el tipo Java del enum.
    @Query("""
            SELECT b FROM Booking b
            WHERE b.customerId = :customerId
              AND (CAST(:from AS LocalDateTime) IS NULL OR b.startTime >= :from)
              AND (CAST(:to AS LocalDateTime) IS NULL OR b.startTime <= :to)
              AND (CAST(:status AS String) IS NULL OR b.status = :status)
            """)
    Page<Booking> findCustomerHistory(
            @Param("customerId") UUID customerId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("status") BookingStatus status,
            Pageable pageable
    );

    // Ruta antigua GET /reservas/profesional/{id}, obsoleta desde HU-14 (ver BookingController).
    List<Booking> findByProfessionalId(UUID professionalId);

    // HU-14: agenda del profesional. Excluye las reservas canceladas (la cancelacion libera
    // la franja) y ordena cronologicamente; el id desempata para que el orden sea estable.
    // "from" es inclusivo y "toExclusive" exclusivo, para abarcar dias completos.
    // Usa el indice idx_bookings_professional_start (professional_id, start_time).
    @Query("""
            SELECT b FROM Booking b
            WHERE b.professionalId = :professionalId
              AND b.startTime >= :from
              AND b.startTime < :toExclusive
              AND b.status <> 'CANCELADA'
            ORDER BY b.startTime ASC, b.id ASC
            """)
    List<Booking> findAgenda(
            @Param("professionalId") UUID professionalId,
            @Param("from") LocalDateTime from,
            @Param("toExclusive") LocalDateTime toExclusive
    );

    // HU-09: bloquea la reserva durante la cancelacion.
    // Debe ejecutarse dentro de un metodo @Transactional.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.id = :bookingId")
    Optional<Booking> findByIdForUpdate(
            @Param("bookingId") UUID bookingId
    );
    // HU-12: cuenta las reservas confirmadas que utilizan un
    // recurso fisico durante un intervalo determinado.
    // Incluye reservas de todos los profesionales.
    // Dos intervalos que solo se tocan no se superponen.
    @Query("""
            SELECT COUNT(b)
            FROM Booking b
            WHERE b.status = 'CONFIRMADA'
              AND b.startTime < :endTime
              AND b.endTime > :startTime
              AND b.serviceId IN (
                  SELECT sr.serviceId
                  FROM ServiceResource sr
                  WHERE sr.resourceId = :resourceId
              )
            """)
    long countConfirmedOverlappingByResource(
            @Param("resourceId") UUID resourceId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

        // HU-15: reservas que ocupan agenda dentro de [from, to), usadas por el reporte de ocupacion.
    @Query("SELECT b FROM Booking b WHERE b.status IN :statuses " +
            "AND b.startTime >= :from AND b.startTime < :to")
    List<Booking> findByStatusesInRange(
            @Param("statuses") Collection<BookingStatus> statuses,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("SELECT COUNT(b) FROM Booking b " +
            "WHERE b.startTime >= :from AND b.startTime < :to")
    long countReservationsInRange(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("SELECT COUNT(b) FROM Booking b " +
            "WHERE b.status = com.bookly.backendcf.booking.domain.model.BookingStatus.CANCELADA " +
            "AND b.startTime >= :from AND b.startTime < :to")
    long countCancelledReservationsInRange(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("SELECT COALESCE(SUM(COALESCE(b.priceAtBooking, s.price)), 0) " +
            "FROM Booking b JOIN ServiceOffering s ON s.id = b.serviceId " +
            "WHERE b.status IN " +
            "(com.bookly.backendcf.booking.domain.model.BookingStatus.CONFIRMADA, " +
            "com.bookly.backendcf.booking.domain.model.BookingStatus.COMPLETED) " +
            "AND b.startTime >= :from AND b.startTime < :to")
    BigDecimal sumRecognizedRevenueInRange(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}
