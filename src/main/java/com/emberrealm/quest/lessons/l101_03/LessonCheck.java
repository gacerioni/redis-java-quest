package com.emberrealm.quest.lessons.l101_03;

import com.emberrealm.quest.core.Check;
import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Verdict;
import redis.clients.jedis.RedisClient;

import java.util.List;
import java.util.Map;

/** Verifies the List lesson: the dungeon queue is a list with 3 players left, the raid queue stayed empty, marker present. */
public final class LessonCheck implements Check {

    @Override
    public void run(Ctx ctx, Verdict v) {
        String queue = ctx.k("queue", "dungeon");
        String raid = ctx.k("queue", "raid");
        long expectedWaiting = JedisLab.ARRIVALS.size() - JedisLab.PARTY_SIZE;

        try (RedisClient jedis = Clients.jedis()) {
            Map<String, String> marker = jedis.hgetAll(ctx.progressKey());
            v.expect(!marker.isEmpty(), "a lição rodou e gravou o marcador", "rode: ./quest run 101-03 jedis (ou lettuce)");

            v.expect("list".equals(jedis.type(queue)), queue + " é uma List", "rode a lição: ./quest run 101-03 jedis");

            long waiting = jedis.llen(queue);
            v.expect(waiting == expectedWaiting,
                    "a fila tem " + expectedWaiting + " aventureiros esperando (" + JedisLab.ARRIVALS.size() + " entraram, " + JedisLab.PARTY_SIZE + " saíram na party)",
                    "tamanho atual: " + waiting + ". Rode a lição de novo, ela recria a fila do zero");

            if (waiting == expectedWaiting) {
                List<String> line = jedis.lrange(queue, 0, -1);
                v.pass("ordem de chegada preservada: " + String.join(", ", line) + " (LPOS " + line.get(0) + " = 0 agora)");
            }

            v.expect(!jedis.exists(raid), "a fila da raid segue vazia: o BRPOP voltou sem ninguém",
                    "algo escreveu em " + raid + "; rode a lição de novo");

            boolean jedisRan = marker.containsKey("ran_jedis");
            boolean lettuceRan = marker.containsKey("ran_lettuce");
            if (jedisRan && !lettuceRan) v.skip("falta experimentar com o Lettuce: ./quest run 101-03 lettuce");
            if (lettuceRan && !jedisRan) v.skip("falta experimentar com o Jedis: ./quest run 101-03 jedis");
            if (jedisRan && lettuceRan) v.pass("os dois clients rodaram a lição: mesmas chaves, mesmo resultado");
        }
    }
}
