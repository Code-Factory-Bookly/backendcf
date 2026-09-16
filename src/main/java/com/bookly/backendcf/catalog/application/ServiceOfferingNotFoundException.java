package com.bookly.backendcf.catalog.application;

import com.bookly.backendcf.shared.error.ResourceNotFoundException;
import java.util.Map;
import java.util.UUID;

public class ServiceOfferingNotFoundException extends ResourceNotFoundException {

    public ServiceOfferingNotFoundException(UUID serviceId) {
        super("SERVICE_NOT_FOUND", "El servicio indicado no existe", Map.of("serviceId", String.valueOf(serviceId)));
    }
}
