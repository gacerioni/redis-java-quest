package com.emberrealm.quest.lessons.l101_02;

import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Lab;
import io.lettuce.core.KeyValue;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** 101-02 (Lettuce): the same character sheet; Lettuce keeps the Redis command names in lower case. */
public final class LettuceLab implements Lab {

    @Override
    public void run(Ctx ctx) {
        String sheet = ctx.k("player", JedisLab.PLAYER);
        String buffs = ctx.k("player", JedisLab.PLAYER, "buffs");
        int seedGold = JedisLab.seedGold();

        try (StatefulRedisConnection<String, String> connection = Clients.lettuceConnection()) {
            RedisCommands<String, String> redis = connection.sync();

            ctx.out.step("Preparando: a ficha volta ao ouro do seed e os campos do lab saem");
            if (redis.exists(sheet) == 0) throw new IllegalStateException("Rode ./quest seed primeiro");
            ctx.out.cmd("HSET " + sheet + " gold " + seedGold);
            redis.hset(sheet, "gold", String.valueOf(seedGold));
            ctx.out.cmd("HDEL " + sheet + " title mount");
            redis.hdel(sheet, "title", "mount");
            ctx.out.cmd("UNLINK " + buffs);
            redis.unlink(buffs);

            ctx.out.step("HGETALL: a ficha inteira de Kaelith");
            ctx.out.cmd("HGETALL " + sheet);
            Map<String, String> all = redis.hgetall(sheet);
            new TreeMap<>(all).forEach(ctx.out::kv);
            ctx.out.info("Um hash guarda campos e valores numa chave só: sem JSON para desserializar, sem uma chave por atributo.");

            ctx.out.step("HGET e HMGET: só os campos que a tela precisa");
            ctx.out.cmd("HGET " + sheet + " hp");
            ctx.out.kv("hp", redis.hget(sheet, "hp"));
            ctx.out.cmd("HMGET " + sheet + " name class level");
            List<KeyValue<String, String>> few = redis.hmget(sheet, "name", "class", "level");
            for (KeyValue<String, String> field : few) {
                ctx.out.kv(field.getKey(), field.getValueOrElse("(nil)"));
            }

            ctx.out.step("HINCRBY: recompensa da missão, +" + JedisLab.QUEST_REWARD + " de ouro, sem ler antes");
            ctx.out.cmd("HINCRBY " + sheet + " gold " + JedisLab.QUEST_REWARD);
            long gold = redis.hincrby(sheet, "gold", JedisLab.QUEST_REWARD);
            ctx.out.kv("gold", gold);
            ctx.out.info("A soma acontece no servidor, atômica: dois servidores de jogo podem premiar ao mesmo tempo sem perder ouro.");

            ctx.out.step("HSET com vários campos: título e montaria novos");
            Map<String, String> extras = new LinkedHashMap<>();
            extras.put("title", "Guardiã das Brasas");
            extras.put("mount", "grifo-cinzento");
            ctx.out.cmd("HSET " + sheet + " title \"Guardiã das Brasas\" mount grifo-cinzento");
            ctx.out.kv("campos novos", redis.hset(sheet, extras));
            ctx.out.info("HSET devolve quantos campos foram criados; atualizar um campo que já existe conta zero.");

            ctx.out.step("HEXISTS e HLEN");
            ctx.out.cmd("HEXISTS " + sheet + " mount");
            ctx.out.kv("tem montaria?", redis.hexists(sheet, "mount"));
            ctx.out.cmd("HEXISTS " + sheet + " wings");
            ctx.out.kv("tem asas?", redis.hexists(sheet, "wings"));
            ctx.out.cmd("HLEN " + sheet);
            long fields = redis.hlen(sheet);
            ctx.out.kv("campos na ficha", fields);

            ctx.out.step("Buffs com prazo por campo: HEXPIRE (Redis 7.4+)");
            Map<String, String> active = new LinkedHashMap<>();
            active.put("haste", "1");
            active.put("shield", "1");
            ctx.out.cmd("HSET " + buffs + " haste 1 shield 1");
            redis.hset(buffs, active);
            String hexpire = "ok";
            ctx.out.cmd("HEXPIRE " + buffs + " " + JedisLab.BUFF_SECONDS + " FIELDS 1 haste");
            try {
                List<Long> applied = redis.hexpire(buffs, JedisLab.BUFF_SECONDS, "haste");
                ctx.out.kv("HEXPIRE", applied + " (1 = prazo aplicado ao campo)");
                ctx.out.cmd("HTTL " + buffs + " FIELDS 1 haste");
                ctx.out.kv("HTTL haste", redis.httl(buffs, "haste") + " s");
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
}
