package com.bookly.backendcf.professional.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookly.backendcf.auth.application.EmailAlreadyRegisteredException;
import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.domain.model.UserRole;
import com.bookly.backendcf.auth.infrastructure.persistence.UserAccountRepository;
import com.bookly.backendcf.professional.domain.model.Professional;
import com.bookly.backendcf.professional.infrastructure.persistence.ProfessionalRepository;
import com.bookly.backendcf.professional.presentation.dto.ProfessionalRequest;
import com.bookly.backendcf.professional.presentation.dto.ProfessionalResponse;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class RegisterProfessionalServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private ProfessionalRepository professionalRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private RegisterProfessionalService service;

    @BeforeEach
    void setUp() {
        service = new RegisterProfessionalService(userAccountRepository, professionalRepository, passwordEncoder);
    }

    @Test
    void registroExitosoCreaLaCuentaConRolProfesionalYElPerfilDeEspecialidad() {
        when(userAccountRepository.existsByEmail("sofia@example.com")).thenReturn(false);
        when(userAccountRepository.saveAndFlush(any(UserAccount.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(professionalRepository.saveAndFlush(any(Professional.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProfessionalResponse response = service.register(new ProfessionalRequest(
                " SOFIA@EXAMPLE.COM ", "Valid1!pass", "  Sofia   Prueba  ", "  Ortodoncia  "));

        assertEquals("sofia@example.com", response.email());
        assertEquals("Sofia Prueba", response.fullName());
        assertEquals("Ortodoncia", response.specialty());
        assertEquals(UserRole.PROFESSIONAL, response.role());

        ArgumentCaptor<UserAccount> savedAccount = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountRepository).saveAndFlush(savedAccount.capture());
        assertEquals(UserRole.PROFESSIONAL, savedAccount.getValue().getRole());
        assertTrue(passwordEncoder.matches("Valid1!pass", savedAccount.getValue().getPasswordHash()));

        ArgumentCaptor<Professional> savedProfessional = ArgumentCaptor.forClass(Professional.class);
        verify(professionalRepository).saveAndFlush(savedProfessional.capture());
        assertEquals(savedAccount.getValue().getId(), savedProfessional.getValue().getId());
        assertEquals("Ortodoncia", savedProfessional.getValue().getSpecialty());
    }

    @Test
    void registroConCorreoYaRegistradoRechazaLaSolicitud() {
        when(userAccountRepository.existsByEmail("sofia@example.com")).thenReturn(true);

        assertThrows(EmailAlreadyRegisteredException.class,
                () -> service.register(new ProfessionalRequest(
                        "sofia@example.com", "Valid1!pass", "Sofia Prueba", "Ortodoncia")));

        verify(userAccountRepository, never()).saveAndFlush(any());
        verify(professionalRepository, never()).saveAndFlush(any());
    }

    @Test
    void carreraDeRegistroSimultaneoDetectadaPorLaBaseTambienSeRechazaComoCorreoDuplicado() {
        when(userAccountRepository.existsByEmail("sofia@example.com")).thenReturn(false);
        when(userAccountRepository.saveAndFlush(any(UserAccount.class)))
                .thenThrow(new DataIntegrityViolationException("uk_app_user_email"));

        assertThrows(EmailAlreadyRegisteredException.class,
                () -> service.register(new ProfessionalRequest(
                        "sofia@example.com", "Valid1!pass", "Sofia Prueba", "Ortodoncia")));

        verify(professionalRepository, never()).saveAndFlush(any());
    }

    @Test
    void registroConContrasenaDebilEsRechazadoPorLaValidacionDelContrato() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        Set<ConstraintViolation<ProfessionalRequest>> violations = validator.validate(
                new ProfessionalRequest("sofia@example.com", "abc12345", "Sofia Prueba", "Ortodoncia"));

        assertEquals(1, violations.size());
        assertEquals("password", violations.iterator().next().getPropertyPath().toString());
    }
}
