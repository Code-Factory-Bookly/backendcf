package com.bookly.backendcf.auth.application;

import com.bookly.backendcf.shared.error.ResourceConflictException;
import java.util.Map;

public class SpecialtyRequiredException extends ResourceConflictException {

    public SpecialtyRequiredException() {
        super("SPECIALTY_REQUIRED",
                "Para asignar el rol PROFESSIONAL se requiere la especialidad",
                Map.of("specialty", "La especialidad es obligatoria"));
    }
}
