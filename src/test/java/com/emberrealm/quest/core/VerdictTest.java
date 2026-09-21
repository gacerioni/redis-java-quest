package com.emberrealm.quest.core;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class VerdictTest {
    @Test void failureIsNotHiddenByAnUnavailableLessonInEitherOrder() {
        assertEquals(1, Main.mergeExitCodes(1, 3));
        assertEquals(1, Main.mergeExitCodes(3, 1));
        assertEquals(3, Main.mergeExitCodes(0, 3));
        assertEquals(0, Main.mergeExitCodes(0, 0));
    }
    @Test void missingEnvironmentCannotBecomeDoneEvenWithOtherPassingChecks() {
        Verdict v = new Verdict();
        v.pass("marker exists");
        v.skip("TLS endpoint missing");
        assertFalse(v.ok());
        assertTrue(v.unavailable());
        assertEquals("unavailable", StepEngine.stateFor(v));
        assertFalse(StepEngine.settled("unavailable"));
    }

    @Test void tryingTheOtherClientIsOptional() {
        Verdict v = new Verdict();
        v.pass("Jedis result verified");
        v.info("try Lettuce too");
        assertTrue(v.ok());
        assertEquals("done", StepEngine.stateFor(v));
    }

    @Test void failedSolveOrEmptyVerificationCannotMarkDone() {
        Verdict v = new Verdict();
        assertFalse(v.ok());
        v.fail("wrong value", "retry");
        assertEquals("pending", StepEngine.stateFor(v));
        assertFalse(StepEngine.settled("pending"));
    }

    @Test void explicitSkipAllowsNavigationButDoesNotCountAsVerified() {
        assertTrue(StepEngine.settled("skipped"));
        var lesson = Lessons.byId("101-01").orElseThrow();
        assertArrayEquals(new int[]{1, 3}, StepEngine.counts(new Keys("test"), lesson,
                Map.of("lab", "done", "mexa", "skipped", "codigo", "pending")));
    }
}
