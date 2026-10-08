package com.bookly.backendcf.auth.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class TotpSecretCipherTest {

    private static final String KEY_A = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
    private static final String KEY_B = Base64.getEncoder().encodeToString("fedcba9876543210fedcba9876543210".getBytes());

    @Test
    void cifraYDescifraRecuperandoElMismoSecreto() {
        TotpSecretCipher cipher = new TotpSecretCipher(KEY_A);

        String encrypted = cipher.encrypt("JBSWY3DPEHPK3PXP");

        assertEquals("JBSWY3DPEHPK3PXP", cipher.decrypt(encrypted));
    }

    @Test
    void elMismoTextoNoProduceElMismoCifrado() {
        TotpSecretCipher cipher = new TotpSecretCipher(KEY_A);

        assertNotEquals(cipher.encrypt("JBSWY3DPEHPK3PXP"), cipher.encrypt("JBSWY3DPEHPK3PXP"));
    }

    @Test
    void unaClaveDistintaNoPuedeDescifrar() {
        String encrypted = new TotpSecretCipher(KEY_A).encrypt("JBSWY3DPEHPK3PXP");
        TotpSecretCipher otherKey = new TotpSecretCipher(KEY_B);

        assertThrows(IllegalStateException.class, () -> otherKey.decrypt(encrypted));
    }

    @Test
    void rechazaUnaClaveVaciaOCorta() {
        assertThrows(IllegalArgumentException.class, () -> new TotpSecretCipher(""));
        assertThrows(IllegalArgumentException.class,
                () -> new TotpSecretCipher(Base64.getEncoder().encodeToString("corta".getBytes())));
    }
}
