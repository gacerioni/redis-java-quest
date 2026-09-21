package com.emberrealm.quest.lessons.l101_06;

import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Lab;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.probabilistic.BfInfoValue;

import static com.emberrealm.quest.lessons.l101_06.JedisLab.BATCH;
import static com.emberrealm.quest.lessons.l101_06.JedisLab.CAPACITY;
import static com.emberrealm.quest.lessons.l101_06.JedisLab.ERROR_RATE;
import static com.emberrealm.quest.lessons.l101_06.JedisLab.NEVER_FROM;
import static com.emberrealm.quest.lessons.l101_06.JedisLab.OPENED;
import static com.emberrealm.quest.lessons.l101_06.JedisLab.PROBES;
import static com.emberrealm.quest.lessons.l101_06.JedisLab.answer;
import static com.emberrealm.quest.lessons.l101_06.JedisLab.chestId;
import static com.emberrealm.quest.lessons.l101_06.JedisLab.chestIds;
import static com.emberrealm.quest.lessons.l101_06.JedisLab.countTrue;
import static com.emberrealm.quest.lessons.l101_06.JedisLab.unknownCommand;
import static com.emberrealm.quest.lessons.l101_06.JedisLab.verdict;

/**
 * 101-06 (Lettuce): same filter, same chests. Lettuce 7.7 ships the BF.* commands on the regular sync API
 * (RedisBloomFilterCommands) and parses BF.INFO into a typed BfInfoValue.
 */
public final class LettuceLab implements Lab {

