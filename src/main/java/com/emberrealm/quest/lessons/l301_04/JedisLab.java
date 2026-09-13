package com.emberrealm.quest.lessons.l301_04;

import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Env;
import com.emberrealm.quest.core.Lab;
import redis.clients.jedis.ConnectionPoolConfig;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.util.JedisURIHelper;
import redis.clients.jedis.util.SafeEncoder;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * 301-04 (Jedis): Jedis 8.0.1 does not negotiate smart client handoffs yet. The lab shows what the server
 * answers about the feature and the tools Jedis does have today: timeouts, pool health checks and retries.
 */
public final class JedisLab implements Lab {

    static final int TICKS = 20;

    @Override
    public void run(Ctx ctx) throws Exception {
        URI uri = URI.create(Env.redisUrl());
        String ticksKey = ctx.k("maint", "ticks");
        String clientsKey = ctx.k("maint", "clients");

        ctx.out.step("Jedis 8.0.1 ainda não fala SCH: veja o que o servidor responde sobre o recurso");
        DefaultJedisClientConfig config = configFrom(uri)
                .connectionTimeoutMillis(2000)
                .socketTimeoutMillis(2000)
                .build();
        ConnectionPoolConfig pool = new ConnectionPoolConfig();
        pool.setMaxTotal(8);
        pool.setMinIdle(1);
        pool.setTestWhileIdle(true);
        pool.setTimeBetweenEvictionRuns(Duration.ofSeconds(5));
        pool.setMaxWait(Duration.ofSeconds(1));

        try (RedisClient jedis = RedisClient.builder().hostAndPort(hostAndPort(uri)).clientConfig(config).poolConfig(pool).build()) {
            String sch;
            ctx.out.cmd("CLIENT MAINT_NOTIFICATIONS OFF");
            try {
                Object raw = jedis.sendCommand(Protocol.Command.CLIENT, "MAINT_NOTIFICATIONS", "OFF");
                ctx.out.kv("resposta", raw instanceof byte[] b ? SafeEncoder.encode(b) : String.valueOf(raw));
                ctx.out.info("O servidor conhece SCH (Redis Cloud ou Redis Software). Mesmo assim o Jedis 8.0.1 não processaria os avisos.");
                sch = "server-only";
            } catch (JedisDataException e) {
                ctx.out.kv("resposta", e.getMessage());
                ctx.out.info("Redis Open Source não tem SCH; e o Jedis, hoje, também não negocia. Nada muda no handshake.");
                sch = "unsupported";
            }

            ctx.out.step("O que o Jedis tem hoje: timeouts, PING nas conexões ociosas e retry");
            ctx.out.kv("socketTimeoutMillis", 2000);
            ctx.out.kv("testWhileIdle", "true a cada 5 s: uma conexão morta pela manutenção sai do pool antes de chegar ao jogador");
            ctx.out.info("Durante uma manutenção sem SCH, a conexão cai: o pool descarta a quebrada, o retry refaz o comando.");

            ctx.out.step(TICKS + " comandos com retry: o relógio da raid não pode pular");
            jedis.unlink(ticksKey);
            ctx.out.cmd("INCR " + ticksKey + "  (x" + TICKS + ")");
            long last = 0;
            for (int i = 0; i < TICKS; i++) {
                last = withRetry(ctx, 3, 200, () -> jedis.incr(ticksKey));
            }
            ctx.out.kv("ticks", last);
            ctx.out.info("Para failover geográfico com circuit breaker, o Jedis tem o MultiDbClient: próxima lição.");

            jedis.hset(clientsKey, ctx.client, Instant.now().toString());
            ctx.done("ticks", String.valueOf(last), "sch", sch);
        }
    }

    static long withRetry(Ctx ctx, int maxAttempts, long firstBackoffMs, Supplier<Long> command) throws InterruptedException {
        long backoff = firstBackoffMs;
        for (int attempt = 1; ; attempt++) {
            try {
                return command.get();
            } catch (JedisConnectionException e) {
                ctx.out.warn("tentativa " + attempt + "/" + maxAttempts + " falhou: " + e.getMessage());
                if (attempt >= maxAttempts) throw e;
                Thread.sleep(backoff);
                backoff *= 2;
            }
        }
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
