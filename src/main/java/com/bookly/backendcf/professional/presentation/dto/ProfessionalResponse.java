package com.bookly.backendcf.professional.presentation.dto;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.domain.model.UserRole;
import com.bookly.backendcf.professional.domain.model.Professional;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ProfessionalResponse(
        UUID id,
        String email,
        String fullName,
        String specialty,
        UserRole role,
        OffsetDateTime createdAt) {

    public static ProfessionalResponse from(UserAccount account, Professional professional) {
        return new ProfessionalResponse(
                account.getId(),
                account.getEmail(),
                account.getFullName(),
                professional.getSpecialty(),
                account.getRole(),
                account.getCreatedAt());
    }
}