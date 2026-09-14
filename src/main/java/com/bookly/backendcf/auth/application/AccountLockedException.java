package com.bookly.backendcf.auth.application;

import java.time.OffsetDateTime;

public class AccountLockedException extends RuntimeException {
    private final OffsetDateTime lockedUntil;

    public AccountLockedException(OffsetDateTime lockedUntil) {
        super("La cuenta está bloqueada temporalmente por demasiados intentos fallidos");
        this.lockedUntil = lockedUntil;
    }

    public OffsetDateTime getLockedUntil() {
        return lockedUntil;
    }
}
