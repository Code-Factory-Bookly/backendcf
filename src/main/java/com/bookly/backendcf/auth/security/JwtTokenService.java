package com.bookly.backendcf.auth.security;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {
    private static final long MFA_PENDING_SECONDS = 300;
    private static final String MFA_PENDING_PREFIX = "{\"typ\":\"mfa\",";

    private final byte[] secret;
    private final long expiresInSeconds;

    public JwtTokenService(@Value("${security.jwt.secret:}") String secret,
                           @Value("${security.jwt.expiration-seconds:3600}") long expiresInSeconds) {
        if (secret.isBlank()) {
            throw new IllegalArgumentException("JWT_SECRET debe estar configurado y ser persistente");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("security.jwt.secret debe tener al menos 32 bytes");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.expiresInSeconds = expiresInSeconds;
    }

    public long getExpiresInSeconds() { return expiresInSeconds; }

    public String createToken(UserAccount account) {
        return issue(account, expiresInSeconds, null);
    }

    public String createMfaPendingToken(UserAccount account) {
        return issue(account, MFA_PENDING_SECONDS, MFA_PENDING_PREFIX);
    }

    public TokenClaims parse(String token) {
        try {
            String payload = verifiedPayload(token);
            if (payload == null || payload.startsWith(MFA_PENDING_PREFIX)) return null;
            String subject = value(payload, "sub");
            String role = value(payload, "role");
            long exp = Long.parseLong(value(payload, "exp"));
            if (Instant.now().getEpochSecond() >= exp) return null;
            return new TokenClaims(subject, role);
        } catch (Exception ignored) { return null; }
    }

    public Optional<UUID> parseMfaPending(String token) {
        try {
            String payload = verifiedPayload(token);
            if (payload == null || !payload.startsWith(MFA_PENDING_PREFIX)) return Optional.empty();
            long exp = Long.parseLong(value(payload, "exp"));
            if (Instant.now().getEpochSecond() >= exp) return Optional.empty();
            return Optional.of(UUID.fromString(value(payload, "sub")));
        } catch (Exception ignored) { return Optional.empty(); }
    }

    private String issue(UserAccount account, long ttlSeconds, String payloadPrefix) {
        long expiry = Instant.now().getEpochSecond() + ttlSeconds;
        String header = encode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String body = "\"sub\":\"" + account.getId() + "\",\"email\":\""
                + escape(account.getEmail()) + "\",\"role\":\"" + account.getRole() + "\",\"exp\":" + expiry + "}";
        String payload = encode((payloadPrefix == null ? "{" : payloadPrefix) + body);
        String unsigned = header + "." + payload;
        return unsigned + "." + sign(unsigned);
    }

    private String verifiedPayload(String token) {
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3 || !MessageDigest.isEqual(sign(parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII),
                parts[2].getBytes(StandardCharsets.US_ASCII))) return null;
        return new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) { throw new IllegalStateException("No se pudo firmar el token", exception); }
    }
    private String encode(String value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8)); }
    private String escape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
    private String value(String json, String key) {
        String prefix = "\"" + key + "\":";
        int start = json.indexOf(prefix) + prefix.length();
        if (start <= prefix.length() - 1) throw new IllegalArgumentException();
        if (json.charAt(start) == '\"') start++;
        int end = json.indexOf(json.charAt(start - 1) == '\"' ? '\"' : ',', start);
        if (end < 0) end = json.indexOf('}', start);
        return json.substring(start, end);
    }
    public record TokenClaims(String subject, String role) { }
}
