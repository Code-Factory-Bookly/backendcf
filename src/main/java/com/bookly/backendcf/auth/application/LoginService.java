package com.bookly.backendcf.auth.application;

import com.bookly.backendcf.auth.domain.model.UserAccount;
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
    private final int maxAttempts;
    private final long lockMinutes;

    public LoginService(UserAccountRepository repository, PasswordEncoder passwordEncoder,
                        JwtTokenService tokenService,
                        LoginAttemptRecorder loginAttemptRecorder,
                        @Value("${security.login.max-attempts:5}") int maxAttempts,
                        @Value("${security.login.lock-minutes:15}") long lockMinutes) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.loginAttemptRecorder = loginAttemptRecorder;
        this.maxAttempts = maxAttempts;
        this.lockMinutes = lockMinutes;
    }

    public LoginResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        UserAccount account = repository.findByEmail(email).orElseThrow(InvalidCredentialsException::new);
        OffsetDateTime now = OffsetDateTime.now();

        if (!account.isEnabled()) {
            throw new InvalidCredentialsException();
        }
        if (account.isLocked(now)) {
            throw new AccountLockedException(account.getLockedUntil());
        }
        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            UserAccount updatedAccount = loginAttemptRecorder.recordFailure(email, maxAttempts, lockMinutes);
            if (updatedAccount.isLocked(OffsetDateTime.now())) {
                throw new AccountLockedException(updatedAccount.getLockedUntil());
            }
            throw new InvalidCredentialsException();
        }

        account.registerSuccessfulLogin(now);
        repository.save(account);
        return new LoginResponse(tokenService.createToken(account), "Bearer", tokenService.getExpiresInSeconds(),
                new LoginResponse.UserSummary(account.getId(), account.getEmail(), account.getFullName(), account.getRole()));
    }
}
