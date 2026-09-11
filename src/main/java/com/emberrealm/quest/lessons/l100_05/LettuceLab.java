package com.emberrealm.quest.lessons.l100_05;

import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Lab;
import com.emberrealm.quest.world.World;
import io.lettuce.core.LettuceFutures;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.TransactionResult;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 100-05 (Lettuce): same drops, same gold. Lettuce already writes async commands as they come; turning
 * auto-flush off makes the batch explicit: nothing leaves the socket until flushCommands().
 * The transaction runs on the async API because every reply only arrives when EXEC runs.
 */
public final class LettuceLab implements Lab {

    @Override
    public void run(Ctx ctx) throws Exception {
        String[] lootKeys = JedisLab.lootKeys(ctx);
        String[] lootValues = JedisLab.lootValues();
        String bromKey = ctx.k("player", "brom");
        String nixKey = ctx.k("player", "nix");
        World.Player brom = JedisLab.player("brom");
        World.Player nix = JedisLab.player("nix");
        try (StatefulRedisConnection<String, String> connection = Clients.lettuceConnection()) {
            RedisCommands<String, String> sync = connection.sync();
            RedisAsyncCommands<String, String> async = connection.async();

            ctx.out.step("Limpando o chão da dungeon");
            ctx.out.cmd("UNLINK " + lootKeys[0] + " ... " + lootKeys[JedisLab.DROPS - 1] + "   (" + JedisLab.DROPS + " chaves em um comando)");
            sync.unlink(lootKeys);

            ctx.out.step("Rodada 1: cem drops, um SET síncrono por vez (cada um espera a resposta)");
            ctx.out.cmd("SET " + lootKeys[0] + " " + lootValues[0] + "   (x" + JedisLab.DROPS + ")");
            long start = System.nanoTime();
            for (int i = 0; i < JedisLab.DROPS; i++) sync.set(lootKeys[i], lootValues[i]);
            double oneByOneMs = JedisLab.elapsedMs(start);
            ctx.out.kv("tempo, " + JedisLab.DROPS + " idas e voltas", JedisLab.ms(oneByOneMs));

            ctx.out.step("Rodada 2: auto-flush desligado, cem SETs assíncronos, um flushCommands()");
            sync.unlink(lootKeys);
            ctx.out.cmd("(pipeline) SET " + lootKeys[0] + " ... SET " + lootKeys[JedisLab.DROPS - 1] + "   (1 ida e volta)");
            start = System.nanoTime();
            List<RedisFuture<String>> futures = new ArrayList<>(JedisLab.DROPS);
            connection.setAutoFlushCommands(false);
            try {
                for (int i = 0; i < JedisLab.DROPS; i++) futures.add(async.set(lootKeys[i], lootValues[i]));
                connection.flushCommands();
                boolean allDone = LettuceFutures.awaitAll(Duration.ofSeconds(5), futures.toArray(new RedisFuture<?>[0]));
                if (!allDone) ctx.out.warn("Nem todas as respostas chegaram em 5 s; confira a rede até o Redis.");
            } finally {
                connection.setAutoFlushCommands(true);
            }
            double pipelineMs = JedisLab.elapsedMs(start);
            long oks = 0;
            for (RedisFuture<String> reply : futures) if ("OK".equals(reply.get())) oks++;
            ctx.out.kv("tempo, 1 ida e volta", JedisLab.ms(pipelineMs));
            ctx.out.kv("respostas OK", oks + "/" + JedisLab.DROPS);
            double speedup = pipelineMs > 0 ? oneByOneMs / pipelineMs : 0;
            ctx.out.kv("aceleração", String.format(Locale.ROOT, "%.1fx", speedup));
            ctx.out.info("Sem auto-flush, os SETs se acumulam no buffer da conexão; flushCommands() empurra tudo de uma vez.");
            ctx.out.info("Dica: só desligue o auto-flush em uma conexão dedicada. Numa conexão compartilhada, outras threads ficariam presas no buffer.");
            ctx.out.info("Pipeline junta viagens, não garante atomicidade: outro cliente pode escrever entre dois SETs seus.");

            ctx.out.step("MULTI/EXEC: Brom paga " + JedisLab.GOLD + " de ouro à Nix, tudo ou nada");
            ctx.out.cmd("HSET " + bromKey + " gold " + brom.gold() + "   (valor do seed, para a lição ser repetível)");
            sync.hset(bromKey, "gold", String.valueOf(brom.gold()));
            ctx.out.cmd("HSET " + nixKey + " gold " + nix.gold());
            sync.hset(nixKey, "gold", String.valueOf(nix.gold()));

            ctx.out.cmd("MULTI");
            ctx.out.cmd("HINCRBY " + bromKey + " gold -" + JedisLab.GOLD);
            ctx.out.cmd("HINCRBY " + nixKey + " gold " + JedisLab.GOLD);
            ctx.out.cmd("EXEC");
            async.multi();
            RedisFuture<Long> bromGold = async.hincrby(bromKey, "gold", -JedisLab.GOLD);
            RedisFuture<Long> nixGold = async.hincrby(nixKey, "gold", JedisLab.GOLD);
            TransactionResult result = async.exec().get(5, TimeUnit.SECONDS);
            ctx.out.kv("EXEC", result.size() + " respostas, descartada: " + result.wasDiscarded());
            ctx.out.kv("ouro do Brom", bromGold.get());
            ctx.out.kv("ouro da Nix", nixGold.get());
            ctx.out.info("No Lettuce a transação vive na API assíncrona: cada comando devolve um future que só completa no EXEC.");
            ctx.out.info("A soma do ouro (" + (brom.gold() + nix.gold()) + ") não muda: ninguém vê o Brom mais pobre sem a Nix mais rica.");

            ctx.done("loot", String.valueOf(JedisLab.DROPS),
                    "one_by_one_ms", String.format(Locale.ROOT, "%.1f", oneByOneMs),
                    "pipeline_ms", String.format(Locale.ROOT, "%.1f", pipelineMs),
                    "speedup", String.format(Locale.ROOT, "%.1f", speedup));
        }
    }
}
