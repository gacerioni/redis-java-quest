package com.emberrealm.quest.lessons.l101_05;

import com.emberrealm.quest.core.Check;
import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Verdict;
import redis.clients.jedis.RedisClient;

import java.util.Map;

/** 101-05 check: the leaderboard is a sorted set, vesper scores exactly 5900 (900 seed + 5000 bonus), marker exists. */
public final class LessonCheck implements Check {

    static final double EXPECTED_XP = JedisLab.SEED_XP + JedisLab.BONUS_XP;

    @Override
    public void run(Ctx ctx, Verdict v) {
        String rank = ctx.k("rank", "xp");
        try (RedisClient jedis = Clients.jedis()) {
            String type = jedis.type(rank);
            v.expect("zset".equals(type), rank + " é um SORTED SET",
                    "achei TYPE = " + type + "; rode ./quest seed e depois a lição");
            if ("zset".equals(type)) {
                Double score = jedis.zscore(rank, JedisLab.HERO);
                v.expect(score != null && score.doubleValue() == EXPECTED_XP,
                        "vesper tem " + JedisLab.xp(EXPECTED_XP) + " XP (900 do seed + 5000 da masmorra)",
                        "esperava " + JedisLab.xp(EXPECTED_XP) + ", achei " + (score == null ? "nada" : JedisLab.xp(score))
                                + "; rode: ./quest run 101-05 jedis (ou lettuce)");
                Long position = jedis.zrevrank(rank, JedisLab.HERO);
                if (position != null) {
                    v.pass("vesper está em " + JedisLab.ordinal(position + 1) + " lugar entre " + jedis.zcard(rank) + " jogadores");
                }
            }

            Map<String, String> marker = jedis.hgetAll(ctx.progressKey());
            v.expect(!marker.isEmpty(), "a lição rodou e gravou o marcador", "rode: ./quest run 101-05 jedis (ou lettuce)");
            boolean ranJedis = marker.containsKey("ran_jedis");
            boolean ranLettuce = marker.containsKey("ran_lettuce");
            if (ranJedis && ranLettuce) v.pass("os dois clients rodaram a lição: mesma história, mesmas chaves");
            else if (ranJedis) v.info("Opcional: experimente com o Lettuce: ./quest run 101-05 lettuce");
            else if (ranLettuce) v.info("Opcional: experimente com o Jedis: ./quest run 101-05 jedis");
        }
    }
}
