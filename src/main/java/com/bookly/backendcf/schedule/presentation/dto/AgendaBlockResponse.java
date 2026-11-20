package com.bookly.backendcf.schedule.presentation.dto;

import com.bookly.backendcf.schedule.domain.model.AgendaBlock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AgendaBlockResponse(
        UUID id,
        UUID professionalId,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String reason,
        OffsetDateTime createdAt) {

    public static AgendaBlockResponse from(AgendaBlock block) {
        return new AgendaBlockResponse(
                block.getId(), block.getProfessionalId(), block.getStartTime(), block.getEndTime(),
                block.getReason(), block.getCreatedAt());
    }
}
