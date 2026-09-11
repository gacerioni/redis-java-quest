package com.emberrealm.quest.core;

import java.util.ArrayList;
import java.util.List;

/** Collects pass/fail lines produced by a Check. */
public final class Verdict {

    private final List<String> passes = new ArrayList<>();
    private final List<String[]> fails = new ArrayList<>();
    private final List<String> skips = new ArrayList<>();

    public void pass(String what) {
        passes.add(what);
    }

    public void fail(String what, String hint) {
        fails.add(new String[]{what, hint});
    }

    public void skip(String why) {
        skips.add(why);
    }

    public void expect(boolean condition, String what, String hint) {
        if (condition) pass(what);
        else fail(what, hint);
    }

    public boolean ok() {
        return fails.isEmpty();
    }

    public void print(Console out) {
        passes.forEach(out::ok);
        for (String[] f : fails) {
            out.fail(f[0]);
            if (f[1] != null && !f[1].isBlank()) out.hint(f[1]);
        }
        skips.forEach(out::warn);
    }
}
