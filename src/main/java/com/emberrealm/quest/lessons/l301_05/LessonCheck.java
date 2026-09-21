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

            verifyEvidence(marker, v);
            if (!finishedAttempt(marker)) return;
            if ("skipped".equals(marker.get("heartbeat")) || "skipped".equals(marker.get("failover"))) return;

            String heartbeat = ctx.k("heartbeat");
            boolean found = hasHeartbeat(Env.get("QUEST_EAST_URL", JedisLab.DEFAULT_EAST), heartbeat)
                    || hasHeartbeat(Env.get("QUEST_WEST_URL", JedisLab.DEFAULT_WEST), heartbeat);
            if (found) v.pass("o heartbeat " + heartbeat + " está gravado em um dos datacenters");
            else v.skip("não consegui ler " + heartbeat + " no east nem no west agora (fora do ar?), a execução permanece parcial até ser possível verificar");

            Map<String, String> clients = jedis.hgetAll(ctx.k("aa", "clients"));
            boolean jedisRan = clients.containsKey("jedis");
            boolean lettuceRan = clients.containsKey("lettuce");
            if (jedisRan && !lettuceRan) v.info("Opcional: experimente com o Lettuce: ./quest run 301-05 lettuce");
            if (lettuceRan && !jedisRan) v.info("Opcional: experimente com o Jedis: ./quest run 301-05 jedis");
            if (jedisRan && lettuceRan) v.pass("os dois clients executaram o heartbeat; a evidência de troca acima corresponde à última execução");
        }
    }

    static void verifyEvidence(Map<String, String> marker, Verdict v) {
        if (!finishedAttempt(marker)) {
            v.fail("a última tentativa não terminou; observações antigas não comprovam a execução atual", "confira a conexão e rode a lição de novo");
            return;
        }
        if ("skipped".equals(marker.get("heartbeat")) || "skipped".equals(marker.get("failover"))) {
            v.skip("east e west não estavam disponíveis. Suba os bancos e rode de novo; nenhuma troca foi comprovada.");
            return;
        }
        long ok = parse(marker.get("beats_ok"));
        v.expect("ok".equals(marker.get("heartbeat")) && ok > 0,
                ok + " batidas gravadas; " + marker.get("beats_failed") + " falhas; ativo no fim: " + marker.get("active"),
                "nenhuma batida confirmada ou marcador antigo: rode a lição de novo");
        if ("observed".equals(marker.get("failover"))) v.pass("failover east → west comprovado por escritas bem-sucedidas");
        else v.skip("failover não observado nesta execução: heartbeat sozinho não comprova troca de região");
        if ("observed".equals(marker.get("failover")) && "observed".equals(marker.get("failback"))) v.pass("failback west → east comprovado após o failover");
        else v.skip("failback não observado nesta execução: restaure east enquanto o lab ainda está rodando");
    }

    private static boolean finishedAttempt(Map<String, String> marker) {
        return "ran".equals(marker.get("status")) || "unavailable".equals(marker.get("status"));
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
