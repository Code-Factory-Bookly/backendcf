
package com.bookly.backendcf.resource.presentation.dto;

import com.bookly.backendcf.resource.domain.model.PhysicalResource;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PhysicalResourceResponse(
        UUID id,
        String name,
        int totalQuantity,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static PhysicalResourceResponse from(PhysicalResource resource) {
        return new PhysicalResourceResponse(
                resource.getId(),
                resource.getName(),
                resource.getTotalQuantity(),
                resource.getCreatedAt(),
                resource.getUpdatedAt()
        );
    }
}
