package com.emberrealm.quest.lessons.l100_04;

import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Lab;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.params.SetParams;
import redis.clients.jedis.resps.ScanResult;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 100-04 (Jedis): Kaelith logs in. Her session is a STRING that expires on its own (SET ... EX),
 * gets renewed (EXPIRE), can lose its deadline (PERSIST) and can change name keeping the TTL (RENAME).
 * Then SCAN walks the whole prefix, page by page, without ever calling KEYS.
 */
public final class JedisLab implements Lab {

    /** Fixed tokens keep the lesson idempotent and let the check find the key; real apps use SecureRandom. */
    static final String LOGIN_TOKEN = "7f3a9c";
    static final String ROTATED_TOKEN = "b81d22";
    static final int SESSION_SECONDS = 1800;
    static final int SCAN_COUNT = 100;
    static final int PAGES_TO_PRINT = 3;

    @Override
    public void run(Ctx ctx) {
        String session = ctx.k("session", LOGIN_TOKEN);
        String rotated = ctx.k("session", ROTATED_TOKEN);
        try (RedisClient jedis = Clients.jedis()) {
            ctx.out.step("Convenção de nomes: app:entidade:id, minúsculas, separado por dois-pontos");
            ctx.out.kv("prefixo", ctx.keys.prefix());
            ctx.out.kv("chave da sessão", session);
            ctx.out.info("O prefixo isola o seu mundo, 'session' diz o que é, o token diz qual. O Browser do Insight agrupa por esses segmentos.");

            ctx.out.step("Limpando sessões de execuções anteriores");
            ctx.out.cmd("UNLINK " + session + " " + rotated);
            jedis.unlink(session, rotated);

            ctx.out.step("Login da Kaelith: SET com EX cria a chave já com prazo de validade");
            ctx.out.cmd("SET " + session + " kaelith EX " + SESSION_SECONDS);
            ctx.out.kv("SET", jedis.set(session, "kaelith", SetParams.setParams().ex(SESSION_SECONDS)));
            ctx.out.cmd("TTL " + session);
            ctx.out.kv("TTL", jedis.ttl(session) + " s (30 minutos)");
            ctx.out.cmd("EXISTS " + session);
            ctx.out.kv("EXISTS", jedis.exists(session));
            ctx.out.cmd("TYPE " + session);
            ctx.out.kv("TYPE", jedis.type(session));

            ctx.out.step("TTL tem dois valores especiais");
            ctx.out.cmd("TTL " + ctx.k("player", "kaelith"));
            ctx.out.kv("TTL da ficha", jedis.ttl(ctx.k("player", "kaelith")) + " (-1: a chave existe e nunca expira)");
            ctx.out.cmd("TTL " + ctx.k("session", "inexistente"));
            ctx.out.kv("TTL de chave inexistente", jedis.ttl(ctx.k("session", "inexistente")) + " (-2: não existe)");

            ctx.out.step("Kaelith continua jogando: EXPIRE renova o prazo (sliding expiration)");
            ctx.out.cmd("EXPIRE " + session + " 3600");
            ctx.out.kv("EXPIRE", jedis.expire(session, 3600));
            ctx.out.cmd("TTL " + session);
            ctx.out.kv("TTL", jedis.ttl(session) + " s");

            ctx.out.step("PERSIST remove o prazo: a sessão vira permanente");
            ctx.out.cmd("PERSIST " + session);
            ctx.out.kv("PERSIST", jedis.persist(session));
            ctx.out.cmd("TTL " + session);
            ctx.out.kv("TTL", jedis.ttl(session));
            ctx.out.warn("Sessão sem TTL nunca sai da memória. Em produção, toda chave de sessão ou cache nasce com prazo.");
            ctx.out.cmd("EXPIRE " + session + " " + SESSION_SECONDS);
            jedis.expire(session, SESSION_SECONDS);
            ctx.out.kv("TTL de volta", jedis.ttl(session) + " s");

            ctx.out.step("Rotação de token: RENAME troca o nome e preserva valor e TTL");
            ctx.out.cmd("RENAME " + session + " " + rotated);
            ctx.out.kv("RENAME", jedis.rename(session, rotated));
            ctx.out.cmd("TTL " + rotated);
            long ttl = jedis.ttl(rotated);
            ctx.out.kv("TTL", ttl + " s");
            ctx.out.cmd("EXISTS " + session);
            ctx.out.kv("EXISTS (token antigo)", jedis.exists(session));

            ctx.out.step("SCAN pelo prefixo: cursor, MATCH e COUNT em vez de KEYS");
            String pattern = ctx.keys.pattern();
            ctx.out.cmd("SCAN 0 MATCH " + pattern + " COUNT " + SCAN_COUNT);
            Set<String> keys = new TreeSet<>();
            ScanParams params = new ScanParams().match(pattern).count(SCAN_COUNT);
            String cursor = ScanParams.SCAN_POINTER_START;
            int pages = 0;
            do {
                ScanResult<String> page = jedis.scan(cursor, params);
                pages++;
                if (pages <= PAGES_TO_PRINT) {
                    ctx.out.info("página " + pages + ": " + page.getResult().size() + " chaves, próximo cursor " + page.getCursor());
                }
                keys.addAll(page.getResult());
                cursor = page.getCursor();
            } while (!cursor.equals(ScanParams.SCAN_POINTER_START));
            if (pages > PAGES_TO_PRINT) ctx.out.info("... mais " + (pages - PAGES_TO_PRINT) + " página(s) até o cursor voltar a 0");
            Map<String, Integer> byType = new TreeMap<>();
            for (String key : keys) byType.merge(label(jedis.type(key)), 1, Integer::sum);
            ctx.out.kv("chaves do prefixo", keys.size() + " em " + pages + " página(s)");
            byType.forEach((type, n) -> ctx.out.info(String.format("%-12s %5d", type, n)));
            ctx.out.info("COUNT é uma dica, não uma promessa: cada página pode trazer mais ou menos chaves. Cursor 0 de volta encerra.");
            ctx.out.info("KEYS " + pattern + " devolveria tudo de uma vez, mas trava o servidor inteiro enquanto varre. Com milhões de chaves, é incidente.");

            ctx.out.cmd("SCAN 0 MATCH " + ctx.k("session", "*") + " COUNT " + SCAN_COUNT);
            ctx.out.kv("sessões ativas", scanAll(jedis, ctx.k("session", "*")));

            ctx.done("session", rotated,
                    "ttl", String.valueOf(ttl),
                    "keys", String.valueOf(keys.size()),
                    "scan_pages", String.valueOf(pages));
        }
    }

    /** Full SCAN loop for a pattern; every page, until the cursor comes back to 0. */
    static Set<String> scanAll(RedisClient jedis, String pattern) {
        Set<String> found = new TreeSet<>();
        ScanParams params = new ScanParams().match(pattern).count(SCAN_COUNT);
        String cursor = ScanParams.SCAN_POINTER_START;
        do {
            ScanResult<String> page = jedis.scan(cursor, params);
            found.addAll(page.getResult());
            cursor = page.getCursor();
        } while (!cursor.equals(ScanParams.SCAN_POINTER_START));
        return found;
    }

    /** TYPE answers with internal names; show the name a Java developer recognises. */
    static String label(String type) {
        return switch (type) {
            case "ReJSON-RL" -> "JSON";
            case "zset" -> "sorted set";
            default -> type;
        };
    }
}
