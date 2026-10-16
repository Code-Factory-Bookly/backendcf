package com.bookly.backendcf.schedule.application;

import java.util.Map;

public class ScheduleValidationException extends RuntimeException {

    private final Map<String, String> details;

    public ScheduleValidationException(Map<String, String> details) {
        super("El horario contiene datos inválidos");
        this.details = details;
    }

    public Map<String, String> getDetails() {
        return details;
    }
}
