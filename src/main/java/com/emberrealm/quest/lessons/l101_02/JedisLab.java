package com.emberrealm.quest.lessons.l101_02;

import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Lab;
import com.emberrealm.quest.world.World;
import redis.clients.jedis.RedisClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 101-02 (Jedis): the character sheet is a Hash: one key, many fields, each field addressable on its own.
 * Reads (HGETALL, HGET, HMGET), an atomic HINCRBY for the quest reward, HSET with several fields,
 * HEXISTS and HLEN, then field level expiration (HEXPIRE, Redis 7.4+) for temporary buffs.
 */
public final class JedisLab implements Lab {

    static final String PLAYER = "kaelith";
    static final long QUEST_REWARD = 250;
    static final long BUFF_SECONDS = 30;

    @Override
    public void run(Ctx ctx) {
        String sheet = ctx.k("player", PLAYER);
        String buffs = ctx.k("player", PLAYER, "buffs");
        int seedGold = seedGold();

        try (RedisClient jedis = Clients.jedis()) {
            ctx.out.step("Preparando: a ficha volta ao ouro do seed e os campos do lab saem");
            if (!jedis.exists(sheet)) throw new IllegalStateException("Rode ./quest seed primeiro");
            ctx.out.cmd("HSET " + sheet + " gold " + seedGold);
            jedis.hset(sheet, "gold", String.valueOf(seedGold));
            ctx.out.cmd("HDEL " + sheet + " title mount");
            jedis.hdel(sheet, "title", "mount");
            ctx.out.cmd("UNLINK " + buffs);
            jedis.unlink(buffs);

            ctx.out.step("HGETALL: a ficha inteira de Kaelith");
            ctx.out.cmd("HGETALL " + sheet);
            Map<String, String> all = jedis.hgetAll(sheet);
            new TreeMap<>(all).forEach(ctx.out::kv);
            ctx.out.info("Um hash guarda campos e valores numa chave só: sem JSON para desserializar, sem uma chave por atributo.");

            ctx.out.step("HGET e HMGET: só os campos que a tela precisa");
            ctx.out.cmd("HGET " + sheet + " hp");
            ctx.out.kv("hp", jedis.hget(sheet, "hp"));
            ctx.out.cmd("HMGET " + sheet + " name class level");
            List<String> few = jedis.hmget(sheet, "name", "class", "level");
            ctx.out.kv("name, class, level", few);

            ctx.out.step("HINCRBY: recompensa da missão, +" + QUEST_REWARD + " de ouro, sem ler antes");
            ctx.out.cmd("HINCRBY " + sheet + " gold " + QUEST_REWARD);
            long gold = jedis.hincrBy(sheet, "gold", QUEST_REWARD);
            ctx.out.kv("gold", gold);
            ctx.out.info("A soma acontece no servidor, atômica: dois servidores de jogo podem premiar ao mesmo tempo sem perder ouro.");

            ctx.out.step("HSET com vários campos: título e montaria novos");
            Map<String, String> extras = new LinkedHashMap<>();
            extras.put("title", "Guardiã das Brasas");
            extras.put("mount", "grifo-cinzento");
            ctx.out.cmd("HSET " + sheet + " title \"Guardiã das Brasas\" mount grifo-cinzento");
            ctx.out.kv("campos novos", jedis.hset(sheet, extras));
            ctx.out.info("HSET devolve quantos campos foram criados; atualizar um campo que já existe conta zero.");

            ctx.out.step("HEXISTS e HLEN");
            ctx.out.cmd("HEXISTS " + sheet + " mount");
            ctx.out.kv("tem montaria?", jedis.hexists(sheet, "mount"));
            ctx.out.cmd("HEXISTS " + sheet + " wings");
            ctx.out.kv("tem asas?", jedis.hexists(sheet, "wings"));
            ctx.out.cmd("HLEN " + sheet);
            long fields = jedis.hlen(sheet);
            ctx.out.kv("campos na ficha", fields);

            ctx.out.step("Buffs com prazo por campo: HEXPIRE (Redis 7.4+)");
            Map<String, String> active = new LinkedHashMap<>();
            active.put("haste", "1");
            active.put("shield", "1");
            ctx.out.cmd("HSET " + buffs + " haste 1 shield 1");
            jedis.hset(buffs, active);
            String hexpire = "ok";
            ctx.out.cmd("HEXPIRE " + buffs + " " + BUFF_SECONDS + " FIELDS 1 haste");
            try {
                List<Long> applied = jedis.hexpire(buffs, BUFF_SECONDS, "haste");
                ctx.out.kv("HEXPIRE", applied + " (1 = prazo aplicado ao campo)");
                ctx.out.cmd("HTTL " + buffs + " FIELDS 1 haste");
                ctx.out.kv("HTTL haste", jedis.httl(buffs, "haste") + " s");
                ctx.out.info("Só haste expira; shield fica. Antes do Redis 7.4 o TTL era da chave inteira, e um buff temporário pedia uma chave separada.");
            } catch (Exception e) {
                hexpire = "unsupported";
                ctx.out.warn("Este servidor não aceita HEXPIRE: " + e.getMessage());
                ctx.out.hint("Expiração por campo pede Redis 7.4 ou mais novo. No Redis Cloud, crie o banco na versão 7.4+; no Docker, use a imagem redis:8.");
            }

            ctx.done("gold", String.valueOf(gold),
                    "fields", String.valueOf(fields),
                    "hexpire", hexpire,
                    "ran_" + ctx.client, "1");
        }
    }

    /** Kaelith's gold in the seed data, so every run starts from the same value. */
    static int seedGold() {
        return World.players().stream()
                .filter(p -> p.id().equals(PLAYER))
                .findFirst()
                .map(World.Player::gold)
                .orElseThrow(() -> new IllegalStateException("player " + PLAYER + " is missing from world data"));
    }
}
