package com.bookly.backendcf.shared.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookly.backendcf.audit.application.InvalidAuditQueryException;
import com.bookly.backendcf.availability.application.InvalidAvailabilityQueryException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

    @Test
    void transformaUnaConsultaDeAuditoriaInvalidaEnErrorBadRequest() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        InvalidAuditQueryException exception = new InvalidAuditQueryException(
                "La página no puede ser negativa", Map.of("page", "Debe ser mayor o igual que 0"));

        ResponseEntity<ApiErrorResponse> response = handler.handleInvalidAuditQuery(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().message()).isEqualTo(exception.getMessage());
        assertThat(response.getBody().details()).containsEntry("page", "Debe ser mayor o igual que 0");
    }
    @Test
    void transformaUnaConsultaDeDisponibilidadInvalidaEnErrorBadRequest() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        InvalidAvailabilityQueryException exception = new InvalidAvailabilityQueryException(
                "Faltan parámetros de la consulta", Map.of("desde", "Es obligatorio"));

        ResponseEntity<ApiErrorResponse> response = handler.handleInvalidAvailabilityQuery(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().details()).containsEntry("desde", "Es obligatorio");
    }
}
