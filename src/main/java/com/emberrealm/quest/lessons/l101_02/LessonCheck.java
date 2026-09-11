package com.emberrealm.quest.lessons.l101_02;

import com.emberrealm.quest.core.Check;
import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Verdict;
import redis.clients.jedis.RedisClient;

import java.util.List;
import java.util.Map;

/**
 * Verifies the Hash lesson: Kaelith's sheet is a hash with gold == seed + 250 and the new fields,
 * the buffs hash exists (or the marker says HEXPIRE is unsupported), and the progress marker is there.
 */
public final class LessonCheck implements Check {

    @Override
    public void run(Ctx ctx, Verdict v) {
        String sheet = ctx.k("player", JedisLab.PLAYER);
        String buffs = ctx.k("player", JedisLab.PLAYER, "buffs");
        long expectedGold = JedisLab.seedGold() + JedisLab.QUEST_REWARD;

        try (RedisClient jedis = Clients.jedis()) {
            Map<String, String> marker = jedis.hgetAll(ctx.progressKey());
            v.expect(!marker.isEmpty(), "a lição rodou e gravou o marcador", "rode: ./quest run 101-02 jedis (ou lettuce)");

            v.expect("hash".equals(jedis.type(sheet)), sheet + " é um hash (a ficha de Kaelith)", "rode ./quest seed para recriar a ficha");

            String gold = jedis.hget(sheet, "gold");
            v.expect(String.valueOf(expectedGold).equals(gold),
                    "o ouro de Kaelith é " + expectedGold + " (" + JedisLab.seedGold() + " do seed + " + JedisLab.QUEST_REWARD + " da missão)",
                    "ouro atual: " + (gold == null ? "(nil)" : gold) + ". Rode a lição de novo: ela recoloca o ouro do seed antes do HINCRBY");

            v.expect(jedis.hexists(sheet, "title") && jedis.hexists(sheet, "mount"),
                    "a ficha ganhou os campos title e mount (HSET com vários campos)",
                    "rode a lição de novo: ./quest run 101-02 jedis");

            if (jedis.exists(buffs)) {
                v.pass("o hash de buffs " + buffs + " existe" + hasteStatus(jedis, buffs));
            } else if ("unsupported".equals(marker.get("hexpire"))) {
                v.skip("HEXPIRE não é suportado neste servidor (precisa de Redis 7.4+); a lição seguiu sem expiração por campo");
            } else {
                v.fail("o hash de buffs " + buffs + " deveria existir (shield não tem prazo)", "rode a lição de novo: ./quest run 101-02 jedis");
            }

            boolean jedisRan = marker.containsKey("ran_jedis");
            boolean lettuceRan = marker.containsKey("ran_lettuce");
            if (jedisRan && !lettuceRan) v.skip("falta experimentar com o Lettuce: ./quest run 101-02 lettuce");
            if (lettuceRan && !jedisRan) v.skip("falta experimentar com o Jedis: ./quest run 101-02 jedis");
            if (jedisRan && lettuceRan) v.pass("os dois clients rodaram a lição: mesmas chaves, mesmo resultado");
        }
    }

    /** Describes the haste field: still counting down, already expired, or HTTL unavailable on this server. */
    private static String hasteStatus(RedisClient jedis, String buffs) {
        try {
            List<Long> ttl = jedis.httl(buffs, "haste");
            long remaining = ttl.isEmpty() || ttl.get(0) == null ? -2 : ttl.get(0);
            if (remaining > 0) return "; haste expira em " + remaining + " s, shield fica";
            if (remaining == -2) return "; haste já expirou sozinho e shield ficou, como devia";
            return "; haste sem prazo (HTTL " + remaining + ")";
        } catch (Exception e) {
            return "";
        }
    }
}
