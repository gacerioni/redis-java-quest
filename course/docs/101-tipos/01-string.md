---
lesson: 101-01
title: "String: sessão e cooldown de habilidade"
minutes: 15
kind: lab
---

# String: sessão e cooldown de habilidade

<p class="lesson-meta">Lição 101-01 · Lab + sua vez · 15 min</p>

Sessão, cooldown, contador: três problemas clássicos de backend resolvidos com o tipo mais simples do Redis; uma String, um valor por chave, com prazo de vida opcional. A graça está nos detalhes: `SET ... EX` cria uma chave que morre sozinha, `SET ... NX EX` implementa um cooldown sem condição de corrida e `INCR` soma sem ler o valor antes.

## Veja funcionando

Primeiro você assiste. O comando abaixo roda um lab pronto (`JedisLab.java` ou `LettuceLab.java`): você não escreve nada ainda, só lê a saída. Cada linha que começa com `>` é o comando Redis que o client enviou; a linha seguinte é a resposta.

```bash
./quest run 101-01 jedis
./quest run 101-01 lettuce    # opcional: mesmo lab, outro client
```

O que reparar na saída:

- `SET ... EX 1800` cria a sessão e o `TTL` logo depois mostra os 1800 segundos contando.
- O primeiro `SET ... NX EX 5` devolve `OK`; o segundo, feito logo em seguida, devolve `nil`: Bola de Fogo em cooldown.
- `INCR`, `INCR`, `INCRBY 3` chegam a 5 sem nenhum `GET` no meio.
- `GETDEL` lê o código de resgate e o apaga na mesma viagem; a segunda leitura volta `nil`.
- `MGET` traz três chaves em uma ida só ao servidor.

## Entenda o código

