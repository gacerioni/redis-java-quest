package com.emberrealm.quest.lessons.l301_01;

import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Env;
import com.emberrealm.quest.core.Lab;
import redis.clients.jedis.ConnectionPoolConfig;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisException;
import redis.clients.jedis.util.JedisURIHelper;

import java.io.IOException;
import java.net.URI;
import java.security.Security;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * 301-01 (Jedis): explicit timeouts, a bounded pool with idle health checks, a retry loop with
 * exponential backoff, and a fast failure against an address that never answers.
 */
public final class JedisLab implements Lab {

    /** A non-routable address: SYN packets go nowhere, so only the connect timeout saves us. */
    static final String GHOST_HOST = "10.255.255.1";
    static final int GHOST_PORT = 6379;
    static final int GHOST_CONNECT_TIMEOUT_MS = 500;

    /** Outcome of a retry loop: result is null when every attempt failed. */
    record Attempt(String result, int attempts, long firstFailureMs, String lastError) {
        boolean ok() {
            return result != null;
        }
    }

    @Override
    public void run(Ctx ctx) throws Exception {
        URI uri = URI.create(Env.redisUrl());

        ctx.out.step("Timeouts explícitos: nenhum comando pode travar o servidor do jogo");
        DefaultJedisClientConfig config = configFrom(uri)
                .connectionTimeoutMillis(2000)   // max time to open the TCP connection
                .socketTimeoutMillis(2000)       // max time waiting for a reply
                .build();
        ctx.out.kv("connectionTimeoutMillis", 2000);
        ctx.out.kv("socketTimeoutMillis", 2000);
        ctx.out.info("Sem isso, uma rede ruim segura a thread do jogador para sempre.");

        ctx.out.step("Pool limitado, com PING nas conexões ociosas");
        ConnectionPoolConfig pool = new ConnectionPoolConfig();
        pool.setMaxTotal(8);
        pool.setMaxIdle(8);
        pool.setMinIdle(1);
        pool.setBlockWhenExhausted(true);
        pool.setMaxWait(Duration.ofSeconds(1));
        pool.setTestWhileIdle(true);
        pool.setTimeBetweenEvictionRuns(Duration.ofSeconds(5));
        ctx.out.kv("maxTotal", pool.getMaxTotal());
        ctx.out.kv("maxWait", "1 s (depois disso, exceção em vez de fila infinita)");
        ctx.out.kv("testWhileIdle", "true (PING nas conexões paradas a cada 5 s)");

        String bossKey = ctx.k("boss", "spawn");
        String clientsKey = ctx.k("ops", "clients");
        try (RedisClient jedis = RedisClient.builder()
                .hostAndPort(hostAndPort(uri))
                .clientConfig(config)
                .poolConfig(pool)
                .build()) {
            jedis.unlink(bossKey);
            ctx.out.cmd("PING");
            ctx.out.kv("PING", jedis.ping());

            ctx.out.step("Retry com backoff exponencial: o spawn do chefão não pode se perder");
            String when = Instant.now().plusSeconds(300).toString();
            ctx.out.cmd("SET " + bossKey + " " + when);
            Attempt spawn = withRetry(ctx, 4, 200, () -> jedis.set(bossKey, when));
            ctx.out.kv("SET", spawn.result() + " na tentativa " + spawn.attempts());
            ctx.out.info("Só JedisConnectionException (e causas de rede) merece retry. Erro de dado, como WRONGTYPE, não melhora repetindo.");

            ctx.out.step("Falha rápida: um datacenter fantasma em " + GHOST_HOST + " com connect timeout de "
                    + GHOST_CONNECT_TIMEOUT_MS + " ms");
            DefaultJedisClientConfig ghostConfig = DefaultJedisClientConfig.builder()
                    .connectionTimeoutMillis(GHOST_CONNECT_TIMEOUT_MS)
                    .socketTimeoutMillis(GHOST_CONNECT_TIMEOUT_MS)
                    .build();
            Attempt ghost;
            try (RedisClient phantom = RedisClient.builder()
                    .hostAndPort(new HostAndPort(GHOST_HOST, GHOST_PORT))
                    .clientConfig(ghostConfig)
                    .build()) {
                ctx.out.cmd("PING  (para " + GHOST_HOST + ":" + GHOST_PORT + ", 2 tentativas)");
                ghost = withRetry(ctx, 2, 200, phantom::ping);
            }
            if (ghost.ok()) {
                ctx.out.warn("inesperado: o fantasma respondeu " + ghost.result());
            } else {
                ctx.out.kv("primeira falha em", ghost.firstFailureMs() + " ms");
                ctx.out.info("Sem timeout de conexão o SO tentaria por mais de um minuto. Falhar rápido libera a thread para o fallback.");
            }
            long fastFailMs = ghost.firstFailureMs();

            ctx.out.step("Cache de DNS da JVM: desligue quando o endpoint pode trocar de IP");
            String before = Security.getProperty("networkaddress.cache.ttl");
            ctx.out.kv("networkaddress.cache.ttl antes", before == null ? "padrão da JVM (30 s para respostas positivas)" : before);
            Security.setProperty("networkaddress.cache.ttl", "0");
            Security.setProperty("networkaddress.cache.negative.ttl", "0");
            ctx.out.kv("networkaddress.cache.ttl agora", Security.getProperty("networkaddress.cache.ttl"));
            ctx.out.info("Failover no Redis Cloud e Active-Active trocam o IP por trás do mesmo nome. Com cache, o client insiste no IP morto.");

            jedis.hset(clientsKey, ctx.client, Instant.now().toString());
            ctx.done("fast_fail_ms", String.valueOf(fastFailMs),
                    "pool_max", String.valueOf(pool.getMaxTotal()),
                    "retries", String.valueOf(ghost.attempts()),
                    "socket_timeout_ms", "2000");
        }
    }

    /**
     * Runs a command up to maxAttempts times, sleeping firstBackoffMs, then double, between attempts.
     * Only connection problems are retried; any other Jedis error propagates untouched.
     */
    static Attempt withRetry(Ctx ctx, int maxAttempts, long firstBackoffMs, Supplier<String> command) throws InterruptedException {
        long backoff = firstBackoffMs;
        long firstFailureMs = -1;
        String lastError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            long start = System.nanoTime();
            try {
                return new Attempt(command.get(), attempt, firstFailureMs, lastError);
            } catch (JedisException e) {
                if (!retryable(e)) throw e;
                long elapsed = (System.nanoTime() - start) / 1_000_000;
                if (firstFailureMs < 0) firstFailureMs = elapsed;
                lastError = firstLine(e.getMessage());
                ctx.out.warn("tentativa " + attempt + "/" + maxAttempts + " falhou em " + elapsed + " ms: " + lastError);
                if (attempt < maxAttempts) {
                    ctx.out.info("esperando " + backoff + " ms antes da próxima");
                    Thread.sleep(backoff);
                    backoff *= 2;
                }
            }
        }
        return new Attempt(null, maxAttempts, firstFailureMs, lastError);
    }

    /** Connection problems (a pool that could not connect, a socket timeout) are worth retrying. */
    static boolean retryable(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof JedisConnectionException || c instanceof IOException) return true;
        }
        return false;
    }

    static String firstLine(String s) {
        return s == null ? "" : s.lines().findFirst().orElse("");
    }

    static HostAndPort hostAndPort(URI uri) {
        return new HostAndPort(uri.getHost(), uri.getPort() < 0 ? 6379 : uri.getPort());
    }

    /** Credentials, database and TLS flag taken from the URL; timeouts are added by the caller. */
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
