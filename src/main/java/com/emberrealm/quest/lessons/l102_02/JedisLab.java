package com.emberrealm.quest.lessons.l102_02;

import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Lab;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.StreamEntryID;
import redis.clients.jedis.params.XAddParams;
import redis.clients.jedis.params.XReadParams;
import redis.clients.jedis.resps.StreamEntry;

import java.util.List;
import java.util.Map;

/**
 * 102-02 (Jedis): the combat log as a Stream. Unlike Pub/Sub, every XADD is stored, ordered by a
 * server minted id (milliseconds-sequence) and can be read again from any point: XRANGE, XREVRANGE, XREAD.
 */
public final class JedisLab implements Lab {

    @Override
    public void run(Ctx ctx) {
        String stream = ctx.k("events", "combat");
        try (RedisClient jedis = Clients.jedis()) {
            ctx.out.step("Diário limpo e 20 golpes registrados com XADD");
            ctx.out.cmd("UNLINK " + stream);
            jedis.unlink(stream);
            XAddParams params = XAddParams.xAddParams().maxLen(1000).approximateTrimming();
            StreamEntryID first = null;
            StreamEntryID last = null;
            for (Map<String, String> event : CombatEvents.events()) {
                last = jedis.xadd(stream, params, event);
                if (first == null) first = last;
            }
            ctx.out.cmd("XADD " + stream + " MAXLEN ~ 1000 * attacker kaelith target lobo-de-cinzas damage 40 zone pico-gelido");
            ctx.out.kv("primeiro id", first);
            ctx.out.kv("último id", last);
            ctx.out.info("O id é <milissegundos>-<sequência>: o servidor gera, ordena e nunca repete. Dois golpes no mesmo ms viram -0 e -1.");
            ctx.out.info("MAXLEN ~ 1000 poda os mais antigos quando dá (o ~ deixa o Redis podar em blocos, muito mais barato).");
            ctx.out.cmd("XLEN " + stream);
            ctx.out.kv("XLEN", jedis.xlen(stream));

            ctx.out.step("Os três primeiros golpes: XRANGE do menor id (-) ao maior (+)");
            ctx.out.cmd("XRANGE " + stream + " - + COUNT 3");
            for (StreamEntry entry : jedis.xrange(stream, "-", "+", 3)) {
                ctx.out.info(CombatEvents.describe(entry.getID().toString(), entry.getFields()));
            }

            ctx.out.step("Os três últimos: XREVRANGE anda de trás para frente");
            ctx.out.cmd("XREVRANGE " + stream + " + - COUNT 3");
            for (StreamEntry entry : jedis.xrevrange(stream, "+", "-", 3)) {
                ctx.out.info(CombatEvents.describe(entry.getID().toString(), entry.getFields()));
            }

            ctx.out.step("XREAD lê como um cursor: a partir do id 0-0, cinco por vez");
            ctx.out.cmd("XREAD COUNT 5 STREAMS " + stream + " 0-0");
            List<Map.Entry<String, List<StreamEntry>>> batch =
                    jedis.xread(XReadParams.xReadParams().count(5), Map.of(stream, new StreamEntryID("0-0")));
            StreamEntryID cursor = null;
            if (batch != null) {
                for (Map.Entry<String, List<StreamEntry>> perStream : batch) {
                    for (StreamEntry entry : perStream.getValue()) {
                        ctx.out.info(CombatEvents.describe(entry.getID().toString(), entry.getFields()));
                        cursor = entry.getID();
                    }
                }
            }
            ctx.out.kv("próximo cursor", cursor);
            ctx.out.info("Para continuar, repita o XREAD passando o último id lido. Com BLOCK ele espera golpes novos, segurando a conexão (lição 102-04).");
            ctx.out.info("Vários leitores com XREAD veem todos os eventos. Para dividir o trabalho entre workers, consumer groups (lição 102-03).");

            ctx.done("events", String.valueOf(CombatEvents.COUNT), "first_id", String.valueOf(first),
                    "last_id", String.valueOf(last), "jedis", "ok");
        }
    }
}
