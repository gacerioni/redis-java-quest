package com.emberrealm.quest.lessons.l101_05;

import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Lab;
import io.lettuce.core.Range;
import io.lettuce.core.ScoredValue;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

import java.util.List;

import static com.emberrealm.quest.lessons.l101_05.JedisLab.BONUS_XP;
import static com.emberrealm.quest.lessons.l101_05.JedisLab.BRACKET_MAX;
import static com.emberrealm.quest.lessons.l101_05.JedisLab.BRACKET_MIN;
import static com.emberrealm.quest.lessons.l101_05.JedisLab.HERO;
import static com.emberrealm.quest.lessons.l101_05.JedisLab.SEED_XP;
import static com.emberrealm.quest.lessons.l101_05.JedisLab.ordinal;
import static com.emberrealm.quest.lessons.l101_05.JedisLab.xp;

/**
 * 101-05 (Lettuce): same leaderboard. Lettuce returns ScoredValue (value + score) and takes Range objects
 * for score intervals, which keeps the "by score" queries readable.
 */
public final class LettuceLab implements Lab {

    @Override
    public void run(Ctx ctx) {
        String rank = ctx.k("rank", "xp");
        try (StatefulRedisConnection<String, String> connection = Clients.lettuceConnection()) {
            RedisCommands<String, String> redis = connection.sync();
            if (redis.exists(rank) == 0) throw new IllegalStateException("Rode ./quest seed primeiro");

            ctx.out.step("Vesper volta ao XP do seed: a lição conta a mesma história em toda rodada");
            ctx.out.cmd("ZADD " + rank + " 900 vesper");
            redis.zadd(rank, SEED_XP, HERO);

            ctx.out.step("O placar do servidor: top 10 por XP");
            ctx.out.cmd("ZREVRANGE " + rank + " 0 9 WITHSCORES");
            List<ScoredValue<String>> top = redis.zrevrangeWithScores(rank, 0, 9);
            int position = 1;
            for (ScoredValue<String> sv : top) {
                ctx.out.kv(ordinal(position++), sv.getValue() + "  " + xp(sv.getScore()) + " XP");
            }
            ctx.out.info("O score ordena, o membro é único. O Redis mantém a ordem a cada escrita; a leitura não ordena nada.");
            ctx.out.cmd("ZCARD " + rank);
            ctx.out.kv("jogadores no ranking", redis.zcard(rank));

            ctx.out.step("Onde está a Vesper?");
            ctx.out.cmd("ZREVRANK " + rank + " vesper");
            Long before = redis.zrevrank(rank, HERO);
            ctx.out.kv("ZREVRANK", before + " (base zero, do maior para o menor)");
            ctx.out.kv("posição", ordinal(before + 1));
            ctx.out.cmd("ZSCORE " + rank + " vesper");
            ctx.out.kv("XP", xp(redis.zscore(rank, HERO)));

            ctx.out.step("Vesper fecha a masmorra do Pântano Sombrio: +5000 XP");
            ctx.out.cmd("ZINCRBY " + rank + " 5000 vesper");
            Double newXp = redis.zincrby(rank, BONUS_XP, HERO);
            ctx.out.kv("novo XP", xp(newXp));
            ctx.out.info("ZINCRBY é atômico: mil jogadores ganhando XP ao mesmo tempo e nenhum incremento se perde.");
            ctx.out.cmd("ZREVRANK " + rank + " vesper");
            Long after = redis.zrevrank(rank, HERO);
            ctx.out.kv("posição agora", ordinal(after + 1)
                    + (after.equals(before) ? " (mesma posição, score bem maior)" : " (subiu " + (before - after) + ")"));
            if (after > 0) {
                ctx.out.cmd("ZREVRANGE " + rank + " " + (after - 1) + " " + (after - 1) + " WITHSCORES");
                ScoredValue<String> rival = redis.zrevrangeWithScores(rank, after - 1, after - 1).get(0);
                ctx.out.kv("logo acima", rival.getValue() + "  " + xp(rival.getScore()) + " XP");
                ctx.out.info("Faltam " + xp(rival.getScore() - newXp + 1) + " XP para a Vesper passar " + rival.getValue()
                        + ". O ranking se reordena sozinho a cada ZINCRBY.");
            }

            ctx.out.step("Matchmaking por faixa de XP: quem tem entre 100 mil e 600 mil?");
            Range<Double> bracketRange = Range.create(BRACKET_MIN, BRACKET_MAX);
            ctx.out.cmd("ZCOUNT " + rank + " 100000 600000");
            Long inBracket = redis.zcount(rank, bracketRange);
            ctx.out.kv("jogadores na faixa", inBracket);
            ctx.out.cmd("ZRANGEBYSCORE " + rank + " 100000 600000 WITHSCORES");
            List<ScoredValue<String>> bracket = redis.zrangebyscoreWithScores(rank, bracketRange);
            for (ScoredValue<String> sv : bracket) ctx.out.kv(sv.getValue(), xp(sv.getScore()) + " XP");
            ctx.out.info("Por score, não por posição: um grupo de nível parecido sai com uma consulta só, sem varrer o ranking inteiro.");

            ctx.done("vesper_xp", xp(newXp), "vesper_rank", String.valueOf(after + 1),
                    "top1", top.get(0).getValue(), "ran_" + ctx.client, "1");
        }
    }
}
