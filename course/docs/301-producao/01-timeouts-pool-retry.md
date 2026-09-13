---
lesson: 301-01
title: "Timeouts, pool e retry"
minutes: 12
kind: lab
---

# Timeouts, pool e retry

<p class="lesson-meta">Lição 301-01 · Lab · 12 min</p>

## Por que isso importa

O servidor do Ember Realm entrou em produção e, numa noite de raid, o link entre a aplicação e o Redis começa a oscilar. Sem timeout, cada thread do servidor de jogo fica presa esperando uma resposta que não vem, o pool esgota e o jogador vê a tela congelar. Timeout explícito, pool limitado e retry com backoff são os três cintos de segurança de qualquer client Redis. No Redis Cloud entra um quarto: o failover troca o IP por trás do mesmo nome, então o cache de DNS da JVM precisa estar desligado.

## O que você vai fazer

- Configurar timeout de conexão e de comando (2 s) no Jedis e no Lettuce
- Limitar o pool do Jedis a 8 conexões, com `PING` nas conexões ociosas
- Refazer um `SET` com retry e backoff exponencial, só para erros de conexão
- Medir uma falha rápida contra `10.255.255.1` com connect timeout de 500 ms
- Desligar o cache de DNS da JVM e entender por quê

## Rode

```bash
./quest run 301-01 jedis
./quest run 301-01 lettuce
./quest check 301-01
```

## O código

=== "Jedis"

    ```java
    DefaultJedisClientConfig config = DefaultJedisClientConfig.builder()
            .connectionTimeoutMillis(2000)   // max time to open the TCP connection
            .socketTimeoutMillis(2000)       // max time waiting for a reply
            .build();

    ConnectionPoolConfig pool = new ConnectionPoolConfig();
    pool.setMaxTotal(8);
    pool.setMaxWait(Duration.ofSeconds(1));   // fail instead of queueing forever
    pool.setTestWhileIdle(true);              // PING idle connections
    pool.setTimeBetweenEvictionRuns(Duration.ofSeconds(5));

    try (RedisClient jedis = RedisClient.builder()
            .hostAndPort(new HostAndPort(host, port))
            .clientConfig(config)
            .poolConfig(pool)
            .build()) {
        withRetry(4, 200, () -> jedis.set(bossKey, when));   // retries JedisConnectionException only
    }

    // fast fail: an address that never answers the SYN
    DefaultJedisClientConfig ghostConfig = DefaultJedisClientConfig.builder()
            .connectionTimeoutMillis(500).socketTimeoutMillis(500).build();
    try (RedisClient phantom = RedisClient.builder()
            .hostAndPort(new HostAndPort("10.255.255.1", 6379))
            .clientConfig(ghostConfig).build()) {
        phantom.ping();   // JedisConnectionException after about 500 ms
    }

    Security.setProperty("networkaddress.cache.ttl", "0");
    Security.setProperty("networkaddress.cache.negative.ttl", "0");
    ```

=== "Lettuce"

    ```java
    RedisURI uri = RedisURI.create(url);
    uri.setTimeout(Duration.ofSeconds(2));                    // command timeout (sync API)

    SocketOptions socket = SocketOptions.builder()
            .connectTimeout(Duration.ofSeconds(2))
            .keepAlive(SocketOptions.KeepAliveOptions.builder()
                    .enable().idle(Duration.ofSeconds(5)).interval(Duration.ofSeconds(5)).count(3).build())
            .build();                                         // tcpUserTimeout(...) needs netty epoll (Linux)

    ClientOptions options = ClientOptions.builder()
            .autoReconnect(true)                              // default: at-least-once
            .socketOptions(socket)
            .timeoutOptions(TimeoutOptions.enabled(Duration.ofSeconds(2)))
            .replayFilter(cmd -> "INCR".equalsIgnoreCase(cmd.getType().toString()))   // never replay INCR
            .build();

    RedisClient client = RedisClient.create(uri);
    client.setOptions(options);
    try (StatefulRedisConnection<String, String> connection = client.connect()) {
        RedisCommands<String, String> redis = connection.sync();
        withRetry(4, 200, () -> redis.set(bossKey, when));   // RedisConnectionException, RedisCommandTimeoutException
    }

    // fast fail with a 500 ms connect timeout
    RedisClient phantom = RedisClient.create(RedisURI.builder().redis("10.255.255.1", 6379).build());
    phantom.setOptions(ClientOptions.builder()
            .socketOptions(SocketOptions.builder().connectTimeout(Duration.ofMillis(500)).build())
            .build());
    phantom.connect();                                        // RedisConnectionException after about 500 ms

    Security.setProperty("networkaddress.cache.ttl", "0");
    ```

