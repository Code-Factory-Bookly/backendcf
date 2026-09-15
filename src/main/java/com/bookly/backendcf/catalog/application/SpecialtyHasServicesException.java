package com.bookly.backendcf.catalog.application;

import com.bookly.backendcf.shared.error.ResourceConflictException;
import java.util.Map;
import java.util.UUID;

public class SpecialtyHasServicesException extends ResourceConflictException {

    public SpecialtyHasServicesException(UUID specialtyId) {
        super("SPECIALTY_HAS_SERVICES",
                "No se puede eliminar una especialidad que tiene servicios asociados",
                Map.of("specialtyId", String.valueOf(specialtyId)));
    }
}
