---
lesson: 301-04
title: "Smart client handoffs"
minutes: 8
kind: lab
---

# Smart client handoffs

<p class="lesson-meta">Lição 301-04 · Lab · 8 min</p>

O Redis Cloud atualiza versão e move nós por baixo do seu banco sem pedir licença. Smart client handoffs (SCH) é o servidor avisando o client, segundos antes, que um shard vai se mover: o client relaxa o timeout durante a manutenção e reconecta no endpoint novo antes do corte. O Lettuce 7 faz isso sozinho em RESP3; o Jedis 8.0.1 ainda não — a lição mostra o que ele oferece no lugar.

## O que o lab faz

- Configurar o Lettuce com `MaintNotificationsConfig.enabled()` e timeouts relaxados de 10 s
- Mandar `CLIENT MAINT_NOTIFICATIONS ON` à mão e ler a resposta do servidor (o Redis local não conhece o recurso)
- Rodar 20 `INCR` assíncronos em `quest:maint:ticks` com a configuração de produção
- No Jedis, rodar os mesmos 20 comandos com retry e health check do pool

## Faça agora

```bash
./quest run 301-04 jedis
./quest run 301-04 lettuce    # opcional: mesmo lab, outro client
./quest verify 301-04
```

## O código

=== "Jedis"

    ```java
    // Jedis 8.0.1 does not negotiate SCH; ask the server about it and move on
    try {
        jedis.sendCommand(Protocol.Command.CLIENT, "MAINT_NOTIFICATIONS", "OFF");
    } catch (JedisDataException e) {
        // Redis Open Source: ERR unknown subcommand 'MAINT_NOTIFICATIONS'
    }

    ConnectionPoolConfig pool = new ConnectionPoolConfig();
    pool.setMaxTotal(8);
    pool.setTestWhileIdle(true);                       // a connection killed by maintenance leaves the pool
    pool.setTimeBetweenEvictionRuns(Duration.ofSeconds(5));

    for (int i = 0; i < 20; i++) {
        withRetry(3, 200, () -> jedis.incr(ticksKey)); // JedisConnectionException: retry with backoff
    }
    ```

=== "Lettuce"

    ```java
    ClientOptions options = ClientOptions.builder()
            .protocolVersion(ProtocolVersion.RESP3)                        // SCH needs RESP3 (the default)
            .maintNotificationsConfig(MaintNotificationsConfig.enabled())  // also the default since 7.0
            .timeoutOptions(TimeoutOptions.builder()
                    .timeoutCommands(true)
                    .fixedTimeout(Duration.ofSeconds(2))                       // normal command timeout
                    .relaxedTimeoutsDuringMaintenance(Duration.ofSeconds(10))  // while a shard moves
                    .build())
            .build();

    RedisClient client = RedisClient.create(uri);
    client.setOptions(options);
    try (StatefulRedisConnection<String, String> connection = client.connect()) {
        connection.addListener(message -> {                            // MOVING, MIGRATING, MIGRATED...
            System.out.println("maintenance: " + message.getType());
        });

        RedisAsyncCommands<String, String> async = connection.async(); // relaxed timeouts apply to async and reactive
        RedisFuture<Long> last = null;
        for (int i = 0; i < 20; i++) last = async.incr(ticksKey);
        last.get(2, TimeUnit.SECONDS);                                 // same connection, same order
    }
    ```

## No Redis Insight

No Browser, `quest:maint:ticks` termina em 20 depois de cada client (o lab zera a chave antes de começar) e `quest:maint:clients` registra quem rodou. No Profiler, as duas versões mandam 20 `INCR`; a do Lettuce chega em rajada, porque a API assíncrona não espera resposta entre um comando e outro. No Workbench, `CLIENT MAINT_NOTIFICATIONS ON` devolve o mesmo erro que o lab mostra: o Redis Open Source local não conhece o subcomando.

??? note "Por dentro"

    | Comando | O que faz |
    |---|---|
    | `HELLO 3` | RESP3 no handshake, pré-requisito do SCH: os avisos chegam como mensagens push |
    | `CLIENT MAINT_NOTIFICATIONS ON` | Pede os avisos de manutenção; o Lettuce manda sozinho ao conectar e ignora a recusa de servidores sem SCH |
    | `MIGRATING` / `MIGRATED` (push) | O shard vai se mover e terminou de se mover: o client relaxa e depois restaura o timeout |
    | `MOVING` (push) | Traz o endpoint novo: o client conecta lá, transfere a fila e fecha a conexão antiga (pre-handoff) |
    | `FAILING_OVER` / `FAILED_OVER` (push) | Uma réplica está assumindo: mesma dança dos timeouts |
    | `INCR quest:maint:ticks` | O comando de negócio que não pode pular durante a manutenção |

??? tip "Em produção"

    - SCH é recurso do Redis Cloud e do Redis Software. No Redis Cloud vem ligado por padrão, com timeouts relaxados e pre-handoffs; sobre AWS PrivateLink e Google Cloud Private Service Connect só os timeouts relaxados valem (sem pre-handoff), e aí configure `endpointType(EndpointType.NONE)`. No Redis Software (8.0.2+) ative `client_maint_notifications` pela API REST do cluster.
    - Precisa de RESP3 e vale para conexões normais: conexões bloqueantes (`BLPOP`, `XREAD BLOCK`) e pub/sub não recebem handoff e continuam dependendo do `autoReconnect` ([lição 102-04](../102-eventos/04-conexoes-bloqueantes.md)). Os timeouts relaxados valem só nas APIs async e reactive do Lettuce. Um client configurado para failover geográfico ([lição 301-05](05-active-active.md)) desliga o SCH por enquanto.
    - Lettuce 7.0+ suporta SCH; Jedis 8.0.1 ainda não. Com Jedis, fique com timeouts curtos, `testWhileIdle` e retry para erros de conexão: a manutenção derruba a conexão, o pool descarta a quebrada e o retry refaz o comando.

??? tip "Desafio"

    Aponte `REDIS_URL` para um banco do Redis Cloud em versão recente e rode a versão Lettuce: `CLIENT MAINT_NOTIFICATIONS ON` responde `OK` e o marcador fica com `sch=on`. Se o banco passar por uma manutenção enquanto você roda um loop mais longo, o listener imprime `MIGRATING` e `MOVING` no console.
