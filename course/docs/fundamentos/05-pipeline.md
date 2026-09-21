---
lesson: 100-05
title: "Pipeline: cem comandos, uma viagem"
minutes: 8
kind: lab
---

# Pipeline: cem comandos, uma viagem

<p class="lesson-meta">Lição 100-05 · Lab · 8 min</p>

Gravar 100 itens um por vez são 100 idas e voltas até o Redis: com 1 ms de rede, um décimo de segundo em que o servidor quase não trabalha e o seu código só espera. Um pipeline manda os 100 comandos de uma vez e lê as respostas depois. E quando duas escritas precisam acontecer juntas ou não acontecer; transferir ouro de uma conta para outra; `MULTI`/`EXEC` garante que ninguém veja o meio do caminho.

## O que o lab faz

- Gravar 100 drops `quest:loot:{n}`, um `SET` por vez, medindo o tempo
- Apagar tudo com um único `UNLINK` e gravar os mesmos 100 em um pipeline, medindo de novo
- Ler a aceleração e entender de onde ela vem
- Transferir 100 de ouro entre dois personagens com `MULTI`/`EXEC`
- Separar os dois conceitos: pipeline é rede, transação é atomicidade

## Faça agora

```bash
./quest run 100-05 jedis
./quest run 100-05 lettuce    # opcional: mesmo lab, outro client
./quest verify 100-05
```

## O código

=== "Jedis"

    ```java
    String[] lootKeys = lootKeys(ctx);                            // quest:loot:1 ... quest:loot:100
    String[] lootValues = lootValues();                           // one item id per drop
    String bromKey = ctx.k("player", "brom"), nixKey = ctx.k("player", "nix");
    try (RedisClient jedis = Clients.jedis()) {
        jedis.unlink(lootKeys);                                   // 100 keys, one command

        long start = System.nanoTime();                           // round 1: one SET per drop
        for (int i = 0; i < 100; i++) jedis.set(lootKeys[i], lootValues[i]);
        double oneByOneMs = elapsedMs(start);

        jedis.unlink(lootKeys);
        start = System.nanoTime();                                // round 2: pipeline
        List<Response<String>> replies = new ArrayList<>();
        try (Pipeline pipeline = jedis.pipelined()) {
            for (int i = 0; i < 100; i++) replies.add(pipeline.set(lootKeys[i], lootValues[i]));
            pipeline.sync();                                      // sends everything, reads 100 replies
        }
        double pipelineMs = elapsedMs(start);
        replies.get(0).get();                                     // "OK" (empty before sync())

        jedis.hset(bromKey, "gold", "15200");                     // seed values, so the run is repeatable
        jedis.hset(nixKey, "gold", "9900");
        try (AbstractTransaction tx = jedis.multi()) {            // MULTI is sent here
            Response<Long> bromGold = tx.hincrBy(bromKey, "gold", -100);
            Response<Long> nixGold = tx.hincrBy(nixKey, "gold", 100);
            List<Object> results = tx.exec();                     // [15100, 10000]
            bromGold.get();                                       // 15100
        }
    }
    ```

=== "Lettuce"

    ```java
    String[] lootKeys = JedisLab.lootKeys(ctx);                   // same keys, same values as the Jedis lab
    String[] lootValues = JedisLab.lootValues();
    String bromKey = ctx.k("player", "brom"), nixKey = ctx.k("player", "nix");
    try (StatefulRedisConnection<String, String> connection = Clients.lettuceConnection()) {
        RedisCommands<String, String> sync = connection.sync();
        RedisAsyncCommands<String, String> async = connection.async();
        sync.unlink(lootKeys);

        long start = System.nanoTime();                           // round 1: one sync SET per drop
        for (int i = 0; i < 100; i++) sync.set(lootKeys[i], lootValues[i]);
        double oneByOneMs = elapsedMs(start);

        sync.unlink(lootKeys);
        start = System.nanoTime();                                // round 2: explicit batch
        List<RedisFuture<String>> futures = new ArrayList<>();
        connection.setAutoFlushCommands(false);                   // hold everything in the buffer
        try {
            for (int i = 0; i < 100; i++) futures.add(async.set(lootKeys[i], lootValues[i]));
            connection.flushCommands();                           // one write to the socket
            LettuceFutures.awaitAll(Duration.ofSeconds(5), futures.toArray(new RedisFuture<?>[0]));
        } finally {
            connection.setAutoFlushCommands(true);
        }
        double pipelineMs = elapsedMs(start);

        sync.hset(bromKey, "gold", "15200");
        sync.hset(nixKey, "gold", "9900");
        async.multi();                                            // transactions live on the async API
        RedisFuture<Long> bromGold = async.hincrby(bromKey, "gold", -100);
        RedisFuture<Long> nixGold = async.hincrby(nixKey, "gold", 100);
        TransactionResult result = async.exec().get(5, TimeUnit.SECONDS);
        bromGold.get();                                           // 15100, completed by EXEC
    }
    ```

