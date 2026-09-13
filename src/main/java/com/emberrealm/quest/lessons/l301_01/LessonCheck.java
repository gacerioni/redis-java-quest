package com.emberrealm.quest.lessons.l301_01;

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
            v.expect(!marker.isEmpty(), "a lição rodou e gravou o marcador", "rode: ./quest run 301-01 jedis (ou lettuce)");
            if (marker.isEmpty()) return;

            long fastFailMs = parse(marker.get("fast_fail_ms"));
            v.expect(fastFailMs >= 0, "a falha rápida foi medida (fast_fail_ms=" + marker.get("fast_fail_ms") + ")",
                    "o marcador não tem fast_fail_ms: rode a lição de novo");
            if (fastFailMs >= 0) {
                v.expect(fastFailMs < 5000, "o datacenter fantasma falhou em " + fastFailMs + " ms, bem antes dos 5 s",
                        "o connect timeout não valeu: confira connectionTimeoutMillis (Jedis) ou SocketOptions.connectTimeout (Lettuce)");
            }

            String bossKey = ctx.k("boss", "spawn");
            v.expect(jedis.exists(bossKey), "o spawn do chefão foi gravado com retry em " + bossKey, "rode a lição de novo");

            Map<String, String> clients = jedis.hgetAll(ctx.k("ops", "clients"));
            boolean jedisRan = clients.containsKey("jedis");
            boolean lettuceRan = clients.containsKey("lettuce");
            if (jedisRan && !lettuceRan) v.skip("falta experimentar com o Lettuce: ./quest run 301-01 lettuce");
            if (lettuceRan && !jedisRan) v.skip("falta experimentar com o Jedis: ./quest run 301-01 jedis");
            if (jedisRan && lettuceRan) v.pass("os dois clients passaram pelo mesmo caminho: timeout, retry e falha rápida");
        }
    }

    static long parse(String s) {
        try {
            return s == null ? -1 : Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
