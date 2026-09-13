package com.emberrealm.quest.lessons.l201_01;

import com.emberrealm.quest.core.Ctx;
import io.lettuce.core.RedisCommandExecutionException;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.search.arguments.CreateArgs;
import io.lettuce.core.search.arguments.FieldArgs;
import io.lettuce.core.search.arguments.NumericFieldArgs;
import io.lettuce.core.search.arguments.SearchArgs;
import io.lettuce.core.search.arguments.TagFieldArgs;
import io.lettuce.core.search.arguments.TextFieldArgs;
import io.lettuce.core.search.arguments.VectorFieldArgs;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.search.FTCreateParams;
import redis.clients.jedis.search.FTSearchParams;
import redis.clients.jedis.search.IndexDataType;
import redis.clients.jedis.search.schemafields.NumericField;
import redis.clients.jedis.search.schemafields.SchemaField;
import redis.clients.jedis.search.schemafields.TagField;
import redis.clients.jedis.search.schemafields.TextField;
import redis.clients.jedis.search.schemafields.VectorField;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * The auction house index shared by lessons 201-01, 201-02 and 201-03. Same schema for both clients:
 *
 *   FT.CREATE {p}:idx:items ON JSON PREFIX 1 {p}:item: SCHEMA
 *     $.name AS name TEXT  $.description AS description TEXT
 *     $.type AS type TAG  $.rarity AS rarity TAG
 *     $.level AS level NUMERIC  $.price AS price NUMERIC
 *     $.classes[*] AS classes TAG
 *     $.embedding AS embedding VECTOR FLAT 6 TYPE FLOAT32 DIM 384 DISTANCE_METRIC COSINE
 *
 * The JSON documents store the embedding as a plain array of numbers; the VECTOR field reads it directly.
 */
public final class ItemsIndex {

    public static final int DOCS = 42;
    public static final int DIM = 384;

    private ItemsIndex() {
    }

    public static String name(Ctx ctx) {
        return ctx.k("idx", "items");
    }

    /** Key prefix the index watches: {p}:item: */
    public static String prefix(Ctx ctx) {
        return ctx.k("item", "");
    }

    /** The FT.CREATE command the way redis-cli would show it. */
    public static String createCommand(Ctx ctx) {
        return "FT.CREATE " + name(ctx) + " ON JSON PREFIX 1 " + prefix(ctx) + " SCHEMA"
                + " $.name AS name TEXT $.description AS description TEXT"
                + " $.type AS type TAG $.rarity AS rarity TAG"
                + " $.level AS level NUMERIC $.price AS price NUMERIC"
                + " $.classes[*] AS classes TAG"
                + " $.embedding AS embedding VECTOR FLAT 6 TYPE FLOAT32 DIM " + DIM + " DISTANCE_METRIC COSINE";
    }

    /** Labs call this with exists({p}:item:espada-de-brasa): the seed must have run first. */
    public static void requireSeed(boolean seeded) {
        if (!seeded) throw new IllegalStateException("Rode ./quest seed primeiro");
    }

    /** Strips the {p}:item: prefix so tables show only the item id. */
    public static String shortId(Ctx ctx, String key) {
        String p = prefix(ctx);
        return key != null && key.startsWith(p) ? key.substring(p.length()) : String.valueOf(key);
    }

    // ------------------------------------------------------------------ Jedis

    public static List<SchemaField> jedisSchema() {
        return List.of(
                TextField.of("$.name").as("name"),
                TextField.of("$.description").as("description"),
                TagField.of("$.type").as("type"),
                TagField.of("$.rarity").as("rarity"),
                NumericField.of("$.level").as("level"),
                NumericField.of("$.price").as("price"),
                TagField.of("$.classes[*]").as("classes"),
                VectorField.builder().fieldName("$.embedding").as("embedding")
                        .algorithm(VectorField.VectorAlgorithm.FLAT)
                        .addAttribute("TYPE", "FLOAT32")
                        .addAttribute("DIM", DIM)
                        .addAttribute("DISTANCE_METRIC", "COSINE")
                        .build());
    }

    public static boolean existsJedis(RedisClient jedis, String index) {
        try {
            jedis.ftInfo(index);
            return true;
        } catch (JedisDataException e) {
            return false;
        }
    }

    public static void dropJedis(Ctx ctx, RedisClient jedis) {
        ctx.out.cmd("FT.DROPINDEX " + name(ctx));
        try {
            jedis.ftDropIndex(name(ctx));
            ctx.out.info("Índice antigo removido (só o índice; os documentos JSON ficam onde estão).");
        } catch (JedisDataException e) {
            ctx.out.info("Não havia índice ainda: " + e.getMessage());
        }
    }

