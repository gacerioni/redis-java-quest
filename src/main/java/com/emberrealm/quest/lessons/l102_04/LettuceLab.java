package com.emberrealm.quest.lessons.l102_04;

import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Lab;
import io.lettuce.core.Consumer;
import io.lettuce.core.KeyValue;
import io.lettuce.core.RedisCommandExecutionException;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.StreamMessage;
import io.lettuce.core.XGroupCreateArgs;
import io.lettuce.core.XReadArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 102-04 (Lettuce): the shared connection is multiplexed, which is great until a blocking command
 * sits in front of everything else. Each waiter gets its own connect(); the lab then measures a PING
 * stuck behind a BLPOP on the shared connection versus the same BLPOP on a dedicated one.
 */
public final class LettuceLab implements Lab {

    @Override
    public void run(Ctx ctx) throws Exception {
        int waiters = Waiters.count(ctx.out);
        String queue = ctx.k("queue", "dungeon");
        String waiterName = ctx.keys.prefix() + "-waiter";
        try (StatefulRedisConnection<String, String> shared = Clients.lettuceConnection()) {
            RedisCommands<String, String> redis = shared.sync();

            ctx.out.step("Fila limpa e uma foto das conexões antes de começar");
            ctx.out.cmd("UNLINK " + queue);
            redis.unlink(queue);
            ctx.out.cmd("INFO clients");
            Waiters.ClientsInfo before = Waiters.ClientsInfo.parse(redis.info("clients"));
            ctx.out.kv("antes", before.summary());

            ctx.out.step(waiters + " jogadores esperando a dungeon abrir: BRPOP " + Waiters.BRPOP_TIMEOUT_S + " s, cada um em um connect() próprio");
            ctx.out.info("A conexão compartilhada do Lettuce é multiplexada: um BRPOP nela travaria todo comando atrás. Bloqueante pede conexão dedicada.");
            AtomicInteger failed = new AtomicInteger();
            ExecutorService executor = Executors.newFixedThreadPool(waiters, Waiters.daemonFactory("waiter"));
            List<Future<Waiters.Outcome>> futures = new ArrayList<>();
            for (int n = 1; n <= waiters; n++) {
                int number = n;
                futures.add(executor.submit(() -> waitForSlot(queue, waiterName, number, failed)));
            }
            ctx.out.cmd("CLIENT SETNAME " + waiterName + "  +  BRPOP " + queue + " " + Waiters.BRPOP_TIMEOUT_S + "   (x" + waiters + ", uma conexão cada)");
            Waiters.poll(() -> Waiters.ClientsInfo.parse(redis.info("clients")).blocked() >= before.blocked() + waiters - failed.get(), 1500);
            ctx.out.cmd("INFO clients");
            ctx.out.kv("durante", Waiters.ClientsInfo.parse(redis.info("clients")).summary());
            ctx.out.cmd("CLIENT LIST");
            String list = redis.clientList();
            ctx.out.kv("conexões name=" + waiterName, Waiters.countLines(list, "name=" + waiterName));
            ctx.out.kv("uma delas", Waiters.sampleLine(list, "name=" + waiterName));
            ctx.out.info("flags=b é cliente bloqueado. A conexão existe, está ocupada e não faz mais nada até o BRPOP voltar.");

            ctx.out.step("A dungeon abre: um RPUSH com " + waiters + " vagas acorda todo mundo");
            String[] slots = Waiters.slots(waiters);
            ctx.out.cmd("RPUSH " + queue + " " + String.join(" ", slots));
            redis.rpush(queue, slots);
            executor.shutdown();
            executor.awaitTermination(Waiters.BRPOP_TIMEOUT_S + 3, TimeUnit.SECONDS);
            List<Waiters.Outcome> outcomes = new ArrayList<>();
            for (Future<Waiters.Outcome> future : futures) outcomes.add(future.get(1, TimeUnit.SECONDS));
            for (Waiters.Outcome outcome : outcomes) ctx.out.info(outcome.describe());
            ctx.out.info("O Redis atende os bloqueados na ordem em que chegaram: cada BRPOP levou exatamente um elemento.");
            long served = outcomes.stream().filter(o -> o.player() != null).count();
            long rejected = outcomes.stream().filter(Waiters.Outcome::rejected).count();
            ctx.out.cmd("LLEN " + queue);
            long left = redis.llen(queue);
            ctx.out.kv("LLEN", left);
            if (left > 0) {
                ctx.out.warn("sobraram " + left + " vagas: jogadores desistiram (timeout ou erro) antes do RPUSH. Limpando a fila.");
                redis.unlink(queue);
            }

            ctx.out.step("O perigo da conexão compartilhada: um BLPOP na frente segura o PING atrás");
            String emptyQueue = ctx.k("queue", "vazia");
            RedisAsyncCommands<String, String> async = shared.async();
            ctx.out.cmd("BLPOP " + emptyQueue + " 2   (conexão compartilhada, async)");
            ctx.out.cmd("PING                          (mesma conexão, logo atrás)");
            long start = System.nanoTime();
            RedisFuture<KeyValue<String, String>> stuck = async.blpop(2, emptyQueue);
            RedisFuture<String> ping = async.ping();
            ping.get(5, TimeUnit.SECONDS);
            long stallMs = Waiters.elapsedMs(start);
            ctx.out.kv("PING respondeu em", stallMs + " ms (esperou o BLPOP inteiro)");
            stuck.get(5, TimeUnit.SECONDS);
            ctx.out.info("O servidor atende uma conexão em ordem: enquanto o BLPOP espera, tudo que veio atrás dele espera junto.");
            long fastMs;
            try (StatefulRedisConnection<String, String> dedicated = Clients.lettuce().connect()) {
                ctx.out.cmd("BLPOP " + emptyQueue + " 2   (conexão dedicada)");
                RedisFuture<KeyValue<String, String>> stuckElsewhere = dedicated.async().blpop(2, emptyQueue);
                ctx.out.cmd("PING                          (conexão compartilhada)");
                start = System.nanoTime();
                String pong = redis.ping();
                fastMs = Waiters.elapsedMs(start);
                ctx.out.kv("PING respondeu em", (fastMs == 0 ? "menos de 1" : String.valueOf(fastMs)) + " ms (" + pong + ")");
                ctx.out.cmd("RPUSH " + emptyQueue + " sentinela   (acorda o BLPOP e libera a conexão dedicada)");
                redis.rpush(emptyQueue, "sentinela");
                ctx.out.kv("BLPOP dedicado recebeu", stuckElsewhere.get(5, TimeUnit.SECONDS).getValue());
            }

            ctx.out.step("O limite de conexões deste servidor: maxclients " + (before.maxclients() < 0 ? "?" : before.maxclients()));
            ctx.out.info("Redis Cloud free: 30 conexões, contando aplicação, Redis Insight e redis-cli. O Docker do curso imita isso (maxclients 30).");
            ctx.out.info("Experimento: QUEST_WAITERS=31 ./quest run 102-04 lettuce contra o Docker local. O 31º connect() falha com max number of clients reached.");
            if (rejected > 0) {
                ctx.out.warn(rejected + " jogador(es) barrado(s) na porta: max number of clients reached");
                ctx.out.hint("É este o erro que a aplicação vê quando as conexões de todas as instâncias somam mais que o plano permite.");
            }

            ctx.out.step("Não é só BRPOP: XREADGROUP BLOCK e SUBSCRIBE também seguram a conexão");
            String stream = ctx.k("events", "combat");
            String channel = ctx.k("chat", "zone", Waiters.CHAT_ZONE);
            try {
                redis.xgroupCreate(XReadArgs.StreamOffset.latest(stream), Waiters.SENTINEL_GROUP, new XGroupCreateArgs().mkstream(true));
            } catch (RedisCommandExecutionException busyGroup) {
                // the group survived a previous run
            }
            try (StatefulRedisConnection<String, String> readerConnection = Clients.lettuce().connect();
                 StatefulRedisPubSubConnection<String, String> pubsub = Clients.lettuce().connectPubSub()) {
                CountDownLatch subscribed = new CountDownLatch(1);
                pubsub.addListener(new RedisPubSubAdapter<>() {
                    @Override
                    public void subscribed(String ch, long count) {
                        subscribed.countDown();
                    }
                });
                ctx.out.cmd("XREADGROUP GROUP " + Waiters.SENTINEL_GROUP + " vigia BLOCK 1500 COUNT 1 STREAMS " + stream + " >   (conexão dedicada)");
                RedisFuture<List<StreamMessage<String, String>>> reading = readerConnection.async().xreadgroup(
                        Consumer.from(Waiters.SENTINEL_GROUP, "vigia"),
                        XReadArgs.Builder.block(1500).count(1),
                        XReadArgs.StreamOffset.lastConsumed(stream));
                ctx.out.cmd("SUBSCRIBE " + channel + "   (conexão Pub/Sub)");
                pubsub.sync().subscribe(channel);
                subscribed.await(3, TimeUnit.SECONDS);
                Waiters.poll(() -> Waiters.ClientsInfo.parse(redis.info("clients")).blocked() >= before.blocked() + 1, 1500);
                ctx.out.cmd("INFO clients");
                ctx.out.kv("durante", Waiters.ClientsInfo.parse(redis.info("clients")).summary());
                ctx.out.info("blocked_clients conta o XREADGROUP BLOCK; pubsub_clients conta o SUBSCRIBE. Cada um é uma conexão a menos para o resto da aplicação.");
                pubsub.sync().unsubscribe(channel);
                List<StreamMessage<String, String>> messages = reading.get(5, TimeUnit.SECONDS);
                ctx.out.kv("XREADGROUP voltou", (messages == null || messages.isEmpty() ? "sem golpes novos" : messages.size() + " golpes") + " após 1500 ms");
            }
            try {
                redis.xgroupDestroy(stream, Waiters.SENTINEL_GROUP);
            } catch (RedisCommandExecutionException ignored) {
                // nothing to clean
            }

            ctx.out.step("Tudo fechado: conexões dos jogadores, dedicada, do leitor e Pub/Sub");
            ctx.out.cmd("CLIENT LIST");
            ctx.out.kv("conexões name=" + waiterName, Waiters.countLines(redis.clientList(), "name=" + waiterName));
            ctx.out.cmd("INFO clients");
            ctx.out.kv("depois", Waiters.ClientsInfo.parse(redis.info("clients")).summary());

            ctx.done("waiters", String.valueOf(waiters), "served", String.valueOf(served),
                    "rejected", String.valueOf(rejected), "stall_ms", String.valueOf(stallMs),
                    "dedicated_ms", String.valueOf(fastMs), "lettuce", "ok");
        }
    }

    /** One waiter: its own connection, named so CLIENT LIST shows it, blocked on BRPOP until a slot arrives. */
    private static Waiters.Outcome waitForSlot(String queue, String name, int number, AtomicInteger failed) {
        long start = System.nanoTime();
        try (StatefulRedisConnection<String, String> own = Clients.lettuce().connect()) {
            RedisCommands<String, String> mine = own.sync();
            mine.clientSetname(name);
            KeyValue<String, String> popped = mine.brpop(Waiters.BRPOP_TIMEOUT_S, queue);
            String player = popped == null || !popped.hasValue() ? null : popped.getValue();
            return new Waiters.Outcome(number, player, null, Waiters.elapsedMs(start));
        } catch (Exception e) {
            failed.incrementAndGet();
            return new Waiters.Outcome(number, null, Waiters.rootMessage(e), Waiters.elapsedMs(start));
        }
    }
}
