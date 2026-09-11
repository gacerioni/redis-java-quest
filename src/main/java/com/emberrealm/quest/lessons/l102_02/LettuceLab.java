package com.emberrealm.quest.lessons.l102_02;

import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Lab;
import io.lettuce.core.Limit;
import io.lettuce.core.Range;
import io.lettuce.core.StreamMessage;
import io.lettuce.core.XAddArgs;
import io.lettuce.core.XReadArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

import java.util.List;
import java.util.Map;

/** 102-02 (Lettuce): same combat log. Ranges are Range objects, COUNT is a Limit, offsets are StreamOffset. */
public final class LettuceLab implements Lab {

    @Override
    public void run(Ctx ctx) {
        String stream = ctx.k("events", "combat");
        try (StatefulRedisConnection<String, String> connection = Clients.lettuceConnection()) {
            RedisCommands<String, String> redis = connection.sync();

            ctx.out.step("Diário limpo e 20 golpes registrados com XADD");
            ctx.out.cmd("UNLINK " + stream);
            redis.unlink(stream);
            XAddArgs args = new XAddArgs().maxlen(1000).approximateTrimming();
            String first = null;
            String last = null;
            for (Map<String, String> event : CombatEvents.events()) {
                last = redis.xadd(stream, args, event);
                if (first == null) first = last;
            }
            ctx.out.cmd("XADD " + stream + " MAXLEN ~ 1000 * attacker kaelith target lobo-de-cinzas damage 40 zone pico-gelido");
            ctx.out.kv("primeiro id", first);
            ctx.out.kv("último id", last);
            ctx.out.info("O id é <milissegundos>-<sequência>: o servidor gera, ordena e nunca repete. Dois golpes no mesmo ms viram -0 e -1.");
            ctx.out.info("MAXLEN ~ 1000 poda os mais antigos quando dá (o ~ deixa o Redis podar em blocos, muito mais barato).");
            ctx.out.cmd("XLEN " + stream);
            ctx.out.kv("XLEN", redis.xlen(stream));

            ctx.out.step("Os três primeiros golpes: XRANGE do menor id (-) ao maior (+)");
            ctx.out.cmd("XRANGE " + stream + " - + COUNT 3");
            for (StreamMessage<String, String> message : redis.xrange(stream, Range.unbounded(), Limit.from(3))) {
                ctx.out.info(CombatEvents.describe(message.getId(), message.getBody()));
            }

            ctx.out.step("Os três últimos: XREVRANGE anda de trás para frente");
            ctx.out.cmd("XREVRANGE " + stream + " + - COUNT 3");
            for (StreamMessage<String, String> message : redis.xrevrange(stream, Range.unbounded(), Limit.from(3))) {
                ctx.out.info(CombatEvents.describe(message.getId(), message.getBody()));
            }

            ctx.out.step("XREAD lê como um cursor: a partir do id 0-0, cinco por vez");
            ctx.out.cmd("XREAD COUNT 5 STREAMS " + stream + " 0-0");
            List<StreamMessage<String, String>> batch =
                    redis.xread(XReadArgs.Builder.count(5), XReadArgs.StreamOffset.from(stream, "0-0"));
            String cursor = null;
            for (StreamMessage<String, String> message : batch) {
                ctx.out.info(CombatEvents.describe(message.getId(), message.getBody()));
                cursor = message.getId();
            }
            ctx.out.kv("próximo cursor", cursor);
            ctx.out.info("Para continuar, repita o XREAD passando o último id lido. Com BLOCK ele espera golpes novos, segurando a conexão (lição 102-04).");
            ctx.out.info("Vários leitores com XREAD veem todos os eventos. Para dividir o trabalho entre workers, consumer groups (lição 102-03).");

            ctx.done("events", String.valueOf(CombatEvents.COUNT), "first_id", String.valueOf(first),
                    "last_id", String.valueOf(last), "lettuce", "ok");
        }
    }
}
