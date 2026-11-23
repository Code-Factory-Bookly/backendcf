package com.bookly.backendcf.schedule.infrastructure.persistence;

import com.bookly.backendcf.professional.domain.model.Professional;
import com.bookly.backendcf.schedule.domain.model.AgendaBlock;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgendaBlockRepository extends JpaRepository<AgendaBlock, UUID> {

    /** Bloqueos que comparten tiempo con [startTime, endTime); los contiguos no se solapan. */
    @Query("SELECT b FROM AgendaBlock b WHERE b.professionalId = :professionalId "
            + "AND b.startTime < :endTime AND b.endTime > :startTime ORDER BY b.startTime")
    List<AgendaBlock> findOverlapping(
            @Param("professionalId") UUID professionalId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    List<AgendaBlock> findByProfessionalIdAndEndTimeAfterOrderByStartTimeAsc(UUID professionalId, LocalDateTime after);

    Optional<AgendaBlock> findByIdAndProfessionalId(UUID id, UUID professionalId);

    /** Bloquea la fila del profesional hasta el fin de la transacción (serializa sus bloqueos de agenda). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Professional p WHERE p.id = :professionalId")
    Optional<Professional> lockProfessional(@Param("professionalId") UUID professionalId);

    /** Bloqueos de cualquier profesional que se cruzan con [startTime, endTime) (reporte de ocupación). */
    @Query("SELECT b FROM AgendaBlock b WHERE b.startTime < :endTime AND b.endTime > :startTime")
    List<AgendaBlock> findAllOverlapping(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);
}
