package com.emberrealm.quest.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;

/**
 * "Sua vez": the student implements the hole in JedisExercise or LettuceExercise and runs it.
 * verify: the exercise marker exists and the "Sua vez" lines of LessonCheck pass.
 * solve: copies the reference solution over the stubs and asks the wrapper to rebuild and run (exit code 5).
 */
public final class ExerciseStep extends Step {

    /** Exit code the quest wrapper understands as "sources changed: rebuild, run the exercise, verify again". */
    public static final int REBUILD_AND_RUN = 5;

    private final Lessons.Lesson lesson;

    public ExerciseStep(Lessons.Lesson lesson, String method) {
        super("codigo", "Sua vez: escreva o código",
                "Abra l" + lesson.id().replace('-', '_') + "/JedisExercise.java (ou LettuceExercise.java), implemente o método "
                        + method + " no lugar do throw new Todo(...) e rode o exercício. Travou? ./quest solve " + lesson.id()
                        + " copia a solução de referência.",
                "./quest exercise " + lesson.id() + " jedis");
        this.lesson = lesson;
    }

    @Override
    public void verify(Ctx ctx, Verdict verdict) throws Exception {
        boolean ran;
        try (redis.clients.jedis.RedisClient jedis = Clients.jedis()) {
            ran = jedis.exists(ctx.exerciseKey());
        }
        if (!ran) {
            verdict.fail("Sua vez: o seu código ainda não rodou",
                    "implemente o método em l" + lesson.id().replace('-', '_') + "/JedisExercise.java (ou LettuceExercise.java) e rode: ./quest exercise "
                            + lesson.id() + " jedis");
            return;
        }
        Optional<Check> check = Lessons.check(lesson);
        if (check.isEmpty()) {
            verdict.pass("Sua vez: o exercício rodou");
            return;
        }
        Verdict full = new Verdict();
        check.get().run(ctx, full);
        Verdict mine = full.filtered(text -> text.startsWith("Sua vez"));
        if (mine.entries().isEmpty()) {
            verdict.pass("Sua vez: o exercício rodou");
        } else {
            verdict.addAll(mine);
        }
    }

    @Override
    public void solve(Ctx ctx) throws IOException {
        Path from = Paths.get("solutions", "l" + lesson.id().replace('-', '_'));
        Path to = Paths.get("src", "main", "java", "com", "emberrealm", "quest", "lessons", "l" + lesson.id().replace('-', '_'));
        if (!Files.isDirectory(from)) {
            throw new IllegalStateException("não há solução de referência em " + from + " (rode a partir da raiz do repositório)");
        }
        List<Path> files;
        try (var stream = Files.list(from)) {
            files = stream.filter(f -> f.toString().endsWith(".java")).sorted().toList();
        }
        for (Path f : files) {
            Files.copy(f, to.resolve(f.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            ctx.out.ok("solução copiada: " + to.resolve(f.getFileName()));
        }
        throw new RebuildRequested(lesson.id());
    }

    /** Signals the CLI that the wrapper must rebuild and run the exercise (sources changed). */
    public static final class RebuildRequested extends RuntimeException {
        public final String lessonId;

        RebuildRequested(String lessonId) {
            super("rebuild " + lessonId);
            this.lessonId = lessonId;
        }
    }
}
