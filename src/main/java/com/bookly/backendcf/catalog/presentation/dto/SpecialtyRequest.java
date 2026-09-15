package com.bookly.backendcf.catalog.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SpecialtyRequest(
        @NotBlank(message = "El nombre de la especialidad es obligatorio")
        @Size(max = 120, message = "El nombre de la especialidad no puede superar 120 caracteres")
        String name,

        @Size(max = 500, message = "La descripción no puede superar 500 caracteres")
        String description) {
}
