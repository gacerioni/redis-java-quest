package com.emberrealm.quest.lessons.l101_01;

import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Lab;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.params.SetParams;

import java.util.List;
import java.util.UUID;

/**
 * 101-01 (Jedis): the String type in four roles. A login session that expires on its own (SET EX),
 * a skill cooldown checked and written in one atomic step (SET NX EX), an atomic kill counter
 * (INCR, INCRBY) and a one-time redeem code consumed with GETDEL. MGET reads several keys in one trip.
 */
public final class JedisLab implements Lab {

    static final int SESSION_TTL_SECONDS = 1800;
    static final int COOLDOWN_SECONDS = 5;

    @Override
    public void run(Ctx ctx) {
        String session = ctx.k("session", "kaelith");
        String cooldown = ctx.k("cooldown", "kaelith", "fireball");
        String kills = ctx.k("kills", "kaelith");
        String redeem = ctx.k("redeem", "kaelith");

        try (RedisClient jedis = Clients.jedis()) {
            ctx.out.step("Limpando as chaves da lição (pode rodar quantas vezes quiser)");
            ctx.out.cmd("UNLINK " + session + " " + cooldown + " " + kills + " " + redeem);
            jedis.unlink(session, cooldown, kills, redeem);

            ctx.out.step("Sessão: Kaelith fez login e o token vive 30 minutos");
            String token = newToken();
            ctx.out.cmd("SET " + session + " " + token + " EX " + SESSION_TTL_SECONDS);
            ctx.out.kv("SET", jedis.set(session, token, SetParams.setParams().ex(SESSION_TTL_SECONDS)));
            ctx.out.cmd("TTL " + session);
            long sessionTtl = jedis.ttl(session);
            ctx.out.kv("TTL", sessionTtl + " s");
            ctx.out.info("Sem job de limpeza: quando o TTL zera, o Redis apaga a sessão sozinho.");

            ctx.out.step("Cooldown: Bola de Fogo só pode ser lançada a cada " + COOLDOWN_SECONDS + " segundos");
            ctx.out.cmd("SET " + cooldown + " 1 NX EX " + COOLDOWN_SECONDS);
            String firstCast = jedis.set(cooldown, "1", SetParams.setParams().nx().ex(COOLDOWN_SECONDS));
            ctx.out.kv("primeiro lançamento", firstCast == null ? "(nil)" : firstCast + ", a magia saiu");
            ctx.out.cmd("SET " + cooldown + " 1 NX EX " + COOLDOWN_SECONDS);
            String secondCast = jedis.set(cooldown, "1", SetParams.setParams().nx().ex(COOLDOWN_SECONDS));
            ctx.out.kv("segundo lançamento", secondCast == null ? "(nil), em cooldown" : secondCast);
            ctx.out.cmd("TTL " + cooldown);
            ctx.out.kv("TTL", jedis.ttl(cooldown) + " s até poder lançar de novo");
            ctx.out.info("NX e EX no mesmo SET: checar e gravar acontecem juntos, atômicos. Dois servidores de jogo tentando ao mesmo tempo: só um ganha.");

            ctx.out.step("Contador de abates: INCR é atômico, sem GET e SET separados");
            ctx.out.cmd("INCR " + kills);
            ctx.out.kv("abate 1", jedis.incr(kills));
            ctx.out.cmd("INCR " + kills);
            ctx.out.kv("abate 2", jedis.incr(kills));
            ctx.out.cmd("INCRBY " + kills + " 3");
            long totalKills = jedis.incrBy(kills, 3);
            ctx.out.kv("um grupo de 3 goblins", totalKills);
            ctx.out.cmd("GET " + kills);
            ctx.out.kv("GET", "\"" + jedis.get(kills) + "\"");
            ctx.out.info("O valor é uma String; o Redis faz a conta em cima do texto e devolve o número novo.");

            ctx.out.step("Código de resgate de uso único: GETDEL lê e apaga na mesma viagem");
            ctx.out.cmd("SET " + redeem + " POCAO-RARA-7");
            jedis.set(redeem, "POCAO-RARA-7");
            ctx.out.cmd("GETDEL " + redeem);
            ctx.out.kv("primeira leitura", jedis.getDel(redeem));
            ctx.out.cmd("GETDEL " + redeem);
            String again = jedis.getDel(redeem);
            ctx.out.kv("segunda leitura", again == null ? "(nil), o código já foi resgatado" : again);

            ctx.out.step("MGET: várias chaves numa ida só ao servidor");
            ctx.out.cmd("MGET " + session + " " + cooldown + " " + kills);
            List<String> values = jedis.mget(session, cooldown, kills);
            ctx.out.kv("sessão", orNil(values.get(0)));
            ctx.out.kv("cooldown", orNil(values.get(1)));
            ctx.out.kv("abates", orNil(values.get(2)));

            ctx.done("kills", String.valueOf(totalKills),
                    "cooldown", secondCast == null ? "ok" : "sem-nx",
                    "session_ttl", String.valueOf(sessionTtl),
                    "ran_" + ctx.client, "1");
        }
    }

    /** A short random token; in a real game it would come from your auth layer. */
    static String newToken() {
        return "tok-" + UUID.randomUUID().toString().substring(0, 8);
    }

    static String orNil(String value) {
        return value == null ? "(nil)" : value;
    }
}