Este é o miolo do lab que acabou de rodar, sem a narração do console e sem a limpeza inicial das chaves. O arquivo completo está em [`l101_01/JedisLab.java`](https://github.com/gacerioni/redis-java-quest/blob/main/src/main/java/com/emberrealm/quest/lessons/l101_01/JedisLab.java) e [`l101_01/LettuceLab.java`](https://github.com/gacerioni/redis-java-quest/blob/main/src/main/java/com/emberrealm/quest/lessons/l101_01/LettuceLab.java); abra na IDE e siga com o cursor.

=== "Jedis"

    ```java
    try (RedisClient jedis = Clients.jedis()) {
        // sessao que expira sozinha
        jedis.set(session, token, SetParams.setParams().ex(1800));
        long ttl = jedis.ttl(session);                                                // 1800

        // cooldown: checar e gravar em um passo atomico
        String first = jedis.set(cooldown, "1", SetParams.setParams().nx().ex(5));   // "OK"
        String second = jedis.set(cooldown, "1", SetParams.setParams().nx().ex(5));  // null: em cooldown

        // contador atomico de abates
        jedis.incr(kills);                                                            // 1
        jedis.incr(kills);                                                            // 2
        long total = jedis.incrBy(kills, 3);                                          // 5

        // codigo de resgate de uso unico
        jedis.set(redeem, "POCAO-RARA-7");
        jedis.getDel(redeem);                                                         // "POCAO-RARA-7"
        jedis.getDel(redeem);                                                         // null

        // tres chaves, uma viagem
        List<String> values = jedis.mget(session, cooldown, kills);
    }
    ```

=== "Lettuce"

    ```java
    try (StatefulRedisConnection<String, String> connection = Clients.lettuceConnection()) {
        RedisCommands<String, String> redis = connection.sync();

        // sessao que expira sozinha
        redis.set(session, token, SetArgs.Builder.ex(1800));
        long ttl = redis.ttl(session);                                                // 1800

        // cooldown: checar e gravar em um passo atomico
        String first = redis.set(cooldown, "1", SetArgs.Builder.nx().ex(5));         // "OK"
        String second = redis.set(cooldown, "1", SetArgs.Builder.nx().ex(5));        // null: em cooldown

        // contador atomico de abates
        redis.incr(kills);                                                            // 1
        redis.incr(kills);                                                            // 2
        long total = redis.incrby(kills, 3);                                          // 5

        // codigo de resgate de uso unico
        redis.set(redeem, "POCAO-RARA-7");
        redis.getdel(redeem);                                                         // "POCAO-RARA-7"
        redis.getdel(redeem);                                                         // null

        // tres chaves, uma viagem
        List<KeyValue<String, String>> values = redis.mget(session, cooldown, kills);
    }
    ```

## Sua vez

Agora é você quem escreve. Kaelith aprendeu **Cura Menor**: a habilidade tem cooldown de 8 segundos e cada cura que sai deve ser contada.

Abra o arquivo do client que você escolheu e implemente o método `castHeal`, que hoje só lança `Todo`:

- Jedis: `src/main/java/com/emberrealm/quest/lessons/l101_01/JedisExercise.java`
- Lettuce: `src/main/java/com/emberrealm/quest/lessons/l101_01/LettuceExercise.java`

Regras do método:

| Regra | Como o verify confere |
|---|---|
| A cura só sai se a chave `{p}:cooldown:kaelith:heal` ainda não existe | duas curas seguidas: só a primeira pode sair |
| Quando sai, a chave nasce com TTL de 8 segundos | `TTL` da chave entre 0 e 8 |
| Quando sai, `{p}:heals:kaelith` cresce em 1 | o contador tem que valer exatamente 1 |
| Checar e gravar acontecem em um único comando | revise o uso de `SET ... NX EX`; duas chamadas sequenciais não provam segurança sob concorrência |
| Devolve `true` quando a cura saiu, `false` em cooldown | a saída do exercício mostra os dois valores |

O restante do arquivo prepara e registra a execução: limpa as chaves, chama `castHeal` duas vezes seguidas, mostra o resultado e registra o exercício para o verify. Não precisa mexer nele.

```bash
./quest exercise 101-01 jedis      # ou lettuce
./quest verify 101-01
```

Enquanto o método não estiver implementado, o `exercise` para com o aviso "Sua vez: implemente castHeal" e o `verify` marca a parte "Sua vez" em vermelho. Quando passar, o `verify` mostra as três linhas verdes da sua vez. Travou? `./quest solve 101-01 --yes` copia a solução de referência por cima do seu arquivo.

O `SET NX EX` torna a decisão do cooldown atômica. O `INCR` que vem depois é outro comando: se o processo falhar entre os dois, a contagem pode não acompanhar o cooldown. O exercício não promete atomicidade conjunta nem entrega exatamente uma vez.

Quer ir além: faça `castHeal` devolver também quantos segundos faltam para poder curar de novo (`TTL`) e mostre isso na saída.

## No Redis Insight

Depois do lab, filtre por `quest:*` no Browser: `quest:session:kaelith` mostra o TTL descendo em tempo real; `quest:cooldown:kaelith:fireball` aparece e some em 5 segundos; `quest:kills:kaelith` guarda `5`. Depois da sua vez, `quest:cooldown:kaelith:heal` e `quest:heals:kaelith` aparecem ao lado. No Profiler, rode o lab de novo e veja a sequência exata de `SET`, `INCR`, `GETDEL` e `MGET` que o client enviou.

??? note "Por dentro"

    | Comando | O que faz |
    |---|---|
    | `SET key value EX 1800` | Grava e já agenda a expiração em 1800 s |
    | `SET key value NX EX 5` | Só grava se a chave não existe (NX), com TTL de 5 s (EX); devolve `nil` se já existia |
    | `TTL key` | Segundos restantes; `-1` sem prazo, `-2` chave não existe |
    | `INCR key`, `INCRBY key n` | Soma atômica no valor numérico da String |
    | `GETDEL key` | Devolve o valor e apaga a chave na mesma operação |
    | `MGET k1 k2 k3` | Vários valores em uma ida ao servidor |

??? tip "Em produção"

    - Sessão com TTL dispensa job de limpeza; renove o prazo com `EXPIRE` a cada requisição válida (sliding session).
    - `SET NX EX` é o lock mais simples do Redis; para liberar com segurança, apague só se o valor ainda for o seu (script Lua ou `DEL` condicional), nunca um `DEL` cego.
    - Contadores com `INCR` são atômicos mesmo com dezenas de instâncias da aplicação escrevendo ao mesmo tempo. Nada de `GET`, somar em Java e `SET` de volta.

??? note "Ver a solução de referência"

    === "Jedis"

        ```java
        static boolean castHeal(RedisClient jedis, String cooldownKey, String healsKey) {
            String reply = jedis.set(cooldownKey, "1", SetParams.setParams().nx().ex(HEAL_COOLDOWN_SECONDS));
            if (reply == null) {
                return false; // a chave ja existia: ainda em cooldown
            }
            jedis.incr(healsKey);
            return true;
        }
        ```

    === "Lettuce"

        ```java
        static boolean castHeal(RedisCommands<String, String> redis, String cooldownKey, String healsKey) {
            String reply = redis.set(cooldownKey, "1", SetArgs.Builder.nx().ex(HEAL_COOLDOWN_SECONDS));
            if (reply == null) {
                return false; // a chave ja existia: ainda em cooldown
            }
            redis.incr(healsKey);
            return true;
        }
        ```
