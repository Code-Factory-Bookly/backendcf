package com.bookly.backendcf.platform.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.domain.model.UserRole;
import com.bookly.backendcf.auth.infrastructure.persistence.UserAccountRepository;
import com.bookly.backendcf.platform.domain.model.Platform;
import com.bookly.backendcf.platform.infrastructure.persistence.PlatformRepository;
import com.bookly.backendcf.platform.presentation.dto.PlatformSetupRequest;
import com.bookly.backendcf.platform.presentation.dto.PlatformSetupResponse;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Cubre los 4 escenarios Gherkin de HU-20 (Historias_de_usuario_azure.md) a nivel de servicio,
 * más el caso de condición de carrera identificado junto al índice único de
 * {@code V3__create_platform.sql} (README_QA.md, sección 6).
 */
@ExtendWith(MockitoExtension.class)
class PlatformSetupServiceTest {

    @Mock
    private PlatformRepository platformRepository;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private WelcomeNotificationPort welcomeNotificationPort;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private PlatformSetupService service;

    @BeforeEach
    void setUp() {
        service = new PlatformSetupService(
                platformRepository, userAccountRepository, passwordEncoder, welcomeNotificationPort);
    }

    // HU-20 - Escenario Gherkin "Aprovisionamiento exitoso": con la plataforma sin configurar,
    // el setup debe crear la plataforma y el admin (rol ADMIN) y devolver ambos identificadores.
    @Test
    void aprovisionamientoExitosoCreaPlataformaYAdminConRolAdmin() {
        when(platformRepository.count()).thenReturn(0L);
        when(platformRepository.saveAndFlush(any(Platform.class))).thenAnswer(invocation -> {
            Platform platform = invocation.getArgument(0);
            ReflectionTestUtils.setField(platform, "id", UUID.randomUUID());
            return platform;
        });
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(invocation -> {
            UserAccount account = invocation.getArgument(0);
            ReflectionTestUtils.setField(account, "id", UUID.randomUUID());
            return account;
        });

        PlatformSetupResponse response = service.setup(new PlatformSetupRequest(
                "  Bookly   Salud  ", " ADMIN@BOOKLY.COM ", "Secreta123*", "  Ada   Admin  "));

        assertNotNull(response.platformId());
        assertNotNull(response.adminUserId());
        assertEquals("Bookly Salud", response.name());

        ArgumentCaptor<UserAccount> savedAccount = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountRepository).save(savedAccount.capture());
        assertEquals("admin@bookly.com", savedAccount.getValue().getEmail());
        assertEquals("Ada Admin", savedAccount.getValue().getFullName());
        assertEquals(UserRole.ADMIN, savedAccount.getValue().getRole());

        verify(welcomeNotificationPort).sendPlatformWelcome(
                eq(response.platformId()), eq("Bookly Salud"), eq("admin@bookly.com"));
    }

    // HU-20 - Escenario Gherkin "Rechazo de un segundo aprovisionamiento": si la plataforma ya
    // fue configurada (count() > 0), el setup debe rechazar antes de tocar cualquier repositorio.
    @Test
    void segundoAprovisionamientoEsRechazadoCuandoYaExistePlataforma() {
        when(platformRepository.count()).thenReturn(1L);

        assertThrows(PlatformAlreadyConfiguredException.class, () -> service.setup(new PlatformSetupRequest(
                "Otra Plataforma", "otro@bookly.com", "Secreta123*", "Otro Admin")));

        verify(platformRepository, never()).saveAndFlush(any());
        verify(userAccountRepository, never()).save(any());
        verify(welcomeNotificationPort, never()).sendPlatformWelcome(any(), any(), any());
    }

    // HU-20 - Caso adicional de QA (no está en el Gherkin literal, acordado tras revisar el
    // riesgo de condición de carrera del README_QA.md sección 6): simula que dos peticiones pasan
    // el count()==0 antes de que cualquiera persista; el índice único uk_platform_singleton
    // (V3__create_platform.sql) deja pasar solo un INSERT y el perdedor recibe
    // DataIntegrityViolationException en el saveAndFlush, no en el count(). El setup debe
    // traducir eso al mismo 409 PLATFORM_ALREADY_CONFIGURED del escenario anterior.
    @Test
    void segundoAprovisionamientoConcurrenteEsRechazadoPorElIndiceUnicoDePlataforma() {
        when(platformRepository.count()).thenReturn(0L);
        when(platformRepository.saveAndFlush(any(Platform.class)))
                .thenThrow(new DataIntegrityViolationException("uk_platform_singleton"));

        assertThrows(PlatformAlreadyConfiguredException.class, () -> service.setup(new PlatformSetupRequest(
                "Bookly Salud", "admin@bookly.com", "Secreta123*", "Ada Admin")));

        verify(userAccountRepository, never()).save(any());
        verify(welcomeNotificationPort, never()).sendPlatformWelcome(any(), any(), any());
    }

    // HU-20 - Escenario Gherkin "La contraseña nunca se almacena en texto plano": el hash
    // guardado debe ser distinto de la contraseña cruda y validar por BCrypt, y la respuesta del
    // caso de uso no debe contener la contraseña en ningún campo.
    @Test
    void elPasswordNuncaSeAlmacenaEnTextoPlanoYNoSeExponeEnLaRespuesta() {
        String rawPassword = "Secreta123*";
        when(platformRepository.count()).thenReturn(0L);
        when(platformRepository.saveAndFlush(any(Platform.class))).thenAnswer(invocation -> {
            Platform platform = invocation.getArgument(0);
            ReflectionTestUtils.setField(platform, "id", UUID.randomUUID());
            return platform;
        });
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(invocation -> {
            UserAccount account = invocation.getArgument(0);
            ReflectionTestUtils.setField(account, "id", UUID.randomUUID());
            return account;
        });

        PlatformSetupResponse response = service.setup(new PlatformSetupRequest(
                "Bookly Salud", "admin@bookly.com", rawPassword, "Ada Admin"));

        ArgumentCaptor<UserAccount> savedAccount = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountRepository).save(savedAccount.capture());
        String storedHash = savedAccount.getValue().getPasswordHash();

        assertNotEquals(rawPassword, storedHash);
        assertTrue(passwordEncoder.matches(rawPassword, storedHash));
        assertFalse(response.toString().contains(rawPassword));
    }
}