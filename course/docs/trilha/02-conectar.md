---
lesson: 100-02
title: "Conectar e o primeiro SET/GET"
minutes: 10
kind: lab
no_steps: true
next_url: trilha/03-ttl/
next_title: "Chaves que expiram sozinhas (TTL)"
state_text: "Rodou o lab e o verify passou? Registre seu estudo."
---

# Conectar e o primeiro SET/GET

<p class="lesson-meta">Ao vivo · 8-18 min · <a href="../../fundamentos/02-conectar/">versão completa</a></p>

Java tem dois clients open source oficiais, mantidos pela Redis: **Jedis** (síncrono, direto, com pool de conexões) e **Lettuce** (sobre netty, uma conexão compartilhada, APIs sync/async/reactive). Os dois usam a mesma URL e mandam os mesmos comandos. Hoje você começa com Jedis; compararemos o Lettuce na demonstração de Hash/ranking. Antes de conectar, confira a [tabela de topologias](index.md#qual-client-para-qual-redis).

## Faça agora

```bash
./quest run 100-02 jedis
./quest verify 100-02
```

`run` executa o lab pronto (`l100_02/JedisLab.java`). `verify` olha o seu Redis e confirma o que aconteceu; ele confere o estado no banco, não o seu código.

## O que aconteceu

O lab fez três coisas: gravou a mensagem do dia, leu de volta e deixou uma prova de execução.

```java
try (RedisClient jedis = Clients.jedis()) {        // cria o client a partir da URL do .env
    jedis.set("quest:world:motd", "Bem-vindo ao Ember Realm.");   // SET → "OK"
    String msg = jedis.get("quest:world:motd");                   // GET → a mensagem
    jedis.set("quest:hello:jedis", "ok");                         // prova de que o Jedis rodou
}   // ao encerrar este programa, close() fecha o client e seu pool
```

No `verify`, cada linha verde é uma coisa que ele achou no seu Redis: a mensagem, a prova do client, o marcador da lição. Linha vermelha vem com a dica do que fazer. A comparação com outro client é opcional.

## Sua primeira alteração

Abra `src/main/java/com/emberrealm/quest/lessons/l100_02/JedisLab.java` e acrescente seu nome ao fim da mensagem (mantenha o começo `Bem-vindo ao Ember Realm`). Rode o lab de novo e veja o novo valor no terminal e no Redis Insight. O wrapper recompila o código alterado.

**Primeira vitória: você mudou Java e viu o dado mudar no Redis.** Agora carregue os dados dos próximos exemplos:

```bash
./quest seed
```

O seed cria perfis, itens e ranking sob o seu prefixo; não é requisito para esse primeiro SET/GET.

## A URL, peça por peça

```text
redis://default:S3nh4Forte@redis-12345.c308.sa-east-1-1.ec2.redns.redis-cloud.com:12345
```

| Parte | Observação |
|---|---|
| `redis://` | Sem TLS. `rediss://` (dois s) liga TLS |
| `default` | Usuário ACL; no free tier é `default`; com usuário próprio, o nome vira o prefixo das chaves |
| `S3nh4Forte` | Fica no `.env`, nunca no código |
| host + porta | No modo padrão do Cloud/Software, o proxy roteia até os shards; com OSS Cluster API habilitada, use o client de cluster |

??? note "E o Lettuce?"

    ```java
    // Um RedisClient por aplicação (ele é dono das threads do netty); connect() por conexão
    try (StatefulRedisConnection<String, String> connection = Clients.lettuceConnection()) {
        RedisCommands<String, String> sync = connection.sync();
        sync.set("quest:world:motd", "Bem-vindo ao Ember Realm.");
        sync.get("quest:world:motd");

        RedisAsyncCommands<String, String> async = connection.async();  // mesma conexão
        async.get("quest:world:motd").get();                            // um RedisFuture
    }
    ```

    - `sync()`, `async()` e `reactive()` são três visões da **mesma** conexão, não três conexões.
    - Uma conexão Lettuce atende várias threads ao mesmo tempo; no Jedis, cada comando em voo pega uma conexão do pool.
    - Rode `./quest run 100-02 lettuce` e compare a saída: mesmos comandos no fio.

??? tip "Ver no Redis Insight"
    No **Browser**, filtre por `quest:*`: aparecem `quest:world:motd` e `quest:hello:jedis`. No **Profiler**, rode o lab de novo e veja o client se apresentar antes do primeiro `SET` (`CLIENT SETINFO` e o `HELLO 3` negociando RESP3).

??? tip "Para ir além"
    - Regra de produção: **um client por aplicação**, nos dois casos. Criar client por requisição abre e fecha conexões o tempo todo.
    - Detalhes de pool, RESP2/RESP3 e como escolher entre os dois: [lição completa 100-02](../fundamentos/02-conectar.md) e [Jedis ou Lettuce?](../referencia/jedis-vs-lettuce.md)
