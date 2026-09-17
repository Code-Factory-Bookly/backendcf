package com.bookly.backendcf.auth.presentation.dto;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.domain.model.UserRole;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RegisterCustomerResponse(
        UUID id,
        String email,
        String fullName,
        UserRole role,
        OffsetDateTime createdAt) {

    public static RegisterCustomerResponse from(UserAccount userAccount) {
        return new RegisterCustomerResponse(
                userAccount.getId(),
                userAccount.getEmail(),
                userAccount.getFullName(),
                userAccount.getRole(),
                userAccount.getCreatedAt());
    }
}
