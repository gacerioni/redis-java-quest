package com.emberrealm.quest.lessons.l100_03;

import com.emberrealm.quest.core.Check;
import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Verdict;
import com.emberrealm.quest.world.World;
import redis.clients.jedis.RedisClient;

import java.util.Map;

/** The seed must be in place (the tour has nothing to show otherwise) and the visit marker must exist. */
public final class LessonCheck implements Check {

    @Override
    public void run(Ctx ctx, Verdict v) {
        int players = World.players().size();
        int items = World.items().size();
        try (RedisClient jedis = Clients.jedis()) {
            long seededPlayers = jedis.scard(ctx.k("players"));
            v.expect(seededPlayers == players,
                    "o mundo está semeado: " + players + " personagens em " + ctx.k("players"),
                    "rode: ./quest seed (encontrei " + seededPlayers + " personagens)");
            String sampleItem = ctx.k("item", "espada-de-brasa");
            v.expect("ReJSON-RL".equals(jedis.type(sampleItem)),
                    "os " + items + " itens são documentos JSON (exemplo: " + sampleItem + ")",
                    "rode: ./quest seed");
            v.expect("zset".equals(jedis.type(ctx.k("rank", "xp"))),
                    "o ranking " + ctx.k("rank", "xp") + " é um sorted set",
                    "rode: ./quest seed");
            v.expect("zset".equals(jedis.type(ctx.k("zone", "geo"))),
                    "o índice GEO " + ctx.k("zone", "geo") + " existe (por dentro, um sorted set)",
                    "rode: ./quest seed");

            String visited = ctx.k("tour", "visited");
            v.expect("string".equals(jedis.type(visited)),
                    "o marco da visita " + visited + " foi gravado",
                    "rode: ./quest run 100-03 jedis (ou lettuce)");

            Map<String, String> marker = jedis.hgetAll(ctx.progressKey());
            v.expect(!marker.isEmpty(), "a lição rodou e gravou o marcador", "rode: ./quest run 100-03 jedis (ou lettuce)");
            if (!marker.isEmpty()) {
                v.pass("o tour encontrou " + marker.get("keys") + " chaves: " + marker.get("items") + " itens JSON, "
                        + marker.get("players") + " fichas de personagem, " + marker.get("types") + " tipos diferentes");
                if ("jedis".equals(marker.get("client"))) v.skip("falta experimentar com o Lettuce: ./quest run 100-03 lettuce");
            }
        }
    }
}
