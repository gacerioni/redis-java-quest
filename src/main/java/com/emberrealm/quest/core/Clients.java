package com.emberrealm.quest.core;

import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;

import java.time.Duration;

/**
 * Connection factories used by every lesson. Two clients, same URL:
 *  - Jedis: redis.clients.jedis.RedisClient (Jedis 7.2+ entry point, pooled, synchronous)
 *  - Lettuce: io.lettuce.core.RedisClient (netty based, multiplexed, sync/async/reactive)
 */
public final class Clients {

    private static volatile io.lettuce.core.RedisClient lettuce;

    private Clients() {
    }

    /** A Jedis client for the configured REDIS_URL. Close it when done. */
    public static redis.clients.jedis.RedisClient jedis() {
        return jedis(Env.redisUrl());
    }

    public static redis.clients.jedis.RedisClient jedis(String url) {
        return redis.clients.jedis.RedisClient.create(url);
    }

    /** One Lettuce client per JVM (it owns the netty event loops); connections are cheap, clients are not. */
    public static io.lettuce.core.RedisClient lettuce() {
        io.lettuce.core.RedisClient local = lettuce;
        if (local == null) {
            synchronized (Clients.class) {
                local = lettuce;
                if (local == null) {
                    RedisURI uri = RedisURI.create(Env.redisUrl());
                    uri.setTimeout(Duration.ofSeconds(5));
                    local = io.lettuce.core.RedisClient.create(uri);
                    lettuce = local;
                }
            }
        }
        return local;
    }

    /** A new Lettuce connection (String codec). Share it across threads unless you block on it. */
    public static StatefulRedisConnection<String, String> lettuceConnection() {
        return lettuce().connect();
    }

    public static void shutdownLettuce() {
        io.lettuce.core.RedisClient local = lettuce;
        if (local != null) {
            local.shutdown(Duration.ZERO, Duration.ofSeconds(2));
            lettuce = null;
        }
    }
}
