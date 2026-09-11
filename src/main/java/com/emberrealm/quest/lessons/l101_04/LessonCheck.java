package com.emberrealm.quest.lessons.l101_04;

import com.emberrealm.quest.core.Check;
import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Verdict;
import redis.clients.jedis.RedisClient;

import java.util.Map;

/** 101-04 check: marisol owns primeiro-sangue, the loot table is a SET with 6 items, the marker exists. */
public final class LessonCheck implements Check {

    @Override
    public void run(Ctx ctx, Verdict v) {
        String marisol = ctx.k("player", "marisol", "achievements");
        String loot = ctx.k("loot", "table");
        try (RedisClient jedis = Clients.jedis()) {
            v.expect(jedis.sismember(marisol, JedisLab.NEW_ACHIEVEMENT),
                    "marisol tem a conquista " + JedisLab.NEW_ACHIEVEMENT + " em " + marisol,
                    "rode: ./quest run 101-04 jedis (ou lettuce)");

            String type = jedis.type(loot);
            v.expect("set".equals(type), loot + " é um SET",
                    "achei TYPE = " + type + "; a tabela de loot deve ser um SET, rode a lição de novo");
            if ("set".equals(type)) {
                long size = jedis.scard(loot);
                v.expect(size == JedisLab.LOOT_TABLE.size(), "a tabela de loot tem " + JedisLab.LOOT_TABLE.size() + " itens",
                        "achei " + size + " itens em " + loot + "; se você trocou SRANDMEMBER por SPOP, volte e rode a lição de novo");
            }

            Map<String, String> marker = jedis.hgetAll(ctx.progressKey());
            v.expect(!marker.isEmpty(), "a lição rodou e gravou o marcador", "rode: ./quest run 101-04 jedis (ou lettuce)");
            boolean ranJedis = marker.containsKey("ran_jedis");
            boolean ranLettuce = marker.containsKey("ran_lettuce");
            if (ranJedis && ranLettuce) v.pass("os dois clients rodaram a lição: mesma história, mesmas chaves");
            else if (ranJedis) v.skip("falta experimentar com o Lettuce: ./quest run 101-04 lettuce");
            else if (ranLettuce) v.skip("falta experimentar com o Jedis: ./quest run 101-04 jedis");
        }
    }
}
