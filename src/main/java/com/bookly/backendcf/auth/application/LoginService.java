package com.bookly.backendcf.auth.application;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.domain.model.UserRole;
import com.bookly.backendcf.auth.infrastructure.persistence.UserAccountRepository;
import com.bookly.backendcf.auth.presentation.dto.LoginRequest;
import com.bookly.backendcf.auth.presentation.dto.LoginResponse;
import com.bookly.backendcf.auth.security.JwtTokenService;
import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class LoginService {
    private final UserAccountRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokenService;
    private final LoginAttemptRecorder loginAttemptRecorder;
    private final DeviceTrustService deviceTrustService;
    private final int maxAttempts;
    private final long lockMinutes;

    public LoginService(UserAccountRepository repository, PasswordEncoder passwordEncoder,
                        JwtTokenService tokenService,
                        LoginAttemptRecorder loginAttemptRecorder,
                        DeviceTrustService deviceTrustService,
                        @Value("${security.login.max-attempts:5}") int maxAttempts,
                        @Value("${security.login.lock-minutes:15}") long lockMinutes) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.loginAttemptRecorder = loginAttemptRecorder;
        this.deviceTrustService = deviceTrustService;
        this.maxAttempts = maxAttempts;
        this.lockMinutes = lockMinutes;
    }

    public LoginResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        UserAccount account = repository.findByEmail(email).orElseThrow(InvalidCredentialsException::new);
        OffsetDateTime now = OffsetDateTime.now();

        if (!account.isEnabled() || account.isLocked(now)) {
            throw new InvalidCredentialsException();
        }
        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            loginAttemptRecorder.recordFailure(email, maxAttempts, lockMinutes);
            throw new InvalidCredentialsException();
        }

        if (account.getRole() == UserRole.ADMIN
                && !deviceTrustService.isTrusted(account.getId(), request.deviceToken())) {
            // El contador de intentos no se resetea aqui: solo el segundo factor lo hace (ADR-010).
            return LoginResponse.mfaChallenge(tokenService, account);
        }
        account.registerSuccessfulLogin(now);
        repository.save(account);
        return LoginResponse.authenticated(tokenService, account);
    }
}
