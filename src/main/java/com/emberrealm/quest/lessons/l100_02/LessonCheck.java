package com.emberrealm.quest.lessons.l100_02;

import com.emberrealm.quest.core.Check;
import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Verdict;
import redis.clients.jedis.RedisClient;

public final class LessonCheck implements Check {

    @Override
    public void run(Ctx ctx, Verdict v) {
        try (RedisClient jedis = Clients.jedis()) {
            String motd = jedis.get(ctx.k("world", "motd"));
            v.expect(motd != null && motd.startsWith("Bem-vindo ao Ember Realm"),
                    "a mensagem do dia está gravada em " + ctx.k("world", "motd"),
                    "rode: ./quest run 100-02 jedis");
            boolean jedisRan = jedis.exists(ctx.k("hello", "jedis"));
            boolean lettuceRan = jedis.exists(ctx.k("hello", "lettuce"));
            v.expect(jedisRan || lettuceRan, "pelo menos um client conectou e escreveu", "rode a lição com jedis ou lettuce");
            if (jedisRan && !lettuceRan) v.info("Opcional: experimente com o Lettuce: ./quest run 100-02 lettuce");
            if (lettuceRan && !jedisRan) v.info("Opcional: experimente com o Jedis: ./quest run 100-02 jedis");
            if (jedisRan && lettuceRan) v.pass("os dois clients conectaram, mesma URL, mesmo resultado");
        }
    }
}
