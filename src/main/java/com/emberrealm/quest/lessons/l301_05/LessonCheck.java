package com.emberrealm.quest.lessons.l301_05;

import com.emberrealm.quest.core.Check;
import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Env;
import com.emberrealm.quest.core.Verdict;
import redis.clients.jedis.RedisClient;

import java.util.Map;

public final class LessonCheck implements Check {

    @Override
    public void run(Ctx ctx, Verdict v) {
        try (RedisClient jedis = Clients.jedis()) {
            Map<String, String> marker = jedis.hgetAll(ctx.progressKey());
            v.expect(!marker.isEmpty(), "a lição rodou e gravou o marcador", "rode: ./quest run 301-05 jedis (ou lettuce)");
            if (marker.isEmpty()) return;

            String failover = marker.get("failover");
            if ("skipped".equals(failover)) {
                v.skip("east e west não estavam de pé, a lição foi pulada: docker compose --profile failover up -d e rode de novo");
                return;
            }
            v.expect("ok".equals(failover), "o heartbeat rodou contra os dois datacenters", "o marcador tem failover=" + failover + ": rode a lição de novo");
            long ok = parse(marker.get("beats_ok"));
            v.expect(ok >= 1, ok + " batidas gravadas, " + marker.get("beats_failed") + " falhas, " + marker.get("switches")
                    + " trocas de datacenter, ativo no fim: " + marker.get("active"), "nenhuma batida chegou: confira QUEST_EAST_URL e QUEST_WEST_URL");

            String heartbeat = ctx.k("heartbeat");
            boolean found = hasHeartbeat(Env.get("QUEST_EAST_URL", JedisLab.DEFAULT_EAST), heartbeat)
                    || hasHeartbeat(Env.get("QUEST_WEST_URL", JedisLab.DEFAULT_WEST), heartbeat);
            if (found) v.pass("o heartbeat " + heartbeat + " está gravado em um dos datacenters");
            else v.skip("não consegui ler " + heartbeat + " no east nem no west agora (fora do ar?), o marcador vale");

            Map<String, String> clients = jedis.hgetAll(ctx.k("aa", "clients"));
            boolean jedisRan = clients.containsKey("jedis");
            boolean lettuceRan = clients.containsKey("lettuce");
            if (jedisRan && !lettuceRan) v.skip("falta experimentar com o Lettuce: ./quest run 301-05 lettuce");
            if (lettuceRan && !jedisRan) v.skip("falta experimentar com o Jedis: ./quest run 301-05 jedis");
            if (jedisRan && lettuceRan) v.pass("os dois clients fizeram failover geográfico com pesos, circuit breaker e failback");
        }
    }

    static boolean hasHeartbeat(String url, String key) {
        try (RedisClient region = Clients.jedis(url)) {
            return region.exists(key);
        } catch (Exception e) {
            return false;
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
