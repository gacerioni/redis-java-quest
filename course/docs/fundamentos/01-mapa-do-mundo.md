---
lesson: 100-01
title: "O mapa do mundo: OSS, Cluster e Redis Cloud"
minutes: 8
kind: lab
---

# O mapa do mundo: OSS, Cluster e Redis Cloud

<p class="lesson-meta">Lição 100-01 · Lab · 8 min</p>

Antes de escrever a primeira linha de código, vale saber onde o seu Redis mora: um processo só, vários shards que dividem as chaves entre si, ou um proxy na frente no Redis Cloud. Essa resposta decide qual classe de client você usa — e errar aqui é o tropeço mais comum do primeiro dia: apontar um client de cluster para um banco que não fala cluster.

## O que o lab faz

- Rodar o doctor (`./quest run 100-01 jedis`, ou só `./quest doctor`) e ler o que o servidor diz sobre si mesmo
- Comparar as três topologias no diagrama: standalone, cluster OSS e Redis Cloud
- Entender por que `RedisClient` (e não `JedisCluster`/`RedisClusterClient`) contra o Redis Cloud
- Medir a latência de ida e volta (RTT) até o seu Redis

## Faça agora

```bash
./quest run 100-01 jedis
./quest run 100-01 lettuce    # opcional: mesmo lab, outro client
./quest verify 100-01
```

## O código

=== "Jedis"

    ```java
    try (RedisClient jedis = Clients.jedis()) {            // RedisClient.create(url), one endpoint
        jedis.ping();                                       // PONG
        String info = jedis.info("server");                 // redis_version, redis_mode, os

        long start = System.nanoTime();
        for (int i = 0; i < 10; i++) jedis.ping();
        double avgMs = (System.nanoTime() - start) / 10 / 1_000_000.0;   // round trip time

        try {
            jedis.sendCommand(Protocol.Command.CLUSTER, "INFO");
            // cluster mode: only here a JedisCluster would be needed to compute slots
        } catch (Exception e) {
            // standalone, or Redis Cloud behind its proxy: the client never sees shards
        }
        jedis.dbSize();                                     // keys in the whole database
    }
    ```

=== "Lettuce"

    ```java
    try (StatefulRedisConnection<String, String> connection = Clients.lettuceConnection()) {
        RedisCommands<String, String> redis = connection.sync();
        redis.ping();
        String info = redis.info("server");

        long start = System.nanoTime();
        for (int i = 0; i < 10; i++) redis.ping();
        double avgMs = (System.nanoTime() - start) / 10 / 1_000_000.0;

        try {
            redis.clusterInfo();
            // cluster mode: only here a RedisClusterClient would be needed
        } catch (Exception e) {
            // standalone, or Redis Cloud behind its proxy: one endpoint
        }
        redis.dbsize();
    }
    ```

A saída do doctor contra o Redis do Docker do curso:

```text
-> Um endpoint só: redis://localhost:6379
   > PING
   PING: PONG
-> Quem está do outro lado?
   > INFO server
   redis_version: 8.6.2
   redis_mode: standalone
-> Latência de ida e volta (10 PINGs)
   RTT médio: 0.30 ms
-> O client sabe de shards? Perguntando ao servidor com CLUSTER INFO
   > CLUSTER INFO
   resposta: ERR This instance has cluster support disabled
   dica: Sem cluster do lado do client: no Redis Cloud o proxy roteia para os shards.
-> Tamanho do banco e conexões abertas
   DBSIZE: 78
   conexões neste banco: 1
   prefixo das suas chaves: quest
```

Como ler: `redis_mode: standalone` diz que, para o client, existe um servidor só (no Redis Cloud a resposta é a mesma). O erro do `CLUSTER INFO` é a prova de que o client não precisa saber de shards. O RTT é o custo de cada ida e volta: cada comando síncrono paga esse preço, e é ele que a lição [100-05](05-pipeline.md) ataca com pipeline. O `DBSIZE` conta o banco inteiro, não só o seu prefixo.

!!! note "Sobre o prefixo"
    Nas páginas do curso as chaves aparecem como `quest:...`. Se você definiu `QUEST_PREFIX` ou conecta com um usuário ACL que não é `default`, o seu prefixo é outro: o doctor imprime qual.

## No Redis Insight

Adicione o banco no Insight com a mesma URL do seu `REDIS_URL` (a lição [100-03](03-redis-insight.md) mostra como abrir o Insight). No topo da tela ficam a versão do Redis, a memória usada e o número de clients conectados: rode o doctor e veja o contador subir por um instante. Em um Redis OSS em cluster, o Insight mostra uma aba com os shards e os slots de cada um; no Redis Cloud e no standalone essa aba não existe, porque para o client só há um endpoint. A lição de conexões bloqueantes ([102-04](../102-eventos/04-conexoes-bloqueantes.md)) vai voltar a esse contador de clients.

