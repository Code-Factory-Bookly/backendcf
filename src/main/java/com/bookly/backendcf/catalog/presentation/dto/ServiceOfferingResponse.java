package com.bookly.backendcf.catalog.presentation.dto;

import com.bookly.backendcf.catalog.domain.model.ServiceOffering;
import com.bookly.backendcf.catalog.domain.model.ServiceStatus;
import java.math.BigDecimal;
import java.util.UUID;

public record ServiceOfferingResponse(
        UUID id,
        String name,
        String description,
        String category,
        int durationMinutes,
        BigDecimal price,
        ServiceStatus status) {

    public static ServiceOfferingResponse from(ServiceOffering offering) {
        return new ServiceOfferingResponse(
                offering.getId(),
                offering.getName(),
                offering.getDescription(),
                offering.getCategory(),
                offering.getDurationMinutes(),
                offering.getPrice(),
                offering.getStatus());
    }
}
