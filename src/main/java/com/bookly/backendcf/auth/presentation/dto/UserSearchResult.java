package com.bookly.backendcf.auth.presentation.dto;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.domain.model.UserRole;
import java.util.UUID;

public record UserSearchResult(UUID id, String email, String fullName, UserRole role, boolean hasProfessionalProfile) {

    public static UserSearchResult of(UserAccount account, boolean hasProfessionalProfile) {
        return new UserSearchResult(
                account.getId(), account.getEmail(), account.getFullName(), account.getRole(), hasProfessionalProfile);
    }
}
