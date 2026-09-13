package com.emberrealm.quest.lessons.l101_01;

import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Lab;
import com.emberrealm.quest.core.Todo;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.params.SetParams;

/**
 * 101-01, your turn (Jedis). Kaelith learned Minor Heal: an 8 second cooldown, and every heal that
 * goes out counts. Implement castHeal below. Everything else in this file is the harness: it cleans
 * the keys, casts twice in a row and records the result for ./quest check 101-01.
 */
public final class JedisExercise implements Lab {

    static final int HEAL_COOLDOWN_SECONDS = 8;

    @Override
    public void run(Ctx ctx) {
        String cooldown = ctx.k("cooldown", "kaelith", "heal");
        String heals = ctx.k("heals", "kaelith");

        try (RedisClient jedis = Clients.jedis()) {
            ctx.out.step("Limpando as chaves do exercício (pode rodar quantas vezes quiser)");
            jedis.unlink(cooldown, heals);

            ctx.out.step("Kaelith tenta curar duas vezes seguidas");
            boolean first = castHeal(jedis, cooldown, heals);
            ctx.out.kv("primeira cura", first ? "saiu" : "recusada");
            boolean second = castHeal(jedis, cooldown, heals);
            ctx.out.kv("segunda cura", second ? "saiu (não devia: está em cooldown)" : "recusada, em cooldown");
            String healCount = jedis.get(heals);
            ctx.out.kv("curas contadas em " + heals, healCount == null ? "(nil)" : healCount);
            ctx.out.kv("TTL do cooldown", jedis.ttl(cooldown) + " s");

            ctx.exerciseDone("first", String.valueOf(first), "second", String.valueOf(second),
                    "heals", String.valueOf(healCount));
        }
    }

    /**
     * YOUR CODE GOES HERE.
     *
     * Rules: the heal only goes out if the cooldown key does not exist yet. When it goes out, create the
     * cooldown key with an 8 second TTL and add one to the heal counter. Return true when the heal went
     * out, false when Kaelith is still in cooldown. Both checks must happen in ONE Redis command.
     *
     * Hints: SetParams.setParams().nx().ex(HEAL_COOLDOWN_SECONDS); jedis.set(...) returns "OK" or null;
     * jedis.incr(healsKey).
     */
    static boolean castHeal(RedisClient jedis, String cooldownKey, String healsKey) {
        throw new Todo("implemente castHeal: SET " + cooldownKey + " 1 NX EX " + HEAL_COOLDOWN_SECONDS
                + " e, só quando o SET devolver OK, INCR " + healsKey);
    }
}
