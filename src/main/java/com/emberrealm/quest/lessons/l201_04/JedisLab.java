package com.emberrealm.quest.lessons.l201_04;

import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Lab;
import com.emberrealm.quest.world.World;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.params.VAddParams;
import redis.clients.jedis.params.VSimParams;

import java.util.Map;

/**
 * 201-04 (Jedis): a vector set with the 42 items. VADD with JSON attributes, VCARD, VDIM, then VSIM by
 * element, VSIM with a FILTER over the attributes, and VSIM with the vector of a text question.
 * One data structure, one key, no index to create.
 */
public final class JedisLab implements Lab {

    @Override
    public void run(Ctx ctx) {
        String key = ctx.k("vs", "items");
        Map<String, World.Item> items = Neighbors.byId();
        try (RedisClient jedis = Clients.jedis()) {
            ctx.out.step("Um vector set do zero: UNLINK e 42 VADD com atributos JSON");
            ctx.out.cmd("UNLINK " + key);
            jedis.unlink(key);
            World.Item sample = items.get(Neighbors.SELF);
            ctx.out.cmd("VADD " + key + " VALUES 384 <v1 ... v384> " + sample.id() + " SETATTR '" + Neighbors.attributes(sample) + "'");
            int added = 0;
            for (World.Item item : items.values()) {
                boolean isNew = jedis.vadd(key, item.embedding(), item.id(), new VAddParams().setAttr(Neighbors.attributes(item)));
                if (isNew) added++;
            }
            ctx.out.kv("elementos adicionados", added);
            ctx.out.info("Cada elemento leva o vetor e um JSON de atributos; sem VADD anterior a chave nasce no primeiro comando.");
            ctx.out.info("Por padrão o Redis quantiza para int8 (Q8): 4x menos memória que FLOAT32, com perda mínima de precisão.");

            ctx.out.step("Tamanho e dimensão: VCARD e VDIM");
            ctx.out.cmd("VCARD " + key);
            long card = jedis.vcard(key);
            ctx.out.kv("VCARD", card);
            ctx.out.cmd("VDIM " + key);
            long dim = jedis.vdim(key);
            ctx.out.kv("VDIM", dim);

            ctx.out.step("Parecidos com a Espada de Brasa: VSIM ELE " + Neighbors.SELF + " COUNT 6 WITHSCORES");
            ctx.out.cmd("VSIM " + key + " ELE " + Neighbors.SELF + " COUNT 6 WITHSCORES");
            Map<String, Double> similar = jedis.vsimByElementWithScores(key, Neighbors.SELF, new VSimParams().count(6));
            Neighbors.table(ctx, similar, items);
            String nearest = Neighbors.nearest(similar, Neighbors.SELF);
            ctx.out.kv("vizinho mais próximo", nearest);
            ctx.out.info("ELE usa o vetor que já está no set, sem mandar 384 números. Score é similaridade: 1.0 é idêntico, o próprio item vem primeiro.");

            ctx.out.step("Só épicos: VSIM ... FILTER '" + Neighbors.EPIC_FILTER + "'");
            ctx.out.cmd("VSIM " + key + " ELE " + Neighbors.SELF + " COUNT 5 WITHSCORES FILTER '" + Neighbors.EPIC_FILTER + "'");
            Map<String, Double> epics = jedis.vsimByElementWithScores(key, Neighbors.SELF,
                    new VSimParams().count(5).filter(Neighbors.EPIC_FILTER));
            Neighbors.table(ctx, epics, items);
            ctx.out.info("FILTER é uma expressão sobre os atributos JSON, avaliada durante a busca: sem segundo índice e sem pós-filtro na aplicação.");

            World.Query question = World.queries().get(0);
            ctx.out.step("Uma pergunta em texto vira vetor: \"" + question.text() + "\"");
            ctx.out.cmd("VSIM " + key + " VALUES 384 <embedding da pergunta> COUNT 5 WITHSCORES");
            Map<String, Double> byText = jedis.vsimWithScores(key, question.embedding(), new VSimParams().count(5));
            Neighbors.table(ctx, byText, items);
            ctx.out.info("Mesmo vetor da lição 201-03. Aqui não existe texto, faixa numérica nem agregação: só vizinhança e o FILTER.");

            ctx.out.step("Os atributos ficam com o elemento: VGETATTR");
            ctx.out.cmd("VGETATTR " + key + " " + Neighbors.SELF);
            ctx.out.kv("VGETATTR", jedis.vgetattr(key, Neighbors.SELF));

            ctx.done("vcard", String.valueOf(card),
                    "vdim", String.valueOf(dim),
                    "nearest", nearest,
                    "epic_hits", String.valueOf(epics.size()),
                    "ran_" + ctx.client, "1");
        }
    }
}
