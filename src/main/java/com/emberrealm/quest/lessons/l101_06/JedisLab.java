package com.emberrealm.quest.lessons.l101_06;

import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Lab;
import redis.clients.jedis.RedisClient;

import java.util.List;
import java.util.Map;

/**
 * 101-06 (Jedis): Bloom filter. "Has kaelith opened this chest already?" answered with a few KB and no false negatives.
 * Story: reserve a filter for 1000 chests at 1% error, add the chests she opened, probe 1000 chests nobody opened
 * and count the false positives, then compare the memory against a plain SET holding the same ids.
 */
public final class JedisLab implements Lab {

    static final double ERROR_RATE = 0.01;
    static final long CAPACITY = 1000;
    /** Chests kaelith opened in her whole career: chest-001 .. chest-1000. */
    static final int OPENED = 1000;
    /** chest-5001 .. chest-6000 were never opened by anyone: every "yes" for them is a false positive. */
    static final int NEVER_FROM = 5001;
    static final int PROBES = 1000;
    /** Ids per BF.MADD / BF.MEXISTS round trip: a few KB per command, a handful of commands in total. */
    static final int BATCH = 250;

    @Override
    public void run(Ctx ctx) throws Exception {
        String chests = ctx.k("chests", "opened");
        String plainSet = ctx.k("chests", "opened", "plain");
        try (RedisClient jedis = Clients.jedis()) {
            ctx.out.step("Um filtro novo para os baús que a Kaelith já abriu");
            ctx.out.cmd("UNLINK " + chests + " " + plainSet);
            jedis.unlink(chests, plainSet);
            ctx.out.cmd("BF.RESERVE " + chests + " 0.01 1000");
            try {
                ctx.out.kv("BF.RESERVE", jedis.bfReserve(chests, ERROR_RATE, CAPACITY));
            } catch (Exception e) {
                if (!unknownCommand(e)) throw e;
                ctx.out.warn("Este servidor não tem o módulo Bloom: " + e.getMessage());
                ctx.out.hint("Redis 8 (docker compose up -d) e o Redis Cloud já vêm com Bloom. Rode ./quest doctor para ver a versão.");
                ctx.done("bloom", "unsupported", "ran_" + ctx.client, "1");
                return;
            }
            ctx.out.info("Pedimos 1% de erro e espaço para 1000 baús. O Redis calcula o número de bits e de funções de hash por você.");

            ctx.out.step("Kaelith abre os primeiros baús das Catacumbas Rubras");
            ctx.out.cmd("BF.ADD " + chests + " chest-001");
            ctx.out.kv("chest-001", jedis.bfAdd(chests, "chest-001") + " (entrou agora)");
            ctx.out.cmd("BF.ADD " + chests + " chest-001");
            ctx.out.kv("chest-001 de novo", jedis.bfAdd(chests, "chest-001") + " (os bits já estavam acesos)");
            ctx.out.cmd("BF.MADD " + chests + " chest-002 chest-003 chest-004");
            ctx.out.kv("BF.MADD", jedis.bfMAdd(chests, "chest-002", "chest-003", "chest-004"));
            ctx.out.info("O filtro não guarda o id: cada item acende alguns bits. Por isso ele é tão pequeno, e por isso não dá para listar o que entrou.");

            ctx.out.step("Esse baú já foi aberto?");
            ctx.out.cmd("BF.EXISTS " + chests + " chest-001");
            ctx.out.kv("chest-001", answer(jedis.bfExists(chests, "chest-001")));
            ctx.out.cmd("BF.EXISTS " + chests + " chest-777");
            ctx.out.kv("chest-777", answer(jedis.bfExists(chests, "chest-777")));
            ctx.out.info("'Não' é certeza absoluta (zero falsos negativos). 'Sim' quer dizer 'provavelmente': até 1 em 100 pode ser falso positivo.");

            ctx.out.step("A carreira inteira: " + OPENED + " baús em " + (OPENED / BATCH) + " lotes de " + BATCH);
            int marked = 0;
            for (int from = 1; from <= OPENED; from += BATCH) {
                int to = Math.min(from + BATCH - 1, OPENED);
                ctx.out.cmd("BF.MADD " + chests + " " + chestId(from) + " ... " + chestId(to));
                marked += countTrue(jedis.bfMAdd(chests, chestIds(from, to)));
            }
            ctx.out.kv("baús marcados como novos", marked + " de " + OPENED);
            ctx.out.info("Menos que " + OPENED + "? Os 4 primeiros já estavam lá, e um ou outro pode ter colidido com bits já acesos: o mesmo mecanismo do falso positivo.");

            ctx.out.step("O teste da verdade: " + PROBES + " baús que ninguém abriu");
            int falsePositives = 0;
            for (int from = NEVER_FROM; from < NEVER_FROM + PROBES; from += BATCH) {
                int to = from + BATCH - 1;
                ctx.out.cmd("BF.MEXISTS " + chests + " " + chestId(from) + " ... " + chestId(to));
                falsePositives += countTrue(jedis.bfMExists(chests, chestIds(from, to)));
            }
            ctx.out.kv("falsos positivos", falsePositives + " de " + PROBES + String.format(" (%.1f%%)", 100.0 * falsePositives / PROBES));
            ctx.out.info(verdict(falsePositives));

            ctx.out.step("Quanto isso custa? BF.CARD, BF.INFO e MEMORY USAGE");
            try {
                ctx.out.cmd("BF.CARD " + chests);
                ctx.out.kv("itens (estimativa)", jedis.bfCard(chests));
                ctx.out.cmd("BF.INFO " + chests);
                Map<String, Object> info = jedis.bfInfo(chests);
                info.forEach(ctx.out::kv);
            } catch (Exception e) {
                ctx.out.warn("BF.CARD/BF.INFO não disponíveis aqui: " + e.getMessage());
                ctx.out.hint("BF.CARD chegou no RedisBloom 2.4.4; a lição segue normalmente sem ele.");
            }
            String bloomBytesText = "?";
            try {
                ctx.out.cmd("MEMORY USAGE " + chests);
                Long bloomBytes = jedis.memoryUsage(chests);
                ctx.out.cmd("SADD " + plainSet + " chest-001 ... " + chestId(OPENED) + "  (os mesmos ids, num SET comum, em 4 lotes)");
                for (int from = 1; from <= OPENED; from += BATCH) {
                    jedis.sadd(plainSet, chestIds(from, Math.min(from + BATCH - 1, OPENED)));
                }
                ctx.out.cmd("MEMORY USAGE " + plainSet);
                Long setBytes = jedis.memoryUsage(plainSet);
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

    static String chestId(int n) {
        return String.format("chest-%03d", n);
    }

    static String[] chestIds(int from, int to) {
        String[] ids = new String[to - from + 1];
        for (int i = 0; i < ids.length; i++) ids[i] = chestId(from + i);
        return ids;
    }

    static int countTrue(List<Boolean> flags) {
        int n = 0;
        for (Boolean f : flags) if (Boolean.TRUE.equals(f)) n++;
        return n;
    }

    static String answer(boolean exists) {
        return exists ? "provavelmente sim" : "com certeza não";
    }

    static String verdict(int falsePositives) {
        if (falsePositives <= PROBES * ERROR_RATE) {
            return "Dentro do 1% prometido (o Redis dimensiona com folga para o filtro poder crescer). E nenhum baú que a Kaelith abriu de verdade recebeu 'não'.";
        }
        return "Um pouco acima de 1%: a taxa de erro é estatística, não um teto rígido. E nenhum baú que a Kaelith abriu de verdade recebeu 'não'.";
    }

    static boolean unknownCommand(Exception e) {
        String message = e.getMessage();
        return message != null && message.toLowerCase().contains("unknown command");
    }
}
