package com.emberrealm.quest.lessons.l201_02;

import com.emberrealm.quest.core.Check;
import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Verdict;
import com.emberrealm.quest.lessons.l201_01.ItemsIndex;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.search.aggr.AggregationBuilder;
import redis.clients.jedis.search.aggr.AggregationResult;
import redis.clients.jedis.search.aggr.Reducers;

import java.util.Map;

/** 201-02: the index exists, a real GROUPBY on rarity yields five groups, and the marker agrees. */
public final class LessonCheck implements Check {

    @Override
    public void run(Ctx ctx, Verdict v) {
        String index = ItemsIndex.name(ctx);
        try (RedisClient jedis = Clients.jedis()) {
            boolean exists = ItemsIndex.existsJedis(jedis, index);
            v.expect(exists, "o índice " + index + " existe",
                    "rode: ./quest run 201-01 jedis (ou a própria 201-02, que cria o índice se faltar)");
            if (exists) {
                AggregationResult byRarity = jedis.ftAggregate(index,
                        new AggregationBuilder("*").groupBy("@rarity", Reducers.count().as("n")));
                int groups = byRarity.getRows().size();
                v.expect(groups == 5, "FT.AGGREGATE agrupa os 42 itens em " + groups + " raridades",
                        "esperava 5 grupos (comum, incomum, raro, epico, lendario); rode ./quest seed e a lição de novo");
            }

            Map<String, String> marker = jedis.hgetAll(ctx.progressKey());
            v.expect(!marker.isEmpty(), "a lição rodou e gravou o marcador",
                    "rode: ./quest run 201-02 jedis (ou lettuce)");
            if (!marker.isEmpty()) {
                v.expect("5".equals(marker.get("groups")), "o marcador registrou groups = 5",
                        "o GROUPBY da lição devolveu " + marker.get("groups") + " grupos; rode ./quest seed e a lição de novo");
                v.pass("tipos agrupados: " + marker.getOrDefault("types", "?")
                        + ", itens de nível 40 ou mais: " + marker.getOrDefault("veterans", "?"));
                boolean jedisRan = marker.containsKey("ran_jedis");
                boolean lettuceRan = marker.containsKey("ran_lettuce");
                if (jedisRan && !lettuceRan) v.info("Opcional: experimente com o Lettuce: ./quest run 201-02 lettuce");
                if (lettuceRan && !jedisRan) v.info("Opcional: experimente com o Jedis: ./quest run 201-02 jedis");
                if (jedisRan && lettuceRan) v.pass("os dois clients rodaram as mesmas agregações");
            }
        }
    }
}
