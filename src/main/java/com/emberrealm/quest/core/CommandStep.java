package com.emberrealm.quest.core;

import java.util.List;

/**
 * "Mexa no Redis": the student does something directly in Redis (Redis Insight Workbench or redis-cli),
 * the lesson verifies the state, and solve runs the same command on the student's behalf.
 */
public final class CommandStep extends Step {

    @FunctionalInterface
    public interface Verify {
        void verify(Ctx ctx, Verdict verdict) throws Exception;
    }

    @FunctionalInterface
    public interface Action {
        void run(Ctx ctx) throws Exception;
    }

    private final Verify verify;
    private final Action solve;
    private final Action setup;
    private final List<String> cleanup;

    public CommandStep(String id, String title, String instruction, String command, Verify verify, Action solve) {
        this(id, title, instruction, command, null, verify, solve, List.of());
    }

    public CommandStep(String id, String title, String instruction, String command, Action setup, Verify verify, Action solve,
                       List<String> cleanupKeyParts) {
        super(id, title, instruction, command);
        this.setup = setup;
        this.verify = verify;
        this.solve = solve;
        this.cleanup = cleanupKeyParts;
    }

    @Override
    public void setup(Ctx ctx) throws Exception {
        if (setup != null) setup.run(ctx);
    }

    @Override
    public void verify(Ctx ctx, Verdict verdict) throws Exception {
        verify.verify(ctx, verdict);
    }

    @Override
    public void solve(Ctx ctx) throws Exception {
        solve.run(ctx);
    }

    @Override
    public List<String> cleanupKeys(Ctx ctx) {
        return cleanup.stream().map(k -> k.replace("{p}", ctx.keys.prefix())).toList();
    }
}
