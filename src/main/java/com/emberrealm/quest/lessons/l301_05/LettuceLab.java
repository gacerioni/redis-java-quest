package com.emberrealm.quest.lessons.l301_05;

import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Env;
import com.emberrealm.quest.core.Lab;
import io.lettuce.core.RedisException;
import io.lettuce.core.RedisURI;
import io.lettuce.core.failover.MultiDbClient;
import io.lettuce.core.failover.api.CircuitBreakerConfig;
import io.lettuce.core.failover.api.DatabaseConfig;
import io.lettuce.core.failover.api.InitializationPolicy;
import io.lettuce.core.failover.api.MultiDbOptions;
import io.lettuce.core.failover.api.StatefulRedisMultiDbConnection;
import io.lettuce.core.failover.event.DatabaseSwitchEvent;
import io.lettuce.core.failover.health.HealthCheckStrategy;
import io.lettuce.core.failover.health.HealthCheckStrategySupplier;
import io.lettuce.core.failover.health.PingStrategy;
import io.lettuce.core.failover.health.ProbingPolicy;
import reactor.core.Disposable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 301-05 (Lettuce): the same geographic failover with Lettuce's MultiDbClient (preview in 7.7): weighted
 * DatabaseConfigs, PING health checks, a circuit breaker per database, failback, and switch events on the event bus.
 */
public final class LettuceLab implements Lab {

