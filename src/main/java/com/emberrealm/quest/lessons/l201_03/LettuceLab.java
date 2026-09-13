package com.emberrealm.quest.lessons.l201_03;

import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Lab;
import com.emberrealm.quest.lessons.l201_01.ItemsIndex;
import com.emberrealm.quest.lessons.l201_01.Table;
import com.emberrealm.quest.world.Vectors;
import com.emberrealm.quest.world.World;
import io.lettuce.core.RedisCommandExecutionException;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.search.HybridReply;
import io.lettuce.core.search.SearchReply;
import io.lettuce.core.search.aggregateutils.Limit;
import io.lettuce.core.search.arguments.QueryDialects;
import io.lettuce.core.search.arguments.SearchArgs;
import io.lettuce.core.search.arguments.SortByArgs;
import io.lettuce.core.search.arguments.hybrid.Combiners;
import io.lettuce.core.search.arguments.hybrid.HybridArgs;
import io.lettuce.core.search.arguments.hybrid.HybridSearchArgs;
import io.lettuce.core.search.arguments.hybrid.HybridVectorArgs;
import io.lettuce.core.search.arguments.hybrid.PostProcessingArgs;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 201-03 (Lettuce): same three searches. The KNN query needs the vector as raw bytes, and
 * SearchArgs.param(K, V) follows the connection codec (String here), so the KNN runs on a second
 * connection whose values are byte[]. HybridArgs has param(K, byte[]) built in.
 */
public final class LettuceLab implements Lab {

    @Override
    public void run(Ctx ctx) {
        String index = ItemsIndex.name(ctx);
        try (StatefulRedisConnection<String, String> connection = Clients.lettuceConnection()) {
            RedisCommands<String, String> redis = connection.sync();
            ItemsIndex.requireSeed(redis.exists(ctx.k("item", "espada-de-brasa")) == 1);

            ctx.out.step("O índice da casa de leilões (lição 201-01) precisa existir");
            ItemsIndex.ensureLettuce(ctx, redis);

            World.Query question = Questions.pick(ctx);
            byte[] blob = Vectors.toBlob(question.embedding());

            ctx.out.step("Só texto: FT.SEARCH " + Questions.TEXT_QUERY + " (um filtro, sem noção de 'parecido')");
            ctx.out.cmd("FT.SEARCH " + index + " \"" + Questions.TEXT_QUERY + "\" RETURN 3 name rarity level LIMIT 0 5");
            SearchReply<String, String> text = redis.ftSearch(index, Questions.TEXT_QUERY, SearchArgs.<String, String>builder()
                    .returnField("name").returnField("rarity").returnField("level").limit(0, 5).build());
            List<String[]> rows = new ArrayList<>();
            for (SearchReply.SearchResult<String, String> hit : text.getResults()) {
                Map<String, String> f = hit.getFields();
                rows.add(new String[]{ItemsIndex.shortId(ctx, hit.getId()), Table.str(f.get("name")),
                        Table.str(f.get("rarity")), Table.str(f.get("level"))});
            }
            Table.print(ctx.out, new String[]{"item", "name", "rarity", "level"}, rows);
            ctx.out.kv("total", text.getCount());
            ctx.out.info("Todas as armas, todas com o mesmo score: o filtro TAG não sabe qual delas responde à pergunta.");

            ctx.out.step("Só vetor: KNN 5 pelo embedding da pergunta (segunda conexão, valores em byte[])");
            ctx.out.cmd("FT.SEARCH " + index + " \"*=>[KNN 5 @embedding $vec AS score]\" PARAMS 2 vec <blob de "
                    + blob.length + " bytes> SORTBY score RETURN 3 name type score DIALECT 2");
            String knnTop = "-";
            try (StatefulRedisConnection<String, byte[]> binary =
                         Clients.lettuce().connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE))) {
                RedisCommands<String, byte[]> bin = binary.sync();
                SearchReply<String, byte[]> knn = bin.ftSearch(index,
                        "*=>[KNN 5 @embedding $vec AS score]".getBytes(StandardCharsets.UTF_8),
                        SearchArgs.<String, byte[]>builder()
                                .param("vec", blob)
                                .sortBy(SortByArgs.<String>builder().attribute("score").build())
                                .returnField("name").returnField("type").returnField("score")
                                .limit(0, 5)
                                .dialect(QueryDialects.DIALECT2)
                                .build());
                rows = new ArrayList<>();
                for (SearchReply.SearchResult<String, byte[]> hit : knn.getResults()) {
                    String id = ItemsIndex.shortId(ctx, hit.getId());
                    if (knnTop.equals("-")) knnTop = id;
                    String score = utf8(hit.getFields().get("score"));
                    rows.add(new String[]{id, utf8(hit.getFields().get("name")), utf8(hit.getFields().get("type")),
                            Table.decimal(score, 3), Questions.similarity(score)});
                }
                Table.print(ctx.out, new String[]{"item", "name", "type", "distancia", "similaridade"}, rows);
            }
            ctx.out.info("Só semântica: acha o que 'parece' com a pergunta, mas não sabe que você quer uma arma; pode vir manto ou poção.");
            ctx.out.info("SearchArgs.param(K, V) codifica o valor com o codec da conexão: com StringCodec o blob viraria texto UTF-8 e quebraria. "
                    + "RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE) manda os bytes intactos.");

