---
lesson: 301-03
title: "TLS"
minutes: 8
kind: lab
---

# TLS

<p class="lesson-meta">Lição 301-03 · Lab · 8 min</p>

## Por que isso importa

Senhas de jogador, tokens de sessão e o ouro do reino cruzam a rede a cada comando. Em produção o Redis fica atrás de TLS: a URL vira `rediss://`, o client valida o certificado do servidor e o resto do código não muda. Esta é a única lição do curso que depende de um plano pago: o plano free do Redis Cloud (30 MB) não oferece TLS. Sem `REDIS_TLS_URL`, o lab explica o que você faria e se marca como pulado.

## O que você vai fazer

- Entender o que muda no `rediss://`: certificado, CA e verificação do servidor
- Ver onde o Redis Cloud entrega o `redis_ca.pem` e por que a JVM já confia nele
- Com `REDIS_TLS_URL` definida: conectar, `PING`, `SET` e `GET` em `quest:tls:probe` nos dois clients
- Opcional: apontar `REDIS_TLS_CA_PEM` para uma CA própria e ver os dois clients confiarem nela

## Rode

```bash
./quest run 301-03 jedis
./quest run 301-03 lettuce
./quest check 301-03
```

Com um banco pago, coloque no `.env` antes de rodar:

```bash
REDIS_TLS_URL=rediss://default:SENHA@host:porta
# opcional, só se o servidor usar uma CA fora do truststore da JVM
REDIS_TLS_CA_PEM=/caminho/redis_ca.pem
```

## O código

=== "Jedis"

    ```java
    // the JVM truststore already trusts the GlobalSign root used by Redis Cloud
    RedisClient jedis = RedisClient.create("rediss://default:SENHA@host:porta");

    // custom CA: build a truststore in memory from the PEM bundle
    SSLSocketFactory factory = socketFactoryFromPem(Path.of(caPem));   // CertificateFactory + KeyStore + TrustManagerFactory
    DefaultJedisClientConfig config = DefaultJedisClientConfig.builder()
            .user(user).password(password)
            .ssl(true)
            .sslSocketFactory(factory)
            .build();
    RedisClient jedis = RedisClient.builder()
            .hostAndPort(new HostAndPort(host, port))
            .clientConfig(config)
            .build();

    jedis.ping();                                  // PONG over TLS
    jedis.set(probe, Instant.now().toString());
    jedis.get(probe);
    ```

=== "Lettuce"

    ```java
    RedisURI uri = RedisURI.create("rediss://default:SENHA@host:porta");   // ssl=true, verifyPeer=true
    uri.setTimeout(Duration.ofSeconds(5));

    ClientOptions.Builder options = ClientOptions.builder();
    if (caPem != null) {
        options.sslOptions(SslOptions.builder().trustManager(new File(caPem)).build());   // PEM accepted directly
    }

    RedisClient client = RedisClient.create(uri);
    client.setOptions(options.build());
    try (StatefulRedisConnection<String, String> connection = client.connect()) {
        RedisCommands<String, String> redis = connection.sync();
        redis.ping();                              // PONG over TLS
        redis.set(probe, Instant.now().toString());
        redis.get(probe);
    }
    ```

## O que olhar no Redis Insight

Adicione o banco TLS no Insight marcando "Use TLS" e, se a CA for própria, colando o conteúdo do `redis_ca.pem` em "CA Certificate". No Browser, `quest:tls:probe` aparece com o instante da última rodada. No Workbench, `INFO server` responde normalmente: o TLS é invisível para os comandos, e essa é exatamente a ideia. Sem `REDIS_TLS_URL`, a lição não cria chave nenhuma: só o marcador `quest:progress:301-03` com `tls=skipped`.

## Por dentro

| Comando | O que faz |
|---|---|
| `rediss://` | Mesma URL, com um `s`: o client abre TLS antes do handshake do Redis |
| `PING` | Primeiro comando dentro do túnel: se o certificado não fosse confiável, ele nem chegaria |
| `SET quest:tls:probe <instante>` | Prova de escrita pela conexão segura |
| `GET quest:tls:probe` | Prova de leitura pela mesma conexão |
| `HSET quest:progress:301-03 tls ok` | O marcador que o `check` lê (`ok` ou `skipped`) |

## Em produção

- Redis Cloud: TLS está nos planos pagos Essentials e Pro. Ative na configuração do banco e baixe o `redis_ca.pem` no console. O bundle traz uma raiz GlobalSign, pública e já presente no truststore da JVM, mais as CAs legadas do Redis Cloud; por isso `RedisClient.create("rediss://...")` funciona sem configurar nada. TLS mútuo (certificado do client) é opcional e só entra se você ligar a autenticação de client no banco.
- Sem `SslOptions`, o Jedis usa o truststore padrão da JVM; para uma CA própria, o caminho documentado é `keytool -importcert` gerando um `truststore.jks` e `SslOptions.builder().truststore(new File("truststore.jks"), senha.toCharArray())`. O lab monta o truststore em memória a partir do PEM para não depender do `keytool`.
- Mantenha a verificação do certificado ligada (o padrão nos dois clients). Desligar resolve o erro do dia e abre a porta para um ataque de interceptação amanhã.

## Desafio

Suba um Redis com TLS na sua máquina (`redis-server --port 0 --tls-port 6395` com um certificado próprio para `localhost`), aponte `REDIS_TLS_URL=rediss://localhost:6395` e rode sem `REDIS_TLS_CA_PEM`: os dois clients recusam o certificado autoassinado. Depois defina `REDIS_TLS_CA_PEM` com a sua CA e veja o `PONG` chegar.
