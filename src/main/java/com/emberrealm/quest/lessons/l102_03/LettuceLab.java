package com.emberrealm.quest.lessons.l102_03;

import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Lab;
import com.emberrealm.quest.lessons.l102_02.CombatEvents;
import io.lettuce.core.Consumer;
import io.lettuce.core.Limit;
import io.lettuce.core.Range;
import io.lettuce.core.RedisCommandExecutionException;
import io.lettuce.core.StreamMessage;
import io.lettuce.core.XAddArgs;
import io.lettuce.core.XAutoClaimArgs;
import io.lettuce.core.XGroupCreateArgs;
import io.lettuce.core.XReadArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.models.stream.ClaimedMessages;
import io.lettuce.core.models.stream.PendingMessage;
import io.lettuce.core.models.stream.PendingMessages;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 102-03 (Lettuce): same loot workers. Consumer.from(group, name) identifies who reads; StreamOffset.lastConsumed(key) is ">". */
public final class LettuceLab implements Lab {

    @Override
    public void run(Ctx ctx) {
        String stream = ctx.k("events", "combat");
        String group = JedisLab.GROUP;
        try (StatefulRedisConnection<String, String> connection = Clients.lettuceConnection()) {
            RedisCommands<String, String> redis = connection.sync();

            ctx.out.step("O diário de combate precisa existir com " + CombatEvents.COUNT + " golpes");
            ensureStream(ctx, redis, stream);

            ctx.out.step("Grupo " + group + " começando do id 0: cada golpe vai para um só worker");
            ctx.out.cmd("XGROUP DESTROY " + stream + " " + group);
            try {
                redis.xgroupDestroy(stream, group);
            } catch (RedisCommandExecutionException noGroup) {
                // first run: nothing to destroy
            }
            ctx.out.cmd("XGROUP CREATE " + stream + " " + group + " 0 MKSTREAM");
            ctx.out.kv("XGROUP CREATE", redis.xgroupCreate(XReadArgs.StreamOffset.from(stream, "0"), group, new XGroupCreateArgs().mkstream(true)));
            ctx.out.info("0 entrega tudo que já está no diário; $ entregaria só o que chegar depois. MKSTREAM cria a chave se ela não existir.");

            Set<String> crashBeforeAck = new HashSet<>();
            for (StreamMessage<String, String> message : redis.xrevrange(stream, Range.unbounded(), Limit.from(2))) {
                crashBeforeAck.add(message.getId());
            }

            ctx.out.step("worker-1 e worker-2 lendo com XREADGROUP, " + JedisLab.BATCH + " golpes por vez, e confirmando com XACK");
            int delivered = 0;
            long acked = 0;
            for (int round = 0; round < 10; round++) {
                boolean anything = false;
                for (String worker : JedisLab.WORKERS) {
                    ctx.out.cmd("XREADGROUP GROUP " + group + " " + worker + " COUNT " + JedisLab.BATCH + " STREAMS " + stream + " >");
                    List<StreamMessage<String, String>> entries = redis.xreadgroup(Consumer.from(group, worker),
                            XReadArgs.Builder.count(JedisLab.BATCH), XReadArgs.StreamOffset.lastConsumed(stream));
                    if (entries == null || entries.isEmpty()) {
                        ctx.out.kv(worker, "nada novo (nil)");
                        continue;
                    }
                    anything = true;
                    delivered += entries.size();
                    ctx.out.kv(worker, entries.size() + " golpes, de " + entries.get(0).getId() + " a " + entries.get(entries.size() - 1).getId());
                    List<String> toAck = new ArrayList<>();
                    for (StreamMessage<String, String> message : entries) {
                        if (!crashBeforeAck.contains(message.getId())) toAck.add(message.getId());
                    }
                    if (toAck.size() < entries.size()) {
                        ctx.out.warn(worker + " caiu depois de processar " + toAck.size() + " golpes: "
                                + (entries.size() - toAck.size()) + " ficam entregues sem XACK");
                    }
                    if (!toAck.isEmpty()) {
                        ctx.out.cmd("XACK " + stream + " " + group + " " + String.join(" ", toAck));
                        acked += redis.xack(stream, group, toAck.toArray(new String[0]));
                    }
                }
                if (!anything) break;
            }
            ctx.out.kv("golpes entregues", delivered);
            ctx.out.kv("confirmados com XACK", acked);
            ctx.out.info("> entrega só o que ninguém do grupo recebeu ainda. Cada entrega entra na PEL (pending entries list) até o XACK.");

            ctx.out.step("XPENDING: entregue mas não confirmado");
            ctx.out.cmd("XPENDING " + stream + " " + group);
            PendingMessages summary = redis.xpending(stream, group);
            ctx.out.kv("pendentes", summary.getCount());
            ctx.out.kv("por consumer", summary.getConsumerMessageCount());
            ctx.out.cmd("XPENDING " + stream + " " + group + " - + 10");
            for (PendingMessage pending : redis.xpending(stream, group, Range.unbounded(), Limit.from(10))) {
                ctx.out.info(pending.getId() + "  dono=" + pending.getConsumer()
                        + "  ocioso=" + pending.getMsSinceLastDelivery() + " ms  entregas=" + pending.getRedeliveryCount());
            }

            ctx.out.step(JedisLab.RECLAIMER + " assume os órfãos com XAUTOCLAIM (min-idle 0 só para a aula)");
            ctx.out.cmd("XAUTOCLAIM " + stream + " " + group + " " + JedisLab.RECLAIMER + " 0 0-0 COUNT 10");
            ClaimedMessages<String, String> claimed = redis.xautoclaim(stream, new XAutoClaimArgs<String>()
                    .consumer(Consumer.from(group, JedisLab.RECLAIMER)).minIdleTime(0).startId("0-0").count(10));
            List<StreamMessage<String, String>> reclaimed = claimed.getMessages();
            ctx.out.kv("reivindicados", reclaimed.size());
            for (StreamMessage<String, String> message : reclaimed) ctx.out.info(CombatEvents.describe(message.getId(), message.getBody()));
            ctx.out.kv("próximo cursor", claimed.getId() + " (0-0 significa que a PEL foi varrida inteira)");
            ctx.out.info("Em produção use um min-idle alto (60000 ms, por exemplo): só pega o que ficou parado tempo suficiente para dar o dono como morto.");
            List<String> reclaimedIds = reclaimed.stream().map(StreamMessage::getId).toList();
            if (!reclaimedIds.isEmpty()) {
                ctx.out.cmd("XACK " + stream + " " + group + " " + String.join(" ", reclaimedIds));
                redis.xack(stream, group, reclaimedIds.toArray(new String[0]));
            }
            ctx.out.cmd("XPENDING " + stream + " " + group);
            long pendingNow = redis.xpending(stream, group).getCount();
            ctx.out.kv("pendentes", pendingNow);

            ctx.out.step("XINFO GROUPS: a foto do grupo");
            ctx.out.cmd("XINFO GROUPS " + stream);
            for (Object raw : redis.xinfoGroups(stream)) ctx.out.info(describeGroup(raw));

            ctx.done("group", group, "delivered", String.valueOf(delivered), "reclaimed", String.valueOf(reclaimed.size()),
                    "pending", String.valueOf(pendingNow), "lettuce", "ok");
        }
    }

