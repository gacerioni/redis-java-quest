package com.emberrealm.quest.lessons.l101_04;

import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Lab;
import com.emberrealm.quest.world.World;
import redis.clients.jedis.RedisClient;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 101-04 (Jedis): Sets. An achievement is a unique badge with no order; a loot table is a bag you draw from at random.
 * Story: marisol unlocks her first achievement, brom and thane compare trophies, the chest of Vila de Brasa drops loot.
 */
public final class JedisLab implements Lab {

    static final String NEW_ACHIEVEMENT = "primeiro-sangue";

    /** Six low-level item ids from the seeded world: the loot table of the starter chest. */
    static final List<String> LOOT_TABLE = List.of(
            "pocao-de-vida-menor", "pocao-de-mana", "anel-de-cobre",
            "espada-curta-de-ferro", "botas-do-viajante", "arco-de-teixo");

    @Override
    public void run(Ctx ctx) {
        String marisol = ctx.k("player", "marisol", "achievements");
        String brom = ctx.k("player", "brom", "achievements");
        String thane = ctx.k("player", "thane", "achievements");
        String loot = ctx.k("loot", "table");
        Map<String, String> names = itemNames();

        try (RedisClient jedis = Clients.jedis()) {
            if (jedis.exists(ctx.k("player", "marisol"), brom, thane) < 3) {
                throw new IllegalStateException("Rode ./quest seed primeiro");
            }

            ctx.out.step("Do zero: marisol sem a conquista nova e o baú vazio (a lição pode rodar mil vezes)");
            ctx.out.cmd("SREM " + marisol + " " + NEW_ACHIEVEMENT);
            jedis.srem(marisol, NEW_ACHIEVEMENT);
            ctx.out.cmd("UNLINK " + loot);
            jedis.unlink(loot);

            ctx.out.step("Marisol derrota o primeiro lobo da Floresta de Cinzas: conquista desbloqueada");
            ctx.out.cmd("SADD " + marisol + " " + NEW_ACHIEVEMENT);
            long added = jedis.sadd(marisol, NEW_ACHIEVEMENT);
            ctx.out.kv("SADD", added + " membro novo");
            ctx.out.cmd("SADD " + marisol + " " + NEW_ACHIEVEMENT);
            long again = jedis.sadd(marisol, NEW_ACHIEVEMENT);
            ctx.out.kv("SADD de novo", again + " membro novo (já estava lá)");
            ctx.out.info("Um Set guarda cada valor uma vez só. Repetir o SADD é seguro: nada duplica, nada quebra.");

            ctx.out.cmd("SISMEMBER " + marisol + " " + NEW_ACHIEVEMENT);
            ctx.out.kv("tem primeiro-sangue?", jedis.sismember(marisol, NEW_ACHIEVEMENT));
            ctx.out.cmd("SMISMEMBER " + marisol + " " + NEW_ACHIEVEMENT + " explorador");
            ctx.out.kv("tem primeiro-sangue e explorador?", jedis.smismember(marisol, NEW_ACHIEVEMENT, "explorador"));
            ctx.out.cmd("SCARD " + marisol);
            ctx.out.kv("conquistas da marisol", jedis.scard(marisol));

            ctx.out.step("Brom e Thane comparam troféus na taverna");
            ctx.out.cmd("SINTER " + brom + " " + thane);
            Set<String> common = jedis.sinter(brom, thane);
            ctx.out.kv("em comum", common);
            ctx.out.cmd("SDIFF " + thane + " " + brom);
            Set<String> onlyThane = jedis.sdiff(thane, brom);
            ctx.out.kv("só o Thane tem", onlyThane);
            ctx.out.cmd("SUNION " + brom + " " + thane);
            Set<String> union = jedis.sunion(brom, thane);
            ctx.out.kv("os dois juntos", union.size() + " conquistas: " + union);
            ctx.out.info("Interseção, diferença e união rodam no servidor: nenhum membro viaja até a JVM só para ser comparado.");

            ctx.out.step("O baú de Vila de Brasa: uma tabela de loot com " + LOOT_TABLE.size() + " itens");
            ctx.out.cmd("SADD " + loot + " " + String.join(" ", LOOT_TABLE));
            jedis.sadd(loot, LOOT_TABLE.toArray(new String[0]));
            ctx.out.cmd("SCARD " + loot);
            ctx.out.kv("itens na tabela", jedis.scard(loot));

            ctx.out.cmd("SRANDMEMBER " + loot);
            String drop = jedis.srandmember(loot);
            ctx.out.kv("marisol abre o baú e encontra", names.getOrDefault(drop, drop));
            ctx.out.cmd("SRANDMEMBER " + loot + " 3");
            List<String> distinct = jedis.srandmember(loot, 3);
            ctx.out.kv("baú raro, 3 itens distintos", pretty(distinct, names));
            ctx.out.cmd("SRANDMEMBER " + loot + " -3");
            List<String> rolls = jedis.srandmember(loot, -3);
            ctx.out.kv("3 rolagens independentes (podem repetir)", pretty(rolls, names));
            ctx.out.info("SRANDMEMBER só olha: a tabela continua com " + jedis.scard(loot) + " itens. SPOP sortearia e removeria.");

            ctx.done("achievement", NEW_ACHIEVEMENT, "common", String.valueOf(common.size()),
                    "loot_size", String.valueOf(LOOT_TABLE.size()), "ran_" + ctx.client, "1");
        }
    }

    /** id to display name for the seeded items, so the console prints "Poção de Mana" instead of pocao-de-mana. */
    static Map<String, String> itemNames() {
        return World.items().stream().collect(Collectors.toMap(World.Item::id, World.Item::name));
    }

    static List<String> pretty(List<String> ids, Map<String, String> names) {
        return ids.stream().map(id -> names.getOrDefault(id, id)).toList();
    }
}
