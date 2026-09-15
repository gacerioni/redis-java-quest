---
lesson: 101-03
title: "List: a fila da dungeon"
minutes: 8
kind: lab
---

# List: a fila da dungeon

<p class="lesson-meta">Lição 101-03 · Lab · 8 min</p>

Uma fila em que a ordem de chegada importa é uma List: sequência ordenada em que inserir e remover nas pontas custa O(1), não importa o tamanho. `RPUSH` coloca quem chegou no fim, `LPOP` entrega a quem espera há mais tempo. E `BRPOP` deixa um worker esperando trabalho sem polling — ao custo de prender uma conexão enquanto espera, tema de [Conexões bloqueantes](../102-eventos/04-conexoes-bloqueantes.md).

## O que o lab faz

- Colocar cinco jogadores na fila com um único `RPUSH`
- Medir e listar a fila com `LLEN` e `LRANGE 0 -1`
- Achar a posição de Nix com `LPOS`
- Formar uma party tirando os dois primeiros com `LPOP ... 2`
- Esperar 1 segundo numa fila vazia com `BRPOP` e ver o `nil` chegar no tempo certo

## Faça agora

```bash
./quest run 101-03 jedis
./quest run 101-03 lettuce    # opcional: mesmo lab, outro client
./quest verify 101-03
```

## O código

=== "Jedis"

    ```java
    try (RedisClient jedis = Clients.jedis()) {
        jedis.unlink(queue, raid);
        long queued = jedis.rpush(queue, "brom", "lyra", "nix", "seraphine", "ysolde");   // 5

        long size = jedis.llen(queue);                          // 5
        List<String> line = jedis.lrange(queue, 0, -1);         // [brom, lyra, nix, seraphine, ysolde]
        Long pos = jedis.lpos(queue, "nix");                    // 2

        List<String> party = jedis.lpop(queue, 2);              // [brom, lyra]
        long waiting = jedis.llen(queue);                       // 3

        // blocking preview: 1 second on an empty list, then null
        List<String> popped = jedis.brpop(1, raid);             // null after ~1000 ms
    }
    ```

=== "Lettuce"

    ```java
    try (StatefulRedisConnection<String, String> connection = Clients.lettuceConnection()) {
        RedisCommands<String, String> redis = connection.sync();
        redis.unlink(queue, raid);
        Long queued = redis.rpush(queue, "brom", "lyra", "nix", "seraphine", "ysolde");   // 5

        Long size = redis.llen(queue);                          // 5
        List<String> line = redis.lrange(queue, 0, -1);
        Long pos = redis.lpos(queue, "nix");                    // 2

        List<String> party = redis.lpop(queue, 2);              // [brom, lyra]
        Long waiting = redis.llen(queue);                       // 3

        // blocking commands get their own connection: the shared one keeps serving
        try (StatefulRedisConnection<String, String> blocking = Clients.lettuceConnection()) {
            KeyValue<String, String> popped = blocking.sync().brpop(1, raid);   // null after ~1000 ms
        }
    }
    ```

## No Redis Insight

No Browser, `quest:queue:dungeon` aparece como List com três elementos e `nix` no índice 0: quem estava em terceiro virou o primeiro da fila. `quest:queue:raid` não aparece: uma List vazia não existe como chave, e o `BRPOP` que esperou nela não criou nada. Abra o Profiler e rode o lab: repare no intervalo de um segundo entre o `BRPOP` e o comando seguinte, com a conexão parada esperando.

??? note "Por dentro"

    | Comando | O que faz |
    |---|---|
    | `RPUSH chave a b c` | Insere no fim (direita) e devolve o tamanho novo; `LPUSH` insere na frente |
    | `LLEN chave` | Tamanho da lista; 0 se a chave não existe |
    | `LRANGE chave 0 -1` | Fatia por índices, inclusive; índices negativos contam do fim |
    | `LPOS chave elemento` | Índice da primeira ocorrência, O(n); `nil` se não estiver na lista |
    | `LPOP chave 2` | Remove e devolve N elementos da frente (Redis 6.2+); `RPOP` faz o mesmo no fim |
    | `BRPOP chave 1` | Como `RPOP`, mas espera até 1 s por um elemento; `nil` quando o tempo acaba |
    | `LMOVE origem destino LEFT RIGHT` | Move um elemento entre listas de forma atômica; base de filas com "em processamento" |

    ```mermaid
    flowchart LR
        R["RPUSH: entra no fim"] --> L["brom | lyra | nix | seraphine | ysolde"]
        L --> P["LPOP 2: sai pela frente"]
        P --> party["party: brom, lyra"]
    ```

    !!! warning "Bloqueio no Lettuce pede conexão dedicada"
        O Lettuce multiplexa uma conexão para a aplicação inteira. Um `BRPOP` nela segura todos os comandos que chegarem atrás durante a espera. Por isso o lab abre uma segunda conexão só para bloquear e fecha em seguida. No Jedis cada comando pega uma conexão do pool, então o bloqueio ocupa uma conexão do pool inteira, o que é o mesmo custo com outra roupa.

??? tip "Em produção"

    - O timeout do bloqueio tem que ser menor que o timeout do client. O Jedis usa 2 s de socket timeout por padrão: um `BRPOP ... 5` estoura `SocketTimeoutException` antes de o Redis responder. Ajuste `socketTimeoutMillis` ou mantenha bloqueios curtos. Mais em [Timeouts, pool e retry](../301-producao/01-timeouts-pool-retry.md).
    - Cada `BRPOP` prende uma conexão inteira enquanto espera. Com 30 conexões no plano free, cinco workers bloqueados já são um sexto do limite. Dimensione os workers pensando nisso.
    - Lista é fila simples: se o worker morre depois do `LPOP`, o item se perde. Para reconhecimento (ack), reentrega e vários consumidores, use [Streams](../102-eventos/02-streams.md) com [consumer groups](../102-eventos/03-consumer-groups.md). Para logs recentes, limite o tamanho com `LTRIM chave 0 999` depois de cada `LPUSH`.

??? tip "Desafio"

    Suba uma `Thread` que faça `RPUSH quest:queue:raid dorian` meio segundo depois de o lab começar a esperar e veja o `BRPOP` voltar na hora com `dorian`, sem esperar o segundo inteiro (o verify continua passando: a fila da raid volta a ficar vazia). Depois troque `LPOP` por `RPOP` e note que o matchmaker passa a pegar quem chegou por último: a mesma lista virou uma pilha.
