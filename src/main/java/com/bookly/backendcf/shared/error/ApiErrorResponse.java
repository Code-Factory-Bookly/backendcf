package com.bookly.backendcf.shared.error;

import java.time.OffsetDateTime;
import java.util.Map;

public record ApiErrorResponse(
        String errorCode,
        String message,
        Map<String, String> details,
        String traceId,
        OffsetDateTime timestamp) {
}
