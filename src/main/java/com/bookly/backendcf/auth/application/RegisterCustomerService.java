package com.bookly.backendcf.auth.application;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.infrastructure.persistence.UserAccountRepository;
import com.bookly.backendcf.auth.presentation.dto.RegisterCustomerRequest;
import com.bookly.backendcf.auth.presentation.dto.RegisterCustomerResponse;
import java.util.Set;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegisterCustomerService {

    // SEC-014: lista corta de dominios de correo desechable/temporal mas comunes. No pretende ser
    // exhaustiva (eso requeriria un servicio externo); frena el caso trivial de abuso.
    private static final Set<String> DISPOSABLE_EMAIL_DOMAINS = Set.of(
            "mailinator.com", "tempmail.com", "temp-mail.org", "10minutemail.com",
            "guerrillamail.com", "yopmail.com", "trashmail.com", "throwawaymail.com",
            "fakeinbox.com", "getnada.com", "dispostable.com", "sharklasers.com"
    );

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public RegisterCustomerService(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public RegisterCustomerResponse register(RegisterCustomerRequest request) {
        String normalizedEmail = normalizeEmail(request.email());

        if (isDisposableDomain(normalizedEmail)) {
            throw new DisposableEmailDomainException();
        }

        if (userAccountRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyRegisteredException();
        }

        UserAccount userAccount = new UserAccount(
                normalizedEmail,
                passwordEncoder.encode(request.password()),
                normalizeFullName(request.fullName()));

        try {
            // La restricción UNIQUE de PostgreSQL resuelve la carrera entre dos registros
            // concurrentes; flush permite traducirla aquí al error funcional correcto.
            return RegisterCustomerResponse.from(userAccountRepository.saveAndFlush(userAccount));
        } catch (DataIntegrityViolationException exception) {
            throw new EmailAlreadyRegisteredException();
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private boolean isDisposableDomain(String normalizedEmail) {
        int at = normalizedEmail.lastIndexOf('@');
        if (at < 0) {
            return false;
        }
        return DISPOSABLE_EMAIL_DOMAINS.contains(normalizedEmail.substring(at + 1));
    }

    private String normalizeFullName(String fullName) {
        return fullName.trim().replaceAll("\\s+", " ");
    }
}
