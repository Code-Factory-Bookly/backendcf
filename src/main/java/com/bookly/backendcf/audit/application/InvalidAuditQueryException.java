package com.bookly.backendcf.audit.application;

import java.util.Map;

/** Indicates an invalid date range or pagination request for the audit query. */
public class InvalidAuditQueryException extends RuntimeException {

    private final Map<String, String> details;

    public InvalidAuditQueryException(String message, Map<String, String> details) {
        super(message);
        this.details = Map.copyOf(details);
    }

    public Map<String, String> getDetails() {
        return details;
    }
}
