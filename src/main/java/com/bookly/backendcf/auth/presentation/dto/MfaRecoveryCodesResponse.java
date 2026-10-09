package com.bookly.backendcf.auth.presentation.dto;

import java.util.List;

public record MfaRecoveryCodesResponse(List<String> recoveryCodes) {
}
