package com.bookly.backendcf.shared.error;

import java.util.Map;

/**
 * Recurso inexistente. Cada subclase aporta su propio código de error, de modo que el manejador
 * global responde 404 con el envoltorio uniforme sin necesitar un método por excepción.
 */
public abstract class ResourceNotFoundException extends RuntimeException {

    private final String errorCode;
    private final Map<String, String> details;

    protected ResourceNotFoundException(String errorCode, String message, Map<String, String> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Map<String, String> getDetails() {
        return details;
    }
}
