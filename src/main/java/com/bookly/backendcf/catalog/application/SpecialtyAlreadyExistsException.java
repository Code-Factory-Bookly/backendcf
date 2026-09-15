package com.bookly.backendcf.catalog.application;

import com.bookly.backendcf.shared.error.ResourceConflictException;
import java.util.Map;

public class SpecialtyAlreadyExistsException extends ResourceConflictException {

    public SpecialtyAlreadyExistsException(String name) {
        super("SPECIALTY_ALREADY_EXISTS", "Ya existe una especialidad con ese nombre", Map.of("name", name));
    }
}
