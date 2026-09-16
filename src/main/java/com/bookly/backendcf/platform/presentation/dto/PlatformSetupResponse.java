package com.bookly.backendcf.platform.presentation.dto;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.platform.domain.model.Platform;
import java.util.UUID;

public record PlatformSetupResponse(
        UUID platformId,
        String name,
        UUID adminUserId) {

    public static PlatformSetupResponse from(Platform platform, UserAccount adminUser) {
        return new PlatformSetupResponse(
                platform.getId(),
                platform.getName(),
                adminUser.getId());
    }
}
