---
lesson: 102-04
title: "Conexões bloqueantes: 31 jogadores na fila"
minutes: 12
kind: lab
---

# Conexões bloqueantes: 31 jogadores na fila

<p class="lesson-meta">Lição 102-04 · Lab · 12 min</p>

`BRPOP`, `XREADGROUP BLOCK` e `SUBSCRIBE` parecem gratuitos; o cliente espera e o Redis não gasta CPU. O custo está na conexão: enquanto espera, ela não serve para mais nada. No Jedis é uma conexão do pool a menos; no Lettuce, um `BRPOP` na conexão compartilhada segura todos os comandos atrás dele. No plano free do Redis Cloud o limite é 30 conexões: o 31º cliente recebe `ERR max number of clients reached`. Esta lição mostra tudo isso acontecendo e como dimensionar.

## O que o lab faz

- Colocar 5 jogadores (`QUEST_WAITERS`) em `BRPOP {p}:queue:raid 3`, cada um na sua conexão, e ver `blocked_clients` e `CLIENT LIST` (`flags=b cmd=brpop`)
- Jedis: esgotar um pool do tamanho exato dos jogadores e ver um `PING` desistir em 500 ms
- Lettuce: medir um `PING` preso atrás de um `BLPOP` na conexão compartilhada (cerca de 2000 ms) contra o mesmo `BLPOP` em conexão dedicada (menos de 1 ms)
- Abrir a dungeon com um `RPUSH` e ver todos acordarem
- Repetir a observação com `XREADGROUP BLOCK` e `SUBSCRIBE`, e fechar tudo

## Faça agora

```bash
./quest run 102-04 jedis
./quest run 102-04 lettuce    # opcional: mesmo lab, outro client
./quest verify 102-04
```

## O código

=== "Jedis"

    ```java
    // Um pool só para os jogadores, do tamanho deles: cada BRPOP ocupa uma conexão
    ConnectionPoolConfig poolConfig = new ConnectionPoolConfig();
    poolConfig.setMaxTotal(waiters);
    poolConfig.setMaxWait(Duration.ofMillis(500));
    URI uri = URI.create(Env.redisUrl());
    DefaultJedisClientConfig config = DefaultJedisClientConfig.builder()
            .user(JedisURIHelper.getUser(uri)).password(JedisURIHelper.getPassword(uri))
            .ssl(JedisURIHelper.isRedisSSLScheme(uri))
            .clientName(prefix + "-waiter")                        // aparece no CLIENT LIST
            .build();
    try (RedisClient waiterPool = RedisClient.builder()
            .hostAndPort(JedisURIHelper.getHostAndPort(uri))
            .clientConfig(config).poolConfig(poolConfig).build()) {
        for (int n = 1; n <= waiters; n++) {
            executor.submit(() -> waiterPool.brpop(3, queue));      // bloqueia uma thread e uma conexão
        }
        waiterPool.ping();          // JedisException: Could not get a resource from the pool (500 ms)
        jedis.ping();               // o client principal tem outro pool: PONG em 1 ms
        jedis.rpush(queue, slots);  // um elemento por jogador: todos acordam
    }                               // close() fecha as conexões do pool
    ```

=== "Lettuce"

    ```java
    // Cada jogador com seu connect(): a conexão compartilhada é multiplexada e não pode bloquear
    executor.submit(() -> {
        try (StatefulRedisConnection<String, String> own = Clients.lettuce().connect()) {
            own.setTimeout(Duration.ofSeconds(5)); // longer than the 3-second server wait
            own.sync().clientSetname(prefix + "-waiter");
            return own.sync().brpop(3, queue);                      // KeyValue<String, String>
        }
    });

    // O estrago de bloquear a conexão compartilhada: o PING espera o BLPOP inteiro
    RedisAsyncCommands<String, String> async = shared.async();
    RedisFuture<KeyValue<String, String>> stuck = async.blpop(2, emptyQueue);
    RedisFuture<String> ping = async.ping();
    ping.get(5, TimeUnit.SECONDS);                                  // cerca de 2000 ms

    // Em conexão dedicada, o resto da aplicação nem percebe
    try (StatefulRedisConnection<String, String> dedicated = Clients.lettuce().connect()) {
        dedicated.async().blpop(2, emptyQueue);
        shared.sync().ping();                                       // menos de 1 ms
        shared.sync().rpush(emptyQueue, "sentinela");               // acorda o BLPOP e libera a conexão
    }
    ```

## No Redis Insight

Com o Insight aberto no mesmo banco enquanto a lição roda: no Workbench, `CLIENT LIST` mostra as linhas `name={p}-waiter flags=b cmd=brpop`, e `INFO clients` mostra `connected_clients`, `blocked_clients` e `pubsub_clients` subindo e voltando ao normal no fim. Em Overview, o gráfico de conexões dá um salto a cada execução. Se rodar com 31 contra o Docker local, o próprio Insight pode perder a conexão: é o efeito real do limite.

