package com.bookly.backendcf.auth.application;

public class TooManyLoginAttemptsException extends RuntimeException {
    public TooManyLoginAttemptsException() {
        super("Demasiados intentos de inicio de sesión desde esta dirección. Intenta de nuevo más tarde.");
    }
}
