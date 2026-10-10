package com.bookly.backendcf.availability.application;

import java.util.Map;

/** Indicates missing or invalid parameters in the availability query. */
public class InvalidAvailabilityQueryException extends RuntimeException {

    private final Map<String, String> details;

    public InvalidAvailabilityQueryException(String message, Map<String, String> details) {
        super(message);
        this.details = Map.copyOf(details);
    }

    public Map<String, String> getDetails() {
        return details;
    }
}