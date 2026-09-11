package com.emberrealm.quest.lessons.l101_05;

import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Lab;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.resps.Tuple;

import java.util.List;

/**
 * 101-05 (Jedis): Sorted Sets. The server leaderboard {p}:rank:xp maps player id to xp and keeps itself ordered.
 * Story: the top 10, vesper's position, a dungeon bonus that moves her score, and a matchmaking bracket by score.
 */
public final class JedisLab implements Lab {

    static final String HERO = "vesper";
    /** vesper's xp in the seed (players.json), re-applied at the start so every run tells the same story. */
    static final double SEED_XP = 900;
    static final double BONUS_XP = 5000;
    static final double BRACKET_MIN = 100_000;
    static final double BRACKET_MAX = 600_000;

    @Override
    public void run(Ctx ctx) {
        String rank = ctx.k("rank", "xp");
        try (RedisClient jedis = Clients.jedis()) {
            if (!jedis.exists(rank)) throw new IllegalStateException("Rode ./quest seed primeiro");

            ctx.out.step("Vesper volta ao XP do seed: a lição conta a mesma história em toda rodada");
            ctx.out.cmd("ZADD " + rank + " 900 vesper");
            jedis.zadd(rank, SEED_XP, HERO);

            ctx.out.step("O placar do servidor: top 10 por XP");
            ctx.out.cmd("ZREVRANGE " + rank + " 0 9 WITHSCORES");
            List<Tuple> top = jedis.zrevrangeWithScores(rank, 0, 9);
            int position = 1;
            for (Tuple t : top) {
                ctx.out.kv(ordinal(position++), t.getElement() + "  " + xp(t.getScore()) + " XP");
            }
            ctx.out.info("O score ordena, o membro é único. O Redis mantém a ordem a cada escrita; a leitura não ordena nada.");
            ctx.out.cmd("ZCARD " + rank);
            ctx.out.kv("jogadores no ranking", jedis.zcard(rank));

            ctx.out.step("Onde está a Vesper?");
            ctx.out.cmd("ZREVRANK " + rank + " vesper");
            Long before = jedis.zrevrank(rank, HERO);
            ctx.out.kv("ZREVRANK", before + " (base zero, do maior para o menor)");
            ctx.out.kv("posição", ordinal(before + 1));
            ctx.out.cmd("ZSCORE " + rank + " vesper");
            ctx.out.kv("XP", xp(jedis.zscore(rank, HERO)));

            ctx.out.step("Vesper fecha a masmorra do Pântano Sombrio: +5000 XP");
            ctx.out.cmd("ZINCRBY " + rank + " 5000 vesper");
            double newXp = jedis.zincrby(rank, BONUS_XP, HERO);
            ctx.out.kv("novo XP", xp(newXp));
            ctx.out.info("ZINCRBY é atômico: mil jogadores ganhando XP ao mesmo tempo e nenhum incremento se perde.");
            ctx.out.cmd("ZREVRANK " + rank + " vesper");
            Long after = jedis.zrevrank(rank, HERO);
            ctx.out.kv("posição agora", ordinal(after + 1)
                    + (after.equals(before) ? " (mesma posição, score bem maior)" : " (subiu " + (before - after) + ")"));
            if (after > 0) {
                ctx.out.cmd("ZREVRANGE " + rank + " " + (after - 1) + " " + (after - 1) + " WITHSCORES");
                Tuple rival = jedis.zrevrangeWithScores(rank, after - 1, after - 1).get(0);
                ctx.out.kv("logo acima", rival.getElement() + "  " + xp(rival.getScore()) + " XP");
                ctx.out.info("Faltam " + xp(rival.getScore() - newXp + 1) + " XP para a Vesper passar " + rival.getElement()
                        + ". O ranking se reordena sozinho a cada ZINCRBY.");
            }

            ctx.out.step("Matchmaking por faixa de XP: quem tem entre 100 mil e 600 mil?");
            ctx.out.cmd("ZCOUNT " + rank + " 100000 600000");
            long inBracket = jedis.zcount(rank, BRACKET_MIN, BRACKET_MAX);
            ctx.out.kv("jogadores na faixa", inBracket);
            ctx.out.cmd("ZRANGEBYSCORE " + rank + " 100000 600000 WITHSCORES");
            List<Tuple> bracket = jedis.zrangeByScoreWithScores(rank, BRACKET_MIN, BRACKET_MAX);
            for (Tuple t : bracket) ctx.out.kv(t.getElement(), xp(t.getScore()) + " XP");
            ctx.out.info("Por score, não por posição: um grupo de nível parecido sai com uma consulta só, sem varrer o ranking inteiro.");

            ctx.done("vesper_xp", xp(newXp), "vesper_rank", String.valueOf(after + 1),
                    "top1", top.get(0).getElement(), "ran_" + ctx.client, "1");
        }
    }

    /** Scores are doubles on the wire; xp is a whole number, so print it without the ".0". */
    static String xp(double score) {
        return String.valueOf((long) score);
    }

    static String ordinal(long position) {
        return position + "º";
    }
}
