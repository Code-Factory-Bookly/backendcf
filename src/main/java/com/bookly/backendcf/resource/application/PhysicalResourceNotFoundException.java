
package com.bookly.backendcf.resource.application;

import com.bookly.backendcf.shared.error.ResourceNotFoundException;
import java.util.Map;
import java.util.UUID;

public class PhysicalResourceNotFoundException extends ResourceNotFoundException {

    public PhysicalResourceNotFoundException(UUID id) {
        super(
                "PHYSICAL_RESOURCE_NOT_FOUND",
                "No existe el recurso físico solicitado",
                Map.of("resourceId", id.toString())
        );
    }
}
