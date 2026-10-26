package com.bookly.backendcf.auth.application;

import java.time.OffsetDateTime;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Bloqueo de login por IP (SEC-006): LoginAttemptRecorder solo bloquea por email, lo que permite
 * credential stuffing rotando correos desde la misma IP. El umbral es mas alto que el de email
 * porque una IP puede representar a varios usuarios legitimos detras de NAT/red compartida.
 *
 * <p>En memoria a proposito: el despliegue actual es de una sola instancia. Si se escala a varias
 * instancias, este estado debe moverse a un almacen compartido (ej. Redis).
 */
@Service
public class IpLoginAttemptLimiter {
    private final int maxAttempts;
    private final long lockMinutes;
    private final ConcurrentHashMap<String, Attempts> attemptsByIp = new ConcurrentHashMap<>();

    public IpLoginAttemptLimiter(
            @Value("${security.login.ip-max-attempts:20}") int maxAttempts,
            @Value("${security.login.ip-lock-minutes:15}") long lockMinutes) {
        this.maxAttempts = maxAttempts;
        this.lockMinutes = lockMinutes;
    }

    public void checkAllowed(String ip) {
        Attempts attempts = attemptsByIp.get(ip);
        if (attempts != null && attempts.isLocked(OffsetDateTime.now())) {
            throw new TooManyLoginAttemptsException();
        }
    }

    public void recordFailure(String ip) {
        attemptsByIp.compute(ip, (key, existing) -> {
            Attempts attempts = existing != null ? existing : new Attempts();
            attempts.registerFailure(OffsetDateTime.now(), maxAttempts, lockMinutes);
            return attempts;
        });
    }

    public void recordSuccess(String ip) {
        attemptsByIp.remove(ip);
    }

    private static final class Attempts {
        private int failedAttempts;
        private OffsetDateTime lockedUntil;

        void registerFailure(OffsetDateTime now, int maxAttempts, long lockMinutes) {
            if (lockedUntil != null && !isLocked(now)) {
                failedAttempts = 0;
                lockedUntil = null;
            }
            failedAttempts++;
            if (failedAttempts >= maxAttempts) {
                lockedUntil = now.plusMinutes(lockMinutes);
            }
        }

        boolean isLocked(OffsetDateTime now) {
            return lockedUntil != null && lockedUntil.isAfter(now);
        }
    }
}
