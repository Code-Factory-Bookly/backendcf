package com.bookly.backendcf.organization.application;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.domain.model.UserRole;
import com.bookly.backendcf.auth.infrastructure.persistence.UserAccountRepository;
import com.bookly.backendcf.organization.domain.model.Organization;
import com.bookly.backendcf.organization.infrastructure.persistence.OrganizationRepository;
import com.bookly.backendcf.organization.presentation.dto.RegisterOrganizationRequest;
import com.bookly.backendcf.organization.presentation.dto.RegisterOrganizationResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegisterOrganizationService {

    private final OrganizationRepository organizationRepository;
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final WelcomeNotificationPort welcomeNotificationPort;

    public RegisterOrganizationService(
            OrganizationRepository organizationRepository,
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            WelcomeNotificationPort welcomeNotificationPort) {
        this.organizationRepository = organizationRepository;
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.welcomeNotificationPort = welcomeNotificationPort;
    }

    /**
     * Aprovisiona la organización y su administrador en una sola transacción: si falla cualquiera de
     * las dos escrituras, no queda nada a medias.
     *
     * <p>Solo puede ejecutarse una vez. La base lo garantiza además con un índice único sobre una
     * expresión constante, de modo que dos peticiones simultáneas no puedan colarse ambas.
     */
    @Transactional
    public RegisterOrganizationResponse register(RegisterOrganizationRequest request) {
        if (organizationRepository.count() > 0) {
            throw new OrganizationAlreadyExistsException();
        }

        Organization organization = organizationRepository.save(
                new Organization(normalizeName(request.name()), request.taxId().trim()));

        UserAccount adminUser = userAccountRepository.save(new UserAccount(
                normalizeEmail(request.adminEmail()),
                passwordEncoder.encode(request.adminPassword()),
                normalizeFullName(request.adminFullName()),
                UserRole.ADMIN));

        welcomeNotificationPort.sendOrganizationWelcome(
                organization.getId(),
                organization.getName(),
                adminUser.getEmail());

        return RegisterOrganizationResponse.from(organization, adminUser);
    }

    private String normalizeName(String name) {
        return name.trim().replaceAll("\\s+", " ");
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private String normalizeFullName(String fullName) {
        return fullName.trim().replaceAll("\\s+", " ");
    }
}
