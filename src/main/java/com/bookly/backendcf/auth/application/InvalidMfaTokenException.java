package com.bookly.backendcf.auth.application;

public class InvalidMfaTokenException extends RuntimeException {
    public InvalidMfaTokenException() {
        super("La sesión de verificación expiró. Inicia sesión de nuevo");
    }
}
