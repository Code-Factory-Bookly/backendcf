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

        // No se distingue el motivo del rechazo (correo inexistente, cuenta deshabilitada,
        // bloqueada o contraseña incorrecta): revelarlo permitiria a un tercero enumerar que
        // correos tienen cuenta en la plataforma observando codigos de respuesta distintos
        // (MantisBT BUG-004). El bloqueo se sigue aplicando igual, solo que en silencio.
        if (!account.isEnabled() || account.isLocked(now)) {
            throw new InvalidCredentialsException();
        }
        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            loginAttemptRecorder.recordFailure(email, maxAttempts, lockMinutes);
            throw new InvalidCredentialsException();
        }

        account.registerSuccessfulLogin(now);
        repository.save(account);
        return new LoginResponse(tokenService.createToken(account), "Bearer", tokenService.getExpiresInSeconds(),
                new LoginResponse.UserSummary(account.getId(), account.getEmail(), account.getFullName(), account.getRole()));
    }
}
