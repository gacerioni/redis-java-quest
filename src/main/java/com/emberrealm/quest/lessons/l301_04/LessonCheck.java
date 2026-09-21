package com.emberrealm.quest.lessons.l301_04;

import com.emberrealm.quest.core.Check;
import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Verdict;
import redis.clients.jedis.RedisClient;

import java.util.Map;

public final class LessonCheck implements Check {

    @Override
    public void run(Ctx ctx, Verdict v) {
        try (RedisClient jedis = Clients.jedis()) {
            Map<String, String> marker = jedis.hgetAll(ctx.progressKey());
            v.expect(!marker.isEmpty(), "a lição rodou e gravou o marcador", "rode: ./quest run 301-04 lettuce (ou jedis)");
            if (marker.isEmpty()) return;

            String operationsKey = ctx.k("maint", "operations");
            long applied = jedis.scard(operationsKey);
            v.expect(applied == JedisLab.OPERATIONS, "as " + JedisLab.OPERATIONS + " operações distintas estão no set (" + operationsKey + " = " + applied + ")",
                    "esperado " + JedisLab.OPERATIONS + " IDs em " + operationsKey + ": rode a lição de novo");

            String sch = marker.get("sch");
            if ("on".equals(sch)) v.pass("o servidor aceitou CLIENT MAINT_NOTIFICATIONS nesta conexão Lettuce");
            else v.info("Negociação SCH: " + sch + ". Isto não comprova a ocorrência de manutenção.");
            v.info("Avisos de manutenção observados: " + marker.getOrDefault("maintenance_events", "não registrados"));

            Map<String, String> clients = jedis.hgetAll(ctx.k("maint", "clients"));
            boolean jedisRan = clients.containsKey("jedis");
            boolean lettuceRan = clients.containsKey("lettuce");
            if (jedisRan && !lettuceRan) v.info("Opcional: veja o lado que tem SCH: ./quest run 301-04 lettuce");
            if (lettuceRan && !jedisRan) v.info("Opcional: veja as alternativas do Jedis: ./quest run 301-04 jedis");
            if (jedisRan && lettuceRan) v.pass("os dois clients rodaram: Lettuce pedindo SCH no handshake, Jedis com retry e health check do pool");
        }
    }
}
