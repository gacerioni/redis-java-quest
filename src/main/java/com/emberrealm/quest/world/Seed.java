package com.emberrealm.quest.world;

import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.json.Path;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads the Ember Realm world into Redis under the student's prefix. Idempotent: run it as often as you like.
 *
 *   {p}:item:{id}                JSON document (used by 201-xx)
 *   {p}:player:{id}              HASH character sheet
 *   {p}:player:{id}:achievements SET
 *   {p}:players                  SET of player ids
 *   {p}:rank:xp                  SORTED SET player -> xp
 *   {p}:zone:{id}                HASH
 *   {p}:zones                    SET of zone ids
 *   {p}:zone:geo                 GEO index of zones
 */
public final class Seed {

    public void run(Ctx ctx) throws Exception {
        ctx.out.h1("Semeando o mundo Ember Realm em " + ctx.keys.prefix() + ":*");
        List<Map<String, Object>> items = World.itemDocuments();
        List<World.Player> players = World.players();
        List<World.Zone> zones = World.zones();

        try (RedisClient jedis = Clients.jedis()) {
            ctx.out.step(items.size() + " itens como documentos JSON");
            for (Map<String, Object> item : items) {
                String key = ctx.k("item", String.valueOf(item.get("id")));
                jedis.jsonSetWithPlainString(key, Path.ROOT_PATH, World.JSON.writeValueAsString(item));
            }
            ctx.out.cmd("JSON.SET " + ctx.k("item", "<id>") + " $ '{...}'");

            ctx.out.step(players.size() + " personagens como hashes, conquistas como sets, ranking como sorted set");
            jedis.del(ctx.k("players"), ctx.k("rank", "xp"));
            for (World.Player p : players) {
                Map<String, String> sheet = new HashMap<>();
                sheet.put("name", p.name());
                sheet.put("class", p.playerClass());
                sheet.put("level", String.valueOf(p.level()));
                sheet.put("hp", String.valueOf(p.hp()));
                sheet.put("mana", String.valueOf(p.mana()));
                sheet.put("gold", String.valueOf(p.gold()));
                sheet.put("xp", String.valueOf(p.xp()));
                sheet.put("zone", p.zone());
                sheet.put("guild", p.guild());
                jedis.hset(ctx.k("player", p.id()), sheet);
                jedis.del(ctx.k("player", p.id(), "achievements"));
                if (!p.achievements().isEmpty()) {
                    jedis.sadd(ctx.k("player", p.id(), "achievements"), p.achievements().toArray(new String[0]));
                }
                jedis.sadd(ctx.k("players"), p.id());
                jedis.zadd(ctx.k("rank", "xp"), p.xp(), p.id());
            }
            ctx.out.cmd("HSET " + ctx.k("player", "<id>") + " name ... level ... gold ...");
            ctx.out.cmd("ZADD " + ctx.k("rank", "xp") + " <xp> <id>");

            ctx.out.step(zones.size() + " zonas como hashes e um índice GEO");
            jedis.del(ctx.k("zones"), ctx.k("zone", "geo"));
            for (World.Zone z : zones) {
                Map<String, String> fields = new HashMap<>();
                fields.put("name", z.name());
                fields.put("levelMin", String.valueOf(z.levelMin()));
                fields.put("levelMax", String.valueOf(z.levelMax()));
                fields.put("danger", z.danger());
                jedis.hset(ctx.k("zone", z.id()), fields);
                jedis.sadd(ctx.k("zones"), z.id());
                jedis.geoadd(ctx.k("zone", "geo"), z.lon(), z.lat(), z.id());
            }
            jedis.set(ctx.k("world", "seeded_at"), Instant.now().toString());
        }
        ctx.out.blank();
        ctx.out.ok("Mundo pronto. Abra o Redis Insight e navegue por " + ctx.keys.prefix() + ":*");
    }
}
