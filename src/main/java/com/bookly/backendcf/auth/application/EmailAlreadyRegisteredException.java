package com.bookly.backendcf.auth.application;

public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException() {
        super("El correo ya está registrado en esta organización");
    }
}
