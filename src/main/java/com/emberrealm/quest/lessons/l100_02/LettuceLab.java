package com.emberrealm.quest.lessons.l100_02;

import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Env;
import com.emberrealm.quest.core.Lab;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;

import java.util.concurrent.ExecutionException;

/**
 * 100-02 (Lettuce): one RedisClient per app, connect(), then pick sync, async or reactive.
 * The same connection serves all three APIs.
 */
public final class LettuceLab implements Lab {

    @Override
    public void run(Ctx ctx) throws ExecutionException, InterruptedException {
        ctx.out.step("RedisClient.create(uri) uma vez por aplicação; connect() por conexão");
        ctx.out.info("RedisClient client = RedisClient.create(RedisURI.create(\"" + Env.redacted(Env.redisUrl()) + "\"));");
        try (StatefulRedisConnection<String, String> connection = Clients.lettuceConnection()) {
            RedisCommands<String, String> sync = connection.sync();
            String motd = ctx.k("world", "motd");
            ctx.out.cmd("SET " + motd + " \"Bem-vindo ao Ember Realm, aventureiro.\"");
            ctx.out.kv("SET (sync)", sync.set(motd, "Bem-vindo ao Ember Realm, aventureiro."));

            ctx.out.cmd("GET " + motd);
            ctx.out.kv("GET (sync)", sync.get(motd));

            ctx.out.step("A mesma conexão, agora assíncrona: o GET vira um future");
            RedisAsyncCommands<String, String> async = connection.async();
            ctx.out.kv("GET (async).get()", async.get(motd).get());

            String hello = ctx.k("hello", "lettuce");
            ctx.out.cmd("SET " + hello + " ok");
            sync.set(hello, "ok");

            ctx.out.step("Fechar a conexão; o client fica vivo até o shutdown da aplicação");
            ctx.done("motd", "1");
        }
    }
}
