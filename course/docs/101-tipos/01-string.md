---
lesson: 101-01
title: "String: sessão e cooldown de habilidade"
minutes: 8
kind: lab
---

# String: sessão e cooldown de habilidade

<p class="lesson-meta">Lição 101-01 · Lab · 8 min</p>

## Por que isso importa

Kaelith faz login, lança Bola de Fogo e derruba goblins. Cada uma dessas ações vira uma chave do tipo String, o tipo mais simples do Redis: um valor por chave, com prazo de vida opcional. A graça está nos detalhes: `SET ... EX` cria uma sessão que morre sozinha, `SET ... NX EX` implementa um cooldown sem condição de corrida e `INCR` soma abates sem ler o valor antes. Três problemas clássicos de backend (sessão, lock com prazo, contador) resolvidos com meia dúzia de comandos.

## O que você vai fazer

- Gravar a sessão de Kaelith com `SET ... EX 1800` e ler o `TTL`
- Lançar Bola de Fogo duas vezes com `SET ... NX EX 5` e ver a segunda voltar `nil` ("em cooldown")
- Contar abates com `INCR` e `INCRBY`, sem `GET` antes
- Consumir um código de resgate de uso único com `GETDEL`
- Ler três chaves numa viagem só com `MGET`

## Rode

```bash
./quest run 101-01 jedis
./quest run 101-01 lettuce
./quest check 101-01
```

## O código

=== "Jedis"

    ```java
    try (RedisClient jedis = Clients.jedis()) {
        // login session that expires on its own
        jedis.set(session, token, SetParams.setParams().ex(1800));
        long ttl = jedis.ttl(session);                                                // 1800

        // skill cooldown: check and write in one atomic step
        String first = jedis.set(cooldown, "1", SetParams.setParams().nx().ex(5));   // "OK"
        String second = jedis.set(cooldown, "1", SetParams.setParams().nx().ex(5));  // null: em cooldown

        // atomic kill counter
        jedis.incr(kills);                                                            // 1
        jedis.incr(kills);                                                            // 2
        long total = jedis.incrBy(kills, 3);                                          // 5

        // one-time redeem code
        jedis.set(redeem, "POCAO-RARA-7");
        jedis.getDel(redeem);                                                         // "POCAO-RARA-7"
        jedis.getDel(redeem);                                                         // null

        // three keys, one round trip
        List<String> values = jedis.mget(session, cooldown, kills);
    }
    ```

=== "Lettuce"

    ```java
    try (StatefulRedisConnection<String, String> connection = Clients.lettuceConnection()) {
        RedisCommands<String, String> redis = connection.sync();

        redis.set(session, token, SetArgs.Builder.ex(1800));
        Long ttl = redis.ttl(session);                                                // 1800

        String first = redis.set(cooldown, "1", SetArgs.Builder.nx().ex(5));          // "OK"
        String second = redis.set(cooldown, "1", SetArgs.Builder.nx().ex(5));         // null: em cooldown

        redis.incr(kills);                                                            // 1
        redis.incr(kills);                                                            // 2
        Long total = redis.incrby(kills, 3);                                          // 5

        redis.set(redeem, "POCAO-RARA-7");
        redis.getdel(redeem);                                                         // "POCAO-RARA-7"
        redis.getdel(redeem);                                                         // null

        List<KeyValue<String, String>> values = redis.mget(session, cooldown, kills);
        values.forEach(kv -> System.out.println(kv.getKey() + " = " + kv.getValueOrElse("(nil)")));
    }
    ```

## O que olhar no Redis Insight

No Browser, filtre por `quest:*` (ou pelo seu prefixo) logo depois de rodar o lab. `quest:session:kaelith` aparece como String com o token e um TTL perto de 1800 s caindo. `quest:cooldown:kaelith:fireball` dura 5 segundos: atualize a lista e veja a chave sumir sozinha. `quest:kills:kaelith` guarda `5` como texto, mesmo tendo nascido de `INCR`. No Workbench, rode `SET quest:cooldown:kaelith:fireball 1 NX EX 5` duas vezes seguidas e veja o `(nil)` da segunda. No Profiler, repare no `MGET`: uma linha só para três chaves.

## Por dentro

| Comando | O que faz |
|---|---|
| `SET chave valor EX 1800` | Grava e já define o prazo em segundos (`PX` para milissegundos) na mesma operação |
| `SET chave valor NX EX 5` | Só grava se a chave não existe, e com prazo; devolve `nil` quando perde a disputa |
| `TTL chave` | Segundos restantes; `-1` quando não há prazo, `-2` quando a chave não existe |
| `INCR chave`, `INCRBY chave 3` | Soma atômica no servidor; a chave nasce em 0 se não existir |
| `GET chave` | Lê o valor; contadores voltam como texto e você converte na aplicação |
| `GETDEL chave` | Lê e apaga numa operação só (Redis 6.2+); ideal para códigos de uso único |
| `MGET c1 c2 c3` | Vários valores numa viagem; `nil` nas posições das chaves que não existem |

!!! tip "Jedis e Lettuce, mesma ideia, nomes diferentes"
    Jedis agrupa as opções do `SET` em `SetParams` (`nx()`, `ex()`, `px()`, `keepttl()`); Lettuce usa `SetArgs.Builder` com os mesmos nomes. Nos dois, um `SET ... NX` que perde a disputa devolve `null`, não uma exceção: trate o `null` como "em cooldown".

## Em produção

- Prazo sempre na mesma chamada: `SET` com `EX`, nunca `SET` e depois `EXPIRE`. Em duas viagens, se o processo cair no meio, a chave fica para sempre sem prazo. Mais sobre TTL e nomes de chave em [Chaves, TTL e SCAN](../fundamentos/04-chaves-ttl-scan.md).
- `SET NX EX` é a base do lock distribuído simples: quem ganha o `NX` tem o lock e o `EX` garante que ele solta sozinho se o dono morrer. Grave um valor único por dono e, ao soltar, compare antes de apagar (script Lua ou, nas versões mais novas do Redis, `DELEX` com condição).
- Contador por janela de tempo (rate limit por minuto, por exemplo): `INCR` e, quando a resposta for 1, `EXPIRE` da janela; ou uma chave por janela no próprio nome (`quest:req:kaelith:202609111530`), que expira sozinha.

## Desafio

Mude `COOLDOWN_SECONDS` para 2, lance a magia, espere com `Thread.sleep(2100)` e lance de novo: agora o segundo `SET NX EX` devolve `OK`, porque o prazo venceu. Depois troque o `INCRBY 3` por `INCRBY -3` (o comando aceita negativos; `DECRBY` é só um atalho), rode o check e veja a reclamação: ele exige pelo menos 3 abates. Volte o valor e rode de novo.
