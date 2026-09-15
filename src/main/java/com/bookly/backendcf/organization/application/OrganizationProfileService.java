package com.bookly.backendcf.organization.application;

import com.bookly.backendcf.organization.domain.model.OrganizationProfile;
import com.bookly.backendcf.organization.infrastructure.persistence.OrganizationProfileRepository;
import com.bookly.backendcf.organization.presentation.dto.OrganizationProfileRequest;
import com.bookly.backendcf.organization.presentation.dto.OrganizationProfileResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationProfileService {

    private final OrganizationProfileRepository organizationProfileRepository;

    public OrganizationProfileService(OrganizationProfileRepository organizationProfileRepository) {
        this.organizationProfileRepository = organizationProfileRepository;
    }

    @Transactional(readOnly = true)
    public OrganizationProfileResponse get() {
        return organizationProfileRepository.findFirstByOrderByCreatedAtAsc()
                .map(OrganizationProfileResponse::from)
                .orElseThrow(OrganizationProfileNotFoundException::new);
    }

    /**
     * Crea el perfil la primera vez y lo actualiza en adelante: como solo hay una organización,
     * nunca existe más de un perfil.
     */
    @Transactional
    public OrganizationProfileResponse save(OrganizationProfileRequest request) {
        String commercialName = normalize(request.commercialName());
        String contactEmail = request.contactEmail().trim().toLowerCase();
        String contactPhone = request.contactPhone().trim();
        String address = normalize(request.address());

        OrganizationProfile profile = organizationProfileRepository.findFirstByOrderByCreatedAtAsc()
                .map(existing -> {
                    existing.update(commercialName, contactEmail, contactPhone, address);
                    return existing;
                })
                .orElseGet(() -> new OrganizationProfile(commercialName, contactEmail, contactPhone, address));

        return OrganizationProfileResponse.from(organizationProfileRepository.save(profile));
    }

    private String normalize(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }
}
