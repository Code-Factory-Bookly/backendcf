package com.bookly.backendcf.schedule.application;

import com.bookly.backendcf.shared.error.ResourceNotFoundException;
import java.util.Map;
import java.util.UUID;

public class AgendaBlockNotFoundException extends ResourceNotFoundException {

    public AgendaBlockNotFoundException(UUID blockId) {
        super("AGENDA_BLOCK_NOT_FOUND", "El bloqueo no existe", Map.of("bloqueoId", blockId.toString()));
    }
}
