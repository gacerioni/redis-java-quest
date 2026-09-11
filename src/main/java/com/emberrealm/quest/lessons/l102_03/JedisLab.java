package com.emberrealm.quest.lessons.l102_03;

import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Lab;
import com.emberrealm.quest.lessons.l102_02.CombatEvents;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.StreamEntryID;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.params.XAddParams;
import redis.clients.jedis.params.XAutoClaimParams;
import redis.clients.jedis.params.XPendingParams;
import redis.clients.jedis.params.XReadGroupParams;
import redis.clients.jedis.resps.StreamEntry;
import redis.clients.jedis.resps.StreamGroupInfo;
import redis.clients.jedis.resps.StreamPendingEntry;
import redis.clients.jedis.resps.StreamPendingSummary;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 102-03 (Jedis): a consumer group splits the combat log between loot workers. Each entry is delivered
 * to exactly one consumer of the group and stays in the pending entries list (PEL) until XACK.
 * One worker "crashes" before acknowledging two entries; XPENDING shows them and XAUTOCLAIM hands
 * them to a third worker.
 */
public final class JedisLab implements Lab {

    static final String GROUP = "loot-workers";
    static final String[] WORKERS = {"worker-1", "worker-2"};
    static final String RECLAIMER = "worker-3";
    static final int BATCH = 5;

