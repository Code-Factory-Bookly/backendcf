package com.bookly.backendcf.organization.infrastructure.persistence;

import com.bookly.backendcf.organization.domain.model.OrganizationProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationProfileRepository extends JpaRepository<OrganizationProfile, UUID> {

    Optional<OrganizationProfile> findFirstByOrderByCreatedAtAsc();
}