    public static void createJedis(Ctx ctx, RedisClient jedis) {
        ctx.out.cmd(createCommand(ctx));
        String reply = jedis.ftCreate(name(ctx),
                FTCreateParams.createParams().on(IndexDataType.JSON).addPrefix(prefix(ctx)),
                jedisSchema());
        ctx.out.kv("FT.CREATE", reply);
        waitUntilIndexed(ctx, () -> jedis.ftSearch(name(ctx), "*",
                FTSearchParams.searchParams().noContent().limit(0, 0)).getTotalResults());
    }

    /** Lessons 201-02 and 201-03 depend on the index from 201-01: create it when missing. */
    public static void ensureJedis(Ctx ctx, RedisClient jedis) {
        if (existsJedis(jedis, name(ctx))) {
            ctx.out.info("Índice " + name(ctx) + " já existe (criado na lição 201-01).");
        } else {
            ctx.out.warn("Índice " + name(ctx) + " não existe; criando com o mesmo schema da lição 201-01.");
            createJedis(ctx, jedis);
        }
    }

    /** FT.INFO returns num_docs as a string on RESP2 and as a number on RESP3. */
    public static long numDocs(Map<String, Object> ftInfo) {
        Object raw = ftInfo.get("num_docs");
        if (raw instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(raw));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ---------------------------------------------------------------- Lettuce

    public static List<FieldArgs<String>> lettuceSchema() {
        List<FieldArgs<String>> fields = new ArrayList<>();
        fields.add(TextFieldArgs.<String>builder().name("$.name").as("name").build());
        fields.add(TextFieldArgs.<String>builder().name("$.description").as("description").build());
        fields.add(TagFieldArgs.<String>builder().name("$.type").as("type").build());
        fields.add(TagFieldArgs.<String>builder().name("$.rarity").as("rarity").build());
        fields.add(NumericFieldArgs.<String>builder().name("$.level").as("level").build());
        fields.add(NumericFieldArgs.<String>builder().name("$.price").as("price").build());
        fields.add(TagFieldArgs.<String>builder().name("$.classes[*]").as("classes").build());
        fields.add(VectorFieldArgs.<String>builder().name("$.embedding").as("embedding")
                .flat()
                .type(VectorFieldArgs.VectorType.FLOAT32)
                .dimensions(DIM)
                .distanceMetric(VectorFieldArgs.DistanceMetric.COSINE)
                .build());
        return fields;
    }

    /** Lettuce 7.7 has no ftInfo; FT._LIST names every index of the database. */
    public static boolean existsLettuce(RedisCommands<String, String> redis, String index) {
        return redis.ftList().contains(index);
    }

    public static void dropLettuce(Ctx ctx, RedisCommands<String, String> redis) {
        ctx.out.cmd("FT.DROPINDEX " + name(ctx));
        try {
            redis.ftDropindex(name(ctx));
            ctx.out.info("Índice antigo removido (só o índice; os documentos JSON ficam onde estão).");
        } catch (RedisCommandExecutionException e) {
            ctx.out.info("Não havia índice ainda: " + e.getMessage());
        }
    }

    public static void createLettuce(Ctx ctx, RedisCommands<String, String> redis) {
        ctx.out.cmd(createCommand(ctx));
        CreateArgs<String, String> args = CreateArgs.<String, String>builder()
                .on(CreateArgs.TargetType.JSON)
                .withPrefix(prefix(ctx))
                .build();
        ctx.out.kv("FT.CREATE", redis.ftCreate(name(ctx), args, lettuceSchema()));
        waitUntilIndexed(ctx, () -> redis.ftSearch(name(ctx), "*",
                SearchArgs.<String, String>builder().noContent().limit(0, 0).build()).getCount());
    }

    public static void ensureLettuce(Ctx ctx, RedisCommands<String, String> redis) {
        if (existsLettuce(redis, name(ctx))) {
            ctx.out.info("Índice " + name(ctx) + " já existe (criado na lição 201-01).");
        } else {
            ctx.out.warn("Índice " + name(ctx) + " não existe; criando com o mesmo schema da lição 201-01.");
            createLettuce(ctx, redis);
        }
    }

    // ----------------------------------------------------------------- shared

    /** Indexing runs in the background on the server; 42 small documents take milliseconds, but we do not guess. */
    private static void waitUntilIndexed(Ctx ctx, LongSupplier count) {
        long deadline = System.currentTimeMillis() + 3000;
        long seen = count.getAsLong();
        while (seen < DOCS && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            seen = count.getAsLong();
        }
        ctx.out.info("Documentos indexados: " + seen + " de " + DOCS + " (a indexação roda em background no servidor).");
    }
}
