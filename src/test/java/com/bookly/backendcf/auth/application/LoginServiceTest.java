package com.bookly.backendcf.auth.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.infrastructure.persistence.UserAccountRepository;
import com.bookly.backendcf.auth.presentation.dto.LoginRequest;
import com.bookly.backendcf.auth.presentation.dto.LoginResponse;
import com.bookly.backendcf.auth.security.JwtTokenService;
import java.util.Optional;
import java.lang.reflect.Proxy;
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
        service = new LoginService(repository, new BCryptPasswordEncoder(),
                new JwtTokenService("test-secret-that-is-at-least-32-bytes-long", 3600),
                new LoginAttemptRecorder(repository), 5, 15);
    }

    @Test
    void loginExitosoEntregaTokenYReiniciaContador() {
        LoginResponse response = service.login(new LoginRequest(" CUSTOMER@EXAMPLE.COM ", "Valid1!pass"));

        assertNotNull(response.accessToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(0, account.getFailedLoginAttempts());
    }

    @Test
    void credencialesIncorrectasDevuelvenErrorYRegistranIntento() {
        assertThrows(InvalidCredentialsException.class,
                () -> service.login(new LoginRequest("customer@example.com", "Wrong1!pass")));

        assertEquals(1, account.getFailedLoginAttempts());
    }

    // MantisBT BUG-004: una cuenta bloqueada no debe distinguirse de una contraseña incorrecta en
    // la respuesta (ambas son InvalidCredentialsException/401), para no revelar que el correo existe.
    @Test
    void quintoIntentoFallidoBloqueaLaCuentaSinDistinguirloEnLaRespuesta() {
        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThrows(InvalidCredentialsException.class,
                    () -> service.login(new LoginRequest("customer@example.com", "Wrong1!pass")));
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
                    () -> service.login(new LoginRequest("customer@example.com", "Wrong1!pass")));
        }
        assertThrows(InvalidCredentialsException.class,
                () -> service.login(new LoginRequest("customer@example.com", "Valid1!pass")));

        assertEquals(5, account.getFailedLoginAttempts());
    }
}
