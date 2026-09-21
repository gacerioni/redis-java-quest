package com.emberrealm.quest.lessons.l301_05;

/** Count transitions only after successful writes, not on initialization or a notification alone. */
final class FailoverEvidence {
    private final String preferred;
    private final String alternate;
    private String previous;
    private boolean failover;
    private boolean failback;

    FailoverEvidence(String preferred, String alternate) {
        this.preferred = preferred;
        this.alternate = alternate;
    }

    void observe(String endpoint) {
        if (preferred.equals(previous) && alternate.equals(endpoint)) failover = true;
        if (failover && alternate.equals(previous) && preferred.equals(endpoint)) failback = true;
        previous = endpoint;
    }

    boolean failoverObserved() { return failover; }
    boolean failbackObserved() { return failback; }
    boolean complete() { return failover && failback; }
}
