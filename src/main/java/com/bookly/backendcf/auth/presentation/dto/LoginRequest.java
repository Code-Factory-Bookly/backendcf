package com.bookly.backendcf.auth.presentation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "El correo es obligatorio") @Email(message = "El correo no tiene un formato válido")
        String email,
        @NotBlank(message = "La contraseña es obligatoria") String password) {

    // Mismo motivo que RegisterCustomerRequest: recorta antes de @Email (MantisBT BUG-001).
    public LoginRequest {
        email = email == null ? null : email.strip();
    }
}
