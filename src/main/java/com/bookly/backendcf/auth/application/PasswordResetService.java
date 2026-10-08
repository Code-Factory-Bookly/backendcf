package com.bookly.backendcf.auth.application;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.infrastructure.persistence.UserAccountRepository;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * No hay recuperacion de contraseña por correo (depende de HU-10, que no existe). Como parche
 * mientras tanto: un ADMIN puede resetear la clave de cualquier usuario a una temporal, y
 * cualquier usuario puede cambiar su propia clave una vez adentro.
 */
@Service
public class PasswordResetService {

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnpqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SPECIAL = "!@#$%*?";
    private static final String ALL = UPPER + LOWER + DIGITS + SPECIAL;
    private static final int LENGTH = 12;

    private final UserAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(UserAccountRepository accounts, PasswordEncoder passwordEncoder) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public String resetToTemporaryPassword(UUID userId) {
        UserAccount account = accounts.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        String temporary = generateTemporaryPassword();
        OffsetDateTime now = OffsetDateTime.now();
        account.updatePassword(passwordEncoder.encode(temporary), now);
        // El motivo mas comun para resetear la clave es justamente que la cuenta quedo bloqueada;
        // sin esto, la clave temporal no serviria de nada hasta que el bloqueo expire solo.
        account.registerSuccessfulLogin(now);
        accounts.save(account);
        return temporary;
    }

    @Transactional
    public void changeOwnPassword(UUID userId, String currentPassword, String newPassword) {
        UserAccount account = accounts.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        if (!passwordEncoder.matches(currentPassword, account.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        account.updatePassword(passwordEncoder.encode(newPassword), OffsetDateTime.now());
        accounts.save(account);
    }

    private String generateTemporaryPassword() {
        StringBuilder seed = new StringBuilder();
        seed.append(UPPER.charAt(random.nextInt(UPPER.length())));
        seed.append(LOWER.charAt(random.nextInt(LOWER.length())));
        seed.append(DIGITS.charAt(random.nextInt(DIGITS.length())));
        seed.append(SPECIAL.charAt(random.nextInt(SPECIAL.length())));
        for (int i = seed.length(); i < LENGTH; i++) {
            seed.append(ALL.charAt(random.nextInt(ALL.length())));
        }
        List<Character> shuffled = new ArrayList<>();
        for (char c : seed.toString().toCharArray()) {
            shuffled.add(c);
        }
        Collections.shuffle(shuffled, random);
        StringBuilder result = new StringBuilder(LENGTH);
        shuffled.forEach(result::append);
        return result.toString();
    }
}
