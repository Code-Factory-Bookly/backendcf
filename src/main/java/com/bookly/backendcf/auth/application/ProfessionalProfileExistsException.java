package com.bookly.backendcf.auth.application;

import com.bookly.backendcf.shared.error.ResourceConflictException;
import java.util.Map;

public class ProfessionalProfileExistsException extends ResourceConflictException {

    public ProfessionalProfileExistsException() {
        super("ROLE_CHANGE_CONFLICT",
                "El usuario tiene un perfil profesional y su rol no puede cambiarse",
                Map.of("role", "Un profesional con citas o perfil activo conserva el rol PROFESSIONAL"));
    }
}
