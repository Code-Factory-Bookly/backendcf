package com.bookly.backendcf.shared.error;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record ApiErrorResponse(
        String errorCode,
        String message,
        Map<String, String> details,
        String traceId,
        OffsetDateTime timestamp) {

    /** Error con el contrato estándar de la API: código, mensaje, detalle, traza y marca de tiempo. */
    public static ApiErrorResponse of(String errorCode, String message, Map<String, String> details) {
        return new ApiErrorResponse(errorCode, message, details == null ? Map.of() : details,
                UUID.randomUUID().toString(), OffsetDateTime.now());
    }
}
