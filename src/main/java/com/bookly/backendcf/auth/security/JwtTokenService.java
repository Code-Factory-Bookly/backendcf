package com.bookly.backendcf.auth.security;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Firma y valida los JWT con jjwt (RFC 7519) en lugar de un parser manual (SEC-001): la librería
 * fija el algoritmo a partir de la clave (rechaza "alg":"none" y confusion attacks) y usa un
 * parser JSON real en vez de busquedas con indexOf.
 */
@Service
public class JwtTokenService {
    private static final int MIN_SECRET_LENGTH = 64;

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
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(account.getId().toString())
                .claim("email", account.getEmail())
                .claim("role", account.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expiresInSeconds)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public TokenClaims parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new TokenClaims(claims.getSubject(), claims.get("role", String.class));
        } catch (RuntimeException ignored) {
            // Cualquier token invalido o malformado se trata igual: acceso no autenticado.
            return null;
        }
    }

    public record TokenClaims(String subject, String role) { }
}
