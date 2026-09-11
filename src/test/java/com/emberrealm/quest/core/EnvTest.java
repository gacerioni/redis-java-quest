package com.emberrealm.quest.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EnvTest {

    @Test
    void userFromUrlReadsTheUserBeforeTheColon() {
        assertEquals("aluno07", Env.userFromUrl("redis://aluno07:s3cret@host:12345"));
        assertEquals("default", Env.userFromUrl("rediss://default:pw@host:1"));
        assertNull(Env.userFromUrl("redis://localhost:6379"));
    }

    @Test
    void redactedHidesThePassword() {
        assertEquals("redis://default:****@host:12345", Env.redacted("redis://default:s3cret@host:12345"));
        assertEquals("redis://localhost:6379", Env.redacted("redis://localhost:6379"));
    }

    @Test
    void sanitizeKeepsPrefixesSafeForKeys() {
        assertEquals("aluno-07-", Env.sanitize("Aluno 07!"));
        assertEquals("quest", Env.sanitize("   "));
        assertEquals("gabs_bank", Env.sanitize("gabs_bank"));
    }
}
