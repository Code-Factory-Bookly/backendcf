package com.bookly.backendcf.catalog.presentation.dto;

import com.bookly.backendcf.catalog.domain.model.Specialty;
import java.util.UUID;

public record SpecialtyResponse(
        UUID id,
        String name,
        String description) {

    public static SpecialtyResponse from(Specialty specialty) {
        return new SpecialtyResponse(specialty.getId(), specialty.getName(), specialty.getDescription());
    }
}
