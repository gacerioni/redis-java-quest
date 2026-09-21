package com.emberrealm.quest.lessons.l301_04;

import com.emberrealm.quest.core.Console;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Keys;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.exceptions.JedisConnectionException;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class IdempotentRetryTest {
    @Test void lostReplyAfterCommitRetriesTheSameIdWithoutDuplicatingItsEffect() throws Exception {
        var storedIds = new HashSet<String>();
        var attempts = new AtomicInteger();
        var ctx = new Ctx(new Keys("test"), new Console(new PrintStream(new ByteArrayOutputStream()), false), "301-04", "jedis");
        long added = JedisLab.withRetry(ctx, 3, 0, () -> {
            boolean firstAddition = storedIds.add("op-1");
            if (attempts.incrementAndGet() == 1) throw new JedisConnectionException("reply lost after SADD committed");
            return firstAddition ? 1L : 0L;
        });
        assertEquals(2, attempts.get());
        assertEquals(1, storedIds.size());
        assertEquals(0, added, "the retried SADD reports an existing member, not a second operation");
    }
}
