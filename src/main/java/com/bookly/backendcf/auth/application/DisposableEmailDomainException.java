package com.bookly.backendcf.auth.application;

public class DisposableEmailDomainException extends RuntimeException {
    public DisposableEmailDomainException() {
        super("No se permiten correos de dominios desechables o temporales");
    }
}
