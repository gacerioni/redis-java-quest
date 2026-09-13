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

            String ticksKey = ctx.k("maint", "ticks");
            String ticks = jedis.get(ticksKey);
            v.expect(String.valueOf(JedisLab.TICKS).equals(ticks), "os " + JedisLab.TICKS + " comandos da raid chegaram (" + ticksKey + " = " + ticks + ")",
                    "esperado " + JedisLab.TICKS + " em " + ticksKey + ": rode a lição de novo");

            String sch = marker.get("sch");
            if ("on".equals(sch)) v.pass("o servidor aceitou CLIENT MAINT_NOTIFICATIONS: SCH ativo nesta conexão Lettuce");
            else if (sch != null) v.pass("o servidor respondeu sobre SCH (" + sch + "): em Redis Open Source o recurso não existe, e o lab seguiu normal");

            Map<String, String> clients = jedis.hgetAll(ctx.k("maint", "clients"));
            boolean jedisRan = clients.containsKey("jedis");
            boolean lettuceRan = clients.containsKey("lettuce");
            if (jedisRan && !lettuceRan) v.skip("falta ver o lado que tem SCH: ./quest run 301-04 lettuce");
            if (lettuceRan && !jedisRan) v.skip("falta ver as alternativas do Jedis: ./quest run 301-04 jedis");
            if (jedisRan && lettuceRan) v.pass("os dois clients rodaram: Lettuce pedindo SCH no handshake, Jedis com retry e health check do pool");
        }
    }
}
