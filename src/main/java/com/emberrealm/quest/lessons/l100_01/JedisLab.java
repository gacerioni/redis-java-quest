package com.emberrealm.quest.lessons.l100_01;

import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Env;
import com.emberrealm.quest.core.Lab;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.util.SafeEncoder;

/**
 * 100-01 (Jedis): the "doctor". One endpoint, one client, no shard math.
 * Shows what the server says about itself and proves the client talks to a single address.
 */
public final class JedisLab implements Lab {

    @Override
    public void run(Ctx ctx) {
        ctx.out.step("Um endpoint só: " + Env.redacted(Env.redisUrl()));
        try (RedisClient jedis = Clients.jedis()) {
            ctx.out.cmd("PING");
            ctx.out.kv("PING", jedis.ping());

            ctx.out.step("Quem está do outro lado?");
            ctx.out.cmd("INFO server");
            String info = jedis.info("server");
            ctx.out.kv("redis_version", field(info, "redis_version"));
            ctx.out.kv("redis_mode", field(info, "redis_mode"));
            ctx.out.kv("os", field(info, "os"));

            ctx.out.step("Latência de ida e volta (10 PINGs)");
            long start = System.nanoTime();
            for (int i = 0; i < 10; i++) jedis.ping();
            double avgMs = (System.nanoTime() - start) / 10 / 1_000_000.0;
            ctx.out.kv("RTT médio", String.format("%.2f ms", avgMs));
            if (avgMs > 50) ctx.out.hint("RTT alto: o Redis está longe da sua aplicação. Em produção, mesma região e mesma zona.");

            ctx.out.step("O client sabe de shards? Perguntando ao servidor com CLUSTER INFO");
            ctx.out.cmd("CLUSTER INFO");
            try {
                Object raw = jedis.sendCommand(Protocol.Command.CLUSTER, "INFO");
                String text = raw instanceof byte[] b ? SafeEncoder.encode(b) : String.valueOf(raw);
                ctx.out.kv("cluster_enabled", field(info, "cluster_enabled"));
                ctx.out.info(text.lines().findFirst().orElse(text));
                ctx.out.hint("Redis OSS em modo cluster: aí sim o client precisaria de RedisClusterClient e de calcular slots.");
            } catch (Exception e) {
                ctx.out.kv("resposta", e.getMessage());
                ctx.out.hint("Sem cluster do lado do client: no Redis Cloud o proxy roteia para os shards. Você só conhece um endpoint.");
            }

            ctx.out.step("Tamanho do banco e conexões abertas");
            ctx.out.cmd("DBSIZE");
            ctx.out.kv("DBSIZE", jedis.dbSize());
            ctx.out.cmd("CLIENT LIST");
            try {
                Object rawList = jedis.sendCommand(Protocol.Command.CLIENT, "LIST");
                String list = rawList instanceof byte[] lb ? SafeEncoder.encode(lb) : String.valueOf(rawList);
                long clients = list.lines().count();
                ctx.out.kv("conexões neste banco", clients);
            } catch (Exception e) {
                ctx.out.kv("CLIENT LIST", "não permitido para este usuário (" + e.getMessage() + ")");
            }
            ctx.out.kv("prefixo das suas chaves", ctx.keys.prefix());

            ctx.done("redis_version", field(info, "redis_version"), "rtt_ms", String.format("%.2f", avgMs));
        }
    }

    static String field(String info, String name) {
        return info.lines()
                .filter(l -> l.startsWith(name + ":"))
                .map(l -> l.substring(name.length() + 1).trim())
                .findFirst()
                .orElse("?");
    }
}
