---
lesson: 100-02
title: "Conectar com Jedis e Lettuce"
minutes: 8
kind: lab
---

# Conectar com Jedis e Lettuce

<p class="lesson-meta">Lição 100-02 · Lab · 8 min</p>

Em Java existem dois clients oficiais para Redis: o Jedis, síncrono e direto, com um pool de conexões por baixo; e o Lettuce, construído sobre netty, com uma conexão compartilhada e três APIs (sync, async, reactive). Os dois falam com a mesma URL e mandam os mesmos comandos — e aparecem lado a lado em todas as lições. Aqui você conecta com os dois, grava a mensagem do dia e fecha tudo direito.

## O que o lab faz

- Criar um `RedisClient` do Jedis a partir de uma URL e rodar `SET` e `GET`
- Criar o `RedisClient` do Lettuce, abrir uma conexão e usar sync e async na mesma conexão
- Ler a anatomia da URL `redis://usuario:senha@host:porta`
- Fechar client e conexão com try-with-resources

## Faça agora

```bash
./quest run 100-02 jedis
./quest run 100-02 lettuce    # opcional: mesmo lab, outro client
./quest verify 100-02
```

## O código

=== "Jedis"

    ```java
    // RedisClient (Jedis 7.2+) is the entry point: it replaces JedisPooled and UnifiedJedis
    // and pools connections for you. Clients.jedis() is RedisClient.create(Env.redisUrl()).
    try (RedisClient jedis = Clients.jedis()) {
        String motd = ctx.k("world", "motd");                        // quest:world:motd
        String reply = jedis.set(motd, "Bem-vindo ao Ember Realm, aventureiro.");   // "OK"
        jedis.get(motd);                                              // the message back

        jedis.set(ctx.k("hello", "jedis"), "ok");                    // proof this client ran
    }   // close() returns every pooled connection
    ```

=== "Lettuce"

    ```java
    // One io.lettuce.core.RedisClient per application (it owns the netty threads);
    // connect() per connection. Clients.lettuceConnection() does client.connect().
    try (StatefulRedisConnection<String, String> connection = Clients.lettuceConnection()) {
        RedisCommands<String, String> sync = connection.sync();
        String motd = ctx.k("world", "motd");
        sync.set(motd, "Bem-vindo ao Ember Realm, aventureiro.");   // "OK"
        sync.get(motd);

        RedisAsyncCommands<String, String> async = connection.async();   // same connection
        async.get(motd).get();                                       // a RedisFuture, awaited here

        sync.set(ctx.k("hello", "lettuce"), "ok");
    }   // closes the connection; the client lives until the app shuts it down
    ```

### A URL

```text
redis://default:S3nh4Forte@redis-12345.c308.sa-east-1-1.ec2.redns.redis-cloud.com:12345
```

| Parte | Exemplo | Observação |
|---|---|---|
| esquema | `redis://` | Sem TLS. `rediss://` (dois s) liga TLS, assunto da lição [301-03](../301-producao/03-tls.md) |
| usuário | `default` | Usuário ACL. No Redis Cloud free é `default`; com um usuário próprio, o curso usa o nome dele como prefixo das chaves |
| senha | `S3nh4Forte` | Vem do console do Redis Cloud. Nunca no código: fica no `.env`, que não vai para o git |
| host | `redis-12345....redis-cloud.com` | O endpoint único da lição anterior |
| porta | `12345` | No Redis Cloud raramente é 6379; no Docker do curso, é |

Os dois clients aceitam essa URL como está: `RedisClient.create(url)` no Jedis e `RedisClient.create(RedisURI.create(url))` no Lettuce. O `Env.redacted(url)` que o lab imprime troca a senha por `****`, para você poder colar a saída em qualquer lugar.

### Jedis e Lettuce, cada um do seu jeito

