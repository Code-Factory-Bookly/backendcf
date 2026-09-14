package com.bookly.backendcf.auth.application;

public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("El correo o la contraseña son incorrectos");
    }
}
