package com.bookly.backendcf.auth.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookly.backendcf.auth.domain.model.MfaRecoveryCode;
import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.domain.model.UserRole;
import com.bookly.backendcf.auth.infrastructure.persistence.MfaRecoveryCodeRepository;
import com.bookly.backendcf.auth.infrastructure.persistence.UserAccountRepository;
import com.bookly.backendcf.auth.security.JwtTokenService;
import com.bookly.backendcf.auth.security.TotpSecretCipher;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class MfaServiceTest {

    private static final String SECRET_KEY = "0123456789abcdef0123456789abcdef";
    private static final String ENCRYPTION_KEY = Base64.getEncoder().encodeToString(SECRET_KEY.getBytes());

    private UserAccountRepository accounts;
    private MfaRecoveryCodeRepository recoveryCodes;
    private LoginAttemptRecorder attempts;
    private TotpSecretCipher cipher;
    private JwtTokenService tokens;
    private MfaService service;
    private UserAccount admin;

    @BeforeEach
    void setUp() {
        accounts = mock(UserAccountRepository.class);
        recoveryCodes = mock(MfaRecoveryCodeRepository.class);
        attempts = mock(LoginAttemptRecorder.class);
        cipher = new TotpSecretCipher(ENCRYPTION_KEY);
        tokens = new JwtTokenService(SECRET_KEY, 3600);
        service = new MfaService(accounts, recoveryCodes, cipher, new BCryptPasswordEncoder(), tokens,
                attempts, 5, 15);

        admin = new UserAccount("admin@example.com", "hash", "Admin", UserRole.ADMIN);
        ReflectionTestUtils.setField(admin, "id", UUID.randomUUID());
        when(accounts.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(accounts.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(recoveryCodes.save(any(MfaRecoveryCode.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void enrolamientoConCodigoValidoActivaTotpYEntregaDiezCodigosDeRecuperacion() {
        String mfaToken = tokens.createMfaPendingToken(admin);
        MfaService.Enrollment enrollment = service.startEnrollment(mfaToken);
        String code = currentCode(enrollment.secret());

        MfaService.ConfirmedEnrollment confirmed = service.confirmEnrollment(mfaToken, code);

        assertTrue(admin.isMfaEnabled());
        assertEquals(10, confirmed.recoveryCodes().size());
        confirmed.recoveryCodes().forEach(
                recovery -> assertTrue(recovery.matches("[A-Z2-9]{4}-[A-Z2-9]{4}"), recovery));
        assertTrue(enrollment.otpauthUri().startsWith("otpauth://totp/Bookly:"));
    }

    @Test
    void noSePuedeReenrolarConSoloLaContrasena() {
        admin.confirmMfaEnrollment(OffsetDateTime.now());
        String mfaToken = tokens.createMfaPendingToken(admin);

        assertThrows(InvalidMfaTokenException.class, () -> service.startEnrollment(mfaToken));
    }

    @Test
    void codigoIncorrectoEnVerificacionRegistraIntentoFallido() {
        String secret = new DefaultSecretGenerator().generate();
        admin.beginMfaEnrollment(cipher.encrypt(secret), OffsetDateTime.now());
        admin.confirmMfaEnrollment(OffsetDateTime.now());
        String mfaToken = tokens.createMfaPendingToken(admin);

        assertThrows(InvalidMfaCodeException.class, () -> service.verify(mfaToken, "000000"));

        verify(attempts).recordFailure(eq("admin@example.com"), eq(5), eq(15L));
    }

    @Test
    void codigoDeRecuperacionDeUsoUnicoFunciona() {
        String secret = new DefaultSecretGenerator().generate();
        admin.beginMfaEnrollment(cipher.encrypt(secret), OffsetDateTime.now());
        admin.confirmMfaEnrollment(OffsetDateTime.now());
        String hash = new BCryptPasswordEncoder().encode("ABCD2345");
        MfaRecoveryCode recovery = new MfaRecoveryCode(admin.getId(), hash, OffsetDateTime.now());
        when(recoveryCodes.findByUserIdAndUsedAtIsNull(admin.getId())).thenReturn(List.of(recovery));
        String mfaToken = tokens.createMfaPendingToken(admin);

        UserAccount verified = service.verify(mfaToken, "abcd-2345");

        assertEquals(admin.getId(), verified.getId());
        assertTrue(recovery.isUsed());
        assertFalse(verified.isLocked(OffsetDateTime.now()));
    }

    private String currentCode(String secret) {
        try {
            return new DefaultCodeGenerator().generate(secret, Math.floorDiv(System.currentTimeMillis() / 1000L, 30L));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