??? note "Por dentro"

    ### As três topologias

    ```mermaid
    flowchart LR
        subgraph A["Redis OSS standalone"]
            a1["App Java<br/>RedisClient"] -->|"um endpoint"| a2["redis-server"]
        end
        subgraph B["Redis OSS em cluster"]
            b1["App Java<br/>JedisCluster / RedisClusterClient"]
            b1 -->|"calcula o slot da chave"| b2["shard 1<br/>slots 0 a 5460"]
            b1 --> b3["shard 2<br/>slots 5461 a 10922"]
            b1 --> b4["shard 3<br/>slots 10923 a 16383"]
            b3 -.->|"MOVED: a chave mudou de shard"| b1
        end
        subgraph C["Redis Cloud"]
            c1["App Java<br/>RedisClient"] -->|"um endpoint, uma porta"| c2["proxy"]
            c2 --> c3["shard 1"]
            c2 --> c4["shard 2"]
            c2 --> c5["shard 3"]
        end
    ```

    **Redis OSS standalone.** Um processo, um endereço. É o Redis do `docker compose up` do curso. Réplicas podem existir para alta disponibilidade, mas o client fala com o primário (ou pergunta a um Sentinel quem é o primário). Client: `RedisClient` no Jedis, `RedisClient` no Lettuce.

    **Redis OSS em cluster.** O espaço de chaves é dividido em 16384 slots e cada shard é dono de uma faixa. Quem sabe disso é o client: ele calcula `CRC16(chave) mod 16384`, mantém um mapa slot para shard e manda cada comando direto para o shard certo. Se o mapa envelheceu (um slot migrou), o servidor responde `MOVED 12182 10.0.0.7:6379` e o client atualiza o mapa e tenta de novo. Comandos com várias chaves só funcionam se todas caírem no mesmo slot (por isso existem as hash tags `{...}`). Client: `JedisCluster` no Jedis, `RedisClusterClient` no Lettuce. Eles existem para carregar esse mapa.

    **Redis Cloud.** O banco pode ter vários shards, mas quem roteia é um proxy na frente deles. A aplicação enxerga um endpoint só (`redis-12345.c308.sa-east-1-1.ec2.redns.redis-cloud.com:12345`), manda os mesmos comandos de sempre e o proxy entrega cada um ao shard certo. Resharding, failover e manutenção acontecem atrás do proxy sem o endpoint mudar. Client: o mesmo do standalone.

    !!! warning "Por que não JedisCluster contra o Redis Cloud"
        Um client de cluster começa pedindo `CLUSTER SLOTS` para montar o mapa. O proxy do Redis Cloud responde com erro, porque o banco não expõe cluster para o client, e a conexão falha antes do primeiro comando útil. A exceção é o banco com a opção **OSS Cluster API** habilitada (planos pagos, para throughput muito alto): aí o client fala direto com cada shard e `JedisCluster`/`RedisClusterClient` passam a ser a escolha certa. No plano free e em qualquer banco sem essa opção, use `RedisClient.create(url)`.

    ### Os comandos do doctor

    | Comando | O que faz |
    |---|---|
    | `PING` | Testa a conexão; a resposta `PONG` mede uma ida e volta completa |
    | `INFO server` | Bloco de texto com `redis_version`, `redis_mode` (`standalone` ou `cluster`), sistema operacional e uptime |
    | `CLUSTER INFO` | Só responde em Redis OSS com cluster habilitado; nos outros casos, erro. É o teste de "preciso de client de cluster?" |
    | `DBSIZE` | Total de chaves do banco inteiro, de todos os prefixos |
    | `CLIENT LIST` | Uma linha por conexão aberta; o plano free do Redis Cloud permite 30 |

??? tip "Em produção"

    - Coloque a aplicação na mesma região (e de preferência na mesma zona) do Redis. O RTT é o piso da latência de cada comando: 0,3 ms local, 1 a 2 ms na mesma região, dezenas de ms entre regiões.
    - No Redis Cloud, o endpoint é estável: shards migram, primários trocam de lugar e o endereço que está no seu `.env` continua o mesmo. O client não precisa de lógica de topologia; o que ele precisa é de timeouts e retry bem configurados ([301-01](../301-producao/01-timeouts-pool-retry.md)).
    - Client de cluster só com a OSS Cluster API habilitada no banco. Se um dia migrar de um Redis OSS em cluster para o Redis Cloud, troque `JedisCluster` por `RedisClient` e o resto do código continua igual.

??? tip "Desafio"

    Rode o doctor duas vezes com `REDIS_URL` diferentes: o Redis do Docker (`redis://localhost:6379`) e o seu banco no Redis Cloud. Compare `RTT médio` e `DBSIZE`. O esperado: menos de 1 ms local e alguns milissegundos na nuvem se o banco estiver na sua região; se estiver em outro continente, dezenas de milissegundos, e cada comando síncrono da sua aplicação pagaria esse preço.
