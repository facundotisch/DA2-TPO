package logired.backend.usuario.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginRateLimiterTests {

    @Test
    void activaYLiberaElLimiteAlAlcanzarElUmbral() {
        LoginRateLimiter limiter = new LoginRateLimiter(2, 60);

        assertEquals(0, limiter.retryAfterSeconds("192.0.2.1"));
        assertEquals(0, limiter.recordFailure("192.0.2.1"));
        assertTrue(limiter.recordFailure("192.0.2.1") > 0);
        assertTrue(limiter.retryAfterSeconds("192.0.2.1") > 0);

        limiter.clear("192.0.2.1");

        assertEquals(0, limiter.retryAfterSeconds("192.0.2.1"));
    }

    @Test
    void contabilizaDireccionesDeFormaIndependiente() {
        LoginRateLimiter limiter = new LoginRateLimiter(1, 60);

        assertTrue(limiter.recordFailure("192.0.2.10") > 0);
        assertEquals(0, limiter.retryAfterSeconds("192.0.2.11"));
    }
}
