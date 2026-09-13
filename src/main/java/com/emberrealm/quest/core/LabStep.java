package com.emberrealm.quest.core;

import java.util.Optional;

/**
 * "Rode o lab": the student runs the ready lab with one client (the other one is encouraged).
 * verify: the progress marker exists, plus the lab lines of the lesson's LessonCheck (everything not prefixed "Sua vez").
 * solve: runs the Jedis lab.
 */
public final class LabStep extends Step {

    private final Lessons.Lesson lesson;

    public LabStep(Lessons.Lesson lesson) {
        super("lab", "Rode o lab pronto",
                "Rode o lab com um client e leia a saída: cada linha que começa com > é o comando Redis enviado, "
                        + "a linha seguinte é a resposta. Depois rode com o outro client e compare.",
                "./quest run " + lesson.id() + " jedis");
        this.lesson = lesson;
    }

    @Override
    public void verify(Ctx ctx, Verdict verdict) throws Exception {
        boolean ran;
        try (redis.clients.jedis.RedisClient jedis = Clients.jedis()) {
            ran = jedis.exists(ctx.progressKey());
        }
        if (!ran) {
            verdict.fail("o lab ainda não rodou", "rode: ./quest run " + lesson.id() + " jedis (ou lettuce)");
            return;
        }
        Optional<Check> check = Lessons.check(lesson);
        if (check.isEmpty()) {
            verdict.pass("o lab rodou e gravou o marcador");
            return;
        }
        Verdict full = new Verdict();
        check.get().run(ctx, full);
        verdict.addAll(full.filtered(text -> !text.startsWith("Sua vez")));
    }

    @Override
    public void solve(Ctx ctx) throws Exception {
        Lab lab = Lessons.lab(lesson, "jedis").orElseThrow(() -> new IllegalStateException("lição sem lab"));
        lab.run(new Ctx(ctx.keys, ctx.out, lesson.id(), "jedis"));
    }
}
