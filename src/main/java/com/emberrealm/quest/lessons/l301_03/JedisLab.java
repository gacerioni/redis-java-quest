package com.emberrealm.quest.lessons.l301_03;

import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Env;
import com.emberrealm.quest.core.Lab;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.util.JedisURIHelper;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.time.Instant;
import java.util.Optional;

/**
 * 301-03 (Jedis): TLS. With REDIS_TLS_URL set, connects over rediss:// (JVM truststore, or a custom CA from
 * REDIS_TLS_CA_PEM) and runs PING, SET and GET. Without it, explains why and marks the lesson as skipped.
 */
public final class JedisLab implements Lab {

    static final String CA_PEM_VAR = "REDIS_TLS_CA_PEM";

    @Override
    public void run(Ctx ctx) throws Exception {
        Optional<String> tlsUrl = Env.redisTlsUrl();
        if (tlsUrl.isEmpty()) {
            explain(ctx);
            ctx.done("tls", "skipped");
            return;
        }

        String url = tlsUrl.get();
        URI uri = URI.create(url);
        ctx.out.step("Conectando com " + uri.getScheme() + ":// em " + Env.redacted(url));
        Optional<String> caPem = Env.optional(CA_PEM_VAR);
        RedisClient jedis;
        if (caPem.isPresent()) {
            ctx.out.info("CA própria em " + caPem.get() + ": montando um truststore em memória a partir do PEM");
            DefaultJedisClientConfig config = configFrom(uri)
                    .ssl(true)
                    .sslSocketFactory(socketFactoryFromPem(Path.of(caPem.get())))
                    .build();
            jedis = RedisClient.builder().hostAndPort(hostAndPort(uri)).clientConfig(config).build();
        } else {
            ctx.out.info("Sem CA própria: RedisClient.create(url) usa o truststore da JVM, que já confia na raiz GlobalSign do Redis Cloud");
            jedis = RedisClient.create(url);
        }
        try (jedis) {
            ctx.out.cmd("PING");
            ctx.out.kv("PING", jedis.ping());
            String probe = ctx.k("tls", "probe");
            String now = Instant.now().toString();
            ctx.out.cmd("SET " + probe + " " + now);
            ctx.out.kv("SET", jedis.set(probe, now));
            ctx.out.cmd("GET " + probe);
            ctx.out.kv("GET", jedis.get(probe));
            ctx.out.info("Mesmos comandos de sempre: o TLS mora na conexão, não no código de negócio.");
            ctx.done("tls", "ok", "scheme", uri.getScheme(), "ca", caPem.isPresent() ? "custom" : "jvm-default");
        }
    }

    static void explain(Ctx ctx) {
        ctx.out.step("Sem REDIS_TLS_URL: esta lição explica em vez de conectar");
        ctx.out.info("O plano free do Redis Cloud (30 MB) não oferece TLS. Os planos pagos Essentials e Pro oferecem.");
        ctx.out.info("No plano pago: ative o TLS no banco, baixe o redis_ca.pem no console e conecte com rediss://usuario:senha@host:porta.");
        ctx.out.info("O bundle redis_ca.pem traz uma raiz GlobalSign, pública e já confiada pela JVM, mais as CAs legadas do Redis Cloud.");
        ctx.out.info("TLS mútuo (certificado do client) é opcional: só entra se você ligar a autenticação de client no banco.");
        ctx.out.hint("No .env: REDIS_TLS_URL=rediss://default:SENHA@host:porta e, se precisar de CA própria, "
                + CA_PEM_VAR + "=/caminho/redis_ca.pem. Depois rode a lição de novo.");
    }

    /** Builds an SSLSocketFactory that trusts only the certificates in the PEM bundle. */
    static SSLSocketFactory socketFactoryFromPem(Path pem) throws GeneralSecurityException, IOException {
        CertificateFactory certificates = CertificateFactory.getInstance("X.509");
        KeyStore trust = KeyStore.getInstance(KeyStore.getDefaultType());
        trust.load(null, null);
        try (InputStream in = Files.newInputStream(pem)) {
            int i = 0;
            for (Certificate ca : certificates.generateCertificates(in)) {
                trust.setCertificateEntry("ca-" + (i++), ca);
            }
        }
        TrustManagerFactory managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        managers.init(trust);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, managers.getTrustManagers(), null);
        return context.getSocketFactory();
    }

    static HostAndPort hostAndPort(URI uri) {
        return new HostAndPort(uri.getHost(), uri.getPort() < 0 ? 6379 : uri.getPort());
    }

    static DefaultJedisClientConfig.Builder configFrom(URI uri) {
        DefaultJedisClientConfig.Builder builder = DefaultJedisClientConfig.builder();
        String user = JedisURIHelper.getUser(uri);
        String password = JedisURIHelper.getPassword(uri);
        if (user != null) builder.user(user);
        if (password != null) builder.password(password);
        if (JedisURIHelper.hasDbIndex(uri)) builder.database(JedisURIHelper.getDBIndex(uri));
        return builder;
    }
}
