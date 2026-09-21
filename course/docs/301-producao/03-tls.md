---
lesson: 301-03
title: "TLS: certificado e identidade do servidor"
minutes: 8
kind: lab
---

# TLS: certificado e identidade do servidor

<p class="lesson-meta">Lição 301-03 · Lab · 8 min</p>

TLS protege o tráfego e permite verificar **com quem a aplicação está falando**. O lab exige `rediss://`, valida a cadeia de certificados e confere se o hostname da URL está no certificado. Uma URL `redis://` é rejeitada antes de conectar.

No Redis Cloud, o plano free não oferece TLS; use um banco com TLS em plano pago ou um ambiente TLS local. Sem `REDIS_TLS_URL`, a lição explica o preparo e fica **pendente**, sem registrar uma conexão segura que não aconteceu.

## Faça agora

Configure no `.env`:

```bash
REDIS_TLS_URL=rediss://default:SENHA@host:porta
# Defina quando a CA não estiver no truststore da JVM:
REDIS_TLS_CA_PEM=/caminho/redis_ca.pem
```

```bash
./quest run 301-03 jedis
./quest run 301-03 lettuce    # comparação opcional
./quest verify 301-03
```

O resultado esperado é `PONG`, escrita e leitura de `quest:tls:probe` através de TLS. O `verify` exige evidência da versão atual do lab, incluindo a verificação de hostname. Um marcador antigo não comprova essa proteção.

## O código

=== "Jedis"

    ```java
    URI uri = requireTlsUrl(url);   // rejects redis:// and missing host
    SSLParameters parameters = new SSLParameters();
    parameters.setEndpointIdentificationAlgorithm("HTTPS"); // verifies certificate hostname

    DefaultJedisClientConfig.Builder config = DefaultJedisClientConfig.builder()
            .user(user).password(password)
            .ssl(true)
            .sslParameters(parameters)
            .connectionTimeoutMillis(2000).socketTimeoutMillis(5000);
    if (caPem != null) {
        config.sslSocketFactory(socketFactoryFromPem(Path.of(caPem)));
    }
    try (RedisClient jedis = RedisClient.builder()
            .hostAndPort(new HostAndPort(uri.getHost(), port))
            .clientConfig(config.build()).build()) {
        jedis.ping();
        jedis.set(probe, Instant.now().toString());
        jedis.get(probe);
    }
    ```

    O helper `socketFactoryFromPem` do lab carrega o bundle em um truststore em memória. A verificação de hostname continua ligada mesmo com CA própria. O nome `HTTPS` é o algoritmo de identificação do endpoint na API Java; a conexão continua usando o protocolo Redis sobre TLS.

=== "Lettuce"

    ```java
    requireTlsUrl(url);
    RedisURI uri = RedisURI.create(url);   // rediss://, peer verification enabled
    uri.setTimeout(Duration.ofSeconds(5));
    ClientOptions.Builder options = ClientOptions.builder();
    if (caPem != null) {
        options.sslOptions(SslOptions.builder().trustManager(new File(caPem)).build());
    }
    RedisClient client = RedisClient.create(uri);
    client.setOptions(options.build());
    try (StatefulRedisConnection<String, String> connection = client.connect()) {
        RedisCommands<String, String> redis = connection.sync();
        redis.ping();
        redis.set(probe, Instant.now().toString());
        redis.get(probe);
    } finally {
        client.shutdown();
    }
    ```

## CA confiável e hostname correto

São verificações diferentes. Confiar na CA não autoriza um certificado emitido para outro hostname. No Cloud, baixe o bundle `redis_ca.pem` do seu banco quando necessário: endpoints com cadeia pública compatível podem funcionar com o truststore da JVM; CAs legadas ou próprias podem exigir o bundle. Não presuma isso apenas pelo nome do produto.

TLS mútuo adiciona o certificado do client e é uma configuração separada. Estes exemplos verificam a identidade do servidor; não configuram mTLS.

## No Redis Insight

Adicione o banco com TLS habilitado e, se necessário, a CA do banco. Observe `quest:tls:probe` depois de rodar. Sem `REDIS_TLS_URL`, não existe probe TLS: apenas a evidência de que essa etapa está indisponível no ambiente atual. `verify` retorna pendência, não sucesso.

??? tip "Experimente depois"
    Num Redis local com TLS, teste três casos: CA não confiável deve falhar; CA confiável com hostname incorreto deve falhar; CA e hostname corretos devem chegar ao PONG. Nunca resolva esses erros desabilitando a verificação do peer.
