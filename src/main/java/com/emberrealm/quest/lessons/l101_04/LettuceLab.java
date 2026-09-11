package com.emberrealm.quest.lessons.l101_04;

import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Lab;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.emberrealm.quest.lessons.l101_04.JedisLab.LOOT_TABLE;
import static com.emberrealm.quest.lessons.l101_04.JedisLab.NEW_ACHIEVEMENT;

/**
 * 101-04 (Lettuce): same story, same keys. The sync API returns boxed types (Long, Boolean) and
 * java.util.Set for the set algebra, so the code reads almost like the Jedis version.
 */
public final class LettuceLab implements Lab {

    @Override
    public void run(Ctx ctx) {
        String marisol = ctx.k("player", "marisol", "achievements");
        String brom = ctx.k("player", "brom", "achievements");
        String thane = ctx.k("player", "thane", "achievements");
        String loot = ctx.k("loot", "table");
        Map<String, String> names = JedisLab.itemNames();

        try (StatefulRedisConnection<String, String> connection = Clients.lettuceConnection()) {
            RedisCommands<String, String> redis = connection.sync();
            if (redis.exists(ctx.k("player", "marisol"), brom, thane) < 3) {
                throw new IllegalStateException("Rode ./quest seed primeiro");
            }

            ctx.out.step("Do zero: marisol sem a conquista nova e o baú vazio (a lição pode rodar mil vezes)");
            ctx.out.cmd("SREM " + marisol + " " + NEW_ACHIEVEMENT);
            redis.srem(marisol, NEW_ACHIEVEMENT);
            ctx.out.cmd("UNLINK " + loot);
            redis.unlink(loot);

            ctx.out.step("Marisol derrota o primeiro lobo da Floresta de Cinzas: conquista desbloqueada");
            ctx.out.cmd("SADD " + marisol + " " + NEW_ACHIEVEMENT);
            Long added = redis.sadd(marisol, NEW_ACHIEVEMENT);
            ctx.out.kv("SADD", added + " membro novo");
            ctx.out.cmd("SADD " + marisol + " " + NEW_ACHIEVEMENT);
            Long again = redis.sadd(marisol, NEW_ACHIEVEMENT);
            ctx.out.kv("SADD de novo", again + " membro novo (já estava lá)");
            ctx.out.info("Um Set guarda cada valor uma vez só. Repetir o SADD é seguro: nada duplica, nada quebra.");

            ctx.out.cmd("SISMEMBER " + marisol + " " + NEW_ACHIEVEMENT);
            ctx.out.kv("tem primeiro-sangue?", redis.sismember(marisol, NEW_ACHIEVEMENT));
            ctx.out.cmd("SMISMEMBER " + marisol + " " + NEW_ACHIEVEMENT + " explorador");
            ctx.out.kv("tem primeiro-sangue e explorador?", redis.smismember(marisol, NEW_ACHIEVEMENT, "explorador"));
            ctx.out.cmd("SCARD " + marisol);
            ctx.out.kv("conquistas da marisol", redis.scard(marisol));

            ctx.out.step("Brom e Thane comparam troféus na taverna");
            ctx.out.cmd("SINTER " + brom + " " + thane);
            Set<String> common = redis.sinter(brom, thane);
            ctx.out.kv("em comum", common);
            ctx.out.cmd("SDIFF " + thane + " " + brom);
            Set<String> onlyThane = redis.sdiff(thane, brom);
            ctx.out.kv("só o Thane tem", onlyThane);
            ctx.out.cmd("SUNION " + brom + " " + thane);
            Set<String> union = redis.sunion(brom, thane);
            ctx.out.kv("os dois juntos", union.size() + " conquistas: " + union);
            ctx.out.info("Interseção, diferença e união rodam no servidor: nenhum membro viaja até a JVM só para ser comparado.");

            ctx.out.step("O baú de Vila de Brasa: uma tabela de loot com " + LOOT_TABLE.size() + " itens");
            ctx.out.cmd("SADD " + loot + " " + String.join(" ", LOOT_TABLE));
            redis.sadd(loot, LOOT_TABLE.toArray(new String[0]));
            ctx.out.cmd("SCARD " + loot);
            ctx.out.kv("itens na tabela", redis.scard(loot));

            ctx.out.cmd("SRANDMEMBER " + loot);
            String drop = redis.srandmember(loot);
            ctx.out.kv("marisol abre o baú e encontra", names.getOrDefault(drop, drop));
            ctx.out.cmd("SRANDMEMBER " + loot + " 3");
            List<String> distinct = redis.srandmember(loot, 3);
            ctx.out.kv("baú raro, 3 itens distintos", JedisLab.pretty(distinct, names));
            ctx.out.cmd("SRANDMEMBER " + loot + " -3");
            List<String> rolls = redis.srandmember(loot, -3);
            ctx.out.kv("3 rolagens independentes (podem repetir)", JedisLab.pretty(rolls, names));
            ctx.out.info("SRANDMEMBER só olha: a tabela continua com " + redis.scard(loot) + " itens. SPOP sortearia e removeria.");

            ctx.done("achievement", NEW_ACHIEVEMENT, "common", String.valueOf(common.size()),
                    "loot_size", String.valueOf(LOOT_TABLE.size()), "ran_" + ctx.client, "1");
        }
    }
}
