package com.emberrealm.quest.core;

import java.util.List;

/**
 * One small, verifiable step of a lesson, with the Instruqt style lifecycle:
 * setup (prepare state), verify (did the student do it?), solve (do it on the student's behalf).
 * Skip is solve plus a "skipped" flag in the progress hash.
 */
public abstract class Step {

    public final String id;
    public final String title;
    public final String instruction;
    public final String command;

    protected Step(String id, String title, String instruction, String command) {
        this.id = id;
        this.title = title;
        this.instruction = instruction;
        this.command = command;
    }

    /** Prepares whatever this step needs before the student starts. Idempotent. Default: nothing. */
    public void setup(Ctx ctx) throws Exception {
    }

    /** Checks the outcome of the step and explains what is missing. */
    public abstract void verify(Ctx ctx, Verdict verdict) throws Exception;

    /** Reaches the end state of the step for the student. */
    public abstract void solve(Ctx ctx) throws Exception;

    /** Keys to remove when the lesson is reset. Default: none. */
    public List<String> cleanupKeys(Ctx ctx) {
        return List.of();
    }

    /** The instruction with the real key prefix in place of {p}. */
    public String instructionFor(Ctx ctx) {
        return instruction.replace("{p}", ctx.keys.prefix());
    }

    public String commandFor(Ctx ctx) {
        return command == null ? null : command.replace("{p}", ctx.keys.prefix());
    }
}
