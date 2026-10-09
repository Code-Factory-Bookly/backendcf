package com.bookly.backendcf.auth.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record MfaEnrollRequest(@NotBlank(message = "El token de verificación es obligatorio") String mfaToken) {
}
