package com.bookly.backendcf.catalog.application;

import com.bookly.backendcf.shared.error.ResourceConflictException;
import java.util.Map;

public class ServiceOfferingAlreadyExistsException extends ResourceConflictException {

    public ServiceOfferingAlreadyExistsException(String name) {
        super("SERVICE_ALREADY_EXISTS", "Ya existe un servicio con ese nombre", Map.of("name", name));
    }
}
