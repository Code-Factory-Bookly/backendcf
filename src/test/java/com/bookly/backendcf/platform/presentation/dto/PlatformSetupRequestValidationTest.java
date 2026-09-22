package com.bookly.backendcf.platform.presentation.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Cubre el escenario Gherkin "Rechazo por datos incompletos" de HU-20 a nivel del contrato del
 * DTO: sin el campo "name", la validación de Bean Validation debe rechazar la solicitud antes de
 * llegar al servicio.
 */
class PlatformSetupRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    // HU-20 - Escenario Gherkin "Rechazo por datos incompletos" (caso literal: sin el campo
    // "name"): la ausencia total del campo se traduce en el DTO como name == null.
    @Test
    void requestSinNombreDePlataformaEsRechazadoPorElContrato() {
        Set<ConstraintViolation<PlatformSetupRequest>> violations = validator.validate(
                new PlatformSetupRequest(null, "admin@bookly.com", "Secreta123*", "Ada Admin"));

        assertEquals(1, violations.size());
        assertEquals("name", violations.iterator().next().getPropertyPath().toString());
    }

    // HU-20 - Caso adicional de QA sobre el mismo escenario "Rechazo por datos incompletos":
    // un name en blanco (solo espacios) debe fallar igual que un name ausente, no solo @NotNull.
    @Test
    void requestConNombreDePlataformaEnBlancoEsRechazadoPorElContrato() {
        Set<ConstraintViolation<PlatformSetupRequest>> violations = validator.validate(
                new PlatformSetupRequest("   ", "admin@bookly.com", "Secreta123*", "Ada Admin"));

        assertEquals(1, violations.size());
        assertEquals("name", violations.iterator().next().getPropertyPath().toString());
    }

    // HU-20 - Caso de control (contraparte negativa de los dos anteriores): un request completo
    // y válido no debe generar ninguna violación de contrato.
    @Test
    void requestValidoNoProduceViolaciones() {
        Set<ConstraintViolation<PlatformSetupRequest>> violations = validator.validate(
                new PlatformSetupRequest("Bookly Salud", "admin@bookly.com", "Secreta123*", "Ada Admin"));

        assertTrue(violations.isEmpty());
    }
}