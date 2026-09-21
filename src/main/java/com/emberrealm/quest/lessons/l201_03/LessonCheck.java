package com.emberrealm.quest.lessons.l201_03;

import com.emberrealm.quest.core.Check;
import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Verdict;
import com.emberrealm.quest.lessons.l201_01.ItemsIndex;
import com.emberrealm.quest.world.Vectors;
import com.emberrealm.quest.world.World;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.search.FTSearchParams;
import redis.clients.jedis.search.SearchResult;

import java.util.Map;

/** 201-03: the index answers a KNN query, and the marker says hybrid ran (ok) or the server lacks it (unsupported). */
public final class LessonCheck implements Check {

    @Override
    public void run(Ctx ctx, Verdict v) {
        String index = ItemsIndex.name(ctx);
        try (RedisClient jedis = Clients.jedis()) {
            boolean exists = ItemsIndex.existsJedis(jedis, index);
            v.expect(exists, "o índice " + index + " existe",
                    "rode: ./quest run 201-01 jedis (ou a própria 201-03, que cria o índice se faltar)");
            if (exists) {
                World.Query q1 = World.queries().get(0);
                SearchResult knn = jedis.ftSearch(index, "*=>[KNN 5 @embedding $vec AS score]",
                        FTSearchParams.searchParams()
                                .addParam("vec", Vectors.toBlob(q1.embedding()))
                                .noContent()
                                .dialect(2));
                v.expect(knn.getTotalResults() == 5,
                        "o campo VECTOR responde a KNN 5 com a pergunta \"" + q1.text() + "\"",
                        "o índice não tem o campo embedding ou os documentos mudaram; rode ./quest run 201-01 jedis para recriar");
            }

            Map<String, String> marker = jedis.hgetAll(ctx.progressKey());
            v.expect(!marker.isEmpty(), "a lição rodou e gravou o marcador",
                    "rode: ./quest run 201-03 jedis (ou lettuce)");
            if (!marker.isEmpty()) {
                String hybrid = marker.getOrDefault("hybrid", "?");
                v.expect(hybrid.equals("ok") || hybrid.equals("unsupported"),
                        "FT.HYBRID: " + hybrid + " (pergunta " + marker.getOrDefault("question", "?")
                                + ", topo KNN " + marker.getOrDefault("knn_top", "?")
                                + ", topo híbrido " + marker.getOrDefault("hybrid_top", "?") + ")",
                        "rode a lição de novo e leia o aviso impresso");
                if (hybrid.equals("unsupported")) {
                    v.skip("este servidor não tem FT.HYBRID (precisa de Redis 8.4+); a lição fechou com a parte KNN, teste no Redis Cloud quando puder");
                }
                boolean jedisRan = marker.containsKey("ran_jedis");
                boolean lettuceRan = marker.containsKey("ran_lettuce");
                if (jedisRan && !lettuceRan) v.info("Opcional: experimente com o Lettuce: ./quest run 201-03 lettuce");
                if (lettuceRan && !jedisRan) v.info("Opcional: experimente com o Jedis: ./quest run 201-03 jedis");
                if (jedisRan && lettuceRan) v.pass("os dois clients rodaram texto, KNN e híbrido");
            }
        }
    }
}
