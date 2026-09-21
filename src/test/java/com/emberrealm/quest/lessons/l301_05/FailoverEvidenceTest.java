package com.emberrealm.quest.lessons.l301_05;

import com.emberrealm.quest.core.Verdict;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class FailoverEvidenceTest {
    @Test void failedNewAttemptInvalidatesEarlierObservedTransitions() {
        Verdict v = new Verdict();
        LessonCheck.verifyEvidence(Map.of("status", "running", "heartbeat", "ok", "beats_ok", "40",
                "failover", "observed", "failback", "observed"), v);
        assertFalse(v.ok());
        assertFalse(v.unavailable());
        assertTrue(v.entries().stream().anyMatch(entry -> entry.kind() == Verdict.Kind.FAIL));
    }

    @Test void healthyHeartbeatDoesNotProveFailover() {
        var evidence = new FailoverEvidence("east", "west");
        evidence.observe("east");
        evidence.observe("east");
        assertFalse(evidence.failoverObserved());
        Verdict v = new Verdict();
        LessonCheck.verifyEvidence(Map.of("status", "ran", "heartbeat", "ok", "beats_ok", "40", "failover", "not_observed", "failback", "not_observed"), v);
        assertFalse(v.ok());
        assertTrue(v.unavailable());
    }

    @Test void startingOnAlternateAndReturningDoesNotInventAnObservedFailover() {
        var evidence = new FailoverEvidence("east", "west");
        evidence.observe("west");
        evidence.observe("east");
        assertFalse(evidence.failoverObserved());
        assertFalse(evidence.failbackObserved());
    }

    @Test void successfulWritesOnBothTransitionsProveFailoverAndFailback() {
        var evidence = new FailoverEvidence("east", "west");
        evidence.observe("east");
        evidence.observe("west");
        assertTrue(evidence.failoverObserved());
        assertFalse(evidence.complete());
        evidence.observe("east");
        assertTrue(evidence.complete());
        Verdict v = new Verdict();
        LessonCheck.verifyEvidence(Map.of("status", "ran", "heartbeat", "ok", "beats_ok", "40", "failover", "observed", "failback", "observed"), v);
        assertTrue(v.ok());
    }
}
