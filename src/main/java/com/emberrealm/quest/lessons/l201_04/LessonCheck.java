package com.emberrealm.quest.lessons.l201_04;

import com.emberrealm.quest.core.Check;
import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Verdict;
import redis.clients.jedis.RedisClient;

import java.util.Map;

/** 201-04: {p}:vs:items is a vector set with 42 elements of 384 dimensions, and the marker agrees. */
public final class LessonCheck implements Check {

    @Override
    public void run(Ctx ctx, Verdict v) {
        String key = ctx.k("vs", "items");
        try (RedisClient jedis = Clients.jedis()) {
            String type = jedis.type(key);
            v.expect("vectorset".equals(type), "a chave " + key + " é um vector set (TYPE = " + type + ")",
                    "rode: ./quest run 201-04 jedis (ou lettuce)");
            if ("vectorset".equals(type)) {
                long card = jedis.vcard(key);
                v.expect(card == 42, "VCARD = " + card + ": os 42 itens do reino estão no set",
                        "faltou item no VADD; rode a lição de novo (ela recria a chave do zero)");
                long dim = jedis.vdim(key);
                v.expect(dim == 384, "VDIM = " + dim + ": mesma dimensão dos embeddings do seed",
                        "a dimensão é fixada pelo primeiro VADD; rode a lição de novo para recriar a chave");
                v.expect(jedis.vismember(key, Neighbors.SELF), "VISMEMBER confirma " + Neighbors.SELF + " no set",
                        "rode a lição de novo");
                String attrs = jedis.vgetattr(key, Neighbors.SELF);
                v.expect(attrs != null && attrs.contains("\"rarity\""), "os atributos JSON estão gravados (VGETATTR tem rarity)",
                        "o VADD precisa de SETATTR com o JSON de atributos; rode a lição de novo");
            }

            Map<String, String> marker = jedis.hgetAll(ctx.progressKey());
            v.expect(!marker.isEmpty(), "a lição rodou e gravou o marcador",
                    "rode: ./quest run 201-04 jedis (ou lettuce)");
            if (!marker.isEmpty()) {
                v.pass("vizinho mais próximo da Espada de Brasa: " + marker.getOrDefault("nearest", "?")
                        + "; épicos parecidos: " + marker.getOrDefault("epic_hits", "?"));
                boolean jedisRan = marker.containsKey("ran_jedis");
                boolean lettuceRan = marker.containsKey("ran_lettuce");
                if (jedisRan && !lettuceRan) v.skip("falta experimentar com o Lettuce: ./quest run 201-04 lettuce");
                if (lettuceRan && !jedisRan) v.skip("falta experimentar com o Jedis: ./quest run 201-04 jedis");
                if (jedisRan && lettuceRan) v.pass("os dois clients montaram o mesmo vector set");
            }
        }
    }
}
