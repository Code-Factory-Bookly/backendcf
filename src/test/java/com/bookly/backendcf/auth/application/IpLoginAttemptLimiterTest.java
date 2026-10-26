package com.bookly.backendcf.auth.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class IpLoginAttemptLimiterTest {

    @Test
    void bloqueaLaIpAlAlcanzarElUmbral() {
        IpLoginAttemptLimiter limiter = new IpLoginAttemptLimiter(3, 15);

        limiter.recordFailure("10.0.0.1");
        limiter.recordFailure("10.0.0.1");
        assertDoesNotThrow(() -> limiter.checkAllowed("10.0.0.1"));
        limiter.recordFailure("10.0.0.1");

        assertThrows(TooManyLoginAttemptsException.class, () -> limiter.checkAllowed("10.0.0.1"));
    }

    @Test
    void elBloqueoDeUnaIpNoAfectaALasDemas() {
        IpLoginAttemptLimiter limiter = new IpLoginAttemptLimiter(1, 15);

        limiter.recordFailure("10.0.0.1");

        assertDoesNotThrow(() -> limiter.checkAllowed("10.0.0.2"));
    }

    @Test
    void unLoginExitosoReiniciaElContador() {
        IpLoginAttemptLimiter limiter = new IpLoginAttemptLimiter(2, 15);

        limiter.recordFailure("10.0.0.1");
        limiter.recordSuccess("10.0.0.1");
        limiter.recordFailure("10.0.0.1");

        assertDoesNotThrow(() -> limiter.checkAllowed("10.0.0.1"));
    }

    @Test
    void trasExpirarElBloqueoElContadorEmpiezaDeNuevo() {
        IpLoginAttemptLimiter limiter = new IpLoginAttemptLimiter(2, 0);

        limiter.recordFailure("10.0.0.1");
        limiter.recordFailure("10.0.0.1");
        assertDoesNotThrow(() -> limiter.checkAllowed("10.0.0.1"));

        limiter.recordFailure("10.0.0.1");
        assertDoesNotThrow(() -> limiter.checkAllowed("10.0.0.1"));
    }
}
