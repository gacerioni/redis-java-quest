package com.emberrealm.quest.lessons.l101_06;

import com.emberrealm.quest.core.Check;
import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Verdict;
import redis.clients.jedis.RedisClient;

import java.util.Map;

/** 101-06 check: {p}:chests:opened is a Bloom filter (TYPE MBbloom--), chest-001 is in it, the marker exists. */
public final class LessonCheck implements Check {

    static final String BLOOM_TYPE = "MBbloom--";

    @Override
    public void run(Ctx ctx, Verdict v) {
        String chests = ctx.k("chests", "opened");
        try (RedisClient jedis = Clients.jedis()) {
            Map<String, String> marker = jedis.hgetAll(ctx.progressKey());
            if ("unsupported".equals(marker.get("bloom"))) {
                v.skip("seu servidor não tem o módulo Bloom; use Redis 8 (docker compose up -d) ou o Redis Cloud para ver o filtro de verdade");
            } else {
                String type = jedis.type(chests);
                v.expect(BLOOM_TYPE.equals(type), chests + " é um Bloom filter (TYPE devolve " + BLOOM_TYPE + ")",
                        "achei TYPE = " + type + "; rode: ./quest run 101-06 jedis (ou lettuce)");
                if (BLOOM_TYPE.equals(type)) {
                    v.expect(jedis.bfExists(chests, "chest-001"), "chest-001 consta como aberto (BF.EXISTS = 1)",
                            "o filtro existe mas está vazio; rode a lição de novo");
                    try {
                        v.pass("BF.CARD estima " + jedis.bfCard(chests) + " baús abertos");
                    } catch (Exception ignored) {
                        // BF.CARD needs RedisBloom 2.4.4+; the lesson does not depend on it
                    }
                }
            }

            v.expect(!marker.isEmpty(), "a lição rodou e gravou o marcador", "rode: ./quest run 101-06 jedis (ou lettuce)");
            if (marker.containsKey("false_positives")) {
                v.pass("falsos positivos na última rodada: " + marker.get("false_positives") + " de " + JedisLab.PROBES);
            }
            boolean ranJedis = marker.containsKey("ran_jedis");
            boolean ranLettuce = marker.containsKey("ran_lettuce");
            if (ranJedis && ranLettuce) v.pass("os dois clients rodaram a lição: mesma história, mesmas chaves");
            else if (ranJedis) v.info("Opcional: experimente com o Lettuce: ./quest run 101-06 lettuce");
            else if (ranLettuce) v.info("Opcional: experimente com o Jedis: ./quest run 101-06 jedis");
        }
    }
}
