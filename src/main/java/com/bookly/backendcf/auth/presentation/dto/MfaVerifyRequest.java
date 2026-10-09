package com.bookly.backendcf.auth.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MfaVerifyRequest(
        @NotBlank(message = "El token de verificación es obligatorio") String mfaToken,
        @NotBlank(message = "El código es obligatorio")
        @Size(max = 20, message = "El código no es válido") String code,
        boolean trustDevice) {
}
