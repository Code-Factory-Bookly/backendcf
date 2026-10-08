package com.bookly.backendcf.auth.application;

import com.bookly.backendcf.auth.domain.model.TrustedDevice;
import com.bookly.backendcf.auth.infrastructure.persistence.TrustedDeviceRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * "Confiar en este dispositivo": el token vive en el navegador (localStorage), no en una cookie,
 * y viaja en el body de /auth/login. Se guarda hasheado, igual que los codigos de recuperacion,
 * para que un dump de la base no alcance para suplantar un dispositivo.
 */
@Service
public class DeviceTrustService {

    private static final int TOKEN_BYTES = 32;
    private static final long TRUST_DAYS = 30;

    private final TrustedDeviceRepository repository;
    private final SecureRandom random = new SecureRandom();

    public DeviceTrustService(TrustedDeviceRepository repository) {
        this.repository = repository;
    }

    public String issueToken(UUID userId) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        OffsetDateTime now = OffsetDateTime.now();
        repository.save(new TrustedDevice(userId, hash(token), now, now.plusDays(TRUST_DAYS)));
        return token;
    }

    public void revokeAll(UUID userId) {
        repository.deleteByUserId(userId);
    }

    public boolean isTrusted(UUID userId, String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        byte[] hashed = hash(token).getBytes(StandardCharsets.UTF_8);
        return repository.findByUserIdAndExpiresAtAfter(userId, OffsetDateTime.now()).stream()
                .anyMatch(device -> MessageDigest.isEqual(
                        device.getTokenHash().getBytes(StandardCharsets.UTF_8), hashed));
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
