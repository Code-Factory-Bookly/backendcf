package com.bookly.backendcf.auth.presentation.dto;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.domain.model.UserRole;
import java.util.UUID;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserSummary user) {

    public record UserSummary(UUID id, String email, String fullName, UserRole role) {
        static UserSummary from(UserAccount account) {
            return new UserSummary(account.getId(), account.getEmail(), account.getFullName(), account.getRole());
        }
    }
}
