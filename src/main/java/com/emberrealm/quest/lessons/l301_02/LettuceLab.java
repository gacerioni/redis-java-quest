package com.emberrealm.quest.lessons.l301_02;

import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Lab;
import io.lettuce.core.TrackingArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.output.StatusOutput;
import io.lettuce.core.protocol.CommandArgs;
import io.lettuce.core.protocol.CommandType;
import io.lettuce.core.support.caching.CacheAccessor;
import io.lettuce.core.support.caching.CacheFrontend;
import io.lettuce.core.support.caching.ClientSideCaching;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 301-02 (Lettuce): ClientSideCaching wraps a RESP3 connection and a plain Map. CLIENT TRACKING ON makes the
 * server push invalidations, and the frontend evicts the stale entry. Lettuce marks this API as legacy from 7.8 on.
 */
public final class LettuceLab implements Lab {

    /** A Map-backed cache that counts what happens, so the console can show hits, misses and evictions. */
    static final class CountingAccessor implements CacheAccessor<String, String> {
        final Map<String, String> map = new ConcurrentHashMap<>();
        final AtomicLong hits = new AtomicLong();
        final AtomicLong misses = new AtomicLong();
        final AtomicLong evictions = new AtomicLong();

        @Override
        public String get(String key) {
            String value = map.get(key);
            if (value == null) misses.incrementAndGet();
            else hits.incrementAndGet();
            return value;
        }

        @Override
        public void put(String key, String value) {
            map.put(key, value);
        }

        @Override
        public void evict(String key) {
            map.remove(key);
            evictions.incrementAndGet();
        }
    }

    @Override
    public void run(Ctx ctx) throws Exception {
        String motd = ctx.k("config", "motd");
        String clientsKey = ctx.k("csc", "clients");

        // the CacheFrontend owns this connection and closes it; the finally block only covers early failures
        StatefulRedisConnection<String, String> cachedConnection = Clients.lettuce().connect();
        try (StatefulRedisConnection<String, String> plainConnection = Clients.lettuceConnection()) {
            RedisCommands<String, String> plain = plainConnection.sync();

            ctx.out.step("A mensagem do dia, lida em todo login");
            ctx.out.cmd("SET " + motd + " \"" + JedisLab.MOTD_V1 + "\"");
            plain.set(motd, JedisLab.MOTD_V1);

            ctx.out.step("Conexão dedicada em RESP3 + CLIENT TRACKING ON: um Map vira cache");
            ctx.out.cmd("CLIENT INFO");
            ctx.out.kv("protocolo negociado", "resp=" + resp(cachedConnection.sync()));
            CountingAccessor accessor = new CountingAccessor();
            ctx.out.cmd("CLIENT TRACKING ON");
            try (CacheFrontend<String, String> frontend =
                         ClientSideCaching.enable(accessor, cachedConnection, TrackingArgs.Builder.enabled())) {

                ctx.out.step(JedisLab.CACHED_READS + " leituras pelo CacheFrontend: a primeira vai ao servidor, as outras ficam em casa");
                ctx.out.cmd("GET " + motd + "  (x" + JedisLab.CACHED_READS + ")");
                long start = System.nanoTime();
                for (int i = 0; i < JedisLab.CACHED_READS; i++) frontend.get(motd);
                long cachedMs = (System.nanoTime() - start) / 1_000_000;
                ctx.out.kv("hits", accessor.hits.get());
                ctx.out.kv("misses", accessor.misses.get());
                ctx.out.kv("tempo", cachedMs + " ms para " + JedisLab.CACHED_READS + " GETs");

                ctx.out.step("Comparando: " + JedisLab.PLAIN_READS + " GETs na conexão comum, cada um viajando até o Redis");
                ctx.out.cmd("GET " + motd + "  (x" + JedisLab.PLAIN_READS + ", sem cache)");
                start = System.nanoTime();
                for (int i = 0; i < JedisLab.PLAIN_READS; i++) plain.get(motd);
                long plainMs = (System.nanoTime() - start) / 1_000_000;
                ctx.out.kv("tempo", plainMs + " ms para " + JedisLab.PLAIN_READS + " GETs");

                ctx.out.step("O mestre do jogo muda a mensagem em outra conexão: o servidor invalida o cache");
                ctx.out.cmd("SET " + motd + " \"" + JedisLab.MOTD_V2 + "\"  (conexão comum)");
                plain.set(motd, JedisLab.MOTD_V2);
                Thread.sleep(200);   // the invalidation is a push message handled by the netty thread
                ctx.out.cmd("GET " + motd + "  (CacheFrontend)");
                String fresh = frontend.get(motd);
                ctx.out.kv("GET", fresh);
                ctx.out.kv("misses agora", accessor.misses.get());
                ctx.out.kv("invalidações (evict)", accessor.evictions.get());
                boolean freshOk = JedisLab.MOTD_V2.equals(fresh);
                if (!freshOk) ctx.out.warn("valor antigo lido: a invalidação ainda não tinha chegado");
                ctx.out.info("A partir do Lettuce 7.8 esta API é marcada como legada; a mecânica (tracking + invalidação) segue igual.");

                plain.hset(clientsKey, ctx.client, Instant.now().toString());
                ctx.done("hits", String.valueOf(accessor.hits.get()),
                        "misses", String.valueOf(accessor.misses.get()),
                        "invalidations", String.valueOf(accessor.evictions.get()),
                        "fresh", freshOk ? "ok" : "stale");
            }
        } finally {
            if (cachedConnection.isOpen()) cachedConnection.close();
        }
    }

    static String resp(RedisCommands<String, String> redis) {
        String info = redis.dispatch(CommandType.CLIENT, new StatusOutput<>(StringCodec.UTF8),
                new CommandArgs<>(StringCodec.UTF8).add("INFO"));
        for (String token : info.trim().split("\\s+")) {
            if (token.startsWith("resp=")) return token.substring(5);
        }
        return "?";
    }
}
