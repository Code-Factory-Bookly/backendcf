package com.bookly.backendcf.auth.application;

import com.bookly.backendcf.auth.domain.model.MfaRecoveryCode;
import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.infrastructure.persistence.MfaRecoveryCodeRepository;
import com.bookly.backendcf.auth.infrastructure.persistence.UserAccountRepository;
import com.bookly.backendcf.auth.security.JwtTokenService;
import com.bookly.backendcf.auth.security.TotpSecretCipher;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MfaService {

    private static final int RECOVERY_CODE_COUNT = 10;
    private static final int RECOVERY_CODE_HALF_LENGTH = 4;
    private static final String RECOVERY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final UserAccountRepository accounts;
    private final MfaRecoveryCodeRepository recoveryCodes;
    private final TotpSecretCipher cipher;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokens;
    private final LoginAttemptRecorder attempts;
    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final CodeVerifier codeVerifier;
    private final SecureRandom random = new SecureRandom();
    private final int maxAttempts;
    private final long lockMinutes;

    public MfaService(UserAccountRepository accounts, MfaRecoveryCodeRepository recoveryCodes,
                      TotpSecretCipher cipher, PasswordEncoder passwordEncoder, JwtTokenService tokens,
                      LoginAttemptRecorder attempts,
                      @Value("${security.login.max-attempts:5}") int maxAttempts,
                      @Value("${security.login.lock-minutes:15}") long lockMinutes) {
        this.accounts = accounts;
        this.recoveryCodes = recoveryCodes;
        this.cipher = cipher;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.attempts = attempts;
        this.maxAttempts = maxAttempts;
        this.lockMinutes = lockMinutes;
        DefaultCodeVerifier verifier = new DefaultCodeVerifier(new DefaultCodeGenerator(), new SystemTimeProvider());
        verifier.setAllowedTimePeriodDiscrepancy(1);
        this.codeVerifier = verifier;
    }

    public record Enrollment(String secret, String otpauthUri) { }

    public record ConfirmedEnrollment(UserAccount account, List<String> recoveryCodes) { }

    @Transactional
    public Enrollment startEnrollment(String mfaToken) {
        UserAccount account = accountFromToken(mfaToken);
        // Con MFA ya activo, la contraseña sola no basta para reemplazar el segundo factor.
        if (account.isMfaEnabled()) {
            throw new InvalidMfaTokenException();
        }
        String secret = secretGenerator.generate();
        account.beginMfaEnrollment(cipher.encrypt(secret), OffsetDateTime.now());
        accounts.save(account);
        return new Enrollment(secret, otpauthUri(account.getEmail(), secret));
    }

    @Transactional
    public ConfirmedEnrollment confirmEnrollment(String mfaToken, String code) {
        UserAccount account = accountFromToken(mfaToken);
        if (account.isMfaEnabled() || account.getMfaSecretEncrypted() == null) {
            throw new InvalidMfaTokenException();
        }
        ensureNotLocked(account);
        if (!isValidTotp(account, code)) {
            throw failure(account);
        }

        OffsetDateTime now = OffsetDateTime.now();
        account.confirmMfaEnrollment(now);
        accounts.save(account);
        List<String> plainCodes = generateRecoveryCodes(account, now);
        return new ConfirmedEnrollment(account, plainCodes);
    }

    @Transactional
    public UserAccount verify(String mfaToken, String code) {
        UserAccount account = accountFromToken(mfaToken);
        if (!account.isMfaEnabled()) {
            throw new InvalidMfaTokenException();
        }
        ensureNotLocked(account);
        boolean valid = isValidTotp(account, code) || consumeRecoveryCode(account, code);
        if (!valid) {
            throw failure(account);
        }
        account.registerSuccessfulLogin(OffsetDateTime.now());
        return accounts.save(account);
    }

    private UserAccount accountFromToken(String mfaToken) {
        UUID id = tokens.parseMfaPending(mfaToken).orElseThrow(InvalidMfaTokenException::new);
        UserAccount account = accounts.findById(id).orElseThrow(InvalidMfaTokenException::new);
        if (!account.isEnabled()) {
            throw new InvalidMfaTokenException();
        }
        return account;
    }

    private void ensureNotLocked(UserAccount account) {
        if (account.isLocked(OffsetDateTime.now())) {
            throw new AccountLockedException(account.getLockedUntil());
        }
    }

    private boolean isValidTotp(UserAccount account, String code) {
        String secret = cipher.decrypt(account.getMfaSecretEncrypted());
        return codeVerifier.isValidCode(secret, code);
    }

    private boolean consumeRecoveryCode(UserAccount account, String code) {
        String normalized = normalizeRecoveryCode(code);
        for (MfaRecoveryCode candidate : recoveryCodes.findByUserIdAndUsedAtIsNull(account.getId())) {
            if (passwordEncoder.matches(normalized, candidate.getCodeHash())) {
                candidate.markUsed(OffsetDateTime.now());
                recoveryCodes.save(candidate);
                return true;
            }
        }
        return false;
    }

    private InvalidMfaCodeException failure(UserAccount account) {
        attempts.recordFailure(account.getEmail(), maxAttempts, lockMinutes);
        return new InvalidMfaCodeException();
    }

    private List<String> generateRecoveryCodes(UserAccount account, OffsetDateTime now) {
        List<String> plainCodes = new ArrayList<>();
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            String raw = randomRecoveryCode();
            recoveryCodes.save(new MfaRecoveryCode(account.getId(), passwordEncoder.encode(raw), now));
            plainCodes.add(raw.substring(0, RECOVERY_CODE_HALF_LENGTH) + "-" + raw.substring(RECOVERY_CODE_HALF_LENGTH));
        }
        return plainCodes;
    }

    private String randomRecoveryCode() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < RECOVERY_CODE_HALF_LENGTH * 2; i++) {
            builder.append(RECOVERY_ALPHABET.charAt(random.nextInt(RECOVERY_ALPHABET.length())));
        }
        return builder.toString();
    }

    private static String normalizeRecoveryCode(String code) {
        return code.replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
    }

    private static String otpauthUri(String email, String secret) {
        return "otpauth://totp/Bookly:" + URLEncoder.encode(email, StandardCharsets.UTF_8)
                + "?secret=" + secret + "&issuer=Bookly";
    }
}
