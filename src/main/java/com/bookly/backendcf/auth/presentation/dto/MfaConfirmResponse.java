package com.bookly.backendcf.auth.presentation.dto;

import java.util.List;

public record MfaConfirmResponse(LoginResponse session, List<String> recoveryCodes) {
}
