package com.emberrealm.quest.lessons.l100_05;

import com.emberrealm.quest.core.Check;
import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Verdict;
import com.emberrealm.quest.world.World;
import redis.clients.jedis.RedisClient;

import java.util.Map;

/** 100 loot keys on the floor, gold conserved between brom and nix, and the marker. */
public final class LessonCheck implements Check {

    @Override
    public void run(Ctx ctx, Verdict v) {
        String[] lootKeys = JedisLab.lootKeys(ctx);
        World.Player brom = JedisLab.player("brom");
        World.Player nix = JedisLab.player("nix");
        long expectedSum = (long) brom.gold() + nix.gold();
        try (RedisClient jedis = Clients.jedis()) {
            long found = jedis.exists(lootKeys);
            v.expect(found == JedisLab.DROPS,
                    "os " + JedisLab.DROPS + " drops estão no chão: " + lootKeys[0] + " ... " + lootKeys[JedisLab.DROPS - 1],
                    "rode: ./quest run 100-05 jedis (ou lettuce); encontrei " + found + " de " + JedisLab.DROPS);

            String bromGold = jedis.hget(ctx.k("player", "brom"), "gold");
            String nixGold = jedis.hget(ctx.k("player", "nix"), "gold");
            Long b = parse(bromGold);
            Long n = parse(nixGold);
            if (b == null || n == null) {
                v.fail("as fichas de brom e nix não têm ouro numérico (brom=" + bromGold + ", nix=" + nixGold + ")",
                        "rode: ./quest seed e depois ./quest run 100-05 jedis (ou lettuce)");
            } else {
                v.expect(b + n == expectedSum,
                        "brom (" + b + ") + nix (" + n + ") = " + expectedSum + " de ouro: a transferência não criou nem perdeu moeda",
                        "rode a lição de novo: ela re-deriva o ouro do seed antes do MULTI/EXEC");
                if (b == brom.gold() - JedisLab.GOLD && n == nix.gold() + JedisLab.GOLD) {
                    v.pass("os " + JedisLab.GOLD + " de ouro saíram do Brom e chegaram na Nix dentro de um único EXEC");
                }
            }

            Map<String, String> marker = jedis.hgetAll(ctx.progressKey());
            v.expect(!marker.isEmpty(), "a lição rodou e gravou o marcador", "rode: ./quest run 100-05 jedis (ou lettuce)");
            if (!marker.isEmpty()) {
                v.pass("um por vez: " + marker.get("one_by_one_ms") + " ms; pipeline: " + marker.get("pipeline_ms")
                        + " ms (" + marker.get("speedup") + "x mais rápido)");
                if ("jedis".equals(marker.get("client"))) v.info("Opcional: experimente com o Lettuce: ./quest run 100-05 lettuce");
            }
        }
    }

    private static Long parse(String value) {
        if (value == null) return null;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
