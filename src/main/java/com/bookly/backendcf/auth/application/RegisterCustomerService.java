package com.bookly.backendcf.auth.application;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.infrastructure.persistence.UserAccountRepository;
import com.bookly.backendcf.auth.presentation.dto.RegisterCustomerRequest;
import com.bookly.backendcf.auth.presentation.dto.RegisterCustomerResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegisterCustomerService {

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

    private String normalizeFullName(String fullName) {
        return fullName.trim().replaceAll("\\s+", " ");
    }
}