O que o console mostra, rodando contra um Redis local (contra o Redis Cloud a distância entre as duas rodadas cresce muito):

```text
-> Rodada 1: cem drops, um SET por vez
   tempo, 100 idas e voltas: 25.5 ms
-> Rodada 2: os mesmos cem drops em um pipeline
   tempo, 1 ida e volta: 3.8 ms
   respostas OK: 100/100
   aceleração: 6.8x
-> MULTI/EXEC: Brom paga 100 de ouro à Nix, tudo ou nada
   EXEC: [15100, 10000]
```

!!! note "Por que o Lettuce usa a API assíncrona na transação?"
    Dentro de `MULTI`, o servidor responde `QUEUED` a cada comando e só entrega os resultados no `EXEC`. Na API síncrona do Lettuce, `hincrby` devolveria `null`; na assíncrona, cada comando devolve um future que completa quando o `EXEC` chega. No Jedis o mesmo papel é do `Response<T>`: vazio até o `exec()`.

## No Redis Insight

Ligue o **Profiler** antes de rodar o lab. Na rodada 1 os 100 `SET` chegam espaçados, um a um; na rodada 2 chegam em rajada, todos no mesmo instante. Depois vêm `MULTI`, dois `HINCRBY` e `EXEC`. No **Browser**, filtre `quest:loot:*` e veja os 100 drops (cada um guarda o id de um item do catálogo); em `quest:player:brom` o campo `gold` está em 15100 e em `quest:player:nix` em 10000.

??? note "Por dentro"

    | Comando | O que faz |
    |---|---|
    | pipeline | Não é um comando: é o client enviar vários comandos sem esperar cada resposta, e ler todas depois. O servidor executa na ordem, um por vez, como sempre |
    | `MULTI` | Abre uma transação: os comandos seguintes entram em fila e respondem `QUEUED` |
    | `EXEC` | Executa a fila inteira de uma vez, sem nenhum outro cliente entre os comandos, e devolve a lista de respostas |
    | `DISCARD` | Esvazia a fila e sai do `MULTI` sem executar |
    | `WATCH chave` | Antes do `MULTI`: se a chave mudar até o `EXEC`, a transação é abortada (`EXEC` devolve nulo). É o lock otimista do Redis |
    | `HINCRBY chave campo n` | Soma `n` ao campo numérico de um hash (negativo subtrai) e devolve o novo valor |
    | `UNLINK chave [chave ...]` | Apaga várias chaves em um comando, liberando memória em segundo plano |

??? tip "Em produção"

    - Pipeline não é atômico e não é transação: outro cliente pode escrever entre dois comandos seus, e um erro no meio não desfaz os anteriores. Ele economiza rede. Junte lotes de algumas centenas de comandos (o Redis guarda todas as respostas em memória até você ler); para milhões de escritas, faça vários pipelines.
    - `MULTI`/`EXEC` não tem rollback: se um comando falhar na execução (um `INCR` em uma string que não é número, por exemplo), os outros continuam valendo. A atomicidade é "ninguém no meio", não "tudo ou nada em caso de erro". Erros de sintaxe, por outro lado, abortam o `EXEC` inteiro.
    - Em um banco com vários shards (Redis Cloud fora do plano de um shard só, ou Redis OSS em cluster), todas as chaves de uma transação precisam estar no mesmo slot. Use hash tags: `quest:{guilda:ordem}:player:brom` e `quest:{guilda:ordem}:player:nix` caem no mesmo shard porque só o que está entre chaves entra no cálculo do slot. Já `UNLINK` e `MGET` com chaves em slots diferentes o proxy do Redis Cloud resolve para você.
    - No Jedis, o pipeline e a transação seguram uma conexão do pool até o `sync()` ou o `exec()`; no Lettuce, só desligue o auto-flush em uma conexão dedicada, nunca na conexão compartilhada da aplicação. Na maior parte dos casos, no Lettuce, basta disparar os comandos assíncronos e aguardar os futures: a conexão já os escreve em lote.
    - `MULTI` no Lettuce também pede conexão dedicada: em uma conexão compartilhada, os comandos das outras threads entrariam na sua fila entre o `MULTI` e o `EXEC`. O lab usa uma conexão só dele por isso.

??? tip "Desafio"

    Acrescente dentro do `MULTI` um terceiro comando que vai falhar na execução, por exemplo `tx.incr(bromKey)` no Jedis ou `async.incr(bromKey)` no Lettuce (a ficha é um hash, não uma string). Rode e veja: o `EXEC` devolve um erro `WRONGTYPE` nessa posição, mas os dois `HINCRBY` foram aplicados mesmo assim. Essa é a diferença entre "atômico" e "com rollback".
