package com.emberrealm.quest.lessons.l101_01;

import com.emberrealm.quest.core.Check;
import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Verdict;
import redis.clients.jedis.RedisClient;

import java.util.Map;

/**
 * Verifies the String lesson: a kill counter of at least 3, the cooldown key (or the marker proving the
 * second SET NX was refused, since the key only lives 5 seconds), the session and the progress marker.
 */
public final class LessonCheck implements Check {

    @Override
    public void run(Ctx ctx, Verdict v) {
        String session = ctx.k("session", "kaelith");
        String cooldown = ctx.k("cooldown", "kaelith", "fireball");
        String kills = ctx.k("kills", "kaelith");

        try (RedisClient jedis = Clients.jedis()) {
            Map<String, String> marker = jedis.hgetAll(ctx.progressKey());
            v.expect(!marker.isEmpty(), "a lição rodou e gravou o marcador", "rode: ./quest run 101-01 jedis (ou lettuce)");

            String rawKills = jedis.get(kills);
            long killCount = parseLong(rawKills);
            v.expect("string".equals(jedis.type(kills)) && killCount >= 3,
                    "o contador " + kills + " é uma String com pelo menos 3 abates (atual: " + JedisLab.orNil(rawKills) + ")",
                    "rode a lição de novo: ela faz INCR, INCR e INCRBY 3");

            long cooldownTtl = jedis.ttl(cooldown);
            if (cooldownTtl >= 0 && cooldownTtl <= JedisLab.COOLDOWN_SECONDS) {
                v.pass("o cooldown " + cooldown + " ainda existe, TTL " + cooldownTtl + " s");
            } else if (cooldownTtl == -2 && "ok".equals(marker.get("cooldown"))) {
                v.pass("o cooldown de " + JedisLab.COOLDOWN_SECONDS + " s já expirou (esperado); o marcador confirma que o segundo SET NX foi barrado");
            } else {
                v.fail("o cooldown " + cooldown + " deveria existir com TTL de até " + JedisLab.COOLDOWN_SECONDS + " s, ou constar no marcador",
                        "rode a lição e o check em seguida: ./quest run 101-01 jedis && ./quest check 101-01");
            }

            long sessionTtl = jedis.ttl(session);
            if (sessionTtl > 0) {
                v.pass("a sessão " + session + " está viva, TTL " + sessionTtl + " s");
            } else if (!marker.isEmpty()) {
                v.pass("a sessão de 30 min já expirou sozinha, como devia; o marcador guarda o TTL inicial: "
                        + marker.getOrDefault("session_ttl", "?") + " s");
            }

            String healCooldown = ctx.k("cooldown", "kaelith", "heal");
            String heals = ctx.k("heals", "kaelith");
            Map<String, String> exercise = jedis.hgetAll(ctx.exerciseKey());
            if (exercise.isEmpty()) {
                v.fail("Sua vez: o seu castHeal ainda não rodou",
                        "implemente castHeal em l101_01/JedisExercise.java (ou LettuceExercise.java) e rode: ./quest exercise 101-01 jedis");
            } else {
                String rawHeals = jedis.get(heals);
                v.expect(parseLong(rawHeals) == 1,
                        "Sua vez: de duas curas seguidas só a primeira contou (" + heals + " = " + JedisLab.orNil(rawHeals) + ")",
                        "castHeal deve gravar o cooldown com NX e EX e só fazer INCR quando o SET devolver OK");
                long healTtl = jedis.ttl(healCooldown);
                if (healTtl >= 0 && healTtl <= JedisExercise.HEAL_COOLDOWN_SECONDS) {
                    v.pass("Sua vez: o cooldown " + healCooldown + " existe, TTL " + healTtl + " s");
                } else if (healTtl == -2 && "false".equals(exercise.get("second"))) {
                    v.pass("Sua vez: o cooldown de " + JedisExercise.HEAL_COOLDOWN_SECONDS + " s já expirou; o exercício registrou a segunda cura recusada");
                } else {
                    v.fail("Sua vez: esperava " + healCooldown + " com TTL de até " + JedisExercise.HEAL_COOLDOWN_SECONDS + " s",
                            "use SET " + healCooldown + " 1 NX EX " + JedisExercise.HEAL_COOLDOWN_SECONDS);
                }
            }

            boolean jedisRan = marker.containsKey("ran_jedis");
            boolean lettuceRan = marker.containsKey("ran_lettuce");
            if (jedisRan && !lettuceRan) v.info("Opcional: experimente com o Lettuce: ./quest run 101-01 lettuce");
            if (lettuceRan && !jedisRan) v.info("Opcional: experimente com o Jedis: ./quest run 101-01 jedis");
            if (jedisRan && lettuceRan) v.pass("os dois clients rodaram a lição: mesmas chaves, mesmo resultado");
        }
    }

    static long parseLong(String raw) {
        if (raw == null) return -1;
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
