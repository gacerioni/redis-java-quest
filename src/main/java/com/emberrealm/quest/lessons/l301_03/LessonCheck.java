package com.emberrealm.quest.lessons.l301_03;

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
            v.expect(!marker.isEmpty(), "a lição rodou e gravou o marcador", "rode: ./quest run 301-03 jedis (ou lettuce)");
            if (marker.isEmpty()) return;

            String tls = marker.get("tls");
            if ("ok".equals(tls)) {
                v.expect(validatedTls(marker), "conexão TLS e hostname validados com " + marker.get("client") + " (" + marker.get("scheme") + "://, CA " + marker.get("ca") + ")",
                        "marcador antigo ou sem comprovação de TLS: configure rediss:// e rode a lição de novo");
            } else if ("skipped".equals(tls)) {
                v.skip("sem REDIS_TLS_URL a lição foi pulada: o plano free não tem TLS. Com um plano pago, configure a variável e rode de novo.");
            } else {
                v.fail("o marcador tem tls=" + tls + ", esperado ok ou skipped", "rode a lição de novo");
            }
        }
    }

    static boolean validatedTls(Map<String, String> marker) {
        return "ran".equals(marker.get("status")) && "ok".equals(marker.get("tls")) && "rediss".equals(marker.get("scheme"))
                && "2".equals(marker.get("tls_schema")) && "true".equals(marker.get("hostname_verified"));
    }
}
