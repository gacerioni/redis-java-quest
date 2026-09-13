package com.emberrealm.quest.lessons.l201_04;

import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Lab;
import com.emberrealm.quest.world.Vectors;
import com.emberrealm.quest.world.World;
import io.lettuce.core.VAddArgs;
import io.lettuce.core.VSimArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

import java.util.Map;

/**
 * 201-04 (Lettuce): the same vector set. Lettuce takes the vector as Double... (Vectors.toDoubles),
 * attributes through VAddArgs, and VSIM by element or by vector through VSimArgs.
 */
public final class LettuceLab implements Lab {

    @Override
    public void run(Ctx ctx) {
        String key = ctx.k("vs", "items");
        Map<String, World.Item> items = Neighbors.byId();
        try (StatefulRedisConnection<String, String> connection = Clients.lettuceConnection()) {
            RedisCommands<String, String> redis = connection.sync();

            ctx.out.step("Um vector set do zero: UNLINK e 42 VADD com atributos JSON");
            ctx.out.cmd("UNLINK " + key);
            redis.unlink(key);
            World.Item sample = items.get(Neighbors.SELF);
            ctx.out.cmd("VADD " + key + " VALUES 384 <v1 ... v384> " + sample.id() + " SETATTR '" + Neighbors.attributes(sample) + "'");
            int added = 0;
            for (World.Item item : items.values()) {
                Boolean isNew = redis.vadd(key, item.id(), new VAddArgs().attributes(Neighbors.attributes(item)),
                        Vectors.toDoubles(item.embedding()));
                if (Boolean.TRUE.equals(isNew)) added++;
            }
            ctx.out.kv("elementos adicionados", added);
            ctx.out.info("O Lettuce manda o vetor como VALUES em texto (Double...); o Jedis aceita float[]. O servidor guarda igual.");
            ctx.out.info("Por padrão o Redis quantiza para int8 (Q8): 4x menos memória que FLOAT32, com perda mínima de precisão.");

            ctx.out.step("Tamanho e dimensão: VCARD e VDIM");
            ctx.out.cmd("VCARD " + key);
            Long card = redis.vcard(key);
            ctx.out.kv("VCARD", card);
            ctx.out.cmd("VDIM " + key);
            Long dim = redis.vdim(key);
            ctx.out.kv("VDIM", dim);

            ctx.out.step("Parecidos com a Espada de Brasa: VSIM ELE " + Neighbors.SELF + " COUNT 6 WITHSCORES");
            ctx.out.cmd("VSIM " + key + " ELE " + Neighbors.SELF + " COUNT 6 WITHSCORES");
            Map<String, Double> similar = redis.vsimWithScore(key, new VSimArgs().count(6L), Neighbors.SELF);
            Neighbors.table(ctx, similar, items);
            String nearest = Neighbors.nearest(similar, Neighbors.SELF);
            ctx.out.kv("vizinho mais próximo", nearest);
            ctx.out.info("ELE usa o vetor que já está no set, sem mandar 384 números. Score é similaridade: 1.0 é idêntico, o próprio item vem primeiro.");

            ctx.out.step("Só épicos: VSIM ... FILTER '" + Neighbors.EPIC_FILTER + "'");
            ctx.out.cmd("VSIM " + key + " ELE " + Neighbors.SELF + " COUNT 5 WITHSCORES FILTER '" + Neighbors.EPIC_FILTER + "'");
            Map<String, Double> epics = redis.vsimWithScore(key,
                    new VSimArgs().count(5L).filter(Neighbors.EPIC_FILTER), Neighbors.SELF);
            Neighbors.table(ctx, epics, items);
            ctx.out.info("FILTER é uma expressão sobre os atributos JSON, avaliada durante a busca: sem segundo índice e sem pós-filtro na aplicação.");

            World.Query question = World.queries().get(0);
            ctx.out.step("Uma pergunta em texto vira vetor: \"" + question.text() + "\"");
            ctx.out.cmd("VSIM " + key + " VALUES 384 <embedding da pergunta> COUNT 5 WITHSCORES");
            Map<String, Double> byText = redis.vsimWithScore(key, new VSimArgs().count(5L),
                    Vectors.toDoubles(question.embedding()));
            Neighbors.table(ctx, byText, items);
            ctx.out.info("Mesmo vetor da lição 201-03. Aqui não existe texto, faixa numérica nem agregação: só vizinhança e o FILTER.");

            ctx.out.step("Os atributos ficam com o elemento: VGETATTR");
            ctx.out.cmd("VGETATTR " + key + " " + Neighbors.SELF);
            ctx.out.kv("VGETATTR", redis.vgetattr(key, Neighbors.SELF));

            ctx.done("vcard", String.valueOf(card),
                    "vdim", String.valueOf(dim),
                    "nearest", nearest,
                    "epic_hits", String.valueOf(epics.size()),
                    "ran_" + ctx.client, "1");
        }
    }
}
