package com.bookly.backendcf.auth.presentation.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * MantisBT BUG-001: un correo con mayúsculas y espacios al borde no debía fallar el formato antes
 * de llegar a la comparación de duplicados.
 */
class RegisterCustomerRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void unCorreoConEspaciosAlBordeNoFallaElFormato() {
        Set<ConstraintViolation<RegisterCustomerRequest>> violations = validator.validate(
                new RegisterCustomerRequest(" CLIENTE1@BOOKLY.LOCAL ", "Valida123!Pass", "Cliente Uno"));

        assertTrue(violations.isEmpty());
    }

    @Test
    void elRecorteExponeElCorreoYaSinEspacios() {
        RegisterCustomerRequest request = new RegisterCustomerRequest(" cliente1@bookly.local ", "Valida123!Pass", "Cliente Uno");

        assertEquals("cliente1@bookly.local", request.email());
    }

    @Test
    void unCorreoRealmenteInvalidoSigueSiendoRechazado() {
        Set<ConstraintViolation<RegisterCustomerRequest>> violations = validator.validate(
                new RegisterCustomerRequest("no-es-un-correo", "Valida123!Pass", "Cliente Uno"));

        assertEquals(1, violations.size());
        assertEquals("email", violations.iterator().next().getPropertyPath().toString());
    }
}
