package com.emberrealm.quest.lessons.l102_04;

import com.emberrealm.quest.core.Check;
import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Verdict;
import redis.clients.jedis.RedisClient;

import java.util.Map;

public final class LessonCheck implements Check {

    @Override
    public void run(Ctx ctx, Verdict v) {
        String queue = ctx.k("queue", "raid");
        String waiterName = ctx.keys.prefix() + "-waiter";
        try (RedisClient jedis = Clients.jedis()) {
            long left = jedis.exists(queue) ? jedis.llen(queue) : 0;
            v.expect(left == 0, "a fila " + queue + " está vazia: todo mundo que esperava entrou na dungeon",
                    "sobraram " + left + " vagas na fila. Rode a lição de novo: ela limpa e refaz a fila.");

            Map<String, String> marker = jedis.hgetAll(ctx.progressKey());
            v.expect(!marker.isEmpty(), "a lição rodou e gravou o marcador", "rode: ./quest run 102-04 jedis (ou lettuce)");
            if (marker.isEmpty()) return;

            int waiters = parse(marker.get("waiters"));
            v.expect(waiters >= 1, "a lição registrou " + marker.get("waiters") + " jogadores em espera (QUEST_WAITERS)",
                    "o marcador não tem o campo waiters. Rode a lição de novo.");
            if (marker.get("served") != null) {
                v.pass(marker.get("served") + " acordaram com uma vaga, " + marker.getOrDefault("rejected", "0")
                        + " barrados por max number of clients");
            }
            if (marker.get("stall_ms") != null) {
                v.pass("Lettuce: PING atrás de um BLPOP na conexão compartilhada levou " + marker.get("stall_ms")
                        + " ms; com BLPOP em conexão dedicada, " + marker.get("dedicated_ms") + " ms");
            }

            try {
                long leaked = Waiters.countLines(JedisLab.clientList(jedis), "name=" + waiterName);
                v.expect(leaked == 0, "nenhuma conexão de jogador ficou aberta (CLIENT LIST sem name=" + waiterName + ")",
                        "há " + leaked + " conexões name=" + waiterName + " abertas: outra execução ainda está rodando ou o pool não foi fechado.");
            } catch (Exception e) {
                v.skip("CLIENT LIST não está liberado para este usuário; pulei a conferência de conexões abertas");
            }

            boolean jedisRan = "ok".equals(marker.get("jedis"));
            boolean lettuceRan = "ok".equals(marker.get("lettuce"));
            if (jedisRan && !lettuceRan) v.skip("falta experimentar com o Lettuce: ./quest run 102-04 lettuce");
            if (lettuceRan && !jedisRan) v.skip("falta experimentar com o Jedis: ./quest run 102-04 jedis");
            if (jedisRan && lettuceRan) v.pass("os dois clients esperaram, acordaram e fecharam tudo");
        }
    }

    private static int parse(String value) {
        try {
            return value == null ? -1 : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
