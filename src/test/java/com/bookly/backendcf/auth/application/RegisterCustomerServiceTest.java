package com.bookly.backendcf.auth.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.domain.model.UserRole;
import com.bookly.backendcf.auth.infrastructure.persistence.UserAccountRepository;
import com.bookly.backendcf.auth.presentation.dto.RegisterCustomerRequest;
import com.bookly.backendcf.auth.presentation.dto.RegisterCustomerResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class RegisterCustomerServiceTest {

    @Mock
    private UserAccountRepository repository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private RegisterCustomerService service;

    @BeforeEach
    void setUp() {
        service = new RegisterCustomerService(repository, passwordEncoder);
    }

    @Test
    void registroExitosoCreaLaCuentaConRolClienteYPasswordHasheado() {
        when(repository.existsByEmail("customer@example.com")).thenReturn(false);
        when(repository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RegisterCustomerResponse response = service.register(
                new RegisterCustomerRequest(" CUSTOMER@EXAMPLE.COM ", "Valid1!pass", "  Customer   One  "));

        assertEquals("customer@example.com", response.email());
        assertEquals("Customer One", response.fullName());
        assertEquals(UserRole.CUSTOMER, response.role());

        ArgumentCaptor<UserAccount> savedAccount = ArgumentCaptor.forClass(UserAccount.class);
        verify(repository).save(savedAccount.capture());
        assertEquals("customer@example.com", savedAccount.getValue().getEmail());
        assertTrue(passwordEncoder.matches("Valid1!pass", savedAccount.getValue().getPasswordHash()));
    }

    @Test
    void registroConCorreoYaRegistradoRechazaLaSolicitud() {
        when(repository.existsByEmail("customer@example.com")).thenReturn(true);

        assertThrows(EmailAlreadyRegisteredException.class,
                () -> service.register(new RegisterCustomerRequest(
                        "customer@example.com", "Valid1!pass", "Customer One")));

        verify(repository, never()).save(any());
    }

    @Test
    void registroConContrasenaDebilEsRechazadoPorLaValidacionDelContrato() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        Set<ConstraintViolation<RegisterCustomerRequest>> violations = validator.validate(
                new RegisterCustomerRequest("customer@example.com", "abc12345", "Customer One"));

        assertEquals(1, violations.size());
        assertEquals("password", violations.iterator().next().getPropertyPath().toString());
    }
}
