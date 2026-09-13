package com.emberrealm.quest.lessons.l301_04;

import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Env;
import com.emberrealm.quest.core.Lab;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.MaintNotificationsConfig;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisCommandExecutionException;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.RedisURI;
import io.lettuce.core.TimeoutOptions;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.output.StatusOutput;
import io.lettuce.core.protocol.CommandArgs;
import io.lettuce.core.protocol.CommandType;
import io.lettuce.core.protocol.ProtocolVersion;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 301-04 (Lettuce): smart client handoffs. MaintNotificationsConfig.enabled() makes Lettuce ask the server for
 * maintenance notifications during the RESP3 handshake; relaxed timeouts stretch the command timeout while a
 * shard moves. Servers without the feature (Redis Open Source) simply refuse, and Lettuce carries on.
 */
public final class LettuceLab implements Lab {

    static final Set<String> MAINT_PUSH_TYPES = Set.of("MOVING", "MIGRATING", "MIGRATED", "FAILING_OVER", "FAILED_OVER");

    @Override
    public void run(Ctx ctx) throws Exception {
        String ticksKey = ctx.k("maint", "ticks");
        String clientsKey = ctx.k("maint", "clients");

        ctx.out.step("ClientOptions: notificações de manutenção + timeouts relaxados");
        ClientOptions options = ClientOptions.builder()
                .protocolVersion(ProtocolVersion.RESP3)                 // SCH needs RESP3 (already the default)
                .maintNotificationsConfig(MaintNotificationsConfig.enabled())
                .timeoutOptions(TimeoutOptions.builder()
                        .timeoutCommands(true)
                        .fixedTimeout(Duration.ofSeconds(2))                     // normal command timeout
                        .relaxedTimeoutsDuringMaintenance(Duration.ofSeconds(10)) // while the server says "maintenance"
                        .build())
                .build();
        ctx.out.kv("maintNotifications", "enabled (endpoint type resolvido automaticamente)");
        ctx.out.kv("timeout normal", "2 s");
        ctx.out.kv("timeout relaxado", "10 s, só durante a manutenção, só nas APIs async e reactive");

        RedisURI uri = RedisURI.create(Env.redisUrl());
        uri.setTimeout(Duration.ofSeconds(2));
        RedisClient client = RedisClient.create(uri);
        client.setOptions(options);
        try (StatefulRedisConnection<String, String> connection = client.connect()) {
            connection.addListener(message -> {
                if (MAINT_PUSH_TYPES.contains(message.getType())) {
                    ctx.out.warn("aviso de manutenção do servidor: " + message.getType() + " " + message.getContent());
                }
            });
            RedisCommands<String, String> sync = connection.sync();

            ctx.out.step("Handshake: o Lettuce pediu CLIENT MAINT_NOTIFICATIONS ON sozinho. Repetindo à mão para ver a resposta");
            ctx.out.cmd("CLIENT MAINT_NOTIFICATIONS ON");
            String sch;
            try {
                String reply = sync.dispatch(CommandType.CLIENT, new StatusOutput<>(StringCodec.UTF8),
                        new CommandArgs<>(StringCodec.UTF8).add("MAINT_NOTIFICATIONS").add("ON"));
                ctx.out.kv("resposta", reply);
                ctx.out.info("O servidor conhece SCH: durante uma manutenção ele avisa MIGRATING e MOVING, e o Lettuce reconecta antes do corte.");
                sch = "on";
            } catch (RedisCommandExecutionException e) {
                ctx.out.kv("resposta", e.getMessage());
                ctx.out.info("Redis Open Source não tem SCH. O Lettuce recebeu a mesma recusa no handshake, ignorou e conectou normalmente.");
                sch = e.getMessage() != null && e.getMessage().toLowerCase().contains("unknown") ? "unsupported" : "refused";
            }

            ctx.out.step(JedisLab.TICKS + " INCRs assíncronos: o relógio da raid não pode pular");
            RedisAsyncCommands<String, String> async = connection.async();
            async.unlink(ticksKey).get(2, TimeUnit.SECONDS);
            ctx.out.cmd("INCR " + ticksKey + "  (x" + JedisLab.TICKS + ", async)");
            RedisFuture<Long> last = null;
            for (int i = 0; i < JedisLab.TICKS; i++) {
                last = async.incr(ticksKey);
            }
            Long ticks = last.get(2, TimeUnit.SECONDS);   // same connection, same order: the last reply means all replied
            ctx.out.kv("ticks", ticks);
            ctx.out.info("Em uma manutenção real do Redis Cloud, esses INCRs esperariam até 10 s em vez de 2 s, e nenhum seria perdido no handoff.");
            ctx.out.info("SCH não cobre conexões bloqueantes (BLPOP, pub/sub): essas dependem do autoReconnect.");

            sync.hset(clientsKey, ctx.client, Instant.now().toString());
            ctx.done("ticks", String.valueOf(ticks), "sch", sch);
        } finally {
            client.shutdown(Duration.ZERO, Duration.ofSeconds(2));
        }
    }
}
