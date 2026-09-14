package com.bookly.backendcf.organization.presentation.dto;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.organization.domain.model.Organization;
import java.util.UUID;

public record RegisterOrganizationResponse(
        UUID organizationId,
        String name,
        UUID adminUserId) {

    public static RegisterOrganizationResponse from(Organization organization, UserAccount adminUser) {
        return new RegisterOrganizationResponse(
                organization.getId(),
                organization.getName(),
                adminUser.getId());
    }
}
