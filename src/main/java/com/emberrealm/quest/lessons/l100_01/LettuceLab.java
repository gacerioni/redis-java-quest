package com.emberrealm.quest.lessons.l100_01;

import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Env;
import com.emberrealm.quest.core.Lab;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

/** 100-01 (Lettuce): same doctor, same single endpoint, netty under the hood. */
public final class LettuceLab implements Lab {

    @Override
    public void run(Ctx ctx) {
        ctx.out.step("Um endpoint só: " + Env.redacted(Env.redisUrl()));
        try (StatefulRedisConnection<String, String> connection = Clients.lettuceConnection()) {
            RedisCommands<String, String> redis = connection.sync();
            ctx.out.cmd("PING");
            ctx.out.kv("PING", redis.ping());

            ctx.out.step("Quem está do outro lado?");
            ctx.out.cmd("INFO server");
            String info = redis.info("server");
            ctx.out.kv("redis_version", JedisLab.field(info, "redis_version"));
            ctx.out.kv("redis_mode", JedisLab.field(info, "redis_mode"));

            ctx.out.step("Latência de ida e volta (10 PINGs)");
            long start = System.nanoTime();
            for (int i = 0; i < 10; i++) redis.ping();
            double avgMs = (System.nanoTime() - start) / 10 / 1_000_000.0;
            ctx.out.kv("RTT médio", String.format("%.2f ms", avgMs));

            ctx.out.step("O client sabe de shards? Perguntando ao servidor com CLUSTER INFO");
            ctx.out.cmd("CLUSTER INFO");
            try {
                String text = redis.clusterInfo();
                ctx.out.info(text.lines().findFirst().orElse(text));
                ctx.out.hint("Cluster API disponível: Redis OSS em cluster ou Redis Cloud/Software com OSS Cluster API. Use io.lettuce.core.cluster.RedisClusterClient.");
            } catch (Exception e) {
                ctx.out.kv("resposta", e.getMessage());
                ctx.out.hint(Topology.failureHint(e));
            }

            ctx.out.step("Tamanho do banco");
            ctx.out.cmd("DBSIZE");
            ctx.out.kv("DBSIZE", redis.dbsize());
            ctx.out.kv("prefixo das suas chaves", ctx.keys.prefix());

            ctx.done("redis_version", JedisLab.field(info, "redis_version"), "rtt_ms", String.format("%.2f", avgMs));
        }
    }
}
