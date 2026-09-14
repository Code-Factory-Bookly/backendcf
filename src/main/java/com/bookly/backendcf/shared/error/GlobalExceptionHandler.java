package com.bookly.backendcf.shared.error;

import com.bookly.backendcf.auth.application.EmailAlreadyRegisteredException;
import com.bookly.backendcf.auth.application.AccountLockedException;
import com.bookly.backendcf.auth.application.InvalidCredentialsException;
import com.bookly.backendcf.organization.application.OrganizationAlreadyExistsException;
import com.bookly.backendcf.organization.application.OrganizationNotFoundException;
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

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateEmail(EmailAlreadyRegisteredException exception) {
        return buildResponse(
                HttpStatus.CONFLICT,
                "EMAIL_ALREADY_REGISTERED",
                exception.getMessage(),
                Map.of("email", "El correo ya está registrado en esta organización"));
    }

    @ExceptionHandler(OrganizationAlreadyExistsException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateOrganization(
            OrganizationAlreadyExistsException exception) {
        return buildResponse(
                HttpStatus.CONFLICT,
                "ORGANIZATION_ALREADY_EXISTS",
                exception.getMessage(),
                Map.of(exception.getField(), exception.getDetail()));
    }

    @ExceptionHandler(OrganizationNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleOrganizationNotFound(
            OrganizationNotFoundException exception) {
        return buildResponse(
                HttpStatus.NOT_FOUND,
                "ORGANIZATION_NOT_FOUND",
                exception.getMessage(),
                Map.of("organizationId", String.valueOf(exception.getOrganizationId())));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCredentials(InvalidCredentialsException exception) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccountLocked(AccountLockedException exception) {
        return buildResponse(HttpStatus.LOCKED, "ACCOUNT_LOCKED", exception.getMessage(),
                Map.of("lockedUntil", exception.getLockedUntil().toString()));
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
