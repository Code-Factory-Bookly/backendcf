package com.bookly.backendcf.auth.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.domain.model.UserRole;
import com.bookly.backendcf.auth.infrastructure.persistence.TrustedDeviceRepository;
import com.bookly.backendcf.auth.infrastructure.persistence.UserAccountRepository;
import com.bookly.backendcf.auth.presentation.dto.LoginRequest;
import com.bookly.backendcf.auth.presentation.dto.LoginResponse;
import com.bookly.backendcf.auth.security.JwtTokenService;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class LoginServiceTest {
    private UserAccountRepository repository;
    private LoginService service;
    private UserAccount account;

    @BeforeEach
    void setUp() {
        account = new UserAccount("customer@example.com", new BCryptPasswordEncoder().encode("Valid1!pass"), "Customer One");
        setId(account, UUID.randomUUID());
        repository = (UserAccountRepository) Proxy.newProxyInstance(
                UserAccountRepository.class.getClassLoader(),
                new Class<?>[]{UserAccountRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("findByEmail")) return Optional.of(account);
                    if (method.getName().equals("save")) return account;
                    if (method.getName().equals("toString")) return "fake-repository";
                    if (method.getName().equals("hashCode")) return 1;
                    if (method.getName().equals("equals")) return proxy == args[0];
                    throw new UnsupportedOperationException(method.getName());
                });
        TrustedDeviceRepository trustedDeviceRepository = (TrustedDeviceRepository) Proxy.newProxyInstance(
                TrustedDeviceRepository.class.getClassLoader(),
                new Class<?>[]{TrustedDeviceRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("findByUserIdAndExpiresAtAfter")) return List.of();
                    if (method.getName().equals("toString")) return "fake-trusted-device-repository";
                    if (method.getName().equals("hashCode")) return 1;
                    if (method.getName().equals("equals")) return proxy == args[0];
                    return null;
                });
        service = new LoginService(repository, new BCryptPasswordEncoder(),
                new JwtTokenService("test-secret-that-is-at-least-sixty-four-characters-long-for-tests", 3600),
                new LoginAttemptRecorder(repository), new DeviceTrustService(trustedDeviceRepository), 5, 15);
    }

    @Test
    void loginExitosoEntregaTokenYReiniciaContador() {
        LoginResponse response = service.login(new LoginRequest(" CUSTOMER@EXAMPLE.COM ", "Valid1!pass", null));

        assertNotNull(response.accessToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(0, account.getFailedLoginAttempts());
    }

    @Test
    void credencialesIncorrectasDevuelvenErrorYRegistranIntento() {
        assertThrows(InvalidCredentialsException.class,
                () -> service.login(new LoginRequest("customer@example.com", "Wrong1!pass", null)));

        assertEquals(1, account.getFailedLoginAttempts());
    }

    // MantisBT BUG-004: una cuenta bloqueada no debe distinguirse de una contraseña incorrecta en
    // la respuesta (ambas son InvalidCredentialsException/401), para no revelar que el correo existe.
    @Test
    void quintoIntentoFallidoBloqueaLaCuentaSinDistinguirloEnLaRespuesta() {
        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThrows(InvalidCredentialsException.class,
                    () -> service.login(new LoginRequest("customer@example.com", "Wrong1!pass", null)));
        }

        assertEquals(5, account.getFailedLoginAttempts());
        assertNotNull(account.getLockedUntil());
    }

    // MantisBT BUG-003 (reportado pero no reproducido): el contador no debe seguir creciendo una
    // vez bloqueada la cuenta, ni siquiera con la contraseña correcta.
    @Test
    void elContadorNoSigueCreciendoTrasElBloqueo() {
        for (int attempt = 1; attempt <= 6; attempt++) {
            assertThrows(InvalidCredentialsException.class,
                    () -> service.login(new LoginRequest("customer@example.com", "Wrong1!pass", null)));
        }
        assertThrows(InvalidCredentialsException.class,
                () -> service.login(new LoginRequest("customer@example.com", "Valid1!pass", null)));

        assertEquals(5, account.getFailedLoginAttempts());
    }

    private void setId(UserAccount account, UUID id) {
        try {
            Field field = UserAccount.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(account, id);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Test
    void adminRecibeRetoMfaSinTokenDeAccesoNiResetDeIntentos() {
        account = new UserAccount("admin@example.com", new BCryptPasswordEncoder().encode("Valid1!pass"),
                "Admin One", UserRole.ADMIN);
        setId(account, UUID.randomUUID());
        account.registerFailedLogin(OffsetDateTime.now(), 10, 15);

        LoginResponse response = service.login(new LoginRequest("admin@example.com", "Valid1!pass", null));

        assertTrue(response.mfaRequired());
        assertTrue(response.mfaSetupRequired());
        assertNotNull(response.mfaToken());
        assertNull(response.accessToken());
        assertEquals(1, account.getFailedLoginAttempts());
    }
}
