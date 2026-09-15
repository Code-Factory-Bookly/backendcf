package com.bookly.backendcf.organization.application;

import com.bookly.backendcf.shared.error.ResourceNotFoundException;
import java.util.Map;

public class OrganizationProfileNotFoundException extends ResourceNotFoundException {

    public OrganizationProfileNotFoundException() {
        super("ORGANIZATION_PROFILE_NOT_FOUND", "La organización aún no ha configurado su perfil", Map.of());
    }
}
