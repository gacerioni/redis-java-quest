package com.emberrealm.quest.lessons.l100_03;

import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Lab;
import io.lettuce.core.KeyScanCursor;
import io.lettuce.core.ScanArgs;
import io.lettuce.core.ScanCursor;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;

/** 100-03 (Lettuce): the same tour; the cursor is an object (ScanCursor) instead of a string. */
public final class LettuceLab implements Lab {

    @Override
    public void run(Ctx ctx) {
        String visited = ctx.k("tour", "visited");
        String pattern = ctx.keys.pattern();
        try (StatefulRedisConnection<String, String> connection = Clients.lettuceConnection()) {
            RedisCommands<String, String> redis = connection.sync();

            ctx.out.step("Apagando o marco da visita anterior (a lição pode rodar quantas vezes quiser)");
            ctx.out.cmd("UNLINK " + visited);
            redis.unlink(visited);

            ctx.out.step("Percorrendo o reino com SCAN, página por página");
            ctx.out.cmd("SCAN 0 MATCH " + pattern + " COUNT 100");
            Set<String> keys = new TreeSet<>();
            ScanArgs args = ScanArgs.Builder.matches(pattern).limit(100);
            ScanCursor cursor = ScanCursor.INITIAL;
            int pages = 0;
            do {
                KeyScanCursor<String> page = redis.scan(cursor, args);
                keys.addAll(page.getKeys());
                cursor = page;
                pages++;
            } while (!cursor.isFinished());
            ctx.out.kv("chaves com o prefixo " + ctx.keys.prefix(), keys.size() + " (em " + pages + " página(s) de SCAN)");
            if (!keys.contains(ctx.k("players"))) throw new IllegalStateException("Rode ./quest seed primeiro");

            ctx.out.step("TYPE em cada chave: o que mora em cada canto do mapa");
            ctx.out.cmd("TYPE " + ctx.k("item", "espada-de-brasa"));
            ctx.out.kv("TYPE", redis.type(ctx.k("item", "espada-de-brasa")));
            Tour tour = new Tour(ctx.keys.prefix());
            for (String key : keys) tour.add(key, redis.type(key));
            ctx.out.info("Agrupando pela entidade (o pedaço logo depois do prefixo), como a árvore do Browser faz:");
            tour.print(ctx);
            ctx.out.info("Itens são documentos JSON, personagens e zonas são hashes, conquistas são sets, o ranking é um sorted set.");
            ctx.out.info("O índice GEO das zonas aparece como sorted set: por dentro, GEO é um zset com a posição codificada no score.");

            ctx.out.step("Deixando um marco da visita");
            String now = Instant.now().toString();
            ctx.out.cmd("SET " + visited + " " + now);
            ctx.out.kv("SET", redis.set(visited, now));
            ctx.out.info("Abra o Redis Insight, filtre por " + pattern + " no Browser e procure " + visited + ".");

            ctx.done("keys", String.valueOf(tour.total()),
                    "items", String.valueOf(tour.count("item", "ReJSON-RL")),
                    "players", String.valueOf(tour.count("player", "hash")),
                    "types", String.valueOf(tour.distinctTypes()));
        }
    }
}
