package com.bookly.backendcf.organization.application;

/**
 * La plataforma atiende a una sola organización, así que el aprovisionamiento es irrepetible:
 * una vez hecho, cualquier intento posterior se rechaza.
 */
public class OrganizationAlreadyExistsException extends RuntimeException {

    public OrganizationAlreadyExistsException() {
        super("La plataforma ya tiene una organización registrada");
    }
}