    @Override
    public void run(Ctx ctx) {
        String stream = ctx.k("events", "combat");
        try (RedisClient jedis = Clients.jedis()) {
            ctx.out.step("O diário de combate precisa existir com " + CombatEvents.COUNT + " golpes");
            ensureStream(ctx, jedis, stream);

            ctx.out.step("Grupo " + GROUP + " começando do id 0: cada golpe vai para um só worker");
            ctx.out.cmd("XGROUP DESTROY " + stream + " " + GROUP);
            try {
                jedis.xgroupDestroy(stream, GROUP);
            } catch (JedisDataException noGroup) {
                // first run: nothing to destroy
            }
            ctx.out.cmd("XGROUP CREATE " + stream + " " + GROUP + " 0 MKSTREAM");
            ctx.out.kv("XGROUP CREATE", jedis.xgroupCreate(stream, GROUP, new StreamEntryID("0-0"), true));
            ctx.out.info("0 entrega tudo que já está no diário; $ entregaria só o que chegar depois. MKSTREAM cria a chave se ela não existir.");

            // The worker that receives the two newest blows will "crash" before acknowledging them.
            Set<StreamEntryID> crashBeforeAck = new HashSet<>();
            for (StreamEntry entry : jedis.xrevrange(stream, "+", "-", 2)) crashBeforeAck.add(entry.getID());

            ctx.out.step("worker-1 e worker-2 lendo com XREADGROUP, " + BATCH + " golpes por vez, e confirmando com XACK");
            int delivered = 0;
            long acked = 0;
            for (int round = 0; round < 10; round++) {
                boolean anything = false;
                for (String worker : WORKERS) {
                    ctx.out.cmd("XREADGROUP GROUP " + GROUP + " " + worker + " COUNT " + BATCH + " STREAMS " + stream + " >");
                    List<Map.Entry<String, List<StreamEntry>>> reply = jedis.xreadGroup(GROUP, worker,
                            XReadGroupParams.xReadGroupParams().count(BATCH),
                            Map.of(stream, StreamEntryID.XREADGROUP_UNDELIVERED_ENTRY));
                    List<StreamEntry> entries = new ArrayList<>();
                    if (reply != null) reply.forEach(perStream -> entries.addAll(perStream.getValue()));
                    if (entries.isEmpty()) {
                        ctx.out.kv(worker, "nada novo (nil)");
                        continue;
                    }
                    anything = true;
                    delivered += entries.size();
                    ctx.out.kv(worker, entries.size() + " golpes, de " + entries.get(0).getID() + " a " + entries.get(entries.size() - 1).getID());
                    List<StreamEntryID> toAck = new ArrayList<>();
                    for (StreamEntry entry : entries) {
                        if (!crashBeforeAck.contains(entry.getID())) toAck.add(entry.getID());
                    }
                    if (toAck.size() < entries.size()) {
                        ctx.out.warn(worker + " caiu depois de processar " + toAck.size() + " golpes: "
                                + (entries.size() - toAck.size()) + " ficam entregues sem XACK");
                    }
                    if (!toAck.isEmpty()) {
                        ctx.out.cmd("XACK " + stream + " " + GROUP + " " + join(toAck));
                        acked += jedis.xack(stream, GROUP, toAck.toArray(new StreamEntryID[0]));
                    }
                }
                if (!anything) break;
            }
            ctx.out.kv("golpes entregues", delivered);
            ctx.out.kv("confirmados com XACK", acked);
            ctx.out.info("> entrega só o que ninguém do grupo recebeu ainda. Cada entrega entra na PEL (pending entries list) até o XACK.");

            ctx.out.step("XPENDING: entregue mas não confirmado");
            ctx.out.cmd("XPENDING " + stream + " " + GROUP);
            StreamPendingSummary summary = jedis.xpending(stream, GROUP);
            ctx.out.kv("pendentes", summary.getTotal());
            ctx.out.kv("por consumer", summary.getConsumerMessageCount());
            ctx.out.cmd("XPENDING " + stream + " " + GROUP + " - + 10");
            for (StreamPendingEntry pending : jedis.xpending(stream, GROUP, XPendingParams.xPendingParams("-", "+", 10))) {
                ctx.out.info(pending.getID() + "  dono=" + pending.getConsumerName()
                        + "  ocioso=" + pending.getIdleTime() + " ms  entregas=" + pending.getDeliveredTimes());
            }

            ctx.out.step(RECLAIMER + " assume os órfãos com XAUTOCLAIM (min-idle 0 só para a aula)");
            ctx.out.cmd("XAUTOCLAIM " + stream + " " + GROUP + " " + RECLAIMER + " 0 0-0 COUNT 10");
            Map.Entry<StreamEntryID, List<StreamEntry>> claimed = jedis.xautoclaim(stream, GROUP, RECLAIMER, 0,
                    new StreamEntryID("0-0"), XAutoClaimParams.xAutoClaimParams().count(10));
            List<StreamEntry> reclaimed = claimed.getValue();
            ctx.out.kv("reivindicados", reclaimed.size());
            for (StreamEntry entry : reclaimed) ctx.out.info(CombatEvents.describe(entry.getID().toString(), entry.getFields()));
            ctx.out.kv("próximo cursor", claimed.getKey() + " (0-0 significa que a PEL foi varrida inteira)");
            ctx.out.info("Em produção use um min-idle alto (60000 ms, por exemplo): só pega o que ficou parado tempo suficiente para dar o dono como morto.");
            List<StreamEntryID> reclaimedIds = reclaimed.stream().map(StreamEntry::getID).toList();
            if (!reclaimedIds.isEmpty()) {
                ctx.out.cmd("XACK " + stream + " " + GROUP + " " + join(reclaimedIds));
                jedis.xack(stream, GROUP, reclaimedIds.toArray(new StreamEntryID[0]));
            }
            ctx.out.cmd("XPENDING " + stream + " " + GROUP);
            long pendingNow = jedis.xpending(stream, GROUP).getTotal();
            ctx.out.kv("pendentes", pendingNow);

            ctx.out.step("XINFO GROUPS: a foto do grupo");
            ctx.out.cmd("XINFO GROUPS " + stream);
            for (StreamGroupInfo group : jedis.xinfoGroups(stream)) {
                ctx.out.info(group.getName() + "  consumers=" + group.getConsumers() + "  pending=" + group.getPending()
                        + "  last-delivered-id=" + group.getLastDeliveredId());
            }

            ctx.done("group", GROUP, "delivered", String.valueOf(delivered), "reclaimed", String.valueOf(reclaimed.size()),
                    "pending", String.valueOf(pendingNow), "jedis", "ok");
        }
    }

    /** Reuses the stream from 102-02 when it has the expected 20 entries; rebuilds it otherwise. */
    static void ensureStream(Ctx ctx, RedisClient jedis, String stream) {
        boolean ok = "stream".equals(jedis.type(stream)) && jedis.xlen(stream) == CombatEvents.COUNT;
        if (ok) {
            ctx.out.kv("XLEN " + stream, CombatEvents.COUNT + " (reaproveitando o diário da lição 102-02)");
            return;
        }
        ctx.out.cmd("UNLINK " + stream);
        jedis.unlink(stream);
        XAddParams params = XAddParams.xAddParams().maxLen(1000).approximateTrimming();
        for (Map<String, String> event : CombatEvents.events()) jedis.xadd(stream, params, event);
        ctx.out.cmd("XADD " + stream + " MAXLEN ~ 1000 * attacker ... target ... damage ... zone ...   (x" + CombatEvents.COUNT + ")");
        ctx.out.kv("XLEN " + stream, jedis.xlen(stream) + " (diário recriado)");
    }

    private static String join(List<StreamEntryID> ids) {
        StringBuilder sb = new StringBuilder();
        for (StreamEntryID id : ids) sb.append(sb.isEmpty() ? "" : " ").append(id);
        return sb.toString();
    }
}
