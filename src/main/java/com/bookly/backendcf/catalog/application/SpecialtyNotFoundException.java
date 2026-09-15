package com.bookly.backendcf.catalog.application;

import com.bookly.backendcf.shared.error.ResourceNotFoundException;
import java.util.Map;
import java.util.UUID;

public class SpecialtyNotFoundException extends ResourceNotFoundException {

    public SpecialtyNotFoundException(UUID specialtyId) {
        super("SPECIALTY_NOT_FOUND", "La especialidad indicada no existe",
                Map.of("specialtyId", String.valueOf(specialtyId)));
    }
}
