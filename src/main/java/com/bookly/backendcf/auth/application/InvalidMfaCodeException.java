package com.bookly.backendcf.auth.application;

public class InvalidMfaCodeException extends RuntimeException {
    public InvalidMfaCodeException() {
        super("El código de verificación es incorrecto o ya fue usado");
    }
}
