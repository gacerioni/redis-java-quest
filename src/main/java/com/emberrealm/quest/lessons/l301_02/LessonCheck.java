package com.emberrealm.quest.lessons.l301_02;

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
            v.expect(!marker.isEmpty(), "a lição rodou e gravou o marcador", "rode: ./quest run 301-02 jedis (ou lettuce)");
            if (marker.isEmpty()) return;

            long hits = parse(marker.get("hits"));
            v.expect(hits > 900, "o cache local respondeu " + hits + " das leituras (misses=" + marker.get("misses") + ")",
                    "menos de 900 hits: o client precisa de RESP3 e de CacheConfig (Jedis) ou ClientSideCaching (Lettuce)");
            long invalidations = parse(marker.get("invalidations"));
            v.expect(invalidations >= 1, "o servidor mandou " + invalidations + " invalidação quando outro client mudou a chave",
                    "nenhuma invalidação chegou: a escrita precisa vir de outro client depois da leitura em cache");
            if ("stale".equals(marker.get("fresh"))) {
                v.skip("a última leitura pegou o valor antigo: a invalidação chegou atrasada, rode de novo para ver o valor novo");
            }

            String motd = ctx.k("config", "motd");
            v.expect("string".equals(jedis.type(motd)), "a mensagem do dia existe em " + motd, "rode a lição de novo");

            Map<String, String> clients = jedis.hgetAll(ctx.k("csc", "clients"));
            boolean jedisRan = clients.containsKey("jedis");
            boolean lettuceRan = clients.containsKey("lettuce");
            if (jedisRan && !lettuceRan) v.info("Opcional: experimente com o Lettuce: ./quest run 301-02 lettuce");
            if (lettuceRan && !jedisRan) v.info("Opcional: experimente com o Jedis: ./quest run 301-02 jedis");
            if (jedisRan && lettuceRan) v.pass("os dois clients usaram tracking do servidor: cache local sem TTL no chute");
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
