package com.emberrealm.quest.core;

import com.emberrealm.quest.world.Seed;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Runs the lifecycle of a lesson's steps: start (setup), verify, solve, skip.
 * Step progress lives in the hash {p}:steps:<lesson> (done, skipped, pending, unavailable).
 */
public final class StepEngine {

    public static final int EXIT_REBUILD = ExerciseStep.REBUILD_AND_RUN;
    public static final int EXIT_UNAVAILABLE = 3;

    private StepEngine() {
    }

    public static List<Step> stepsFor(Lessons.Lesson lesson) {
        Optional<Steps> declared = Lessons.steps(lesson);
        if (declared.isPresent()) return declared.get().steps();
        List<Step> defaults = new ArrayList<>();
        if (Lessons.lab(lesson, "jedis").isPresent()) defaults.add(new LabStep(lesson));
        if (Lessons.exercise(lesson, "jedis").isPresent()) defaults.add(new ExerciseStep(lesson, "marcado com Todo"));
        return defaults;
    }

    public static String stepsKey(Keys keys, String lessonId) {
        return keys.of("steps", lessonId);
    }

    public static Map<String, String> progress(Keys keys, String lessonId) {
        try (redis.clients.jedis.RedisClient jedis = Clients.jedis()) {
            return jedis.hgetAll(stepsKey(keys, lessonId));
        }
    }

    static void mark(Keys keys, String lessonId, String stepId, String state) {
        try (redis.clients.jedis.RedisClient jedis = Clients.jedis()) {
            jedis.hset(stepsKey(keys, lessonId), stepId, state);
        }
    }

    /** Idempotent: loads the world when it is missing. */
    public static void ensureSeed(Ctx ctx) throws Exception {
        boolean seeded;
        try (redis.clients.jedis.RedisClient jedis = Clients.jedis()) {
            seeded = jedis.exists(ctx.k("world", "seeded_at"));
        }
        if (!seeded) {
            ctx.out.step("Mundo ainda não carregado neste prefixo: rodando o seed");
            new Seed().run(ctx);
        }
    }

    /** start: setup of every step, then the step list and the next instruction. */
    public static int start(Ctx ctx, Lessons.Lesson lesson) throws Exception {
        List<Step> steps = stepsFor(lesson);
        if (steps.isEmpty()) {
            ctx.out.warn("Lição " + lesson.id() + " ainda não tem passos (em breve).");
            return 3;
        }
        ensureSeed(ctx);
        for (Step s : steps) s.setup(ctx);
        return show(ctx, lesson, steps, "Lição preparada. Passos:");
    }

    /** Lists the steps with their status and points to the next one. */
    public static int show(Ctx ctx, Lessons.Lesson lesson, List<Step> steps, String heading) {
        Map<String, String> progress = progress(ctx.keys, lesson.id());
        ctx.out.step(heading);
        Step next = null;
        for (int i = 0; i < steps.size(); i++) {
            Step s = steps.get(i);
            String state = progress.get(s.id);
            String mark = "done".equals(state) ? "[x]" : "skipped".equals(state) ? "[>]" : "[ ]";
            if (next == null && !settled(state)) next = s;
            ctx.out.info(mark + " " + (i + 1) + ". " + s.title + (s.command != null ? "    " + s.commandFor(ctx) : ""));
        }
        ctx.out.blank();
        if (next == null) {
            long skipped = steps.stream().filter(s -> "skipped".equals(progress.get(s.id))).count();
            if (skipped > 0) ctx.out.warn("Percurso encerrado com " + skipped + " passo(s) pulado(s), sem comprovar sua execução. Próxima: ./quest next");
            else ctx.out.ok("Todos os passos da lição " + lesson.id() + " estão verificados. Próxima: ./quest next");
            return 0;
        }
        int number = steps.indexOf(next) + 1;
        ctx.out.step("Próximo passo " + number + ": " + next.title);
        ctx.out.info(next.instructionFor(ctx));
        if (next.command != null) ctx.out.cmd(next.commandFor(ctx));
        ctx.out.blank();
        ctx.out.info("Quando fizer: ./quest verify " + lesson.id() + "     Travou: ./quest solve " + lesson.id() + "     Pular: ./quest skip " + lesson.id());
        return 0;
    }

    /** verify: every step in order; a passing step is marked done. Exit 0 only when all steps pass. */
    public static int verify(Ctx ctx, Lessons.Lesson lesson) throws Exception {
        List<Step> steps = stepsFor(lesson);
        if (steps.isEmpty()) {
            ctx.out.warn("Lição " + lesson.id() + " ainda não tem passos (em breve).");
            return 3;
        }
        Map<String, String> progress = progress(ctx.keys, lesson.id());
        int ok = 0;
        int skippedCount = 0;
        boolean failed = false;
        Step firstFailing = null;
        for (int i = 0; i < steps.size(); i++) {
            Step s = steps.get(i);
            Verdict v = new Verdict();
            boolean skipped = "skipped".equals(progress.get(s.id));
            ctx.out.step("Passo " + (i + 1) + ": " + s.title + (skipped ? " (pulado)" : ""));
            if (skipped) {
                skippedCount++;
                ctx.out.warn("Pulado explicitamente; não conta como execução verificada. Para retomar: ./quest solve " + lesson.id() + " " + s.id);
                continue;
            }
            s.verify(ctx, v);
            v.print(ctx.out);
            mark(ctx.keys, lesson.id(), s.id, stateFor(v));
            if (v.ok()) {
                ok++;
            } else {
                failed |= !v.unavailable();
                if (firstFailing == null) firstFailing = s;
            }
        }
        ctx.out.blank();
        if (firstFailing == null) {
            if (skippedCount > 0) {
                ctx.out.warn("Lição " + lesson.id() + ": " + ok + " verificados, " + skippedCount + " pulados. Percurso encerrado com passos não verificados.");
                return EXIT_UNAVAILABLE;
            }
            ctx.out.ok("Lição " + lesson.id() + " completa: " + ok + " de " + steps.size() + " passos.");
            return 0;
        }
        int number = steps.indexOf(firstFailing) + 1;
        if (failed) ctx.out.fail(ok + " de " + steps.size() + " passos verificados. Falta o passo " + number + ": " + firstFailing.title);
        else ctx.out.warn("Verificação parcial: " + ok + " de " + steps.size() + " passos; falta ambiente ou observação para " + firstFailing.title);
        ctx.out.info(firstFailing.instructionFor(ctx));
        if (firstFailing.command != null) ctx.out.cmd(firstFailing.commandFor(ctx));
        ctx.out.hint("Travou? ./quest solve " + lesson.id() + " faz este passo por você. ./quest skip " + lesson.id() + " marca como pulado.");
        return failed ? 1 : EXIT_UNAVAILABLE;
    }

