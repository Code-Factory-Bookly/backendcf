package com.bookly.backendcf.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.infrastructure.persistence.UserAccountRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class PasswordResetServiceTest {

    private UserAccountRepository accounts;
    private PasswordResetService service;
    private UserAccount account;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        accounts = mock(UserAccountRepository.class);
        service = new PasswordResetService(accounts, new BCryptPasswordEncoder());
        account = new UserAccount("usuario@example.com", new BCryptPasswordEncoder().encode("Actual1!pass"), "Usuario");
        when(accounts.findById(userId)).thenReturn(Optional.of(account));
        when(accounts.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void generaUnaClaveTemporalQueCumpleLaComplejidadYQuedaCifrada() {
        String temporary = service.resetToTemporaryPassword(userId);

        assertThat(temporary).hasSize(12);
        assertThat(temporary).containsPattern("[A-Z]").containsPattern("[a-z]")
                .containsPattern("[0-9]").containsPattern("[^A-Za-z0-9]");
        assertThat(new BCryptPasswordEncoder().matches(temporary, account.getPasswordHash())).isTrue();
        verify(accounts).save(account);
    }

    @Test
    void resetDeClaveDesbloqueaUnaCuentaBloqueada() {
        account.registerFailedLogin(OffsetDateTime.now(), 1, 15);
        assertThat(account.isLocked(OffsetDateTime.now())).isTrue();

        service.resetToTemporaryPassword(userId);

        assertThat(account.isLocked(OffsetDateTime.now())).isFalse();
        assertThat(account.getFailedLoginAttempts()).isZero();
    }

    @Test
    void resetDeClaveFallaSiElUsuarioNoExiste() {
        when(accounts.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetToTemporaryPassword(userId))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void cambiaLaClavePropiaConLaContrasenaActualCorrecta() {
        service.changeOwnPassword(userId, "Actual1!pass", "Nueva1!pass");

        assertThat(new BCryptPasswordEncoder().matches("Nueva1!pass", account.getPasswordHash())).isTrue();
        verify(accounts).save(account);
    }

    @Test
    void rechazaElCambioDeClaveConLaContrasenaActualIncorrecta() {
        assertThatThrownBy(() -> service.changeOwnPassword(userId, "Incorrecta1!", "Nueva1!pass"))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
