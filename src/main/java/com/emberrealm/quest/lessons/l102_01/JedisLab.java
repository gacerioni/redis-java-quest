package com.emberrealm.quest.lessons.l102_01;

import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Lab;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.util.SafeEncoder;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 102-01 (Jedis): the zone chat over Pub/Sub.
 * Pub/Sub is fire and forget: a PUBLISH with nobody subscribed is gone for good, so the lab loses one
 * message on purpose, then subscribes on a dedicated thread (SUBSCRIBE blocks the calling thread and
 * holds one pooled connection until unsubscribe()) and receives five messages.
 */
public final class JedisLab implements Lab {

    static final String ZONE = "floresta-de-cinzas";
    static final String LOST_LINE = "Thane: alguém aí?";
    static final List<String> LINES = List.of(
            "Kaelith: alguém viu o lobo de cinzas por aqui?",
            "Brom: vi, perto da ponte quebrada. Cuidado com a matilha.",
            "Lyra: estou a caminho com flechas de prata.",
            "Nix: alguém vende poção de mana? Pago bem.",
            "Seraphine: grupo saindo para as ruínas em cinco minutos.");

    @Override
    public void run(Ctx ctx) throws Exception {
        String channel = ctx.k("chat", "zone", ZONE);
        try (RedisClient jedis = Clients.jedis()) {
            ctx.out.step("Ninguém no canal ainda: a primeira mensagem se perde");
            ctx.out.cmd("PUBSUB NUMSUB " + channel);
            ctx.out.kv("assinantes", numsub(jedis, channel));
            ctx.out.cmd("PUBLISH " + channel + " \"" + LOST_LINE + "\"");
            long receiversBefore = jedis.publish(channel, LOST_LINE);
            ctx.out.kv("receptores", receiversBefore);
            int lost = receiversBefore == 0 ? 1 : 0;
            ctx.out.info("PUBLISH devolve quantos clientes receberam. Zero: ninguém ouviu e o Redis não guarda a mensagem.");

            ctx.out.step("Assinando o canal em uma thread dedicada (SUBSCRIBE bloqueia a conexão)");
            CountDownLatch subscribed = new CountDownLatch(1);
            CountDownLatch received = new CountDownLatch(LINES.size());
            List<String> inbox = new CopyOnWriteArrayList<>();
            JedisPubSub listener = new JedisPubSub() {
                @Override
                public void onSubscribe(String ch, int subscribedChannels) {
                    subscribed.countDown();
                }

                @Override
                public void onMessage(String ch, String message) {
                    inbox.add(message);
                    received.countDown();
                }
            };
            // subscribe(...) only returns after unsubscribe(): it needs its own thread.
            Thread subscriber = new Thread(() -> jedis.subscribe(listener, channel), "zone-chat-subscriber");
            subscriber.setDaemon(true);
            subscriber.start();
            ctx.out.cmd("SUBSCRIBE " + channel);
            if (!subscribed.await(3, TimeUnit.SECONDS)) {
                throw new IllegalStateException("o servidor não confirmou o SUBSCRIBE em 3 s");
            }
            ctx.out.kv("onSubscribe", "confirmado, a thread " + subscriber.getName() + " agora só escuta");
            ctx.out.cmd("PUBSUB NUMSUB " + channel);
            ctx.out.kv("assinantes", numsub(jedis, channel));
            ctx.out.info("Essa conexão do pool ficou presa no SUBSCRIBE. As outras threads seguem usando o resto do pool.");

            ctx.out.step("Cinco mensagens no chat da zona");
            long receivers = 0;
            for (String line : LINES) {
                ctx.out.cmd("PUBLISH " + channel + " \"" + line + "\"");
                receivers += jedis.publish(channel, line);
            }
            boolean allArrived = received.await(3, TimeUnit.SECONDS);
            ctx.out.kv("receptores somados", receivers + " (1 por PUBLISH)");
            ctx.out.kv("recebidas pela thread assinante", inbox.size() + (allArrived ? "" : " (faltou mensagem em 3 s)"));
            for (String message : inbox) ctx.out.info("  < " + message);

            ctx.out.step("Saindo do canal: UNSUBSCRIBE devolve a conexão e a thread termina");
            ctx.out.cmd("UNSUBSCRIBE " + channel);
            listener.unsubscribe();
            subscriber.join(3000);
            ctx.out.kv("thread assinante viva", subscriber.isAlive());
            ctx.out.cmd("PUBSUB NUMSUB " + channel);
            ctx.out.kv("assinantes", numsub(jedis, channel));

            ctx.done("received", String.valueOf(inbox.size()), "lost", String.valueOf(lost),
                    "published", String.valueOf(LINES.size() + 1), "jedis", "ok");
        }
    }

    /** PUBSUB NUMSUB has no typed helper in Jedis 8, so we send it raw: reply is [channel, count]. */
    static long numsub(RedisClient jedis, String channel) {
        Object raw = jedis.sendCommand(Protocol.Command.PUBSUB, "NUMSUB", channel);
        if (raw instanceof List<?> list) {
            for (Object element : list) {
                if (element instanceof Long n) return n;
                if (element instanceof Map.Entry<?, ?> entry && entry.getValue() instanceof Long n) return n;
            }
            if (list.size() >= 2 && list.get(1) instanceof byte[] b) return Long.parseLong(SafeEncoder.encode(b));
        }
        return -1;
    }
}
