
package com.bookly.backendcf.resource.presentation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PhysicalResourceRequest(

        @NotBlank(message = "El nombre del recurso es obligatorio")
        @Size(
                max = 150,
                message = "El nombre del recurso no puede superar 150 caracteres"
        )
        String name,

        @NotNull(message = "La cantidad disponible es obligatoria")
        @Min(
                value = 1,
                message = "El inventario del recurso debe ser un número entero mayor a cero"
        )
        Integer totalQuantity
) {
}
