package com.bookly.backendcf.auth.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.domain.model.UserRole;
import java.lang.reflect.Field;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtTokenServiceTest {

    private static final String VALID_SECRET =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private JwtTokenService tokenService;
    private UserAccount account;

    @BeforeEach
    void setUp() {
        tokenService = new JwtTokenService(VALID_SECRET, 3600);
        account = new UserAccount("admin@example.com", "hashed-password", "Admin One", UserRole.ADMIN);
        setId(account, UUID.randomUUID());
    }

    @Test
    void unSecretoVacioNoPermiteCrearElServicio() {
        assertThrows(IllegalArgumentException.class, () -> new JwtTokenService("", 3600));
    }

    @Test
    void unSecretoMenorA64CaracteresNoPermiteCrearElServicio() {
        assertThrows(IllegalArgumentException.class, () -> new JwtTokenService("secreto-corto", 3600));
    }

    @Test
    void exponeElTiempoDeExpiracionConfigurado() {
        assertEquals(3600, tokenService.getExpiresInSeconds());
    }

    @Test
    void unTokenValidoSeParseaConElMismoSubjectYRol() {
        String token = tokenService.createToken(account);

        JwtTokenService.TokenClaims claims = tokenService.parse(token);

        assertNotNull(claims);
        assertEquals(account.getId().toString(), claims.subject());
        assertEquals("ADMIN", claims.role());
    }

    @Test
    void unTokenConFirmaAlteradaSeRechaza() {
        String token = tokenService.createToken(account);
        String tampered = token.substring(0, token.length() - 1) + (token.endsWith("A") ? "B" : "A");

        assertNull(tokenService.parse(tampered));
    }

    @Test
    void unTokenConPayloadAlteradoSeRechazaPorFirmaInvalida() {
        String token = tokenService.createToken(account);
        String[] parts = token.split("\\.", -1);
        String tamperedPayload = parts[1].substring(0, parts[1].length() - 1) + (parts[1].endsWith("A") ? "B" : "A");

        assertNull(tokenService.parse(parts[0] + "." + tamperedPayload + "." + parts[2]));
    }

    @Test
    void unTokenExpiradoSeRechaza() {
        JwtTokenService expiredService = new JwtTokenService(VALID_SECRET, -10);
        String token = expiredService.createToken(account);

        assertNull(expiredService.parse(token));
    }

    @Test
    void unTokenFirmadoConOtroSecretoSeRechaza() {
        JwtTokenService otherService = new JwtTokenService(
                "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210", 3600);
        String token = otherService.createToken(account);

        assertNull(tokenService.parse(token));
    }

    @Test
    void unTokenConEstructuraInvalidaSeRechaza() {
        assertNull(tokenService.parse("token-sin-puntos"));
        assertNull(tokenService.parse("solo.dospartes"));
        assertNull(tokenService.parse("demasiadas.partes.en.este.token"));
    }

    // SEC-001 vector 1: un token con "alg":"none" y firma vacia (el ataque clasico de
    // algorithm confusion) debe rechazarse igual que cualquier firma invalida.
    @Test
    void unTokenConAlgNoneYFirmaVaciaSeRechaza() {
        String header = base64("{\"alg\":\"none\",\"typ\":\"JWT\"}");
        String payload = base64("{\"sub\":\"" + account.getId() + "\",\"role\":\"ADMIN\",\"exp\":9999999999}");
        String forged = header + "." + payload + ".";

        assertNull(tokenService.parse(forged));
    }

    private static String base64(String value) {
        return java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void unTokenConCaracteresNoBase64NoLanzaExcepcionYSeRechaza() {
        assertNull(tokenService.parse("!!!.###.$$$"));
    }

    private void setId(UserAccount account, UUID id) {
        try {
            Field field = UserAccount.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(account, id);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
