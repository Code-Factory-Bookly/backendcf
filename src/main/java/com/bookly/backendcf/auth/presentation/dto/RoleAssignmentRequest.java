package com.bookly.backendcf.auth.presentation.dto;

import com.bookly.backendcf.auth.domain.model.UserRole;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RoleAssignmentRequest(
        @NotNull(message = "El rol es obligatorio")
        UserRole role,

        @Size(max = 100, message = "La especialidad no puede superar 100 caracteres")
        String specialty) {
}
