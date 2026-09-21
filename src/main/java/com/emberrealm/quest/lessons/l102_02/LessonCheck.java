package com.emberrealm.quest.lessons.l102_02;

import com.emberrealm.quest.core.Check;
import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Verdict;
import redis.clients.jedis.RedisClient;

import java.util.Map;

public final class LessonCheck implements Check {

    @Override
    public void run(Ctx ctx, Verdict v) {
        String stream = ctx.k("events", "combat");
        try (RedisClient jedis = Clients.jedis()) {
            String type = jedis.type(stream);
            v.expect("stream".equals(type), stream + " é um Stream",
                    "tipo atual: " + type + ". Rode: ./quest run 102-02 jedis (ou lettuce)");
            if ("stream".equals(type)) {
                long length = jedis.xlen(stream);
                v.expect(length == CombatEvents.COUNT, "o diário tem " + CombatEvents.COUNT + " golpes (XLEN)",
                        "XLEN devolveu " + length + ". Rode a lição de novo: ela recria o diário do zero.");
            }

            Map<String, String> marker = jedis.hgetAll(ctx.progressKey());
            v.expect(!marker.isEmpty(), "a lição rodou e gravou o marcador", "rode: ./quest run 102-02 jedis (ou lettuce)");
            if (marker.isEmpty()) return;
            if (marker.get("first_id") != null && marker.get("last_id") != null) {
                v.pass("ids do primeiro ao último golpe: " + marker.get("first_id") + " .. " + marker.get("last_id"));
            }

            boolean jedisRan = "ok".equals(marker.get("jedis"));
            boolean lettuceRan = "ok".equals(marker.get("lettuce"));
            if (jedisRan && !lettuceRan) v.info("Opcional: experimente com o Lettuce: ./quest run 102-02 lettuce");
            if (lettuceRan && !jedisRan) v.info("Opcional: experimente com o Jedis: ./quest run 102-02 jedis");
            if (jedisRan && lettuceRan) v.pass("os dois clients escreveram e leram o diário");
        }
    }
}
