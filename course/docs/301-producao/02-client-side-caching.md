---
lesson: 301-02
title: "Client-side caching"
minutes: 10
kind: lab
---

# Client-side caching

<p class="lesson-meta">Lição 301-02 · Lab · 10 min</p>

Milhares de `GET` iguais por minuto para um valor que muda raramente: client-side caching guarda a resposta na memória da própria aplicação e deixa o servidor avisar quando ela mudar. O Redis faz o tracking das chaves que cada conexão leu e manda uma invalidação quando alguém escreve nelas. Leitura em microssegundos, zero viagens de rede, nenhum TTL no chute.

## O que o lab faz

- Criar `quest:config:motd` e lê-la 1000 vezes com um client com cache local (1 miss, 999 hits)
- Comparar com 200 `GET` sem cache e medir a diferença
- Mudar a mensagem por um segundo client e ver a invalidação chegar
- Jedis: RESP3 + `CacheConfig` + `getCache().getStats()`; Lettuce: `ClientSideCaching` + `CacheFrontend`

## Faça agora

```bash
./quest run 301-02 jedis
./quest run 301-02 lettuce    # opcional: mesmo lab, outro client
./quest verify 301-02
```

## O código

=== "Jedis"

    ```java
    DefaultJedisClientConfig config = DefaultJedisClientConfig.builder()
            .resp3()                                    // invalidations arrive as RESP3 push messages
            .build();
    CacheConfig cacheConfig = CacheConfig.builder().maxSize(1000).build();

    try (RedisClient cached = RedisClient.builder()
            .hostAndPort(new HostAndPort(host, port))
            .clientConfig(config)
            .cacheConfig(cacheConfig)
            .build()) {

        for (int i = 0; i < 1000; i++) cached.get(motd);   // 1 miss, then the cache answers
        CacheStats stats = cached.getCache().getStats();
        stats.getHitCount();            // 999
        stats.getMissCount();           // 1

        plain.set(motd, "Aviso do mestre do jogo: ...");   // another client writes the key
        cached.get(motd);               // invalidated: goes to the server, returns the new value
        stats.getInvalidationCount();   // 1
    }
    ```

=== "Lettuce"

    ```java
    StatefulRedisConnection<String, String> connection = client.connect();   // RESP3 is the default
    Map<String, String> map = new ConcurrentHashMap<>();

    try (CacheFrontend<String, String> frontend = ClientSideCaching.enable(
            CacheAccessor.forMap(map), connection, TrackingArgs.Builder.enabled())) {   // CLIENT TRACKING ON

        for (int i = 0; i < 1000; i++) frontend.get(motd);   // one GET, then the map answers

        plain.set(motd, "Aviso do mestre do jogo: ...");      // another connection writes the key
        frontend.get(motd);                                   // the push evicted the entry: GET again
    }
    ```

    No lab, um `CacheAccessor` próprio (dez linhas em volta do mesmo `Map`) conta hits, misses e evicts para mostrar os números no console.

## No Redis Insight

Abra o Profiler antes de rodar. Com cache, as 1000 leituras aparecem como um único `GET quest:config:motd`; as 200 sem cache aparecem uma a uma. No Workbench, rode `CLIENT LIST` durante o lab: a conexão com cache tem `resp=3` e a flag `t` (tracking ligado). No Browser, `quest:config:motd` mostra a mensagem nova depois que o mestre do jogo a trocou, e `quest:csc:clients` diz qual client rodou.

??? note "Por dentro"

    | Comando | O que faz |
    |---|---|
    | `HELLO 3` | Negocia RESP3 no handshake; os dois clients fazem isso sozinhos |
    | `CLIENT TRACKING ON` | Pede ao servidor para lembrar as chaves lidas nesta conexão (o Jedis manda ao abrir cada conexão com `cacheConfig`) |
    | `GET quest:config:motd` | Só o primeiro vai ao servidor; a resposta fica no cache local |
    | `SET quest:config:motd ...` | Feito por outro client: dispara a mensagem de invalidação para quem leu a chave |
    | `invalidate` (push) | Mensagem RESP3 do servidor; o client apaga a entrada e a próxima leitura volta ao Redis |
    | `CLIENT INFO` | Mostra `resp=3`, a prova de que a conexão aceita mensagens push |

??? tip "Em produção"

    - Redis Cloud e Redis Software: client-side caching exige banco na versão 7.4 ou superior e RESP3. O modo de duas conexões (`REDIRECT`) não é suportado nesses produtos, e o Jedis não implementa `BCAST`, `OPTIN` nem `OPTOUT`: use o modo padrão, como no lab.
    - O exemplo usa `ClientSideCaching` na versão Lettuce 7.7.0 fixada no projeto. Ao atualizar o client, confira o status e os requisitos dessa API na documentação da versão adotada; tracking e invalidação continuam sendo os conceitos a entender.
    - Cache é para o que muda pouco e lê muito. Contadores e rankings geram uma enxurrada de invalidações: sirva esses por uma conexão sem cache. Dimensione `maxSize` pelo tamanho médio dos valores (`MEMORY USAGE`) e lembre que qualquer desconexão do client zera o cache.

??? tip "Desafio"

    Mude `maxSize` para 1 e leia duas chaves alternadas (`quest:config:motd` e `quest:world:motd`, da [lição 100-02](../fundamentos/02-conectar.md)): veja `getEvictCount()` subir e os hits caírem. Depois chame `cached.getCache().deleteByRedisKey(motd)` e confirme que a leitura seguinte vira miss sem nenhuma escrita no servidor.
