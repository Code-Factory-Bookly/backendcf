package com.bookly.backendcf.schedule.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ScheduleRequest(
        @NotNull(message = "Las franjas son obligatorias")
        @Valid
        List<SlotRequest> slots) {
}
