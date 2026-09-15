package com.bookly.backendcf.catalog.infrastructure.persistence;

import com.bookly.backendcf.catalog.domain.model.Specialty;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpecialtyRepository extends JpaRepository<Specialty, UUID> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    List<Specialty> findAllByOrderByNameAsc();
}
