package com.bookly.backendcf.auth.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record MfaDisableRequest(
        @NotBlank(message = "La contraseña es obligatoria") String password,
        @NotBlank(message = "El código es obligatorio") String code) {
}