??? note "Por dentro"

    | Comando | O que faz |
    |---|---|
    | `BRPOP key timeout` | Espera até `timeout` segundos por um elemento; devolve `[key, valor]` ou nil |
    | `RPUSH key v1 v2 ...` | Cada elemento acorda um cliente bloqueado, na ordem em que bloquearam |
    | `XREADGROUP ... BLOCK ms ...` | Espera por entradas novas para o grupo; a conexão fica bloqueada |
    | `SUBSCRIBE canal` | A conexão vira assinante até o `UNSUBSCRIBE` |
    | `CLIENT SETNAME nome` | Nomeia a conexão para você achá-la no `CLIENT LIST` (no Jedis, `clientName` na config) |
    | `CLIENT LIST` | Uma linha por conexão: `flags=b` bloqueada, `flags=P` Pub/Sub, `cmd=` último comando |
    | `INFO clients` | `connected_clients`, `blocked_clients`, `pubsub_clients` e `maxclients` |

    ### O que acontece na conexão

    ```mermaid
    sequenceDiagram
        participant T1 as Thread 1
        participant C as Conexão TCP
        participant R as Redis
        participant T2 as Thread 2
        T1->>C: BRPOP {p}:queue:raid 3
        C->>R: BRPOP
        Note over R: fila vazia: cliente bloqueado (flags=b)
        T2->>C: PING (mesma conexão)
        Note over C: espera na fila, atrás do BRPOP
        R-->>R: RPUSH chega (ou os 3 s passam)
        R-->>C: resposta do BRPOP
        R-->>C: PONG (só agora)
    ```

    Uma conexão é uma fila ordenada: o Redis responde na ordem em que os comandos chegaram, e um comando bloqueante segura a fila inteira. Por isso comando bloqueante pede conexão própria, seja uma emprestada do pool (Jedis) ou um `connect()` a mais (Lettuce).

    ### Jedis e Lettuce lado a lado

    | | Jedis (`RedisClient` com pool) | Lettuce (`RedisClient` sobre netty) |
    |---|---|---|
    | Modelo | Uma conexão por comando em voo, emprestada do pool | Uma conexão compartilhada e multiplexada para todas as threads |
    | Comando bloqueante | Ocupa uma conexão do pool até voltar; as outras threads usam as demais | Na conexão compartilhada trava tudo que vier atrás; use um `connect()` dedicado |
    | Sintoma de errar | `Could not get a resource from the pool` depois do `maxWait` | Latência de tudo sobe até o tamanho do `BLOCK`; `RedisCommandTimeoutException` |
    | Como dimensionar | `maxTotal` = concorrência normal + bloqueantes simultâneos | Conexões dedicadas = bloqueantes simultâneos, e cada uma conta no limite |
    | Pub/Sub | `subscribe` bloqueia a thread e segura uma conexão do pool | `connectPubSub()` abre uma conexão separada |
    | Conta de conexões por instância | até `maxTotal` | 1 compartilhada + dedicadas + Pub/Sub |

    ### O experimento dos 31 jogadores

    O plano free do Redis Cloud aceita 30 conexões, contando aplicação, Redis Insight e `redis-cli`. O `docker-compose.yml` do curso imita isso com `--maxclients 30`. Contra o Docker local:

    ```bash
    QUEST_WAITERS=31 ./quest run 102-04 jedis
    QUEST_WAITERS=31 ./quest run 102-04 lettuce
    ```

    Alguns jogadores voltam com `ERR max number of clients reached`: o servidor aceita o TCP, responde o erro e fecha a conexão. Não é exatamente um: o client principal da lição já usa uma conexão, e o Insight, se estiver aberto, usa outras. A lição conta quantos foram barrados e limpa as vagas que sobraram sem jogador. Em produção a conta é `instâncias x (pool + dedicadas + Pub/Sub)`, e ela precisa caber no limite do plano com folga para o Insight e para o deploy em que a versão velha e a nova ficam vivas ao mesmo tempo.

    !!! warning "Só contra o Docker local"
        Com 31 jogadores contra o Redis Cloud free você esgota as conexões do seu próprio banco: o Insight cai e qualquer outra aplicação conectada também. O experimento existe para ver o erro em um lugar seguro.

??? tip "Em produção"

    - Use **conexão dedicada e timeout de client maior que o prazo de espera no servidor**, com margem para a rede. No Jedis, configure o timeout de socket para comandos bloqueantes separadamente; não o trate como o timeout normal de comandos. No Lettuce, a conexão dedicada pode usar `setTimeout(Duration.ofSeconds(5))` para um `BRPOP 3`. Uma conexão separada com timeout curto demais ainda falha antes do bloqueio terminar. Prefira esperas finitas em loop para facilitar reconexão e encerramento.
    - Ajuste o lote e o número de workers pelo tempo de processamento. `XREADGROUP COUNT 50 BLOCK 2000` pode reduzir viagens e conexões comparado a lotes menores; não garante a mesma vazão de vários workers quando o processamento é o gargalo.
    - Monitore `blocked_clients` e `connected_clients` contra `maxclients` (Redis Insight, `INFO clients`, métricas do Redis Cloud). `rejected_connections` em `INFO stats` acusa que o limite já foi batido.

??? tip "Desafio"

    Rode `QUEST_WAITERS=8 ./quest run 102-04 jedis` e depois mude `poolConfig.setMaxTotal(waiters)` no lab para `waiters + 1`: o `PING` pelo pool dos jogadores passa a responder na hora, porque sobrou uma conexão livre. Essa conexão a mais é a folga que um serviço de produção precisa para não travar por causa de quem está esperando.
