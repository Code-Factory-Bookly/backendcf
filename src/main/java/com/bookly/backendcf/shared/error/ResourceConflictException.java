package com.bookly.backendcf.shared.error;

import java.util.Map;

/**
 * Operación que choca con el estado actual de los datos: duplicados o dependencias que impiden
 * borrar. Cada subclase aporta su código de error y el manejador global responde 409.
 */
public abstract class ResourceConflictException extends RuntimeException {

    private final String errorCode;
    private final Map<String, String> details;

    protected ResourceConflictException(String errorCode, String message, Map<String, String> details) {
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
