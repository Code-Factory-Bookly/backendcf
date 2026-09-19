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
}