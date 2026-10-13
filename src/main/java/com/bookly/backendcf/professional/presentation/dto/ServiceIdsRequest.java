package com.bookly.backendcf.professional.presentation.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record ServiceIdsRequest(@NotNull(message = "La lista de servicios es obligatoria") List<UUID> serviceIds) {
}
