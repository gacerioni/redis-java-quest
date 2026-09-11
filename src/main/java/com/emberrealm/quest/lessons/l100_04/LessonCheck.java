package com.emberrealm.quest.lessons.l100_04;

import com.emberrealm.quest.core.Check;
import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Verdict;
import redis.clients.jedis.RedisClient;

import java.util.Map;
import java.util.Set;

/** A session key must exist under {p}:session:* as a STRING with a positive TTL, plus the marker. */
public final class LessonCheck implements Check {

    @Override
    public void run(Ctx ctx, Verdict v) {
        String pattern = ctx.k("session", "*");
        try (RedisClient jedis = Clients.jedis()) {
            Set<String> sessions = JedisLab.scanAll(jedis, pattern);
            v.expect(!sessions.isEmpty(),
                    "existe uma sessão em " + pattern,
                    "rode: ./quest run 100-04 jedis (ou lettuce). A sessão dura 30 minutos; se passou disso, rode de novo");
            for (String key : sessions) {
                String type = jedis.type(key);
                long ttl = jedis.ttl(key);
                v.expect("string".equals(type), key + " é uma STRING", "a sessão deveria ser uma STRING criada com SET ... EX");
                if (ttl == -1) {
                    v.fail(key + " está sem TTL", "a lição termina com EXPIRE; uma sessão sem prazo nunca sai da memória");
                } else {
                    v.expect(ttl > 0, key + " expira em " + ttl + " s", "a sessão expirou; rode a lição de novo para criar uma nova");
                }
            }

            Map<String, String> marker = jedis.hgetAll(ctx.progressKey());
            v.expect(!marker.isEmpty(), "a lição rodou e gravou o marcador", "rode: ./quest run 100-04 jedis (ou lettuce)");
            if (!marker.isEmpty()) {
                v.pass("SCAN percorreu " + marker.get("keys") + " chaves em " + marker.get("scan_pages") + " página(s), sem KEYS");
                if ("jedis".equals(marker.get("client"))) v.skip("falta experimentar com o Lettuce: ./quest run 100-04 lettuce");
            }
        }
    }
}
