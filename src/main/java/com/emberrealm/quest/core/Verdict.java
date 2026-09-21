package com.emberrealm.quest.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Collects pass/fail/skip lines produced by a Check or a Step, in insertion order. */
public final class Verdict {

    public enum Kind { PASS, FAIL, SKIP, INFO }

    public record Entry(Kind kind, String text, String hint) {
    }

    private final List<Entry> entries = new ArrayList<>();

    public void pass(String what) {
        entries.add(new Entry(Kind.PASS, what, null));
    }

    public void fail(String what, String hint) {
        entries.add(new Entry(Kind.FAIL, what, hint));
    }

    public void skip(String why) {
        entries.add(new Entry(Kind.SKIP, why, null));
    }

    /** Optional advice; unlike an unavailable verification, it does not block completion. */
    public void info(String what) {
        entries.add(new Entry(Kind.INFO, what, null));
    }

    public void expect(boolean condition, String what, String hint) {
        if (condition) pass(what);
        else fail(what, hint);
    }

    public boolean ok() {
        return entries.stream().anyMatch(e -> e.kind() == Kind.PASS)
                && entries.stream().noneMatch(e -> e.kind() == Kind.FAIL || e.kind() == Kind.SKIP);
    }

    public boolean unavailable() {
        return entries.stream().anyMatch(e -> e.kind() == Kind.SKIP)
                && entries.stream().noneMatch(e -> e.kind() == Kind.FAIL);
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    public void addAll(Verdict other) {
        entries.addAll(other.entries);
    }

    /** A new verdict with only the entries whose text matches. */
    public Verdict filtered(Predicate<String> textMatches) {
        Verdict v = new Verdict();
        for (Entry e : entries) if (textMatches.test(e.text())) v.entries.add(e);
        return v;
    }

    public void print(Console out) {
        for (Entry e : entries) {
            switch (e.kind()) {
                case PASS -> out.ok(e.text());
                case SKIP -> out.warn(e.text());
                case INFO -> out.info(e.text());
                case FAIL -> {
                    out.fail(e.text());
                    if (e.hint() != null && !e.hint().isBlank()) out.hint(e.hint());
                }
            }
        }
    }
}
