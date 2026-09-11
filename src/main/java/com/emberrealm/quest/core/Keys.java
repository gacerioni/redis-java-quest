package com.emberrealm.quest.core;

/** Builds namespaced keys: prefix:part:part. Every key a lesson touches goes through here. */
public record Keys(String prefix) {

    public String of(String... parts) {
        return prefix + ":" + String.join(":", parts);
    }

    /** Glob pattern matching every key of this student, for SCAN. */
    public String pattern() {
        return prefix + ":*";
    }
}
