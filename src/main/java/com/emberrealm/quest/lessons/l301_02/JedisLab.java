package com.emberrealm.quest.lessons.l301_02;

import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Env;
import com.emberrealm.quest.core.Lab;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.csc.CacheConfig;
import redis.clients.jedis.csc.CacheStats;
import redis.clients.jedis.util.JedisURIHelper;
import redis.clients.jedis.util.SafeEncoder;

import java.net.URI;
import java.time.Instant;

/**
 * 301-02 (Jedis): server-assisted client-side caching. RESP3 + CacheConfig make every read-only
 * command check a local cache first; the server tracks the keys and pushes an invalidation when they change.
 */
public final class JedisLab implements Lab {

    static final String MOTD_V1 = "Bem-vindo ao Ember Realm: evento de XP em dobro até domingo";
    static final String MOTD_V2 = "Aviso do mestre do jogo: o dragão de cinzas acordou na Floresta";
    static final int CACHED_READS = 1000;
    static final int PLAIN_READS = 200;

    @Override
    public void run(Ctx ctx) throws Exception {
        String motd = ctx.k("config", "motd");
        String clientsKey = ctx.k("csc", "clients");
        URI uri = URI.create(Env.redisUrl());

        try (RedisClient plain = Clients.jedis()) {
            ctx.out.step("A mensagem do dia, lida em todo login");
            ctx.out.cmd("SET " + motd + " \"" + MOTD_V1 + "\"");
            plain.set(motd, MOTD_V1);

            ctx.out.step("Client com cache local: RESP3 obrigatório + CacheConfig(maxSize 1000)");
            DefaultJedisClientConfig config = configFrom(uri).resp3().build();
            CacheConfig cacheConfig = CacheConfig.builder().maxSize(1000).build();
            try (RedisClient cached = RedisClient.builder()
                    .hostAndPort(hostAndPort(uri))
                    .clientConfig(config)
                    .cacheConfig(cacheConfig)
                    .build()) {
                ctx.out.cmd("CLIENT INFO");
                ctx.out.kv("protocolo negociado", "resp=" + resp(cached));

                ctx.out.step(CACHED_READS + " leituras: a primeira vai ao servidor, as outras ficam em casa");
                ctx.out.cmd("GET " + motd + "  (x" + CACHED_READS + ")");
                long start = System.nanoTime();
                for (int i = 0; i < CACHED_READS; i++) cached.get(motd);
                long cachedMs = (System.nanoTime() - start) / 1_000_000;
                CacheStats stats = cached.getCache().getStats();
                ctx.out.kv("hits", stats.getHitCount());
                ctx.out.kv("misses", stats.getMissCount());
                ctx.out.kv("tempo", cachedMs + " ms para " + CACHED_READS + " GETs");

                ctx.out.step("Comparando: " + PLAIN_READS + " GETs no client comum, cada um viajando até o Redis");
                ctx.out.cmd("GET " + motd + "  (x" + PLAIN_READS + ", sem cache)");
                start = System.nanoTime();
                for (int i = 0; i < PLAIN_READS; i++) plain.get(motd);
                long plainMs = (System.nanoTime() - start) / 1_000_000;
                ctx.out.kv("tempo", plainMs + " ms para " + PLAIN_READS + " GETs");

                ctx.out.step("O mestre do jogo muda a mensagem em outro client: o servidor invalida o cache");
                ctx.out.cmd("SET " + motd + " \"" + MOTD_V2 + "\"  (client comum)");
                plain.set(motd, MOTD_V2);
                Thread.sleep(200);   // give the invalidation push time to arrive
                ctx.out.cmd("GET " + motd + "  (client com cache)");
                String fresh = cached.get(motd);
                CacheStats after = cached.getCache().getStats();
                ctx.out.kv("GET", fresh);
                ctx.out.kv("misses agora", after.getMissCount());
                ctx.out.kv("invalidações recebidas", after.getInvalidationCount());
                boolean freshOk = MOTD_V2.equals(fresh);
                if (!freshOk) ctx.out.warn("valor antigo lido: a invalidação ainda não tinha chegado");
                ctx.out.info("Tracking: o servidor lembra quais chaves este client leu e avisa quando mudam. Nada de TTL no chute.");

                plain.hset(clientsKey, ctx.client, Instant.now().toString());
                ctx.done("hits", String.valueOf(after.getHitCount()),
                        "misses", String.valueOf(after.getMissCount()),
                        "invalidations", String.valueOf(after.getInvalidationCount()),
                        "fresh", freshOk ? "ok" : "stale");
            }
        }
    }

    /** The resp field of CLIENT INFO: 3 means the connection speaks RESP3, which the cache needs for push messages. */
    static String resp(RedisClient client) {
        Object raw = client.sendCommand(Protocol.Command.CLIENT, "INFO");
        String info = raw instanceof byte[] b ? SafeEncoder.encode(b) : String.valueOf(raw);
        for (String token : info.trim().split("\\s+")) {
            if (token.startsWith("resp=")) return token.substring(5);
        }
        return "?";
    }

    static HostAndPort hostAndPort(URI uri) {
        return new HostAndPort(uri.getHost(), uri.getPort() < 0 ? 6379 : uri.getPort());
    }

    static DefaultJedisClientConfig.Builder configFrom(URI uri) {
        DefaultJedisClientConfig.Builder builder = DefaultJedisClientConfig.builder();
        String user = JedisURIHelper.getUser(uri);
        String password = JedisURIHelper.getPassword(uri);
        if (user != null) builder.user(user);
        if (password != null) builder.password(password);
        if (JedisURIHelper.hasDbIndex(uri)) builder.database(JedisURIHelper.getDBIndex(uri));
        if (JedisURIHelper.isRedisSSLScheme(uri)) builder.ssl(true);
        return builder;
    }
}
