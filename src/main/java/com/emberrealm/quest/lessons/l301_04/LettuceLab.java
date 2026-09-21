package com.emberrealm.quest.lessons.l301_04;

import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Env;
import com.emberrealm.quest.core.Lab;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.MaintNotificationsConfig;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisCommandExecutionException;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
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
        String operationsKey = ctx.k("maint", "operations");
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
            AtomicInteger maintenanceEvents = new AtomicInteger();
            connection.addListener(message -> {
                if (MAINT_PUSH_TYPES.contains(message.getType())) {
                    maintenanceEvents.incrementAndGet();
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
                ctx.out.info("O pedido não foi aceito; pode ser falta de suporte ou permissão. Confira a resposta antes de concluir qual é o produto.");
                sch = e.getMessage() != null && e.getMessage().toLowerCase().contains("unknown") ? "unsupported" : "refused";
            }

            ctx.out.step(JedisLab.OPERATIONS + " SADDs assíncronos: um ID fixo por operação");
            RedisAsyncCommands<String, String> async = connection.async();
            async.unlink(operationsKey).get(12, TimeUnit.SECONDS);
            ctx.out.cmd("SADD " + operationsKey + " op-1 ... op-" + JedisLab.OPERATIONS + "  (async, um comando por ID)");
            List<CompletableFuture<Long>> pending = new ArrayList<>();
            for (int i = 1; i <= JedisLab.OPERATIONS; i++) {
                pending.add(async.sadd(operationsKey, "op-" + i).toCompletableFuture());
            }
            // The outer wait must exceed SCH's 10-second relaxed command timeout.
            CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new)).get(12, TimeUnit.SECONDS);
            Long applied = async.scard(operationsKey).get(12, TimeUnit.SECONDS);
            ctx.out.kv("operações distintas aplicadas", applied);
            ctx.out.info("Replay de SADD com o mesmo ID é idempotente. Uma resposta perdida não cria um segundo efeito.");
            ctx.out.kv("avisos de manutenção observados", maintenanceEvents.get());
            ctx.out.info("Sem aviso observado, validamos a configuração e os comandos, não um handoff real.");
            ctx.out.info("SCH não cobre conexões bloqueantes (BLPOP, pub/sub): essas dependem do autoReconnect.");

            async.hset(clientsKey, ctx.client, Instant.now().toString()).get(12, TimeUnit.SECONDS);
            ctx.done("operations", String.valueOf(applied), "sch", sch,
                    "maintenance_events", String.valueOf(maintenanceEvents.get()));
        } finally {
            client.shutdown(Duration.ZERO, Duration.ofSeconds(2));
        }
    }
}
