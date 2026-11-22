
package com.bookly.backendcf.resource.infrastructure.persistence;

import com.bookly.backendcf.resource.domain.model.PhysicalResource;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PhysicalResourceRepository
        extends JpaRepository<PhysicalResource, UUID> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    List<PhysicalResource> findAllByOrderByNameAsc();

    // HU-12: bloquea el recurso mientras se valida su inventario.
    // Debe ejecutarse dentro de una transaccion.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM PhysicalResource r WHERE r.id = :resourceId")
    Optional<PhysicalResource> findByIdForUpdate(
            @Param("resourceId") UUID resourceId
    );
}
