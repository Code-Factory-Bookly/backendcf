package com.bookly.backendcf.auth.security;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Firma y valida los JWT con jjwt (RFC 7519) en lugar de un parser manual (SEC-001): la librería
 * fija el algoritmo a partir de la clave (rechaza "alg":"none" y confusion attacks) y usa un
 * parser JSON real en vez de busquedas con indexOf.
 *
 * <p>Los tokens intermedios de MFA (ADR-010) llevan el claim "typ":"mfa" y una vida corta; parse()
 * los rechaza y parseMfaPending() exige exactamente ese claim, para que un token pendiente de
 * verificacion nunca sirva como sesion completa.
 */
@Service
public class JwtTokenService {
    private static final int MIN_SECRET_LENGTH = 64;
    private static final long MFA_PENDING_SECONDS = 300;
    private static final String TYPE_CLAIM = "typ";
    private static final String MFA_PENDING_TYPE = "mfa";

    private final SecretKey key;
    private final long expiresInSeconds;

    public JwtTokenService(@Value("${security.jwt.secret:}") String secret,
                           @Value("${security.jwt.expiration-seconds:3600}") long expiresInSeconds) {
        if (secret.isBlank()) {
            throw new IllegalArgumentException("JWT_SECRET debe estar configurado y ser persistente");
        }
        if (secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalArgumentException(
                    "security.jwt.secret debe tener al menos " + MIN_SECRET_LENGTH + " caracteres");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiresInSeconds = expiresInSeconds;
    }

    public long getExpiresInSeconds() { return expiresInSeconds; }

    public String createToken(UserAccount account) {
        return issue(account, expiresInSeconds, null);
    }

    public String createMfaPendingToken(UserAccount account) {
        return issue(account, MFA_PENDING_SECONDS, MFA_PENDING_TYPE);
    }

    public TokenClaims parse(String token) {
        Claims claims = verifiedClaims(token);
        if (claims == null || MFA_PENDING_TYPE.equals(claims.get(TYPE_CLAIM, String.class))) return null;
        return new TokenClaims(claims.getSubject(), claims.get("role", String.class));
    }

    public Optional<UUID> parseMfaPending(String token) {
        Claims claims = verifiedClaims(token);
        if (claims == null || !MFA_PENDING_TYPE.equals(claims.get(TYPE_CLAIM, String.class))) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(claims.getSubject()));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private String issue(UserAccount account, long ttlSeconds, String mfaType) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(account.getId().toString())
                .claim("email", account.getEmail())
                .claim("role", account.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)));
        if (mfaType != null) {
            builder.claim(TYPE_CLAIM, mfaType);
        }
        return builder.signWith(key, Jwts.SIG.HS256).compact();
    }

    private Claims verifiedClaims(String token) {
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (RuntimeException ignored) {
            // Cualquier token invalido o malformado se trata igual: acceso no autenticado.
            return null;
        }
    }

    public record TokenClaims(String subject, String role) { }
}
