package com.bookly.backendcf.booking.infrastructure.persistence;

import com.bookly.backendcf.booking.domain.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {

    @Query("SELECT b FROM Booking b WHERE b.professionalId = :professionalId " +
            "AND b.status = 'CONFIRMADA' " +
            "AND ((b.startTime < :endTime) AND (b.endTime > :startTime))")
    List<Booking> findOverlappingBookings(@Param("professionalId") UUID professionalId,
                                          @Param("startTime") LocalDateTime startTime,
                                          @Param("endTime") LocalDateTime endTime);

    List<Booking> findByCustomerId(UUID customerId);
    List<Booking> findByProfessionalId(UUID professionalId);
}