    @Override
    public void run(Ctx ctx) throws Exception {
        String chests = ctx.k("chests", "opened");
        String plainSet = ctx.k("chests", "opened", "plain");
        try (StatefulRedisConnection<String, String> connection = Clients.lettuceConnection()) {
            RedisCommands<String, String> redis = connection.sync();

            ctx.out.step("Um filtro novo para os baús que a Kaelith já abriu");
            ctx.out.cmd("UNLINK " + chests + " " + plainSet);
            redis.unlink(chests, plainSet);
            ctx.out.cmd("BF.RESERVE " + chests + " 0.01 1000");
            try {
                ctx.out.kv("BF.RESERVE", redis.bfReserve(chests, ERROR_RATE, CAPACITY));
            } catch (Exception e) {
                if (!unknownCommand(e)) throw e;
                ctx.out.warn("Este servidor não tem o módulo Bloom: " + e.getMessage());
                ctx.out.hint("Redis 8 (docker compose up -d) e o Redis Cloud já vêm com Bloom. Rode ./quest doctor para ver a versão.");
                ctx.unavailable("bloom", "unsupported", "ran_" + ctx.client, "1");
                return;
            }
            ctx.out.info("Pedimos 1% de erro e espaço para 1000 baús. O Redis calcula o número de bits e de funções de hash por você.");

            ctx.out.step("Kaelith abre os primeiros baús das Catacumbas Rubras");
            ctx.out.cmd("BF.ADD " + chests + " chest-001");
            ctx.out.kv("chest-001", redis.bfAdd(chests, "chest-001") + " (entrou agora)");
            ctx.out.cmd("BF.ADD " + chests + " chest-001");
            ctx.out.kv("chest-001 de novo", redis.bfAdd(chests, "chest-001") + " (os bits já estavam acesos)");
            ctx.out.cmd("BF.MADD " + chests + " chest-002 chest-003 chest-004");
            ctx.out.kv("BF.MADD", redis.bfMAdd(chests, "chest-002", "chest-003", "chest-004"));
            ctx.out.info("O filtro não guarda o id: cada item acende alguns bits. Por isso ele é tão pequeno, e por isso não dá para listar o que entrou.");

            ctx.out.step("Esse baú já foi aberto?");
            ctx.out.cmd("BF.EXISTS " + chests + " chest-001");
            ctx.out.kv("chest-001", answer(redis.bfExists(chests, "chest-001")));
            ctx.out.cmd("BF.EXISTS " + chests + " chest-777");
            ctx.out.kv("chest-777", answer(redis.bfExists(chests, "chest-777")));
            ctx.out.info("'Não' é certeza absoluta (zero falsos negativos). 'Sim' quer dizer 'provavelmente': até 1 em 100 pode ser falso positivo.");

            ctx.out.step("A carreira inteira: " + OPENED + " baús em " + (OPENED / BATCH) + " lotes de " + BATCH);
            int marked = 0;
            for (int from = 1; from <= OPENED; from += BATCH) {
                int to = Math.min(from + BATCH - 1, OPENED);
                ctx.out.cmd("BF.MADD " + chests + " " + chestId(from) + " ... " + chestId(to));
                marked += countTrue(redis.bfMAdd(chests, chestIds(from, to)));
            }
            ctx.out.kv("baús marcados como novos", marked + " de " + OPENED);
            ctx.out.info("Menos que " + OPENED + "? Os 4 primeiros já estavam lá, e um ou outro pode ter colidido com bits já acesos: o mesmo mecanismo do falso positivo.");

            ctx.out.step("O teste da verdade: " + PROBES + " baús que ninguém abriu");
            int falsePositives = 0;
            for (int from = NEVER_FROM; from < NEVER_FROM + PROBES; from += BATCH) {
                int to = from + BATCH - 1;
                ctx.out.cmd("BF.MEXISTS " + chests + " " + chestId(from) + " ... " + chestId(to));
                falsePositives += countTrue(redis.bfMExists(chests, chestIds(from, to)));
            }
            ctx.out.kv("falsos positivos", falsePositives + " de " + PROBES + String.format(" (%.1f%%)", 100.0 * falsePositives / PROBES));
            ctx.out.info(verdict(falsePositives));

            ctx.out.step("Quanto isso custa? BF.CARD, BF.INFO e MEMORY USAGE");
            try {
                ctx.out.cmd("BF.CARD " + chests);
                ctx.out.kv("itens (estimativa)", redis.bfCard(chests));
                ctx.out.cmd("BF.INFO " + chests);
                BfInfoValue info = redis.bfInfo(chests);
                ctx.out.kv("Capacity", info.getCapacity());
                ctx.out.kv("Size", info.getSize());
                ctx.out.kv("Number of filters", info.getNumberOfFilters());
                ctx.out.kv("Number of items inserted", info.getNumberOfItemsInserted());
                ctx.out.kv("Expansion rate", info.getExpansionRate());
            } catch (Exception e) {
                ctx.out.warn("BF.CARD/BF.INFO não disponíveis aqui: " + e.getMessage());
                ctx.out.hint("BF.CARD chegou no RedisBloom 2.4.4; a lição segue normalmente sem ele.");
            }
            String bloomBytesText = "?";
            try {
                ctx.out.cmd("MEMORY USAGE " + chests);
                Long bloomBytes = redis.memoryUsage(chests);
                ctx.out.cmd("SADD " + plainSet + " chest-001 ... " + chestId(OPENED) + "  (os mesmos ids, num SET comum, em 4 lotes)");
                for (int from = 1; from <= OPENED; from += BATCH) {
                    redis.sadd(plainSet, chestIds(from, Math.min(from + BATCH - 1, OPENED)));
                }
                ctx.out.cmd("MEMORY USAGE " + plainSet);
                Long setBytes = redis.memoryUsage(plainSet);
                ctx.out.kv("Bloom filter", bloomBytes + " bytes");
                ctx.out.kv("SET com os mesmos ids", setBytes + " bytes");
                if (bloomBytes != null && setBytes != null && bloomBytes > 0) {
                    ctx.out.info("O SET responde com certeza e gasta " + (setBytes / bloomBytes) + "x mais memória. O filtro troca essa certeza por 1% de 'talvez'.");
                    bloomBytesText = String.valueOf(bloomBytes);
                }
            } catch (Exception e) {
                ctx.out.warn("MEMORY USAGE não permitido para este usuário: " + e.getMessage());
                ctx.out.hint("Sem problema: um filtro de 1000 itens a 1% ocupa perto de 1,2 KB; um SET com os mesmos ids, dezenas de KB.");
            }

            ctx.done("bloom", "ok", "false_positives", String.valueOf(falsePositives),
                    "bloom_bytes", bloomBytesText, "ran_" + ctx.client, "1");
        }
    }
}
