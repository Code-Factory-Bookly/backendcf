package com.bookly.backendcf.shared.error;

import com.bookly.backendcf.auth.application.EmailAlreadyRegisteredException;
import com.bookly.backendcf.auth.application.InvalidCredentialsException;
import com.bookly.backendcf.audit.application.InvalidAuditQueryException;
import com.bookly.backendcf.platform.application.PlatformAlreadyConfiguredException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> details = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> details.putIfAbsent(error.getField(), error.getDefaultMessage()));

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "El request contiene datos inválidos",
                details);
    }

    // MantisBT BUG-002: sin este handler, un path variable con formato invalido (ej. un UUID mal
    // escrito) queda sin resolver, Spring reenvia internamente a /error, y como esa ruta no esta en
    // la lista permitAll de SecurityConfiguration, el filtro de seguridad la rechaza con 401 en vez
    // del 400 real. Resolverlo aqui evita que la peticion llegue a pasar por /error.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        String parameter = exception.getName();
        String expectedType = exception.getRequiredType() != null
                ? exception.getRequiredType().getSimpleName()
                : "el tipo esperado";
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "El request contiene datos inválidos",
                Map.of(parameter, "El valor de '" + parameter + "' no tiene el formato de " + expectedType));
    }

    @ExceptionHandler(InvalidAuditQueryException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidAuditQuery(InvalidAuditQueryException exception) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                exception.getMessage(),
                exception.getDetails());
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateEmail(EmailAlreadyRegisteredException exception) {
        return buildResponse(
                HttpStatus.CONFLICT,
                "EMAIL_ALREADY_REGISTERED",
                exception.getMessage(),
                Map.of("email", "El correo ya está registrado"));
    }

    @ExceptionHandler(PlatformAlreadyConfiguredException.class)
    public ResponseEntity<ApiErrorResponse> handlePlatformAlreadyConfigured(
            PlatformAlreadyConfiguredException exception) {
        return buildResponse(
                HttpStatus.CONFLICT,
                "PLATFORM_ALREADY_CONFIGURED",
                exception.getMessage(),
                Map.of("platform", "El aprovisionamiento solo puede realizarse una vez"));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCredentials(InvalidCredentialsException exception) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceNotFound(ResourceNotFoundException exception) {
        return buildResponse(
                HttpStatus.NOT_FOUND,
                exception.getErrorCode(),
                exception.getMessage(),
                exception.getDetails());
    }

    @ExceptionHandler(ResourceConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceConflict(ResourceConflictException exception) {
        return buildResponse(
                HttpStatus.CONFLICT,
                exception.getErrorCode(),
                exception.getMessage(),
                exception.getDetails());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation() {
        return buildResponse(
                HttpStatus.CONFLICT,
                "REGISTRATION_CONFLICT",
                "No fue posible completar el registro porque los datos ya existen",
                Map.of());
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(
            HttpStatus status,
            String errorCode,
            String message,
            Map<String, String> details) {
        ApiErrorResponse response = new ApiErrorResponse(
                errorCode,
                message,
                details,
                UUID.randomUUID().toString(),
                OffsetDateTime.now());

        return ResponseEntity.status(status).body(response);
    }
}
