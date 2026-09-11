package com.emberrealm.quest.lessons.l100_05;

import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Lab;
import com.emberrealm.quest.world.World;
import redis.clients.jedis.AbstractTransaction;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 100-05 (Jedis): a boss falls and drops 100 items. Round 1 sends one SET per drop (100 round trips).
 * Round 2 sends the same 100 SETs through a pipeline (one round trip) and prints the speedup.
 * Then MULTI/EXEC moves gold between two players atomically: both HINCRBYs run, or none does.
 */
public final class JedisLab implements Lab {

    static final int DROPS = 100;
    static final int GOLD = 100;

    @Override
    public void run(Ctx ctx) {
        String[] lootKeys = lootKeys(ctx);
        String[] lootValues = lootValues();
        String bromKey = ctx.k("player", "brom");
        String nixKey = ctx.k("player", "nix");
        World.Player brom = player("brom");
        World.Player nix = player("nix");
        try (RedisClient jedis = Clients.jedis()) {
            ctx.out.step("Limpando o chão da dungeon");
            ctx.out.cmd("UNLINK " + lootKeys[0] + " ... " + lootKeys[DROPS - 1] + "   (" + DROPS + " chaves em um comando)");
            jedis.unlink(lootKeys);

            ctx.out.step("Rodada 1: cem drops, um SET por vez (cada um espera a resposta antes do próximo)");
            ctx.out.cmd("SET " + lootKeys[0] + " " + lootValues[0] + "   (x" + DROPS + ")");
            long start = System.nanoTime();
            for (int i = 0; i < DROPS; i++) jedis.set(lootKeys[i], lootValues[i]);
            double oneByOneMs = elapsedMs(start);
            ctx.out.kv("tempo, " + DROPS + " idas e voltas", ms(oneByOneMs));

            ctx.out.step("Rodada 2: os mesmos cem drops em um pipeline (manda tudo, depois lê tudo)");
            jedis.unlink(lootKeys);
            ctx.out.cmd("(pipeline) SET " + lootKeys[0] + " ... SET " + lootKeys[DROPS - 1] + "   (1 ida e volta)");
            start = System.nanoTime();
            List<Response<String>> replies = new ArrayList<>(DROPS);
            try (Pipeline pipeline = jedis.pipelined()) {
                for (int i = 0; i < DROPS; i++) replies.add(pipeline.set(lootKeys[i], lootValues[i]));
                pipeline.sync();
            }
            double pipelineMs = elapsedMs(start);
            long oks = replies.stream().filter(r -> "OK".equals(r.get())).count();
            ctx.out.kv("tempo, 1 ida e volta", ms(pipelineMs));
            ctx.out.kv("respostas OK", oks + "/" + DROPS);
            double speedup = pipelineMs > 0 ? oneByOneMs / pipelineMs : 0;
            ctx.out.kv("aceleração", String.format(Locale.ROOT, "%.1fx", speedup));
            ctx.out.info("Antes do sync() cada Response está vazio; o sync() envia tudo, lê as " + DROPS + " respostas e preenche os objetos.");
            ctx.out.info("Localmente a diferença já aparece; contra o Redis Cloud, com 1 ms de rede por comando, ela passa de 50x.");
            ctx.out.info("Pipeline junta viagens, não garante atomicidade: outro cliente pode escrever entre dois SETs seus.");

            ctx.out.step("MULTI/EXEC: Brom paga " + GOLD + " de ouro à Nix, tudo ou nada");
            ctx.out.cmd("HSET " + bromKey + " gold " + brom.gold() + "   (valor do seed, para a lição ser repetível)");
            jedis.hset(bromKey, "gold", String.valueOf(brom.gold()));
            ctx.out.cmd("HSET " + nixKey + " gold " + nix.gold());
            jedis.hset(nixKey, "gold", String.valueOf(nix.gold()));

            ctx.out.cmd("MULTI");
            ctx.out.cmd("HINCRBY " + bromKey + " gold -" + GOLD);
            ctx.out.cmd("HINCRBY " + nixKey + " gold " + GOLD);
            ctx.out.cmd("EXEC");
            try (AbstractTransaction tx = jedis.multi()) {
                Response<Long> bromGold = tx.hincrBy(bromKey, "gold", -GOLD);
                Response<Long> nixGold = tx.hincrBy(nixKey, "gold", GOLD);
                List<Object> results = tx.exec();
                ctx.out.kv("EXEC", results);
                ctx.out.kv("ouro do Brom", bromGold.get());
                ctx.out.kv("ouro da Nix", nixGold.get());
            }
            ctx.out.info("Entre MULTI e EXEC os comandos ficam na fila; o EXEC roda todos de uma vez, sem outro cliente no meio.");
            ctx.out.info("A soma do ouro (" + (brom.gold() + nix.gold()) + ") não muda: ninguém vê o Brom mais pobre sem a Nix mais rica.");

            ctx.done("loot", String.valueOf(DROPS),
                    "one_by_one_ms", String.format(Locale.ROOT, "%.1f", oneByOneMs),
                    "pipeline_ms", String.format(Locale.ROOT, "%.1f", pipelineMs),
                    "speedup", String.format(Locale.ROOT, "%.1f", speedup));
        }
    }

    static String[] lootKeys(Ctx ctx) {
        String[] keys = new String[DROPS];
        for (int i = 0; i < DROPS; i++) keys[i] = ctx.k("loot", String.valueOf(i + 1));
        return keys;
    }

    /** Each drop is an item id from the world, cycling through the catalog. */
    static String[] lootValues() {
        List<World.Item> items = World.items();
        String[] values = new String[DROPS];
        for (int i = 0; i < DROPS; i++) values[i] = items.get(i % items.size()).id();
        return values;
    }

    static World.Player player(String id) {
        return World.players().stream().filter(p -> p.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalStateException("player " + id + " is missing from the world data"));
    }

    static double elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000.0;
    }

    static String ms(double value) {
        return String.format(Locale.ROOT, "%.1f ms", value);
    }
}
