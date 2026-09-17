package com.bookly.backendcf.platform.application;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.domain.model.UserRole;
import com.bookly.backendcf.auth.infrastructure.persistence.UserAccountRepository;
import com.bookly.backendcf.platform.domain.model.Platform;
import com.bookly.backendcf.platform.infrastructure.persistence.PlatformRepository;
import com.bookly.backendcf.platform.presentation.dto.PlatformSetupRequest;
import com.bookly.backendcf.platform.presentation.dto.PlatformSetupResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformSetupService {

    private final PlatformRepository platformRepository;
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final WelcomeNotificationPort welcomeNotificationPort;

    public PlatformSetupService(
            PlatformRepository platformRepository,
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            WelcomeNotificationPort welcomeNotificationPort) {
        this.platformRepository = platformRepository;
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.welcomeNotificationPort = welcomeNotificationPort;
    }

    /**
     * Aprovisiona la plataforma y su administrador en una sola transacción: si falla cualquiera de
     * las dos escrituras, no queda nada a medias.
     *
     * <p>Solo puede ejecutarse una vez. La base lo garantiza además con un índice único sobre una
     * expresión constante, de modo que dos peticiones simultáneas no puedan colarse ambas.
     */
    @Transactional
    public PlatformSetupResponse setup(PlatformSetupRequest request) {
        if (platformRepository.count() > 0) {
            throw new PlatformAlreadyConfiguredException();
        }

        final Platform platform;
        try {
            // El índice único de platform((true)) es la autoridad ante dos aprovisionamientos
            // simultáneos; flush traduce la violación dentro del caso de uso.
            platform = platformRepository.saveAndFlush(new Platform(normalizeName(request.name())));
        } catch (DataIntegrityViolationException exception) {
            throw new PlatformAlreadyConfiguredException();
        }

        UserAccount adminUser = userAccountRepository.save(new UserAccount(
                normalizeEmail(request.adminEmail()),
                passwordEncoder.encode(request.adminPassword()),
                normalizeFullName(request.adminFullName()),
                UserRole.ADMIN));

        welcomeNotificationPort.sendPlatformWelcome(
                platform.getId(),
                platform.getName(),
                adminUser.getEmail());

        return PlatformSetupResponse.from(platform, adminUser);
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
