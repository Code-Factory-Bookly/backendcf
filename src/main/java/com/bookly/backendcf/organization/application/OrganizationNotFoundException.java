package com.bookly.backendcf.organization.application;

import java.util.UUID;

public class OrganizationNotFoundException extends RuntimeException {

    private final UUID organizationId;

    public OrganizationNotFoundException(UUID organizationId) {
        super("La organización indicada no existe");
        this.organizationId = organizationId;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }
}