            ctx.out.step("Híbrido: FT.HYBRID = SEARCH + VSIM, rankings fundidos por RRF");
            ctx.out.cmd("FT.HYBRID " + index + " SEARCH \"" + Questions.TEXT_QUERY + "\" VSIM @embedding $vec KNN 2 K 10"
                    + " COMBINE RRF 2 WINDOW 20 LOAD 5 @__key @__score @name @rarity @price LIMIT 0 5 PARAMS 2 vec <blob>");
            String hybridStatus;
            String hybridTop = "-";
            try {
                HybridReply<String, String> hybrid = redis.ftHybrid(index, HybridArgs.<String, String>builder()
                        .search(HybridSearchArgs.<String, String>builder().query(Questions.TEXT_QUERY).build())
                        .vectorSearch(HybridVectorArgs.<String, String>builder()
                                .field("@embedding")
                                .vector("$vec")
                                .method(HybridVectorArgs.Knn.of(10))
                                .build())
                        .combine(Combiners.<String>rrf().window(20))
                        .postProcessing(PostProcessingArgs.<String, String>builder()
                                .load("@__key", "@__score", "@name", "@rarity", "@price")
                                .limit(Limit.<String, String>of(0, 5))
                                .build())
                        .param("vec", blob)
                        .build());
                rows = new ArrayList<>();
                for (Map<String, String> row : hybrid.getResults()) {
                    String id = ItemsIndex.shortId(ctx, row.get("__key"));
                    if (hybridTop.equals("-")) hybridTop = id;
                    rows.add(new String[]{id, Table.str(row.get("name")), Table.str(row.get("rarity")),
                            Table.money(row.get("price")), Table.decimal(row.get("__score"), 4)});
                }
                Table.print(ctx.out, new String[]{"item", "name", "rarity", "price", "rrf"}, rows);
                ctx.out.kv("total_results", hybrid.getTotalResults());
                ctx.out.info("RRF soma 1/(60 + posição) em cada ranking: quem vai bem no texto E no vetor sobe; quem só aparece em um fica atrás.");
                ctx.out.info("HybridArgs.param(K, byte[]) já existe para o vetor; LOAD pede os campos com @ e __score é a nota final.");
                hybridStatus = "ok";
            } catch (RedisCommandExecutionException e) {
                if (!Questions.isUnknownCommand(e)) throw e;
                ctx.out.warn("FT.HYBRID não existe neste servidor: " + e.getMessage());
                ctx.out.hint("Busca híbrida nativa pede Redis 8.4 ou mais novo (o Redis Cloud já tem). A parte KNN funcionou; a fusão você faria na aplicação.");
                hybridStatus = "unsupported";
            }

            ctx.done("hybrid", hybridStatus,
                    "question", question.id(),
                    "knn_top", knnTop,
                    "hybrid_top", hybridTop,
                    "ran_" + ctx.client, "1");
        }
    }

    private static String utf8(byte[] value) {
        return value == null ? null : new String(value, StandardCharsets.UTF_8);
    }
}
