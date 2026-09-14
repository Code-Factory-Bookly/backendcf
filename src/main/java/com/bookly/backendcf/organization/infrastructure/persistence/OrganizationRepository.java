package com.bookly.backendcf.organization.infrastructure.persistence;

import com.bookly.backendcf.organization.domain.model.Organization;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByTaxId(String taxId);
}
