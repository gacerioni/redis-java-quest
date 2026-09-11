package com.emberrealm.quest.lessons.l100_01;

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
            v.expect(!marker.isEmpty(), "o doctor rodou e gravou o marcador", "rode: ./quest run 100-01 jedis (ou lettuce)");
            if (!marker.isEmpty()) {
                v.pass("servidor Redis " + marker.get("redis_version") + ", RTT médio " + marker.get("rtt_ms") + " ms");
            }
        }
    }
}