    /**
     * solve or skip one step: the given one (number or id) or the first that is not done.
     * Returns EXIT_REBUILD when sources changed and the wrapper must rebuild and run the exercise.
     */
    public static int solve(Ctx ctx, Lessons.Lesson lesson, String selector, boolean skip) throws Exception {
        List<Step> steps = stepsFor(lesson);
        if (steps.isEmpty()) {
            ctx.out.warn("Lição " + lesson.id() + " ainda não tem passos (em breve).");
            return 3;
        }
        Step target = select(steps, selector, progress(ctx.keys, lesson.id()));
        if (target == null) {
            ctx.out.info("Sem passos pendentes na lição " + lesson.id() + ": há passos verificados ou pulados. Use ./quest steps " + lesson.id() + " para conferir.");
            return 0;
        }
        int number = steps.indexOf(target) + 1;
        ctx.out.step((skip ? "Pulando" : "Resolvendo") + " o passo " + number + ": " + target.title);
        ensureSeed(ctx);
        target.setup(ctx);
        try {
            target.solve(ctx);
        } catch (ExerciseStep.RebuildRequested rebuild) {
            mark(ctx.keys, lesson.id(), target.id, skip ? "skipped" : "pending");
            ctx.out.info("O quest vai recompilar e rodar o exercício com a solução de referência.");
            return EXIT_REBUILD;
        }
        Verdict v = new Verdict();
        target.verify(ctx, v);
        v.print(ctx.out);
        mark(ctx.keys, lesson.id(), target.id, skip ? "skipped" : stateFor(v));
        ctx.out.blank();
        if (skip) ctx.out.warn("Passo " + number + " pulado explicitamente, sem contar como verificado. Veja o próximo com: ./quest start " + lesson.id());
        else if (v.ok()) ctx.out.ok("Passo " + number + " resolvido e verificado. Veja o próximo com: ./quest start " + lesson.id());
        else ctx.out.warn("Passo " + number + " continua pendente; confira a verificação acima.");
        return skip ? 0 : v.ok() ? 0 : v.unavailable() ? EXIT_UNAVAILABLE : 1;
    }

    private static Step select(List<Step> steps, String selector, Map<String, String> progress) {
        if (selector != null) {
            for (int i = 0; i < steps.size(); i++) {
                Step s = steps.get(i);
                if (selector.equals(String.valueOf(i + 1)) || selector.equals(s.id)) return s;
            }
            throw new IllegalArgumentException("passo desconhecido: " + selector + " (use o número ou o id do passo)");
        }
        for (Step s : steps) {
            if (!settled(progress.get(s.id))) return s;
        }
        return null;
    }

    /** First lesson with a pending step. */
    public static Optional<Lessons.Lesson> nextLesson(Keys keys) {
        for (Lessons.Lesson l : Lessons.all()) {
            List<Step> steps = stepsFor(l);
            if (steps.isEmpty()) continue;
            Map<String, String> progress = progress(keys, l.id());
            for (Step s : steps) if (!settled(progress.get(s.id))) return Optional.of(l);
        }
        return Optional.empty();
    }

    /** Verified steps over total; skipped/unavailable steps are never counted as done. */
    public static int[] counts(Keys keys, Lessons.Lesson lesson, Map<String, String> progress) {
        List<Step> steps = stepsFor(lesson);
        int done = 0;
        for (Step s : steps) if ("done".equals(progress.get(s.id))) done++;
        return new int[]{done, steps.size()};
    }

    static boolean settled(String state) {
        return "done".equals(state) || "skipped".equals(state);
    }

    static String stateFor(Verdict verdict) {
        return verdict.ok() ? "done" : verdict.unavailable() ? "unavailable" : "pending";
    }

    /** JSON with every lesson's steps, the single source of truth the course site renders. */
    public static String toJson() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        for (Lessons.Lesson l : Lessons.all()) {
            List<Step> steps = stepsFor(l);
            if (steps.isEmpty()) continue;
            ArrayNode arr = root.putArray(l.id());
            for (Step s : steps) {
                ObjectNode node = arr.addObject();
                node.put("id", s.id);
                node.put("title", s.title);
                node.put("instruction", s.instruction);
                if (s.command != null) node.put("command", s.command);
            }
        }
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
    }
}
