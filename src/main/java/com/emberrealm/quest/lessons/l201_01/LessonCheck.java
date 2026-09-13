package com.emberrealm.quest.lessons.l201_01;

import com.emberrealm.quest.core.Check;
import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Verdict;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.exceptions.JedisDataException;

import java.util.Map;

/** 201-01: the index exists, FT.INFO reports the 42 seeded documents and the schema carries the VECTOR field. */
public final class LessonCheck implements Check {

    @Override
    public void run(Ctx ctx, Verdict v) {
        String index = ItemsIndex.name(ctx);
        try (RedisClient jedis = Clients.jedis()) {
            Map<String, Object> info = null;
            try {
                info = jedis.ftInfo(index);
            } catch (JedisDataException e) {
                // no index yet: reported below
            }
            v.expect(info != null, "o índice " + index + " existe (FT.INFO responde)",
                    "rode: ./quest run 201-01 jedis (ou lettuce)");
            if (info != null) {
                long numDocs = ItemsIndex.numDocs(info);
                v.expect(numDocs == ItemsIndex.DOCS,
                        "FT.INFO mostra num_docs = " + numDocs + ": todos os itens do seed estão indexados",
                        "rode ./quest seed e depois a lição de novo; confira hash_indexing_failures no FT.INFO");
                String attributes = String.valueOf(info.get("attributes"));
                v.expect(attributes.contains("embedding") && attributes.contains("VECTOR"),
                        "o schema tem o campo VECTOR embedding (a lição 201-03 depende dele)",
                        "o índice foi criado com outro schema; rode a lição 201-01 de novo para recriar");
            }

            Map<String, String> marker = jedis.hgetAll(ctx.progressKey());
            v.expect(!marker.isEmpty(), "a lição rodou e gravou o marcador",
                    "rode: ./quest run 201-01 jedis (ou lettuce)");
            if (!marker.isEmpty()) {
                v.pass(marker.getOrDefault("queries", "?") + " consultas FT.SEARCH rodaram; fuzzy %espda% achou "
                        + marker.getOrDefault("fuzzy_hits", "?") + " itens");
                boolean jedisRan = marker.containsKey("ran_jedis");
                boolean lettuceRan = marker.containsKey("ran_lettuce");
                if (jedisRan && !lettuceRan) v.skip("falta experimentar com o Lettuce: ./quest run 201-01 lettuce");
                if (lettuceRan && !jedisRan) v.skip("falta experimentar com o Jedis: ./quest run 201-01 jedis");
                if (jedisRan && lettuceRan) v.pass("os dois clients criaram o mesmo índice e rodaram as mesmas consultas");
            }
        }
    }
}
