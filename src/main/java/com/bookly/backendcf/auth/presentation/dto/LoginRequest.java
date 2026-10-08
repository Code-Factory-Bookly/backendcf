package com.bookly.backendcf.auth.presentation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "El correo es obligatorio") @Email(message = "El correo no tiene un formato válido")
        String email,
        @NotBlank(message = "La contraseña es obligatoria") String password,
        // Opcional: token de "confiar en este dispositivo" guardado por el front. Si es valido y
        // vigente, un ADMIN se salta el segundo factor.
        String deviceToken) {

    // Mismo motivo que RegisterCustomerRequest: recorta antes de @Email (MantisBT BUG-001).
    public LoginRequest {
        email = email == null ? null : email.strip();
    }
}
