package com.bookly.backendcf.schedule.application;

import com.bookly.backendcf.shared.error.ResourceConflictException;
import java.util.Map;

public class ScheduleConflictException extends ResourceConflictException {

    public ScheduleConflictException(Map<String, String> conflicts) {
        super("SCHEDULE_CONFLICT", "El horario contiene franjas en conflicto", conflicts);
    }
}
