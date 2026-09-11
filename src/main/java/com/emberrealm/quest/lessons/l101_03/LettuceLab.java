package com.emberrealm.quest.lessons.l101_03;

import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Lab;
import io.lettuce.core.KeyValue;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

import java.util.List;

/**
 * 101-03 (Lettuce): same queue, same keys. The blocking BRPOP runs on a dedicated connection on purpose:
 * Lettuce multiplexes one connection for the whole app, and a blocking command there would stall every
 * other command queued behind it.
 */
public final class LettuceLab implements Lab {

    @Override
    public void run(Ctx ctx) {
        String queue = ctx.k("queue", "dungeon");
        String raid = ctx.k("queue", "raid");

        try (StatefulRedisConnection<String, String> connection = Clients.lettuceConnection()) {
            RedisCommands<String, String> redis = connection.sync();

            ctx.out.step("Limpando as filas (pode rodar quantas vezes quiser)");
            ctx.out.cmd("UNLINK " + queue + " " + raid);
            redis.unlink(queue, raid);

            ctx.out.step("Cinco aventureiros entram na fila da dungeon, em ordem de chegada");
            ctx.out.cmd("RPUSH " + queue + " " + String.join(" ", JedisLab.ARRIVALS));
            long queued = redis.rpush(queue, JedisLab.ARRIVALS.toArray(new String[0]));
            ctx.out.kv("na fila", queued);
            ctx.out.info("RPUSH coloca no fim (direita). Quem chegou primeiro fica na frente (esquerda).");

            ctx.out.step("Quem está esperando?");
            ctx.out.cmd("LLEN " + queue);
            ctx.out.kv("LLEN", redis.llen(queue));
            ctx.out.cmd("LRANGE " + queue + " 0 -1");
            ctx.out.kv("fila", redis.lrange(queue, 0, -1));
            ctx.out.cmd("LPOS " + queue + " nix");
            ctx.out.kv("posição de nix", redis.lpos(queue, "nix"));
            ctx.out.info("Índice 2, contando do zero: Nix é o terceiro da fila. LPOS varre a lista, O(n): bom para filas curtas.");

            ctx.out.step("O matchmaker forma uma party com os " + JedisLab.PARTY_SIZE + " primeiros");
            ctx.out.cmd("LPOP " + queue + " " + JedisLab.PARTY_SIZE);
            List<String> party = redis.lpop(queue, JedisLab.PARTY_SIZE);
            ctx.out.kv("party", party);
            ctx.out.cmd("LLEN " + queue);
            long waiting = redis.llen(queue);
            ctx.out.kv("ainda esperando", waiting);
            ctx.out.cmd("LRANGE " + queue + " 0 -1");
            ctx.out.kv("fila", redis.lrange(queue, 0, -1));
            ctx.out.info("LPOP tira da frente: FIFO. Quer uma pilha (LIFO)? RPUSH com RPOP, ou LPUSH com LPOP.");

            ctx.out.step("Prévia: BRPOP espera alguém entrar numa fila vazia (timeout de " + JedisLab.BLOCK_SECONDS + " s)");
            ctx.out.info("No Lettuce, bloqueio pede uma conexão dedicada: client.connect() só para isso. A compartilhada segue livre.");
            ctx.out.cmd("BRPOP " + raid + " " + JedisLab.BLOCK_SECONDS);
            long start = System.nanoTime();
            KeyValue<String, String> popped;
            try (StatefulRedisConnection<String, String> blocking = Clients.lettuceConnection()) {
                popped = blocking.sync().brpop(JedisLab.BLOCK_SECONDS, raid);
            }
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            boolean empty = popped == null || !popped.hasValue();
            ctx.out.kv("BRPOP", empty ? "(nil) depois de " + elapsedMs + " ms" : popped.getKey() + " -> " + popped.getValue());
            ctx.out.info("A conexão dedicada ficou presa esperando. No plano free (30 conexões) isso pesa: lição 102-04.");
            ctx.out.hint("Timeout do bloqueio sempre menor que o command timeout do Lettuce (60 s por padrão; 5 s neste curso).");

            ctx.done("queued", String.valueOf(queued),
                    "party", String.join(",", party),
                    "waiting", String.valueOf(waiting),
                    "ran_" + ctx.client, "1");
        }
    }
}
