package com.bookly.backendcf.professional.infrastructure.persistence;

import com.bookly.backendcf.professional.domain.model.Professional;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProfessionalRepository extends JpaRepository<Professional, UUID> {

    /** Bloquea la fila del profesional hasta el fin de la transacción (serializa cambios de su agenda). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Professional p WHERE p.id = :id")
    Optional<Professional> lockById(@Param("id") UUID id);
}