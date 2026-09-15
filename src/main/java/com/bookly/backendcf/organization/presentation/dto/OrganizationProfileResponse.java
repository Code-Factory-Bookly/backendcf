package com.bookly.backendcf.organization.presentation.dto;

import com.bookly.backendcf.organization.domain.model.OrganizationProfile;
import java.time.OffsetDateTime;
import java.util.UUID;

public record OrganizationProfileResponse(
        UUID id,
        String commercialName,
        String contactEmail,
        String contactPhone,
        String address,
        OffsetDateTime updatedAt) {

    public static OrganizationProfileResponse from(OrganizationProfile profile) {
        return new OrganizationProfileResponse(
                profile.getId(),
                profile.getCommercialName(),
                profile.getContactEmail(),
                profile.getContactPhone(),
                profile.getAddress(),
                profile.getUpdatedAt());
    }
}
