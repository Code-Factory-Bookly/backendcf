
package com.bookly.backendcf.resource.application;

import com.bookly.backendcf.shared.error.ResourceConflictException;
import java.util.Map;

public class PhysicalResourceAlreadyExistsException extends ResourceConflictException {

    public PhysicalResourceAlreadyExistsException(String name) {
        super(
                "PHYSICAL_RESOURCE_ALREADY_EXISTS",
                "Ya existe un recurso físico con ese nombre",
                Map.of("name", name)
        );
    }
}
