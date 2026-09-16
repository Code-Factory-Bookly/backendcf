package com.bookly.backendcf.catalog.presentation.dto;

import com.bookly.backendcf.catalog.domain.model.ServiceStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ServiceOfferingRequest(
        @NotBlank(message = "El nombre del servicio es obligatorio")
        @Size(max = 150, message = "El nombre del servicio no puede superar 150 caracteres")
        String name,

        @Size(max = 500, message = "La descripción no puede superar 500 caracteres")
        String description,

        @NotBlank(message = "La categoría es obligatoria")
        @Size(max = 100, message = "La categoría no puede superar 100 caracteres")
        String category,

        @NotNull(message = "La duración es obligatoria")
        @Min(value = 1, message = "La duración debe ser un valor positivo")
        @Max(value = 480, message = "La duración máxima es de 480 minutos")
        Integer durationMinutes,

        @NotNull(message = "El precio es obligatorio")
        @DecimalMin(value = "0.0", inclusive = false, message = "El precio debe ser un valor positivo")
        @Digits(integer = 10, fraction = 2, message = "El precio admite hasta 10 enteros y 2 decimales")
        BigDecimal price,

        // Solo se usa al actualizar: en la creación el sistema siempre asigna ACTIVO.
        ServiceStatus status) {
}
