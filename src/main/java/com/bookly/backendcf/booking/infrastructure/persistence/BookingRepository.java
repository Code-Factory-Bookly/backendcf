
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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

    @Query("""
            SELECT b FROM Booking b
            WHERE b.customerId = :customerId
              AND (:from IS NULL OR b.startTime >= :from)
              AND (:to IS NULL OR b.startTime <= :to)
              AND (:status IS NULL OR b.status = :status)
            """)
    Page<Booking> findCustomerHistory(
            @Param("customerId") UUID customerId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("status") BookingStatus status,
            Pageable pageable
    );

    List<Booking> findByProfessionalId(UUID professionalId);

    // HU-09: bloquea la reserva durante la cancelacion.
    // Debe ejecutarse dentro de un metodo @Transactional.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.id = :bookingId")
    Optional<Booking> findByIdForUpdate(
            @Param("bookingId") UUID bookingId
    );
}