**Jedis.** `RedisClient` é o ponto de entrada desde o Jedis 7.2 e substitui `JedisPooled` e `UnifiedJedis` (ainda existem, mas o caminho novo é este). Ele carrega um pool: cada chamada pega uma conexão emprestada, manda o comando, espera a resposta e devolve a conexão. É thread-safe, síncrono e simples de ler. Por padrão fala RESP2; RESP3 é opcional (`DefaultJedisClientConfig.builder().resp3()`), e a lição [301-02](../301-producao/02-client-side-caching.md) precisa dele. Timeouts e tamanho do pool ficam em `RedisClient.builder()`, na lição [301-01](../301-producao/01-timeouts-pool-retry.md).

**Lettuce.** `RedisClient` é caro (é dono das threads do netty): crie um por aplicação e faça `shutdown()` no fim. Conexões (`connect()`) são baratas e uma só atende muitas threads ao mesmo tempo: os comandos são escritos no socket na ordem em que chegam e as respostas voltam na mesma ordem. `sync()`, `async()` e `reactive()` são três visões da mesma conexão, não três conexões. Por padrão o Lettuce negocia RESP3 com `HELLO 3` e cai para RESP2 se o servidor não souber. A exceção da conexão compartilhada são os comandos bloqueantes e as transações, que precisam de conexão própria: lição [102-04](../102-eventos/04-conexoes-bloqueantes.md).

!!! tip "try-with-resources"
    `RedisClient` do Jedis e `StatefulRedisConnection` do Lettuce são `AutoCloseable`. O bloco `try (...) { }` garante que o pool ou a conexão sejam fechados mesmo se uma exceção passar no meio. Conexão esquecida aberta é a forma mais comum de bater no limite de 30 clients do plano free.

## No Redis Insight

No **Browser**, filtre por `quest:*`: aparecem `quest:world:motd` com a mensagem do dia e as duas provas de execução, `quest:hello:jedis` e `quest:hello:lettuce`. No **Profiler**, rode o lab e veja o client se apresentar antes do primeiro `SET`: `CLIENT SETINFO` com o nome e a versão da biblioteca e, no Lettuce, o `HELLO 3` negociando RESP3. No topo da tela, o contador de clients conectados sobe enquanto o lab roda e volta quando ele fecha tudo.

??? note "Por dentro"

    | Comando | O que faz |
    |---|---|
    | `SET chave valor` | Grava uma STRING e responde `OK` |
    | `GET chave` | Lê a STRING; `nil` se a chave não existir (`null` em Java) |
    | `HELLO 3` | Enviado pelo Lettuce ao conectar: negocia a versão do protocolo (RESP3) e pode autenticar no mesmo passo |
    | `AUTH usuario senha` | O que o Jedis envia ao abrir cada conexão quando a URL tem credenciais; o Lettuce faz isso dentro do `HELLO` |
    | `CLIENT SETINFO LIB-NAME ...` | O client se identifica; aparece no `CLIENT LIST` e ajuda a saber quem está conectado em produção |

??? tip "Em produção"

    - Um client por aplicação, nos dois casos. Criar `RedisClient` por requisição abre e fecha conexões (e, no Lettuce, threads) o tempo todo; é o erro clássico que derruba o limite de conexões.
    - Senha fora do código: `.env` em desenvolvimento, secret manager em produção. A URL redigida (`****`) é a que vai para log.
    - Escolha um client por serviço e fique com ele. Jedis quando a base de código é síncrona e simples; Lettuce quando você já vive de `CompletableFuture`, Reactor ou Spring WebFlux. A página [Jedis ou Lettuce?](../referencia/jedis-vs-lettuce.md) compara os dois em detalhe.

??? tip "Desafio"

    No `LettuceLab`, troque o `GET` assíncrono pela terceira API: `connection.reactive().get(motd).block()`. O valor que volta é o mesmo, sem abrir nenhuma conexão nova. Depois abra o Profiler e confirme: um único `GET` no fio, seja qual for a API que você escolheu do lado Java.
