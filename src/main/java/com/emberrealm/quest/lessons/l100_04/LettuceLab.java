package com.emberrealm.quest.lessons.l100_04;

import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Lab;
import io.lettuce.core.KeyScanCursor;
import io.lettuce.core.ScanArgs;
import io.lettuce.core.ScanCursor;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 100-04 (Lettuce): same session story. Lettuce answers with Boolean for EXPIRE/PERSIST and Long for EXISTS,
 * and SCAN hands back a KeyScanCursor you feed into the next call.
 */
public final class LettuceLab implements Lab {

    @Override
    public void run(Ctx ctx) {
        String session = ctx.k("session", JedisLab.LOGIN_TOKEN);
        String rotated = ctx.k("session", JedisLab.ROTATED_TOKEN);
        try (StatefulRedisConnection<String, String> connection = Clients.lettuceConnection()) {
            RedisCommands<String, String> redis = connection.sync();

            ctx.out.step("Convenção de nomes: app:entidade:id, minúsculas, separado por dois-pontos");
            ctx.out.kv("prefixo", ctx.keys.prefix());
            ctx.out.kv("chave da sessão", session);
            ctx.out.info("O prefixo isola o seu mundo, 'session' diz o que é, o token diz qual. O Browser do Insight agrupa por esses segmentos.");

            ctx.out.step("Limpando sessões de execuções anteriores");
            ctx.out.cmd("UNLINK " + session + " " + rotated);
            redis.unlink(session, rotated);

            ctx.out.step("Login da Kaelith: SET com EX cria a chave já com prazo de validade");
            ctx.out.cmd("SET " + session + " kaelith EX " + JedisLab.SESSION_SECONDS);
            ctx.out.kv("SET", redis.set(session, "kaelith", SetArgs.Builder.ex(JedisLab.SESSION_SECONDS)));
            ctx.out.cmd("TTL " + session);
            ctx.out.kv("TTL", redis.ttl(session) + " s (30 minutos)");
            ctx.out.cmd("EXISTS " + session);
            ctx.out.kv("EXISTS", redis.exists(session));
            ctx.out.cmd("TYPE " + session);
            ctx.out.kv("TYPE", redis.type(session));

            ctx.out.step("TTL tem dois valores especiais");
            ctx.out.cmd("TTL " + ctx.k("player", "kaelith"));
            ctx.out.kv("TTL da ficha", JedisLab.describeTtl(redis.ttl(ctx.k("player", "kaelith"))));
            ctx.out.cmd("TTL " + ctx.k("session", "inexistente"));
            ctx.out.kv("TTL da chave de exemplo", JedisLab.describeTtl(redis.ttl(ctx.k("session", "inexistente"))));

            ctx.out.step("Kaelith continua jogando: EXPIRE renova o prazo (sliding expiration)");
            ctx.out.cmd("EXPIRE " + session + " 3600");
            ctx.out.kv("EXPIRE", redis.expire(session, 3600));
            ctx.out.cmd("TTL " + session);
            ctx.out.kv("TTL", redis.ttl(session) + " s");

            ctx.out.step("PERSIST remove o prazo: a sessão vira permanente");
            ctx.out.cmd("PERSIST " + session);
            ctx.out.kv("PERSIST", redis.persist(session));
            ctx.out.cmd("TTL " + session);
            ctx.out.kv("TTL", redis.ttl(session));
            ctx.out.warn("Sessão sem TTL nunca sai da memória. Em produção, toda chave de sessão ou cache nasce com prazo.");
            ctx.out.cmd("EXPIRE " + session + " " + JedisLab.SESSION_SECONDS);
            redis.expire(session, JedisLab.SESSION_SECONDS);
            ctx.out.kv("TTL de volta", redis.ttl(session) + " s");

            ctx.out.step("Rotação de token: RENAME troca o nome e preserva valor e TTL");
            ctx.out.cmd("RENAME " + session + " " + rotated);
            ctx.out.kv("RENAME", redis.rename(session, rotated));
            ctx.out.cmd("TTL " + rotated);
            long ttl = redis.ttl(rotated);
            ctx.out.kv("TTL", ttl + " s");
            ctx.out.cmd("EXISTS " + session);
            ctx.out.kv("EXISTS (token antigo)", redis.exists(session));

            ctx.out.step("SCAN pelo prefixo: cursor, MATCH e COUNT em vez de KEYS");
            String pattern = ctx.keys.pattern();
            ctx.out.cmd("SCAN 0 MATCH " + pattern + " COUNT " + JedisLab.SCAN_COUNT);
            Set<String> keys = new TreeSet<>();
            ScanArgs args = ScanArgs.Builder.matches(pattern).limit(JedisLab.SCAN_COUNT);
            ScanCursor cursor = ScanCursor.INITIAL;
            int pages = 0;
            do {
                KeyScanCursor<String> page = redis.scan(cursor, args);
                pages++;
                if (pages <= JedisLab.PAGES_TO_PRINT) {
                    ctx.out.info("página " + pages + ": " + page.getKeys().size() + " chaves, próximo cursor " + page.getCursor());
                }
                keys.addAll(page.getKeys());
                cursor = page;
            } while (!cursor.isFinished());
            if (pages > JedisLab.PAGES_TO_PRINT) ctx.out.info("... mais " + (pages - JedisLab.PAGES_TO_PRINT) + " página(s) até o cursor voltar a 0");
            Map<String, Integer> byType = new TreeMap<>();
            for (String key : keys) byType.merge(JedisLab.label(redis.type(key)), 1, Integer::sum);
            ctx.out.kv("chaves do prefixo", keys.size() + " em " + pages + " página(s)");
            byType.forEach((type, n) -> ctx.out.info(String.format("%-12s %5d", type, n)));
            ctx.out.info("COUNT é uma dica, não uma promessa: cada página pode trazer mais ou menos chaves. Cursor 0 de volta encerra.");
            ctx.out.info("KEYS " + pattern + " devolveria tudo de uma vez, mas trava o servidor inteiro enquanto varre. Com milhões de chaves, é incidente.");

            ctx.out.cmd("SCAN 0 MATCH " + ctx.k("session", "*") + " COUNT " + JedisLab.SCAN_COUNT);
            ctx.out.kv("sessões ativas", scanAll(redis, ctx.k("session", "*")));

            ctx.done("session", rotated,
                    "ttl", String.valueOf(ttl),
                    "keys", String.valueOf(keys.size()),
                    "scan_pages", String.valueOf(pages));
        }
    }

    /** Full SCAN loop for a pattern; every page, until the cursor reports finished. */
    static Set<String> scanAll(RedisCommands<String, String> redis, String pattern) {
        Set<String> found = new TreeSet<>();
        ScanArgs args = ScanArgs.Builder.matches(pattern).limit(JedisLab.SCAN_COUNT);
        ScanCursor cursor = ScanCursor.INITIAL;
        do {
            KeyScanCursor<String> page = redis.scan(cursor, args);
            found.addAll(page.getKeys());
            cursor = page;
        } while (!cursor.isFinished());
        return found;
    }
}
