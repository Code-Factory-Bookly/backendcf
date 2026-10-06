package com.bookly.backendcf.schedule.application;

import com.bookly.backendcf.shared.error.ResourceNotFoundException;
import java.util.Map;
import java.util.UUID;

public class ProfessionalNotFoundException extends ResourceNotFoundException {

    public ProfessionalNotFoundException(UUID professionalId) {
        super("PROFESSIONAL_NOT_FOUND", "El profesional no existe",
                Map.of("professionalId", professionalId.toString()));
    }
}