## O que olhar no Redis Insight

No Browser, filtre pelo seu prefixo: `quest:boss:spawn` é a STRING com o instante do próximo spawn, gravada pelo `SET` protegido por retry, e `quest:ops:clients` é o HASH que diz qual client rodou e quando. No Profiler, os dois labs mostram o `PING` inicial e o `SET`; do datacenter fantasma não chega nada, porque a falha acontece antes de existir conexão. No Workbench, `CLIENT LIST` durante o lab mostra as conexões do pool do Jedis (no máximo 8) ou a conexão única do Lettuce.

## Por dentro

| Comando | O que faz |
|---|---|
| `PING` | Prova que a conexão está viva; é o que o pool do Jedis manda nas conexões ociosas com `testWhileIdle` |
| `SET quest:boss:spawn <instante>` | A escrita protegida pelo retry: idempotente, pode repetir sem efeito colateral |
| `HSET quest:ops:clients jedis <instante>` | Registra qual client rodou, para o `check` |
| `CLIENT LIST` | No Workbench, mostra quantas conexões cada client abriu e há quanto tempo estão paradas |

| Ajuste | Jedis | Lettuce |
|---|---|---|
| Timeout de conexão | `connectionTimeoutMillis` | `SocketOptions.connectTimeout` |
| Timeout de comando | `socketTimeoutMillis` (e `blockingSocketTimeoutMillis` para `BLPOP` e afins) | `RedisURI.setTimeout` (sync) e `TimeoutOptions` (async e reactive) |
| Conexão morta sem tráfego | `testWhileIdle` + `timeBetweenEvictionRuns` | `KeepAliveOptions`; `TcpUserTimeoutOptions` só com netty epoll no Linux |
| Reconexão | cada comando pega outra conexão do pool | `autoReconnect(true)`: comandos na fila são reenviados (at-least-once) |
| O que não repetir | não envolva comandos não idempotentes em retry | `replayFilter`: quem casa com o predicado fica fora do reenvio |

## Em produção

- Dimensione o pool pela concorrência real mais os comandos bloqueantes ([lição 102-04](../102-eventos/04-conexoes-bloqueantes.md)). O plano free aceita 30 conexões, então `maxTotal` 8 por instância da aplicação já é generoso. `maxWait` curto: melhor um erro em 1 s do que uma fila que só cresce.
- Retry só para erro de conexão e timeout, com backoff exponencial e um teto de tentativas. `INCR` e `LPUSH` não são idempotentes: no Jedis, não repita; no Lettuce, marque no `replayFilter`.
- `networkaddress.cache.ttl=0` antes da primeira resolução de nome (ou `-Dsun.net.inetaddr.ttl=0` na JVM). Sem isso, um failover do Redis Cloud deixa sua aplicação batendo no IP antigo até o cache expirar.

## Desafio

Troque o connect timeout do fantasma para 3000 ms e rode de novo: a primeira falha deve levar cerca de 3 s. Depois aponte `REDIS_URL` para uma porta fechada, como `redis://localhost:6399`, e compare: `connection refused` chega na hora, porque o sistema operacional responde; o timeout só existe quando a rede engole o pacote.
