package com.bookly.backendcf.professional.application;

import com.bookly.backendcf.auth.application.EmailAlreadyRegisteredException;
import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.domain.model.UserRole;
import com.bookly.backendcf.auth.infrastructure.persistence.UserAccountRepository;
import com.bookly.backendcf.professional.domain.model.Professional;
import com.bookly.backendcf.professional.infrastructure.persistence.ProfessionalRepository;
import com.bookly.backendcf.professional.presentation.dto.ProfessionalRequest;
import com.bookly.backendcf.professional.presentation.dto.ProfessionalResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegisterProfessionalService {

    private final UserAccountRepository userAccountRepository;
    private final ProfessionalRepository professionalRepository;
    private final PasswordEncoder passwordEncoder;

    public RegisterProfessionalService(
            UserAccountRepository userAccountRepository,
            ProfessionalRepository professionalRepository,
            PasswordEncoder passwordEncoder) {
        this.userAccountRepository = userAccountRepository;
        this.professionalRepository = professionalRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public ProfessionalResponse register(ProfessionalRequest request) {
        String normalizedEmail = normalizeEmail(request.email());

        if (userAccountRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyRegisteredException();
        }

        UserAccount account = new UserAccount(
                normalizedEmail,
                passwordEncoder.encode(request.password()),
                normalizeText(request.fullName()),
                UserRole.PROFESSIONAL);

        final UserAccount savedAccount;
        try {
            savedAccount = userAccountRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException exception) {
            throw new EmailAlreadyRegisteredException();
        }

        Professional professional = professionalRepository.saveAndFlush(
                new Professional(savedAccount.getId(), normalizeText(request.specialty())));

        return ProfessionalResponse.from(savedAccount, professional);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private String normalizeText(String text) {
        return text.trim().replaceAll("\\s+", " ");
    }
}