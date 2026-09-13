package com.emberrealm.quest.lessons.l301_03;

import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Env;
import com.emberrealm.quest.core.Lab;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SslOptions;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * 301-03 (Lettuce): TLS. A rediss:// RedisURI turns SSL on; SslOptions.trustManager(File) adds a custom CA
 * (REDIS_TLS_CA_PEM) when the JVM truststore is not enough. Without REDIS_TLS_URL the lesson is skipped.
 */
public final class LettuceLab implements Lab {

    @Override
    public void run(Ctx ctx) throws Exception {
        Optional<String> tlsUrl = Env.redisTlsUrl();
        if (tlsUrl.isEmpty()) {
            JedisLab.explain(ctx);
            ctx.done("tls", "skipped");
            return;
        }

        String url = tlsUrl.get();
        RedisURI uri = RedisURI.create(url);   // rediss:// sets ssl=true, verifyPeer=true
        uri.setSsl(true);
        uri.setTimeout(Duration.ofSeconds(5));
        ctx.out.step("Conectando com rediss:// em " + Env.redacted(url));
        ctx.out.kv("RedisURI.isSsl", uri.isSsl());
        ctx.out.kv("RedisURI.isVerifyPeer", uri.isVerifyPeer());

        ClientOptions.Builder options = ClientOptions.builder();
        Optional<String> caPem = Env.optional(JedisLab.CA_PEM_VAR);
        if (caPem.isPresent()) {
            ctx.out.info("CA própria em " + caPem.get() + ": SslOptions.trustManager(File) aceita o PEM direto");
            options.sslOptions(SslOptions.builder().trustManager(new File(caPem.get())).build());
        } else {
            ctx.out.info("Sem CA própria: o truststore da JVM já confia na raiz GlobalSign do Redis Cloud");
        }

        RedisClient client = RedisClient.create(uri);
        client.setOptions(options.build());
        try (StatefulRedisConnection<String, String> connection = client.connect()) {
            RedisCommands<String, String> redis = connection.sync();
            ctx.out.cmd("PING");
            ctx.out.kv("PING", redis.ping());
            String probe = ctx.k("tls", "probe");
            String now = Instant.now().toString();
            ctx.out.cmd("SET " + probe + " " + now);
            ctx.out.kv("SET", redis.set(probe, now));
            ctx.out.cmd("GET " + probe);
            ctx.out.kv("GET", redis.get(probe));
            ctx.out.info("Mesmos comandos de sempre: o TLS mora na conexão, não no código de negócio.");
            ctx.done("tls", "ok", "scheme", "rediss", "ca", caPem.isPresent() ? "custom" : "jvm-default");
        } finally {
            client.shutdown(Duration.ZERO, Duration.ofSeconds(2));
        }
    }
}
