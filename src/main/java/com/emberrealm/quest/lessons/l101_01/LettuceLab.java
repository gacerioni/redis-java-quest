package com.emberrealm.quest.lessons.l101_01;

import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Lab;
import io.lettuce.core.KeyValue;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

import java.util.List;

/** 101-01 (Lettuce): same four String roles, same keys; SetArgs plays the part of Jedis' SetParams. */
public final class LettuceLab implements Lab {

    @Override
    public void run(Ctx ctx) {
        String session = ctx.k("session", "kaelith");
        String cooldown = ctx.k("cooldown", "kaelith", "fireball");
        String kills = ctx.k("kills", "kaelith");
        String redeem = ctx.k("redeem", "kaelith");

        try (StatefulRedisConnection<String, String> connection = Clients.lettuceConnection()) {
            RedisCommands<String, String> redis = connection.sync();

            ctx.out.step("Limpando as chaves da lição (pode rodar quantas vezes quiser)");
            ctx.out.cmd("UNLINK " + session + " " + cooldown + " " + kills + " " + redeem);
            redis.unlink(session, cooldown, kills, redeem);

            ctx.out.step("Sessão: Kaelith fez login e o token vive 30 minutos");
            String token = JedisLab.newToken();
            ctx.out.cmd("SET " + session + " " + token + " EX " + JedisLab.SESSION_TTL_SECONDS);
            ctx.out.kv("SET", redis.set(session, token, SetArgs.Builder.ex(JedisLab.SESSION_TTL_SECONDS)));
            ctx.out.cmd("TTL " + session);
            long sessionTtl = redis.ttl(session);
            ctx.out.kv("TTL", sessionTtl + " s");
            ctx.out.info("Sem job de limpeza: quando o TTL zera, o Redis apaga a sessão sozinho.");

            ctx.out.step("Cooldown: Bola de Fogo só pode ser lançada a cada " + JedisLab.COOLDOWN_SECONDS + " segundos");
            ctx.out.cmd("SET " + cooldown + " 1 NX EX " + JedisLab.COOLDOWN_SECONDS);
            String firstCast = redis.set(cooldown, "1", SetArgs.Builder.nx().ex(JedisLab.COOLDOWN_SECONDS));
            ctx.out.kv("primeiro lançamento", firstCast == null ? "(nil)" : firstCast + ", a magia saiu");
            ctx.out.cmd("SET " + cooldown + " 1 NX EX " + JedisLab.COOLDOWN_SECONDS);
            String secondCast = redis.set(cooldown, "1", SetArgs.Builder.nx().ex(JedisLab.COOLDOWN_SECONDS));
            ctx.out.kv("segundo lançamento", secondCast == null ? "(nil), em cooldown" : secondCast);
            ctx.out.cmd("TTL " + cooldown);
            ctx.out.kv("TTL", redis.ttl(cooldown) + " s até poder lançar de novo");
            ctx.out.info("NX e EX no mesmo SET: checar e gravar acontecem juntos, atômicos. Dois servidores de jogo tentando ao mesmo tempo: só um ganha.");

            ctx.out.step("Contador de abates: INCR é atômico, sem GET e SET separados");
            ctx.out.cmd("INCR " + kills);
            ctx.out.kv("abate 1", redis.incr(kills));
            ctx.out.cmd("INCR " + kills);
            ctx.out.kv("abate 2", redis.incr(kills));
            ctx.out.cmd("INCRBY " + kills + " 3");
            long totalKills = redis.incrby(kills, 3);
            ctx.out.kv("um grupo de 3 goblins", totalKills);
            ctx.out.cmd("GET " + kills);
            ctx.out.kv("GET", "\"" + redis.get(kills) + "\"");
            ctx.out.info("O valor é uma String; o Redis faz a conta em cima do texto e devolve o número novo.");

            ctx.out.step("Código de resgate de uso único: GETDEL lê e apaga na mesma viagem");
            ctx.out.cmd("SET " + redeem + " POCAO-RARA-7");
            redis.set(redeem, "POCAO-RARA-7");
            ctx.out.cmd("GETDEL " + redeem);
            ctx.out.kv("primeira leitura", redis.getdel(redeem));
            ctx.out.cmd("GETDEL " + redeem);
            String again = redis.getdel(redeem);
            ctx.out.kv("segunda leitura", again == null ? "(nil), o código já foi resgatado" : again);

            ctx.out.step("MGET: várias chaves numa ida só ao servidor");
            ctx.out.cmd("MGET " + session + " " + cooldown + " " + kills);
            List<KeyValue<String, String>> values = redis.mget(session, cooldown, kills);
            for (KeyValue<String, String> kv : values) {
                ctx.out.kv(kv.getKey(), kv.getValueOrElse("(nil)"));
            }
            ctx.out.info("No Lettuce, MGET devolve KeyValue: a chave e o valor, que pode estar vazio (hasValue).");

            ctx.done("kills", String.valueOf(totalKills),
                    "cooldown", secondCast == null ? "ok" : "sem-nx",
                    "session_ttl", String.valueOf(sessionTtl),
                    "ran_" + ctx.client, "1");
        }
    }
}
