package com.bookly.backendcf.catalog.presentation.dto;

import com.bookly.backendcf.catalog.domain.model.ServiceOffering;
import java.math.BigDecimal;
import java.util.UUID;

public record ServiceOfferingResponse(
        UUID id,
        UUID specialtyId,
        String specialtyName,
        String name,
        String description,
        int durationMinutes,
        BigDecimal price) {

    public static ServiceOfferingResponse from(ServiceOffering offering) {
        return new ServiceOfferingResponse(
                offering.getId(),
                offering.getSpecialty().getId(),
                offering.getSpecialty().getName(),
                offering.getName(),
                offering.getDescription(),
                offering.getDurationMinutes(),
                offering.getPrice());
    }
}
