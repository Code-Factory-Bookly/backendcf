package com.bookly.backendcf.auth.presentation.dto;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.domain.model.UserRole;
import java.util.UUID;

public record RoleAssignmentResponse(UUID id, String email, String fullName, UserRole role) {

    public static RoleAssignmentResponse from(UserAccount account) {
        return new RoleAssignmentResponse(account.getId(), account.getEmail(), account.getFullName(), account.getRole());
    }
}
