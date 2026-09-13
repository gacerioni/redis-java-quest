package com.emberrealm.quest.lessons.l101_01;

import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.CommandStep;
import com.emberrealm.quest.core.ExerciseStep;
import com.emberrealm.quest.core.LabStep;
import com.emberrealm.quest.core.Lessons;
import com.emberrealm.quest.core.Step;
import com.emberrealm.quest.core.Steps;
import redis.clients.jedis.RedisClient;

import java.util.List;

/**
 * 101-01 as a workflow: run the lab, touch Redis yourself (INCRBY in Redis Insight or redis-cli), then write code.
 * Each step has setup / verify / solve, so the student can be checked (and unblocked) at every point.
 */
public final class LessonSteps implements Steps {

    private static final Lessons.Lesson LESSON = Lessons.byId("101-01").orElseThrow();
    static final long BONUS_KILLS = 10;

    @Override
    public List<Step> steps() {
        return List.of(
                new LabStep(LESSON),
                new CommandStep("mexa", "Mexa no Redis: dez abates a mais para Kaelith",
                        "Sem Java agora. No Redis Insight (aba Workbench) ou no redis-cli, some 10 abates ao contador que o lab criou. "
                                + "Depois leia o valor com GET e repare que o Redis fez a conta em cima da String.",
                        "INCRBY {p}:kills:kaelith 10",
                        (ctx, v) -> {
                            try (RedisClient jedis = Clients.jedis()) {
                                String raw = jedis.get(ctx.k("kills", "kaelith"));
                                long kills = LessonCheck.parseLong(raw);
                                v.expect(kills >= 5 + BONUS_KILLS,
                                        "Mexa no Redis: o contador " + ctx.k("kills", "kaelith") + " chegou a " + JedisLab.orNil(raw)
                                                + " (o lab deixa 5, você somou pelo menos 10)",
                                        "no Workbench do Insight ou no redis-cli: INCRBY " + ctx.k("kills", "kaelith") + " 10 (rode o lab antes, se o contador não existir)");
                            }
                        },
                        ctx -> {
                            try (RedisClient jedis = Clients.jedis()) {
                                ctx.out.cmd("INCRBY " + ctx.k("kills", "kaelith") + " " + BONUS_KILLS);
                                ctx.out.kv("abates agora", jedis.incrBy(ctx.k("kills", "kaelith"), BONUS_KILLS));
                            }
                        }),
                new ExerciseStep(LESSON, "castHeal"));
    }
}
