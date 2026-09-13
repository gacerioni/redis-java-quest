package com.emberrealm.quest.lessons.l301_05;

import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Env;
import com.emberrealm.quest.core.Lab;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.Endpoint;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.MultiDbClient;
import redis.clients.jedis.MultiDbConfig;
import redis.clients.jedis.exceptions.JedisException;
import redis.clients.jedis.mcf.HealthCheckStrategy;
import redis.clients.jedis.mcf.InitializationPolicy;
import redis.clients.jedis.mcf.PingStrategy;
import redis.clients.jedis.mcf.ProbingPolicy;
import redis.clients.jedis.util.JedisURIHelper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 301-05 (Jedis): client-side geographic failover with MultiDbClient. Two weighted endpoints (east 1.0, west 0.5),
 * PING health checks, a resilience4j circuit breaker, automatic failback, and a heartbeat that shows who is serving.
 * Stop the east container while it runs to watch the switch; start it again to watch the failback.
 */
public final class JedisLab implements Lab {

    static final String DEFAULT_EAST = "redis://localhost:6391";
    static final String DEFAULT_WEST = "redis://localhost:6392";
    static final int BEATS = 12;
    static final long BEAT_MS = 500;

    @Override
    public void run(Ctx ctx) throws Exception {
        // Jedis logs a WARN with a full stack trace every second while an endpoint is down; the lab reports switches itself
        System.setProperty("org.slf4j.simpleLogger.log.redis.clients.jedis.mcf.HealthCheckImpl", "error");
        String eastUrl = Env.get("QUEST_EAST_URL", DEFAULT_EAST);
        String westUrl = Env.get("QUEST_WEST_URL", DEFAULT_WEST);
        URI eastUri = URI.create(eastUrl);
        URI westUri = URI.create(westUrl);
        HostAndPort east = hostAndPort(eastUri);
        HostAndPort west = hostAndPort(westUri);
        String heartbeat = ctx.k("heartbeat");

        ctx.out.step("Dois datacenters, um mundo: east (peso 1.0) e west (peso 0.5)");
        ctx.out.kv("east", Env.redacted(eastUrl));
        ctx.out.kv("west", Env.redacted(westUrl));
        boolean eastUp = reachable(east);
        boolean westUp = reachable(west);
        ctx.out.kv("east alcançável", eastUp);
        ctx.out.kv("west alcançável", westUp);
        if (!eastUp && !westUp) {
            ctx.out.warn("Nenhum datacenter responde. Suba os dois: docker compose --profile failover up -d");
            ctx.out.hint("Ou aponte QUEST_EAST_URL e QUEST_WEST_URL para dois bancos seus (duas réplicas Active-Active, por exemplo).");
            ctx.done("failover", "skipped");
            return;
        }
        if (!eastUp || !westUp) ctx.out.warn("Um dos datacenters está fora; o MultiDbClient começa pelo que está de pé.");

        ctx.out.step("MultiDbConfig: pesos, health check por PING, circuit breaker, retry e failback");
        HealthCheckStrategy.Config health = new HealthCheckStrategy.Config(1000, 500, 1, 100, ProbingPolicy.BuiltIn.ALL_SUCCESS);
        MultiDbConfig.StrategySupplier pingEverySecond = (hostAndPort, clientConfig) -> new PingStrategy(hostAndPort, clientConfig, health);
        MultiDbConfig multiConfig = MultiDbConfig.builder()
                .database(MultiDbConfig.DatabaseConfig.builder(east, configFrom(eastUri))
                        .weight(1.0f).healthCheckStrategySupplier(pingEverySecond).build())
                .database(MultiDbConfig.DatabaseConfig.builder(west, configFrom(westUri))
                        .weight(0.5f).healthCheckStrategySupplier(pingEverySecond).build())
                .failureDetector(MultiDbConfig.CircuitBreakerConfig.builder()
                        .slidingWindowSize(2)          // seconds of history
                        .minNumOfFailures(2)           // trip after 2 connection failures...
                        .failureRateThreshold(50.0f)   // ...if they are at least half of the calls
                        .build())
                .commandRetry(MultiDbConfig.RetryConfig.builder()
                        .maxAttempts(2).waitDuration(100).exponentialBackoffMultiplier(2).build())
                .retryOnFailover(true)
                .fastFailover(true)
                .failbackSupported(true)
                .failbackCheckInterval(1000)           // look for the preferred endpoint every second
                .gracePeriod(2000)                     // keep a recovered endpoint disabled for 2 s (avoids flapping)
                .initializationPolicy(InitializationPolicy.BuiltIn.ONE_AVAILABLE)
                .build();
        ctx.out.kv("health check", "PING a cada 1 s, timeout 500 ms");
        ctx.out.kv("circuit breaker", "janela 2 s, abre com 2 falhas de conexão (50%)");
        ctx.out.kv("failback", "confere o east a cada 1 s, com carência de 2 s");
        ctx.out.info("Em produção os padrões são mais calmos (janela maior, carência de 60 s). Aqui tudo é curto para caber em 6 s.");

        heartbeat(ctx, multiConfig, east, west, heartbeat);
    }

