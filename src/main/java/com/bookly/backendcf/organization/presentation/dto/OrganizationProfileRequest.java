package com.bookly.backendcf.organization.presentation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OrganizationProfileRequest(
        @NotBlank(message = "El nombre comercial es obligatorio")
        @Size(max = 200, message = "El nombre comercial no puede superar 200 caracteres")
        String commercialName,

        @NotBlank(message = "El correo de contacto es obligatorio")
        @Email(message = "El correo de contacto no tiene un formato válido")
        @Size(max = 320, message = "El correo de contacto no puede superar 320 caracteres")
        String contactEmail,

        @NotBlank(message = "El teléfono de contacto es obligatorio")
        @Pattern(regexp = "^\\+?[0-9 ()-]{7,30}$", message = "El teléfono de contacto no tiene un formato válido")
        String contactPhone,

        @NotBlank(message = "La dirección de la sede es obligatoria")
        @Size(max = 300, message = "La dirección no puede superar 300 caracteres")
        String address) {
}
