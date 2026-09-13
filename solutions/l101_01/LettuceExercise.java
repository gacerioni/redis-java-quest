package com.emberrealm.quest.lessons.l101_01;

import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Lab;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

/**
 * 101-01, reference solution (Lettuce). Same task as JedisExercise: Minor Heal with an 8 second cooldown and a
 * heal counter. Implement castHeal below; the harness cleans the keys, casts twice and records the result.
 */
public final class LettuceExercise implements Lab {

    static final int HEAL_COOLDOWN_SECONDS = 8;

    @Override
    public void run(Ctx ctx) {
        String cooldown = ctx.k("cooldown", "kaelith", "heal");
        String heals = ctx.k("heals", "kaelith");

        try (StatefulRedisConnection<String, String> connection = Clients.lettuceConnection()) {
            RedisCommands<String, String> redis = connection.sync();
            ctx.out.step("Limpando as chaves do exercício (pode rodar quantas vezes quiser)");
            redis.unlink(cooldown, heals);

            ctx.out.step("Kaelith tenta curar duas vezes seguidas");
            boolean first = castHeal(redis, cooldown, heals);
            ctx.out.kv("primeira cura", first ? "saiu" : "recusada");
            boolean second = castHeal(redis, cooldown, heals);
            ctx.out.kv("segunda cura", second ? "saiu (não devia: está em cooldown)" : "recusada, em cooldown");
            String healCount = redis.get(heals);
            ctx.out.kv("curas contadas em " + heals, healCount == null ? "(nil)" : healCount);
            ctx.out.kv("TTL do cooldown", redis.ttl(cooldown) + " s");

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
     * Hints: SetArgs.Builder.nx().ex(HEAL_COOLDOWN_SECONDS); redis.set(key, value, args) returns "OK" or null;
     * redis.incr(healsKey).
     */
    static boolean castHeal(RedisCommands<String, String> redis, String cooldownKey, String healsKey) {
        // NX: only if the key does not exist; EX: it expires by itself. One atomic command.
        String reply = redis.set(cooldownKey, "1", SetArgs.Builder.nx().ex(HEAL_COOLDOWN_SECONDS));
        if (reply == null) {
            return false; // the key was already there: still in cooldown
        }
        redis.incr(healsKey);
        return true;
    }
}
