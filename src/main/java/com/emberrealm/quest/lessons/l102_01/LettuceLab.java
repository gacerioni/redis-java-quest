package com.emberrealm.quest.lessons.l102_01;

import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Lab;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 102-01 (Lettuce): same zone chat. Lettuce keeps Pub/Sub on its own connection type
 * (connectPubSub()) and delivers messages to a listener on the netty event loop, no thread of ours.
 */
public final class LettuceLab implements Lab {

    @Override
    public void run(Ctx ctx) throws Exception {
        String channel = ctx.k("chat", "zone", JedisLab.ZONE);
        try (StatefulRedisConnection<String, String> connection = Clients.lettuceConnection()) {
            RedisCommands<String, String> redis = connection.sync();

            ctx.out.step("Ninguém no canal ainda: a primeira mensagem se perde");
            ctx.out.cmd("PUBSUB NUMSUB " + channel);
            ctx.out.kv("assinantes", redis.pubsubNumsub(channel).get(channel));
            ctx.out.cmd("PUBLISH " + channel + " \"" + JedisLab.LOST_LINE + "\"");
            long receiversBefore = redis.publish(channel, JedisLab.LOST_LINE);
            ctx.out.kv("receptores", receiversBefore);
            int lost = receiversBefore == 0 ? 1 : 0;
            ctx.out.info("PUBLISH devolve quantos clientes receberam. Zero: ninguém ouviu e o Redis não guarda a mensagem.");

            ctx.out.step("Assinando em uma conexão Pub/Sub dedicada: client.connectPubSub()");
            CountDownLatch subscribed = new CountDownLatch(1);
            CountDownLatch received = new CountDownLatch(JedisLab.LINES.size());
            List<String> inbox = new CopyOnWriteArrayList<>();
            try (StatefulRedisPubSubConnection<String, String> pubsub = Clients.lettuce().connectPubSub()) {
                pubsub.addListener(new RedisPubSubAdapter<>() {
                    @Override
                    public void subscribed(String ch, long count) {
                        subscribed.countDown();
                    }

                    @Override
                    public void message(String ch, String message) {
                        inbox.add(message);
                        received.countDown();
                    }
                });
                ctx.out.cmd("SUBSCRIBE " + channel);
                pubsub.sync().subscribe(channel);
                if (!subscribed.await(3, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("o servidor não confirmou o SUBSCRIBE em 3 s");
                }
                ctx.out.kv("subscribed()", "confirmado, o listener roda na thread de I/O do netty");
                ctx.out.cmd("PUBSUB NUMSUB " + channel);
                ctx.out.kv("assinantes", redis.pubsubNumsub(channel).get(channel));
                ctx.out.info("A conexão Pub/Sub só escuta. Os comandos normais continuam na conexão compartilhada.");

                ctx.out.step("Cinco mensagens no chat da zona");
                long receivers = 0;
                for (String line : JedisLab.LINES) {
                    ctx.out.cmd("PUBLISH " + channel + " \"" + line + "\"");
                    receivers += redis.publish(channel, line);
                }
                boolean allArrived = received.await(3, TimeUnit.SECONDS);
                ctx.out.kv("receptores somados", receivers + " (1 por PUBLISH)");
                ctx.out.kv("recebidas pelo listener", inbox.size() + (allArrived ? "" : " (faltou mensagem em 3 s)"));
                for (String message : inbox) ctx.out.info("  < " + message);

                ctx.out.step("Saindo do canal: UNSUBSCRIBE e close() da conexão Pub/Sub");
                ctx.out.cmd("UNSUBSCRIBE " + channel);
                pubsub.sync().unsubscribe(channel);
            }
            ctx.out.cmd("PUBSUB NUMSUB " + channel);
            ctx.out.kv("assinantes", redis.pubsubNumsub(channel).get(channel));

            ctx.done("received", String.valueOf(inbox.size()), "lost", String.valueOf(lost),
                    "published", String.valueOf(JedisLab.LINES.size() + 1), "lettuce", "ok");
        }
    }
}
