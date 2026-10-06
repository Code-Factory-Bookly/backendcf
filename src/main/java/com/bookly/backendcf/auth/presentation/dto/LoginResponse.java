package com.bookly.backendcf.auth.presentation.dto;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.domain.model.UserRole;
import com.bookly.backendcf.auth.security.JwtTokenService;
import java.util.UUID;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserSummary user,
        boolean mfaRequired,
        boolean mfaSetupRequired,
        String mfaToken) {

    public static LoginResponse authenticated(JwtTokenService tokens, UserAccount account) {
        return new LoginResponse(tokens.createToken(account), "Bearer", tokens.getExpiresInSeconds(),
                UserSummary.from(account), false, false, null);
    }

    public static LoginResponse mfaChallenge(JwtTokenService tokens, UserAccount account) {
        return new LoginResponse(null, null, 0, null, true, !account.isMfaEnabled(),
                tokens.createMfaPendingToken(account));
    }

    public record UserSummary(UUID id, String email, String fullName, UserRole role) {
        static UserSummary from(UserAccount account) {
            return new UserSummary(account.getId(), account.getEmail(), account.getFullName(), account.getRole());
        }
    }
}
