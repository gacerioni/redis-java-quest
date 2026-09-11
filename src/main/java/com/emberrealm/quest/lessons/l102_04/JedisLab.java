package com.emberrealm.quest.lessons.l102_04;

import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Env;
import com.emberrealm.quest.core.Lab;
import redis.clients.jedis.ConnectionPoolConfig;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.StreamEntryID;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.exceptions.JedisException;
import redis.clients.jedis.params.XReadGroupParams;
import redis.clients.jedis.util.JedisURIHelper;
import redis.clients.jedis.util.SafeEncoder;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 102-04 (Jedis): a blocking command holds one connection for as long as it waits.
 * With Jedis that connection comes out of the pool, so N waiters on BRPOP occupy N pooled connections
 * and anything else on the same pool queues up behind them. The lab shows it with a dedicated pool
 * sized exactly for the waiters, then wakes everyone with one RPUSH, then repeats the point with
 * XREADGROUP BLOCK and SUBSCRIBE. Everything it opens is closed before the marker is written.
 */
public final class JedisLab implements Lab {

    @Override
    public void run(Ctx ctx) throws Exception {
        int waiters = Waiters.count(ctx.out);
        String queue = ctx.k("queue", "dungeon");
        String waiterName = ctx.keys.prefix() + "-waiter";
        try (RedisClient jedis = Clients.jedis()) {
            ctx.out.step("Fila limpa e uma foto das conexões antes de começar");
            ctx.out.cmd("UNLINK " + queue);
            jedis.unlink(queue);
            ctx.out.cmd("INFO clients");
            Waiters.ClientsInfo before = Waiters.ClientsInfo.parse(jedis.info("clients"));
            ctx.out.kv("antes", before.summary());

            ctx.out.step(waiters + " jogadores esperando a dungeon abrir: BRPOP " + Waiters.BRPOP_TIMEOUT_S + " s, cada um em uma conexão");
            ctx.out.info("Pool dedicado com maxTotal " + waiters + " e clientName " + waiterName + ": um comando bloqueante ocupa a conexão do pool até voltar.");
            ConnectionPoolConfig poolConfig = new ConnectionPoolConfig();
            poolConfig.setMaxTotal(waiters);
            poolConfig.setMaxWait(Duration.ofMillis(500));
            URI uri = URI.create(Env.redisUrl());
            DefaultJedisClientConfig config = DefaultJedisClientConfig.builder()
                    .user(JedisURIHelper.getUser(uri))
                    .password(JedisURIHelper.getPassword(uri))
                    .ssl(JedisURIHelper.isRedisSSLScheme(uri))
                    .clientName(waiterName)
                    .build();
            List<Waiters.Outcome> outcomes = new ArrayList<>();
            AtomicInteger failed = new AtomicInteger();
            try (RedisClient waiterPool = RedisClient.builder()
                    .hostAndPort(JedisURIHelper.getHostAndPort(uri))
                    .clientConfig(config)
                    .poolConfig(poolConfig)
                    .build()) {
                ExecutorService executor = Executors.newFixedThreadPool(waiters, Waiters.daemonFactory("waiter"));
                List<Future<Waiters.Outcome>> futures = new ArrayList<>();
                for (int n = 1; n <= waiters; n++) {
                    int number = n;
                    futures.add(executor.submit(() -> waitForSlot(waiterPool, queue, number, failed)));
                }
                ctx.out.cmd("BRPOP " + queue + " " + Waiters.BRPOP_TIMEOUT_S + "   (x" + waiters + ", uma thread e uma conexão cada)");
                Waiters.poll(() -> Waiters.ClientsInfo.parse(jedis.info("clients")).blocked() >= before.blocked() + waiters - failed.get(), 1500);
                ctx.out.cmd("INFO clients");
                ctx.out.kv("durante", Waiters.ClientsInfo.parse(jedis.info("clients")).summary());
                ctx.out.cmd("CLIENT LIST");
                String list = clientList(jedis);
                ctx.out.kv("conexões name=" + waiterName, Waiters.countLines(list, "name=" + waiterName));
                ctx.out.kv("uma delas", Waiters.sampleLine(list, "name=" + waiterName));
                ctx.out.kv("pool: getNumActive()", waiterPool.getPool().getNumActive() + " de " + waiters);
                ctx.out.info("flags=b é cliente bloqueado. A conexão existe, está ocupada e não faz mais nada até o BRPOP voltar.");

                ctx.out.step("Pool cheio: um PING pelo mesmo pool espera na fila e desiste em 500 ms");
                ctx.out.cmd("PING   (pool dos jogadores, maxTotal " + waiters + ", maxWait 500 ms)");
                long start = System.nanoTime();
                try {
                    String pong = waiterPool.ping();
                    ctx.out.kv("PING", pong + " em " + Waiters.elapsedMs(start) + " ms (alguma conexão do pool estava livre)");
                } catch (JedisException e) {
                    String why = Waiters.rootMessage(e);
                    ctx.out.kv("PING", "falhou após " + Waiters.elapsedMs(start) + " ms: " + why);
                    if (why.toLowerCase().contains("max number of clients")) {
                        ctx.out.info("O pool ainda tinha vaga, mas o servidor não: tentou abrir mais uma conexão e o Redis recusou.");
                    } else {
                        ctx.out.info("Timeout em cascata: quem não tinha nada a ver com a fila também parou. Dimensione o pool para a concorrência normal mais os bloqueantes.");
                    }
                }
                ctx.out.cmd("PING   (client principal, outro pool)");
                start = System.nanoTime();
                ctx.out.kv("PING", jedis.ping() + " em " + Waiters.elapsedMs(start) + " ms");

                ctx.out.step("A dungeon abre: um RPUSH com " + waiters + " vagas acorda todo mundo");
                String[] slots = Waiters.slots(waiters);
                ctx.out.cmd("RPUSH " + queue + " " + String.join(" ", slots));
                jedis.rpush(queue, slots);
                executor.shutdown();
                executor.awaitTermination(Waiters.BRPOP_TIMEOUT_S + 3, TimeUnit.SECONDS);
                for (Future<Waiters.Outcome> future : futures) outcomes.add(future.get(1, TimeUnit.SECONDS));
                for (Waiters.Outcome outcome : outcomes) ctx.out.info(outcome.describe());
                ctx.out.info("O Redis atende os bloqueados na ordem em que chegaram: cada BRPOP levou exatamente um elemento.");
            }
            long served = outcomes.stream().filter(o -> o.player() != null).count();
            long rejected = outcomes.stream().filter(Waiters.Outcome::rejected).count();
            ctx.out.cmd("LLEN " + queue);
            long left = jedis.llen(queue);
            ctx.out.kv("LLEN", left);
            if (left > 0) {
                ctx.out.warn("sobraram " + left + " vagas: jogadores desistiram (timeout ou erro) antes do RPUSH. Limpando a fila.");
                jedis.unlink(queue);
            }

            ctx.out.step("O limite de conexões deste servidor: maxclients " + (before.maxclients() < 0 ? "?" : before.maxclients()));
            ctx.out.info("Redis Cloud free: 30 conexões, contando aplicação, Redis Insight e redis-cli. O Docker do curso imita isso (maxclients 30).");
            ctx.out.info("Experimento: QUEST_WAITERS=31 ./quest run 102-04 jedis contra o Docker local. O 31º jogador recebe ERR max number of clients reached.");
            if (rejected > 0) {
                ctx.out.warn(rejected + " jogador(es) barrado(s) na porta: max number of clients reached");
                ctx.out.hint("É este o erro que a aplicação vê quando os pools de todas as instâncias somam mais que o plano permite.");
            }

            ctx.out.step("Não é só BRPOP: XREADGROUP BLOCK e SUBSCRIBE também seguram a conexão");
            String stream = ctx.k("events", "combat");
            String channel = ctx.k("chat", "zone", Waiters.CHAT_ZONE);
            try {
                jedis.xgroupCreate(stream, Waiters.SENTINEL_GROUP, StreamEntryID.XGROUP_LAST_ENTRY, true);
            } catch (JedisDataException busyGroup) {
                // the group survived a previous run
            }
            Thread reader = new Thread(() -> jedis.xreadGroup(Waiters.SENTINEL_GROUP, "vigia",
                    XReadGroupParams.xReadGroupParams().block(1500).count(1),
                    Map.of(stream, StreamEntryID.XREADGROUP_UNDELIVERED_ENTRY)), "sentinel-reader");
            reader.setDaemon(true);
            CountDownLatch subscribed = new CountDownLatch(1);
            JedisPubSub listener = new JedisPubSub() {
                @Override
                public void onSubscribe(String ch, int subscribedChannels) {
                    subscribed.countDown();
                }
            };
            Thread subscriber = new Thread(() -> jedis.subscribe(listener, channel), "sentinel-subscriber");
            subscriber.setDaemon(true);
            ctx.out.cmd("XREADGROUP GROUP " + Waiters.SENTINEL_GROUP + " vigia BLOCK 1500 COUNT 1 STREAMS " + stream + " >");
            ctx.out.cmd("SUBSCRIBE " + channel);
            reader.start();
            subscriber.start();
            subscribed.await(3, TimeUnit.SECONDS);
            Waiters.poll(() -> Waiters.ClientsInfo.parse(jedis.info("clients")).blocked() >= before.blocked() + 1, 1500);
            ctx.out.cmd("INFO clients");
            ctx.out.kv("durante", Waiters.ClientsInfo.parse(jedis.info("clients")).summary());
            ctx.out.info("blocked_clients conta o XREADGROUP BLOCK; pubsub_clients conta o SUBSCRIBE. Cada um é uma conexão a menos para o resto da aplicação.");
            listener.unsubscribe();
            reader.join(3000);
            subscriber.join(3000);
            try {
                jedis.xgroupDestroy(stream, Waiters.SENTINEL_GROUP);
            } catch (JedisDataException ignored) {
                // nothing to clean
            }

            ctx.out.step("Tudo fechado: pool dos jogadores, thread do XREADGROUP e assinante");
            ctx.out.cmd("CLIENT LIST");
            ctx.out.kv("conexões name=" + waiterName, Waiters.countLines(clientList(jedis), "name=" + waiterName));
            ctx.out.cmd("INFO clients");
            ctx.out.kv("depois", Waiters.ClientsInfo.parse(jedis.info("clients")).summary());
            ctx.out.info("O que sobrou é o pool do client principal: conexões ociosas, devolvidas ao pool e fechadas no close().");

            ctx.done("waiters", String.valueOf(waiters), "served", String.valueOf(served),
                    "rejected", String.valueOf(rejected), "jedis", "ok");
        }
    }

    /** One waiter: borrows a pooled connection and blocks on BRPOP until a slot arrives or the timeout passes. */
    private static Waiters.Outcome waitForSlot(RedisClient pool, String queue, int number, AtomicInteger failed) {
        long start = System.nanoTime();
        try {
            List<String> popped = pool.brpop(Waiters.BRPOP_TIMEOUT_S, queue);
            String player = popped == null || popped.size() < 2 ? null : popped.get(1);
            return new Waiters.Outcome(number, player, null, Waiters.elapsedMs(start));
        } catch (Exception e) {
            failed.incrementAndGet();
            return new Waiters.Outcome(number, null, Waiters.rootMessage(e), Waiters.elapsedMs(start));
        }
    }

    /** CLIENT LIST has no typed helper in Jedis 8: send it raw and decode the bulk string. */
    static String clientList(RedisClient jedis) {
        Object raw = jedis.sendCommand(Protocol.Command.CLIENT, "LIST");
        return raw instanceof byte[] bytes ? SafeEncoder.encode(bytes) : String.valueOf(raw);
    }
}
