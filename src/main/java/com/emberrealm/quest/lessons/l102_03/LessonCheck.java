package com.emberrealm.quest.lessons.l102_03;

import com.emberrealm.quest.core.Check;
import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Verdict;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.resps.StreamGroupInfo;

import java.util.Map;

public final class LessonCheck implements Check {

    @Override
    public void run(Ctx ctx, Verdict v) {
        String stream = ctx.k("events", "combat");
        try (RedisClient jedis = Clients.jedis()) {
            boolean isStream = "stream".equals(jedis.type(stream));
            v.expect(isStream, stream + " existe como Stream", "rode: ./quest run 102-03 jedis (ou lettuce)");
            if (isStream) {
                StreamGroupInfo group = null;
                for (StreamGroupInfo candidate : jedis.xinfoGroups(stream)) {
                    if (JedisLab.GROUP.equals(candidate.getName())) group = candidate;
                }
                v.expect(group != null, "o grupo " + JedisLab.GROUP + " existe (XINFO GROUPS)",
                        "sem grupo no diário. Rode: ./quest run 102-03 jedis (ou lettuce)");
                if (group != null) {
                    v.expect(group.getPending() == 0, "nenhum golpe pendente no grupo (pending = 0)",
                            "há " + group.getPending() + " golpes entregues sem XACK. Rode a lição de novo: o XAUTOCLAIM do worker-3 limpa a PEL.");
                    v.pass(group.getConsumers() + " consumers conhecidos, last-delivered-id " + group.getLastDeliveredId());
                }
            }

            Map<String, String> marker = jedis.hgetAll(ctx.progressKey());
            v.expect(!marker.isEmpty(), "a lição rodou e gravou o marcador", "rode: ./quest run 102-03 jedis (ou lettuce)");
            if (marker.isEmpty()) return;
            v.expect("2".equals(marker.get("reclaimed")), "worker-3 reivindicou os 2 golpes órfãos com XAUTOCLAIM",
                    "reivindicados: " + marker.get("reclaimed") + ". Rode a lição de novo do zero.");

            boolean jedisRan = "ok".equals(marker.get("jedis"));
            boolean lettuceRan = "ok".equals(marker.get("lettuce"));
            if (jedisRan && !lettuceRan) v.skip("falta experimentar com o Lettuce: ./quest run 102-03 lettuce");
            if (lettuceRan && !jedisRan) v.skip("falta experimentar com o Jedis: ./quest run 102-03 jedis");
            if (jedisRan && lettuceRan) v.pass("os dois clients dividiram o loot entre workers e recuperaram os órfãos");
        }
    }
}
