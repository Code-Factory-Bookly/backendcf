package com.bookly.backendcf.shared.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

/** #214: las excepciones sin manejador propio salen con su estado real y el contrato estándar, no como 401. */
class GlobalExceptionHandlerFallbackTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void unConflictoDeConcurrenciaEsUn409() {
        ResponseEntity<ApiErrorResponse> response =
                handler.handleConcurrencyFailure(new ConcurrencyFailureException("deadlock"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo("CONCURRENT_UPDATE");
        assertThat(response.getBody().traceId()).isNotBlank();
    }

    @Test
    void unaRutaInexistenteEsUn404() {
        assertStatusAndCode(new ErrorResponseException(HttpStatus.NOT_FOUND), HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    @Test
    void unMetodoNoPermitidoEsUn405() {
        assertStatusAndCode(new HttpRequestMethodNotSupportedException("PATCH"),
                HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED");
    }

    @Test
    void unTipoDeContenidoNoSoportadoEsUn415() {
        assertStatusAndCode(new HttpMediaTypeNotSupportedException("text/plain"),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void unaPeticionMalFormadaEsUn400() {
        assertStatusAndCode(new ErrorResponseException(HttpStatus.BAD_REQUEST),
                HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
    }

    @Test
    void otroEstadoDeSpringConservaSuCodigoHttp() {
        assertStatusAndCode(new ErrorResponseException(HttpStatus.CONFLICT), HttpStatus.CONFLICT, "HTTP_ERROR");
    }

    @Test
    void unaExcepcionInesperadaEsUn500YNoUn401() {
        assertStatusAndCode(new IllegalStateException("fallo"),
                HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR");
    }

    private void assertStatusAndCode(Exception exception, HttpStatus status, String errorCode) {
        ResponseEntity<ApiErrorResponse> response = handler.handleUnexpected(exception);

        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo(errorCode);
        assertThat(response.getBody().timestamp()).isNotNull();
    }
}
