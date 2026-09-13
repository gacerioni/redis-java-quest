package com.emberrealm.quest.lessons.l301_01;

import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Env;
import com.emberrealm.quest.core.Lab;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisCommandTimeoutException;
import io.lettuce.core.RedisConnectionException;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.resource.EpollProvider;
import io.lettuce.core.resource.IOUringProvider;

import java.security.Security;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * 301-01 (Lettuce): command timeout on the RedisURI, socket options (connect timeout, keepalive,
 * TCP_USER_TIMEOUT), at-least-once reconnects with a replay filter, and the same fast-fail demo.
 */
public final class LettuceLab implements Lab {

    @Override
    public void run(Ctx ctx) throws Exception {
        ctx.out.step("RedisURI com timeout de comando + ClientOptions com timeouts de socket");
        RedisURI uri = RedisURI.create(Env.redisUrl());
        uri.setTimeout(Duration.ofSeconds(2));   // command timeout used by the sync API
        boolean nativeTransport = EpollProvider.isAvailable() || IOUringProvider.isAvailable();
        SocketOptions.Builder socket = SocketOptions.builder()
                .connectTimeout(Duration.ofSeconds(2))
                .keepAlive(SocketOptions.KeepAliveOptions.builder()
                        .enable().idle(Duration.ofSeconds(5)).interval(Duration.ofSeconds(5)).count(3).build());
        if (nativeTransport) {
            // TCP_USER_TIMEOUT needs netty epoll or io_uring (Linux); Lettuce refuses to start without them
            socket.tcpUserTimeout(SocketOptions.TcpUserTimeoutOptions.builder()
                    .enable().tcpUserTimeout(Duration.ofSeconds(20)).build());
        }
        ClientOptions options = ClientOptions.builder()
                .autoReconnect(true)                                   // default: at-least-once
                .socketOptions(socket.build())
                .timeoutOptions(TimeoutOptions.enabled(Duration.ofSeconds(2)))
                .replayFilter(cmd -> "INCR".equalsIgnoreCase(cmd.getType().toString()))   // never replay INCR
                .build();
        ctx.out.kv("command timeout", "2 s (RedisURI.setTimeout)");
        ctx.out.kv("connectTimeout", "2 s");
        ctx.out.kv("keepAlive", "idle 5 s, interval 5 s, count 3 (JDK 11+ NIO ou netty epoll)");
        ctx.out.kv("tcpUserTimeout", nativeTransport
                ? "20 s (netty epoll/io_uring disponível)"
                : "não aplicado: precisa de netty epoll ou io_uring (Linux). Aqui o keepAlive faz o papel");
        ctx.out.kv("autoReconnect", "true: comandos na fila são reenviados depois da reconexão (at-least-once)");
        ctx.out.kv("replayFilter", "INCR fica de fora do reenvio: não é idempotente");

        String bossKey = ctx.k("boss", "spawn");
        String clientsKey = ctx.k("ops", "clients");
        RedisClient client = RedisClient.create(uri);
        client.setOptions(options);
        try (StatefulRedisConnection<String, String> connection = client.connect()) {
            RedisCommands<String, String> redis = connection.sync();
            redis.unlink(bossKey);
            ctx.out.cmd("PING");
            ctx.out.kv("PING", redis.ping());

            ctx.out.step("Retry com backoff exponencial: o spawn do chefão não pode se perder");
            String when = Instant.now().plusSeconds(300).toString();
            ctx.out.cmd("SET " + bossKey + " " + when);
            JedisLab.Attempt spawn = withRetry(ctx, 4, 200, () -> redis.set(bossKey, when));
            ctx.out.kv("SET", spawn.result() + " na tentativa " + spawn.attempts());
            ctx.out.info("Com autoReconnect o Lettuce já refaz muita coisa sozinho; o retry cobre o timeout de comando.");

            ctx.out.step("Falha rápida: um datacenter fantasma em " + JedisLab.GHOST_HOST + " com connect timeout de "
                    + JedisLab.GHOST_CONNECT_TIMEOUT_MS + " ms");
            RedisURI ghostUri = RedisURI.builder()
                    .redis(JedisLab.GHOST_HOST, JedisLab.GHOST_PORT)
                    .withTimeout(Duration.ofSeconds(2))
                    .build();
            RedisClient phantom = RedisClient.create(ghostUri);   // demo only: production keeps one client per app
            phantom.setOptions(ClientOptions.builder()
                    .socketOptions(SocketOptions.builder()
                            .connectTimeout(Duration.ofMillis(JedisLab.GHOST_CONNECT_TIMEOUT_MS)).build())
                    .build());
            JedisLab.Attempt ghost;
            try {
                ctx.out.cmd("PING  (para " + JedisLab.GHOST_HOST + ":" + JedisLab.GHOST_PORT + ", 2 tentativas)");
                ghost = withRetry(ctx, 2, 200, () -> {
                    try (StatefulRedisConnection<String, String> c = phantom.connect()) {
                        return c.sync().ping();
                    }
                });
            } finally {
                phantom.shutdown(Duration.ZERO, Duration.ofSeconds(1));
            }
            if (ghost.ok()) {
                ctx.out.warn("inesperado: o fantasma respondeu " + ghost.result());
            } else {
                ctx.out.kv("primeira falha em", ghost.firstFailureMs() + " ms");
                ctx.out.info("connect() falha com RedisConnectionException; o autoReconnect só entra depois de uma conexão que existiu.");
            }

            ctx.out.step("Cache de DNS da JVM: desligue quando o endpoint pode trocar de IP");
            String before = Security.getProperty("networkaddress.cache.ttl");
            ctx.out.kv("networkaddress.cache.ttl antes", before == null ? "padrão da JVM (30 s para respostas positivas)" : before);
            Security.setProperty("networkaddress.cache.ttl", "0");
            Security.setProperty("networkaddress.cache.negative.ttl", "0");
            ctx.out.kv("networkaddress.cache.ttl agora", Security.getProperty("networkaddress.cache.ttl"));
            ctx.out.info("Failover no Redis Cloud e Active-Active trocam o IP por trás do mesmo nome. Com cache, o client insiste no IP morto.");

            redis.hset(clientsKey, ctx.client, Instant.now().toString());
            ctx.done("fast_fail_ms", String.valueOf(ghost.firstFailureMs()),
                    "connect_timeout_ms", String.valueOf(JedisLab.GHOST_CONNECT_TIMEOUT_MS),
                    "retries", String.valueOf(ghost.attempts()),
                    "command_timeout_ms", "2000");
        } finally {
            client.shutdown(Duration.ZERO, Duration.ofSeconds(2));
        }
    }

    /** Same loop as the Jedis lab; the retryable exceptions are the Lettuce ones. */
    static JedisLab.Attempt withRetry(Ctx ctx, int maxAttempts, long firstBackoffMs, Supplier<String> command) throws InterruptedException {
        long backoff = firstBackoffMs;
        long firstFailureMs = -1;
        String lastError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            long start = System.nanoTime();
            try {
                return new JedisLab.Attempt(command.get(), attempt, firstFailureMs, lastError);
            } catch (RedisConnectionException | RedisCommandTimeoutException e) {
                long elapsed = (System.nanoTime() - start) / 1_000_000;
                if (firstFailureMs < 0) firstFailureMs = elapsed;
                lastError = JedisLab.firstLine(e.getMessage());
                ctx.out.warn("tentativa " + attempt + "/" + maxAttempts + " falhou em " + elapsed + " ms: " + lastError);
                if (attempt < maxAttempts) {
                    ctx.out.info("esperando " + backoff + " ms antes da próxima");
                    Thread.sleep(backoff);
                    backoff *= 2;
                }
            }
        }
        return new JedisLab.Attempt(null, maxAttempts, firstFailureMs, lastError);
    }
}
