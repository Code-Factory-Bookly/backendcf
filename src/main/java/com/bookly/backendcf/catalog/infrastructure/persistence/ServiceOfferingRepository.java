package com.bookly.backendcf.catalog.infrastructure.persistence;

import com.bookly.backendcf.catalog.domain.model.ServiceOffering;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceOfferingRepository extends JpaRepository<ServiceOffering, UUID> {

    boolean existsBySpecialtyIdAndNameIgnoreCase(UUID specialtyId, String name);

    boolean existsBySpecialtyIdAndNameIgnoreCaseAndIdNot(UUID specialtyId, String name, UUID id);

    boolean existsBySpecialtyId(UUID specialtyId);

    // El catálogo siempre muestra el nombre de la especialidad: se carga en la misma consulta
    // para no disparar una consulta extra por cada servicio listado.
    @EntityGraph(attributePaths = "specialty")
    List<ServiceOffering> findAllByOrderByNameAsc();

    @EntityGraph(attributePaths = "specialty")
    List<ServiceOffering> findAllBySpecialtyIdOrderByNameAsc(UUID specialtyId);
}
