package com.bookly.backendcf.catalog.infrastructure.persistence;

import com.bookly.backendcf.catalog.domain.model.ServiceOffering;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceOfferingRepository extends JpaRepository<ServiceOffering, UUID> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    List<ServiceOffering> findAllByOrderByNameAsc();

    List<ServiceOffering> findAllByCategoryIgnoreCaseOrderByNameAsc(String category);
}
