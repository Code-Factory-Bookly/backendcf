package com.bookly.backendcf.report.application;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/** Controla la expiración por inactividad de la sesión administrativa del reporte. */
@Component
public class ReportSessionActivity {

    private final ConcurrentMap<String, Instant> lastActivity = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Boolean> expiredSessions = new ConcurrentHashMap<>();
    private final Duration inactivity;

    public ReportSessionActivity(
            @Value("${security.report.inactivity-minutes:15}") long inactivityMinutes) {
        this.inactivity = Duration.ofMinutes(inactivityMinutes);
    }

    public void touch(UUID actorId, String sessionKey) {
        String key = sessionKey == null || sessionKey.isBlank() ? actorId.toString() : sessionKey;
        if (expiredSessions.containsKey(key)) {
            throw new AccessDeniedException("La sesión del reporte expiró por inactividad");
        }
        Instant now = Instant.now();
        Instant previous = lastActivity.get(key);
        if (previous != null && previous.plus(inactivity).isBefore(now)) {
            expiredSessions.put(key, Boolean.TRUE);
            throw new AccessDeniedException("La sesión del reporte expiró por inactividad");
        }
        lastActivity.put(key, now);
    }
}
