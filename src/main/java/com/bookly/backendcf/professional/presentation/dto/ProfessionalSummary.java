package com.bookly.backendcf.professional.presentation.dto;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.professional.domain.model.Professional;
import java.util.UUID;

/**
 * Versión pública de {@link ProfessionalResponse}: sin correo. La usa cualquier visitante al
 * elegir profesional para un servicio (HU-07/HU-08), no solo ADMIN.
 */
public record ProfessionalSummary(UUID id, String fullName, String specialty) {

    public static ProfessionalSummary from(UserAccount account, Professional professional) {
        return new ProfessionalSummary(account.getId(), account.getFullName(), professional.getSpecialty());
    }
}