    @Override
    public void run(Ctx ctx) throws Exception {
        // the failover package logs full stack traces for a region that is down; the lab reports switches itself
        System.setProperty("org.slf4j.simpleLogger.log.io.lettuce.core.failover", "off");
        String eastUrl = Env.get("QUEST_EAST_URL", JedisLab.DEFAULT_EAST);
        String westUrl = Env.get("QUEST_WEST_URL", JedisLab.DEFAULT_WEST);
        RedisURI east = RedisURI.create(eastUrl);
        RedisURI west = RedisURI.create(westUrl);
        east.setTimeout(Duration.ofSeconds(1));
        west.setTimeout(Duration.ofSeconds(1));
        String heartbeat = ctx.k("heartbeat");

        ctx.out.step("Dois datacenters, um mundo: east (peso 1.0) e west (peso 0.5)");
        ctx.out.kv("east", Env.redacted(eastUrl));
        ctx.out.kv("west", Env.redacted(westUrl));
        boolean eastUp = JedisLab.reachable(JedisLab.hostAndPort(java.net.URI.create(eastUrl)));
        boolean westUp = JedisLab.reachable(JedisLab.hostAndPort(java.net.URI.create(westUrl)));
        ctx.out.kv("east alcançável", eastUp);
        ctx.out.kv("west alcançável", westUp);
        if (!eastUp && !westUp) {
            ctx.out.warn("Nenhum datacenter responde. Suba os dois: docker compose --profile failover up -d");
            ctx.out.hint("Ou aponte QUEST_EAST_URL e QUEST_WEST_URL para dois bancos seus (duas réplicas Active-Active, por exemplo).");
            ctx.done("failover", "skipped");
            return;
        }
        if (!eastUp || !westUp) ctx.out.warn("Um dos datacenters está fora; o MultiDbClient começa pelo que está de pé.");

        ctx.out.step("MultiDbClient do Lettuce (preview na 7.7): DatabaseConfig por região, circuit breaker e failback");
        HealthCheckStrategy.Config health = new HealthCheckStrategy.Config(1000, 500, 1, 100, ProbingPolicy.BuiltIn.ALL_SUCCESS);
        HealthCheckStrategySupplier pingEverySecond = (uri, factory) -> new PingStrategy(factory, health);
        CircuitBreakerConfig breaker = CircuitBreakerConfig.builder()
                .metricsWindowSize(2)            // seconds of history
                .minimumNumberOfFailures(2)      // trip after 2 failures...
                .failureRateThreshold(50.0f)     // ...if they are at least half of the calls
                .build();
        DatabaseConfig eastDb = DatabaseConfig.builder(east).weight(1.0f)
                .circuitBreakerConfig(breaker).healthCheckStrategySupplier(pingEverySecond).build();
        DatabaseConfig westDb = DatabaseConfig.builder(west).weight(0.5f)
                .circuitBreakerConfig(breaker).healthCheckStrategySupplier(pingEverySecond).build();
        MultiDbOptions options = MultiDbOptions.builder()
                .failbackSupported(true)
                .failbackCheckInterval(Duration.ofSeconds(1))
                .gracePeriod(Duration.ofSeconds(2))
                .delayInBetweenFailoverAttempts(Duration.ofSeconds(1))
                .initializationPolicy(InitializationPolicy.BuiltIn.ONE_AVAILABLE)
                .build();
        ctx.out.kv("health check", "PING a cada 1 s, timeout 500 ms");
        ctx.out.kv("circuit breaker", "janela 2 s, abre com 2 falhas (50%)");
        ctx.out.kv("failback", "confere o east a cada 1 s, com carência de 2 s");

        AtomicInteger switches = new AtomicInteger();
        MultiDbClient client = MultiDbClient.create(List.of(eastDb, westDb), options);
        Disposable events = client.getResources().eventBus().get()
                .filter(event -> event instanceof DatabaseSwitchEvent)
                .cast(DatabaseSwitchEvent.class)
                .subscribe(event -> {
                    switches.incrementAndGet();
                    ctx.out.warn("troca de datacenter: " + label(event.getFromDb()) + " -> " + label(event.getToDb())
                            + " (" + event.getReason() + ")");
                });
        try (StatefulRedisMultiDbConnection<String, String> connection = client.connect()) {
            ctx.out.kv("ativo no início", label(connection.getCurrentEndpoint()));

            ctx.out.step("Heartbeat: SET " + heartbeat + " a cada 500 ms por 6 s. Derrube o east em outro terminal e veja a troca");
            ctx.out.cmd("SET " + heartbeat + " <instante>  (x" + JedisLab.BEATS + ")");
            int ok = 0;
            int failed = 0;
            String active = "?";
            for (int i = 1; i <= JedisLab.BEATS; i++) {
                try {
                    connection.sync().set(heartbeat, Instant.now().toString());
                    active = label(connection.getCurrentEndpoint());
                    ok++;
                    ctx.out.info(String.format("batida %2d -> %s", i, active));
                } catch (RedisException e) {
                    failed++;
                    ctx.out.warn(String.format("batida %2d falhou: %s", i, JedisLab.firstLine(e.getMessage())));
                }
                if (i < JedisLab.BEATS) Thread.sleep(JedisLab.BEAT_MS);
            }
            ctx.out.kv("east saudável", health(connection, east));
            ctx.out.kv("west saudável", health(connection, west));
            ctx.out.info("Alternativa do lado do servidor: o Redis Cloud redireciona o endpoint da réplica Active-Active que caiu para a que está de pé.");

            try (redis.clients.jedis.RedisClient home = Clients.jedis()) {
                home.hset(ctx.k("aa", "clients"), ctx.client, Instant.now().toString());
            }
            ctx.done("failover", "ok",
                    "beats_ok", String.valueOf(ok),
                    "beats_failed", String.valueOf(failed),
                    "switches", String.valueOf(switches.get()),
                    "active", active);
        } finally {
            events.dispose();
            client.shutdown(Duration.ZERO, Duration.ofSeconds(2));
        }
    }

    static String label(RedisURI uri) {
        return uri == null ? "?" : uri.getHost() + ":" + uri.getPort();
    }

    /** A database that was down when the client started is not registered yet, and isHealthy() rejects it. */
    static String health(StatefulRedisMultiDbConnection<String, String> connection, RedisURI uri) {
        try {
            return String.valueOf(connection.isHealthy(uri));
        } catch (IllegalArgumentException e) {
            return "não registrado (fora do ar desde o início)";
        }
    }
}