    static void ensureStream(Ctx ctx, RedisCommands<String, String> redis, String stream) {
        boolean ok = "stream".equals(redis.type(stream)) && redis.xlen(stream) == CombatEvents.COUNT;
        if (ok) {
            ctx.out.kv("XLEN " + stream, CombatEvents.COUNT + " (reaproveitando o diário da lição 102-02)");
            return;
        }
        ctx.out.cmd("UNLINK " + stream);
        redis.unlink(stream);
        XAddArgs args = new XAddArgs().maxlen(1000).approximateTrimming();
        for (Map<String, String> event : CombatEvents.events()) redis.xadd(stream, args, event);
        ctx.out.cmd("XADD " + stream + " MAXLEN ~ 1000 * attacker ... target ... damage ... zone ...   (x" + CombatEvents.COUNT + ")");
        ctx.out.kv("XLEN " + stream, redis.xlen(stream) + " (diário recriado)");
    }

    /** XINFO GROUPS comes back untyped: a map under RESP3, a flat key/value list under RESP2. */
    static String describeGroup(Object raw) {
        StringBuilder sb = new StringBuilder();
        if (raw instanceof Map<?, ?> map) {
            map.forEach((k, v) -> sb.append(k).append('=').append(v).append("  "));
        } else if (raw instanceof List<?> list) {
            Iterator<?> it = list.iterator();
            while (it.hasNext()) {
                Object key = it.next();
                Object value = it.hasNext() ? it.next() : "";
                sb.append(key).append('=').append(value).append("  ");
            }
        } else {
            sb.append(raw);
        }
        return sb.toString().trim();
    }
}
