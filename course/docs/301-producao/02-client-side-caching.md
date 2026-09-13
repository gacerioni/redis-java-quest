---
lesson: 301-02
title: "Client-side caching"
minutes: 10
kind: lab
---

# Client-side caching

<p class="lesson-meta">Lição 301-02 · Lab · 10 min</p>

## Por que isso importa

Todo login no Ember Realm mostra a mensagem do dia: milhares de `GET` iguais por minuto para um valor que muda uma vez por semana. Client-side caching guarda essa resposta na memória da própria aplicação e deixa o servidor avisar quando ela mudar. O Redis faz o tracking: lembra quais chaves cada conexão leu e manda uma mensagem de invalidação quando alguém escreve nelas. Resultado: leitura em microssegundos, zero viagens de rede e nenhum TTL no chute.

## O que você vai fazer

- Criar `quest:config:motd` e lê-la 1000 vezes com um client com cache local (1 miss, 999 hits)
- Comparar com 200 `GET` sem cache e medir a diferença
- Mudar a mensagem por um segundo client e ver a invalidação chegar
- Jedis: RESP3 + `CacheConfig` + `getCache().getStats()`; Lettuce: `ClientSideCaching` + `CacheFrontend`

## Rode

```bash
./quest run 301-02 jedis
./quest run 301-02 lettuce
./quest check 301-02
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

## O que olhar no Redis Insight

Abra o Profiler antes de rodar. Com cache, as 1000 leituras aparecem como um único `GET quest:config:motd`; as 200 sem cache aparecem uma a uma. No Workbench, rode `CLIENT LIST` durante o lab: a conexão com cache tem `resp=3` e a flag `t` (tracking ligado). No Browser, `quest:config:motd` mostra a mensagem nova depois que o mestre do jogo a trocou, e `quest:csc:clients` diz qual client rodou.

## Por dentro

| Comando | O que faz |
|---|---|
| `HELLO 3` | Negocia RESP3 no handshake; os dois clients fazem isso sozinhos |
| `CLIENT TRACKING ON` | Pede ao servidor para lembrar as chaves lidas nesta conexão (o Jedis manda ao abrir cada conexão com `cacheConfig`) |
| `GET quest:config:motd` | Só o primeiro vai ao servidor; a resposta fica no cache local |
| `SET quest:config:motd ...` | Feito por outro client: dispara a mensagem de invalidação para quem leu a chave |
| `invalidate` (push) | Mensagem RESP3 do servidor; o client apaga a entrada e a próxima leitura volta ao Redis |
| `CLIENT INFO` | Mostra `resp=3`, a prova de que a conexão aceita mensagens push |

## Em produção

- Redis Cloud e Redis Software: client-side caching exige banco na versão 7.4 ou superior e RESP3. O modo de duas conexões (`REDIRECT`) não é suportado nesses produtos, e o Jedis não implementa `BCAST`, `OPTIN` nem `OPTOUT`: use o modo padrão, como no lab.
- O Lettuce marca a API `ClientSideCaching` como legada a partir da versão 7.8; a mecânica (tracking + invalidação) continua a mesma, então o que você aprendeu aqui vale para a API que vier depois.
- Cache é para o que muda pouco e lê muito. Contadores e rankings geram uma enxurrada de invalidações: sirva esses por uma conexão sem cache. Dimensione `maxSize` pelo tamanho médio dos valores (`MEMORY USAGE`) e lembre que qualquer desconexão do client zera o cache.

## Desafio

Mude `maxSize` para 1 e leia duas chaves alternadas (`quest:config:motd` e `quest:world:motd`, da [lição 100-02](../fundamentos/02-conectar.md)): veja `getEvictCount()` subir e os hits caírem. Depois chame `cached.getCache().deleteByRedisKey(motd)` e confirme que a leitura seguinte vira miss sem nenhuma escrita no servidor.
