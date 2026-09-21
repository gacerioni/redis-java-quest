package com.emberrealm.quest.lessons.l301_03;

import org.junit.jupiter.api.Test;
import java.net.URI;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class TlsValidationTest {
    @Test void plaintextUrlIsRejectedBeforeOpeningAConnection() {
        assertThrows(IllegalArgumentException.class, () -> JedisLab.requireTlsUrl("redis://localhost:6379"));
        assertThrows(IllegalArgumentException.class, () -> JedisLab.requireTlsUrl("rediss:///missing-host"));
        assertEquals("localhost", JedisLab.requireTlsUrl("rediss://localhost:6380").getHost());
    }

    @Test void bothTruststorePathsRetainHostnameVerification() {
        var config = JedisLab.configFrom(URI.create("rediss://default:secret@localhost:6380")).build();
        assertTrue(config.isSsl());
        assertEquals("HTTPS", config.getSslParameters().getEndpointIdentificationAlgorithm());
    }

    @Test void oldFalsePositiveAndUnverifiedMarkersAreRejected() {
        assertFalse(LessonCheck.validatedTls(Map.of("tls", "ok", "scheme", "redis")));
        assertFalse(LessonCheck.validatedTls(Map.of("tls", "ok", "scheme", "rediss")));
        assertFalse(LessonCheck.validatedTls(Map.of("status", "running", "tls", "ok", "scheme", "rediss", "tls_schema", "2", "hostname_verified", "true")));
        assertTrue(LessonCheck.validatedTls(Map.of("status", "ran", "tls", "ok", "scheme", "rediss", "tls_schema", "2", "hostname_verified", "true")));
    }
}
