package com.bookly.backendcf.auth.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record MfaRegenerateRequest(
        @NotBlank(message = "El código es obligatorio") String code) {
}
