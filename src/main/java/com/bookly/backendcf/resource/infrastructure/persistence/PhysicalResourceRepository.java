
package com.bookly.backendcf.resource.infrastructure.persistence;

import com.bookly.backendcf.resource.domain.model.PhysicalResource;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PhysicalResourceRepository
        extends JpaRepository<PhysicalResource, UUID> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    List<PhysicalResource> findAllByOrderByNameAsc();
}
