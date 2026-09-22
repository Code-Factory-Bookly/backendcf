package com.bookly.backendcf.catalog.presentation.dto;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ServiceOfferingRequest - validación de duración en el endpoint")
class ServiceOfferingRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    @DisplayName("HU-06: asignación exitosa con 45 minutos")
    void shouldAccept45Minutes() {
        ServiceOfferingRequest request = new ServiceOfferingRequest(
                "Limpieza dental", "Profesional", "Odontología", 45, BigDecimal.valueOf(80000), null);

        Set<ConstraintViolation<ServiceOfferingRequest>> violations = validator.validate(request);

        assertTrue(violations.isEmpty(), "No debe haber violaciones para 45 minutos");
    }

    @Test
    @DisplayName("HU-06: rechazo de duración 0")
    void shouldRejectZeroDuration() {
        ServiceOfferingRequest request = new ServiceOfferingRequest(
                "Servicio", null, "Cat", 0, BigDecimal.valueOf(10000), null);

        Set<ConstraintViolation<ServiceOfferingRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("durationMinutes")));
    }

    @Test
    @DisplayName("HU-06: rechazo de duración negativa")
    void shouldRejectNegativeDuration() {
        ServiceOfferingRequest request = new ServiceOfferingRequest(
                "Servicio", null, "Cat", -10, BigDecimal.valueOf(10000), null);

        Set<ConstraintViolation<ServiceOfferingRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("durationMinutes")));
    }

    @Test
    @DisplayName("HU-06: rechazo de 600 minutos (supera máximo 480)")
    void shouldReject600Minutes() {
        ServiceOfferingRequest request = new ServiceOfferingRequest(
                "Servicio", null, "Cat", 600, BigDecimal.valueOf(10000), null);

        Set<ConstraintViolation<ServiceOfferingRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("durationMinutes")));
    }

    @Test
    @DisplayName("HU-06: rechazo de duración nula")
    void shouldRejectNullDuration() {
        ServiceOfferingRequest request = new ServiceOfferingRequest(
                "Servicio", null, "Cat", null, BigDecimal.valueOf(10000), null);

        Set<ConstraintViolation<ServiceOfferingRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("durationMinutes")));
    }

    @Test
    @DisplayName("duración límite inferior válida: 1 minuto")
    void shouldAccept1Minute() {
        ServiceOfferingRequest request = new ServiceOfferingRequest(
                "Express", null, "Cat", 1, BigDecimal.valueOf(5000), null);

        Set<ConstraintViolation<ServiceOfferingRequest>> violations = validator.validate(request);

        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("duración límite superior válida: 480 minutos")
    void shouldAccept480Minutes() {
        ServiceOfferingRequest request = new ServiceOfferingRequest(
                "Spa completo", null, "Spa", 480, BigDecimal.valueOf(500000), null);

        Set<ConstraintViolation<ServiceOfferingRequest>> violations = validator.validate(request);

        assertTrue(violations.isEmpty());
    }

    // HU-02 - Escenario Gherkin "Intento de registrar un servicio con datos obligatorios
    // faltantes": nombre en blanco.
    @Test
    @DisplayName("HU-02: rechazo de nombre en blanco")
    void shouldRejectBlankName() {
        ServiceOfferingRequest request = new ServiceOfferingRequest(
                "   ", null, "Odontología", 45, BigDecimal.valueOf(80000), null);

        Set<ConstraintViolation<ServiceOfferingRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("name")));
    }

    // HU-02 - Escenario Gherkin "Intento de registrar un servicio con datos obligatorios
    // faltantes": nombre nulo.
    @Test
    @DisplayName("HU-02: rechazo de nombre nulo")
    void shouldRejectNullName() {
        ServiceOfferingRequest request = new ServiceOfferingRequest(
                null, null, "Odontología", 45, BigDecimal.valueOf(80000), null);

        Set<ConstraintViolation<ServiceOfferingRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("name")));
    }

    // HU-02 - Escenario Gherkin "Intento de registrar un servicio con datos obligatorios
    // faltantes": categoría en blanco.
    @Test
    @DisplayName("HU-02: rechazo de categoría en blanco")
    void shouldRejectBlankCategory() {
        ServiceOfferingRequest request = new ServiceOfferingRequest(
                "Limpieza dental", null, "   ", 45, BigDecimal.valueOf(80000), null);

        Set<ConstraintViolation<ServiceOfferingRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("category")));
    }

    // HU-02 - Escenario Gherkin "Intento de registrar un servicio con datos obligatorios
    // faltantes": categoría nula.
    @Test
    @DisplayName("HU-02: rechazo de categoría nula")
    void shouldRejectNullCategory() {
        ServiceOfferingRequest request = new ServiceOfferingRequest(
                "Limpieza dental", null, null, 45, BigDecimal.valueOf(80000), null);

        Set<ConstraintViolation<ServiceOfferingRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("category")));
    }

    // HU-02 - Escenario Gherkin "Intento de registrar un servicio con datos obligatorios
    // faltantes": precio nulo.
    @Test
    @DisplayName("HU-02: rechazo de precio nulo")
    void shouldRejectNullPrice() {
        ServiceOfferingRequest request = new ServiceOfferingRequest(
                "Limpieza dental", null, "Odontología", 45, null, null);

        Set<ConstraintViolation<ServiceOfferingRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("price")));
    }

    // HU-02 - Caso de control adicional: precio en cero no cumple "positivo" según el contrato
    // del DTO (@DecimalMin exclusivo).
    @Test
    @DisplayName("HU-02: rechazo de precio en cero")
    void shouldRejectZeroPrice() {
        ServiceOfferingRequest request = new ServiceOfferingRequest(
                "Limpieza dental", null, "Odontología", 45, BigDecimal.ZERO, null);

        Set<ConstraintViolation<ServiceOfferingRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("price")));
    }

    // HU-02 - Caso de control: request completo y válido no debe generar ninguna violación.
    @Test
    @DisplayName("HU-02: request completo y válido no genera violaciones")
    void shouldAcceptFullyValidRequest() {
        ServiceOfferingRequest request = new ServiceOfferingRequest(
                "Limpieza dental", "Limpieza profesional", "Odontología", 45, BigDecimal.valueOf(80000), null);

        Set<ConstraintViolation<ServiceOfferingRequest>> violations = validator.validate(request);

        assertTrue(violations.isEmpty());
    }
}