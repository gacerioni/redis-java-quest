package com.emberrealm.quest.lessons.l102_01;

import com.emberrealm.quest.core.Check;
import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Verdict;
import redis.clients.jedis.RedisClient;

import java.util.Map;

public final class LessonCheck implements Check {

    @Override
    public void run(Ctx ctx, Verdict v) {
        String channel = ctx.k("chat", "zone", JedisLab.ZONE);
        try (RedisClient jedis = Clients.jedis()) {
            Map<String, String> marker = jedis.hgetAll(ctx.progressKey());
            v.expect(!marker.isEmpty(), "a lição rodou e gravou o marcador", "rode: ./quest run 102-01 jedis (ou lettuce)");
            if (marker.isEmpty()) return;

            v.expect("5".equals(marker.get("received")),
                    "a thread assinante recebeu as 5 mensagens do chat",
                    "recebidas: " + marker.get("received") + ". Rode a lição de novo; se faltar mensagem, veja se algo mais publica no canal.");
            v.expect("1".equals(marker.get("lost")),
                    "a mensagem publicada antes do SUBSCRIBE se perdeu (0 receptores)",
                    "alguém já estava assinando " + channel + " (Redis Insight? outra aba?). Feche e rode de novo.");

            long subscribers = JedisLab.numsub(jedis, channel);
            v.expect(subscribers == 0, "nenhum assinante ficou pendurado no canal " + channel,
                    "ainda há " + subscribers + " assinante(s): a thread do SUBSCRIBE não chamou unsubscribe()?");

            boolean jedisRan = "ok".equals(marker.get("jedis"));
            boolean lettuceRan = "ok".equals(marker.get("lettuce"));
            if (jedisRan && !lettuceRan) v.info("Opcional: experimente com o Lettuce: ./quest run 102-01 lettuce");
            if (lettuceRan && !jedisRan) v.info("Opcional: experimente com o Jedis: ./quest run 102-01 jedis");
            if (jedisRan && lettuceRan) v.pass("os dois clients assinaram, publicaram e saíram do canal");
        }
    }
}
