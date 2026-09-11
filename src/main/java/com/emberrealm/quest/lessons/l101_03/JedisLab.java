package com.emberrealm.quest.lessons.l101_03;

import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Lab;
import redis.clients.jedis.RedisClient;

import java.util.List;

/**
 * 101-03 (Jedis): the dungeon queue is a List. Players enter at the tail (RPUSH), the matchmaker takes
 * them from the head (LPOP), LPOS finds someone's place, and a 1 second BRPOP on an empty queue previews
 * what a blocking command does to a connection (the full story is lesson 102-04).
 */
public final class JedisLab implements Lab {

    static final List<String> ARRIVALS = List.of("brom", "lyra", "nix", "seraphine", "ysolde");
    static final int PARTY_SIZE = 2;
    static final int BLOCK_SECONDS = 1;

    @Override
    public void run(Ctx ctx) {
        String queue = ctx.k("queue", "dungeon");
        String raid = ctx.k("queue", "raid");

        try (RedisClient jedis = Clients.jedis()) {
            ctx.out.step("Limpando as filas (pode rodar quantas vezes quiser)");
            ctx.out.cmd("UNLINK " + queue + " " + raid);
            jedis.unlink(queue, raid);

            ctx.out.step("Cinco aventureiros entram na fila da dungeon, em ordem de chegada");
            ctx.out.cmd("RPUSH " + queue + " " + String.join(" ", ARRIVALS));
            long queued = jedis.rpush(queue, ARRIVALS.toArray(new String[0]));
            ctx.out.kv("na fila", queued);
            ctx.out.info("RPUSH coloca no fim (direita). Quem chegou primeiro fica na frente (esquerda).");

            ctx.out.step("Quem está esperando?");
            ctx.out.cmd("LLEN " + queue);
            ctx.out.kv("LLEN", jedis.llen(queue));
            ctx.out.cmd("LRANGE " + queue + " 0 -1");
            ctx.out.kv("fila", jedis.lrange(queue, 0, -1));
            ctx.out.cmd("LPOS " + queue + " nix");
            ctx.out.kv("posição de nix", jedis.lpos(queue, "nix"));
            ctx.out.info("Índice 2, contando do zero: Nix é o terceiro da fila. LPOS varre a lista, O(n): bom para filas curtas.");

            ctx.out.step("O matchmaker forma uma party com os " + PARTY_SIZE + " primeiros");
            ctx.out.cmd("LPOP " + queue + " " + PARTY_SIZE);
            List<String> party = jedis.lpop(queue, PARTY_SIZE);
            ctx.out.kv("party", party);
            ctx.out.cmd("LLEN " + queue);
            long waiting = jedis.llen(queue);
            ctx.out.kv("ainda esperando", waiting);
            ctx.out.cmd("LRANGE " + queue + " 0 -1");
            ctx.out.kv("fila", jedis.lrange(queue, 0, -1));
            ctx.out.info("LPOP tira da frente: FIFO. Quer uma pilha (LIFO)? RPUSH com RPOP, ou LPUSH com LPOP.");

            ctx.out.step("Prévia: BRPOP espera alguém entrar numa fila vazia (timeout de " + BLOCK_SECONDS + " s)");
            ctx.out.cmd("BRPOP " + raid + " " + BLOCK_SECONDS);
            long start = System.nanoTime();
            List<String> popped = jedis.brpop(BLOCK_SECONDS, raid);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            ctx.out.kv("BRPOP", popped == null ? "(nil) depois de " + elapsedMs + " ms" : popped);
            ctx.out.info("A conexão ficou presa esperando, sem servir mais nada. No plano free (30 conexões) isso pesa: lição 102-04.");
            ctx.out.hint("Timeout do bloqueio sempre menor que o socket timeout do client (Jedis: 2 s por padrão), senão a exceção chega antes da resposta.");

            ctx.done("queued", String.valueOf(queued),
                    "party", String.join(",", party),
                    "waiting", String.valueOf(waiting),
                    "ran_" + ctx.client, "1");
        }
    }
}
