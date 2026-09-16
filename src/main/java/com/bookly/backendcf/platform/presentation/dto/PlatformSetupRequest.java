package com.bookly.backendcf.platform.presentation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PlatformSetupRequest(
        @NotBlank(message = "El nombre de la plataforma es obligatorio")
        @Size(max = 200, message = "El nombre de la plataforma no puede superar 200 caracteres")
        String name,

        @NotBlank(message = "El correo del administrador es obligatorio")
        @Email(message = "El correo no tiene un formato válido")
        @Size(max = 320, message = "El correo no puede superar 320 caracteres")
        String adminEmail,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, max = 64, message = "La contraseña debe tener entre 8 y 64 caracteres")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d]).+$",
                message = "La contraseña debe incluir mayúscula, minúscula, número y carácter especial")
        String adminPassword,

        @NotBlank(message = "El nombre completo del administrador es obligatorio")
        @Size(max = 150, message = "El nombre completo no puede superar 150 caracteres")
        String adminFullName) {
}
