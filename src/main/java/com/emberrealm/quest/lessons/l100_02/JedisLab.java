package com.emberrealm.quest.lessons.l100_02;

import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Env;
import com.emberrealm.quest.core.Lab;
import redis.clients.jedis.RedisClient;

/**
 * 100-02 (Jedis): connect from a URL, run SET and GET, close.
 * RedisClient is the pooled entry point. Jedis 8 negotiates RESP3 with RESP2 fallback.
 */
public final class JedisLab implements Lab {

    @Override
    public void run(Ctx ctx) {
        ctx.out.step("RedisClient.create(url): a URL carrega host, porta, usuário e senha");
        ctx.out.info("RedisClient jedis = RedisClient.create(\"" + Env.redacted(Env.redisUrl()) + "\");");
        try (RedisClient jedis = Clients.jedis()) {
            String motd = ctx.k("world", "motd");
            ctx.out.cmd("SET " + motd + " \"Bem-vindo ao Ember Realm, aventureiro.\"");
            String reply = jedis.set(motd, "Bem-vindo ao Ember Realm, aventureiro.");
            ctx.out.kv("SET", reply);

            ctx.out.cmd("GET " + motd);
            ctx.out.kv("GET", jedis.get(motd));

            String hello = ctx.k("hello", "jedis");
            ctx.out.cmd("SET " + hello + " ok");
            jedis.set(hello, "ok");

            ctx.out.step("Fechar o client devolve as conexões (try-with-resources faz isso por você)");
            ctx.done("motd", "1");
        }
    }
}