    /** MultiDbClient decorates every command with resilience4j (circuit breaker + retry), hence resilience4j-all in the pom. */
    static void heartbeat(Ctx ctx, MultiDbConfig multiConfig, HostAndPort east, HostAndPort west, String heartbeat) throws Exception {
        AtomicInteger switches = new AtomicInteger();
        try (MultiDbClient client = MultiDbClient.builder()
                .multiDbConfig(multiConfig)
                .databaseSwitchListener(event -> {
                    switches.incrementAndGet();
                    ctx.out.warn("troca de datacenter: agora em " + label(event.getEndpoint()) + " (" + event.getReason() + ")");
                })
                .build()) {
            ctx.out.kv("ativo no início", label(client.getActiveDatabaseEndpoint()));

            ctx.out.step("Heartbeat: SET " + heartbeat + " a cada 500 ms por 6 s. Derrube o east em outro terminal e veja a troca");
            ctx.out.cmd("SET " + heartbeat + " <instante>  (x" + BEATS + ")");
            int ok = 0;
            int failed = 0;
            String active = "?";
            for (int i = 1; i <= BEATS; i++) {
                try {
                    client.set(heartbeat, Instant.now().toString());
                    active = label(client.getActiveDatabaseEndpoint());
                    ok++;
                    ctx.out.info(String.format("batida %2d -> %s", i, active));
                } catch (JedisException e) {
                    failed++;
                    ctx.out.warn(String.format("batida %2d falhou: %s", i, firstLine(e.getMessage())));
                }
                if (i < BEATS) Thread.sleep(BEAT_MS);
            }
            ctx.out.kv("east saudável", client.isHealthy(east));
            ctx.out.kv("west saudável", client.isHealthy(west));
            ctx.out.info("Cada região escreve localmente; o Active-Active (CRDT) reconcilia entre elas. O client só escolhe a porta de entrada.");

            try (redis.clients.jedis.RedisClient home = Clients.jedis()) {
                home.hset(ctx.k("aa", "clients"), ctx.client, Instant.now().toString());
            }
            ctx.done("failover", "ok",
                    "beats_ok", String.valueOf(ok),
                    "beats_failed", String.valueOf(failed),
                    "switches", String.valueOf(switches.get()),
                    "active", active);
        }
    }

    static String label(Endpoint endpoint) {
        return endpoint == null ? "?" : endpoint.getHost() + ":" + endpoint.getPort();
    }

    /** A 500 ms TCP probe, client agnostic, so the lesson can explain instead of failing when nothing is running. */
    static boolean reachable(HostAndPort target) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(target.getHost(), target.getPort()), 500);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    static String firstLine(String s) {
        return s == null ? "" : s.lines().findFirst().orElse("");
    }

    static HostAndPort hostAndPort(URI uri) {
        return new HostAndPort(uri.getHost(), uri.getPort() < 0 ? 6379 : uri.getPort());
    }

    /** Each region has its own credentials; short timeouts make the circuit breaker see failures quickly. */
    static DefaultJedisClientConfig configFrom(URI uri) {
        DefaultJedisClientConfig.Builder builder = DefaultJedisClientConfig.builder()
                .connectionTimeoutMillis(1000)
                .socketTimeoutMillis(1000);
        String user = JedisURIHelper.getUser(uri);
        String password = JedisURIHelper.getPassword(uri);
        if (user != null) builder.user(user);
        if (password != null) builder.password(password);
        if (JedisURIHelper.hasDbIndex(uri)) builder.database(JedisURIHelper.getDBIndex(uri));
        if (JedisURIHelper.isRedisSSLScheme(uri)) builder.ssl(true);
        return builder.build();
    }
}
