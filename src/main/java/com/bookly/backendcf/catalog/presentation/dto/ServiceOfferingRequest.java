package com.bookly.backendcf.catalog.presentation.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record ServiceOfferingRequest(
        @NotNull(message = "La especialidad es obligatoria")
        UUID specialtyId,

        @NotBlank(message = "El nombre del servicio es obligatorio")
        @Size(max = 150, message = "El nombre del servicio no puede superar 150 caracteres")
        String name,

        @Size(max = 500, message = "La descripción no puede superar 500 caracteres")
        String description,

        @NotNull(message = "La duración es obligatoria")
        @Min(value = 5, message = "La duración mínima es de 5 minutos")
        @Max(value = 480, message = "La duración máxima es de 480 minutos")
        Integer durationMinutes,

        @NotNull(message = "El precio es obligatorio")
        @DecimalMin(value = "0.00", message = "El precio no puede ser negativo")
        @Digits(integer = 10, fraction = 2, message = "El precio admite hasta 10 enteros y 2 decimales")
        BigDecimal price) {
}
