package com.bookly.backendcf.auth.presentation.dto;

public record MfaEnrollmentResponse(String secret, String otpauthUri) {
}
