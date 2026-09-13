package com.emberrealm.quest.lessons.l201_03;

import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Lab;
import com.emberrealm.quest.lessons.l201_01.ItemsIndex;
import com.emberrealm.quest.lessons.l201_01.Table;
import com.emberrealm.quest.world.Vectors;
import com.emberrealm.quest.world.World;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.args.SortingOrder;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.search.Combiners;
import redis.clients.jedis.search.Document;
import redis.clients.jedis.search.FTSearchParams;
import redis.clients.jedis.search.Limit;
import redis.clients.jedis.search.SearchResult;
import redis.clients.jedis.search.hybrid.FTHybridParams;
import redis.clients.jedis.search.hybrid.FTHybridPostProcessingParams;
import redis.clients.jedis.search.hybrid.FTHybridSearchParams;
import redis.clients.jedis.search.hybrid.FTHybridVectorParams;
import redis.clients.jedis.search.hybrid.HybridResult;

import java.util.ArrayList;
import java.util.List;

/**
 * 201-03 (Jedis): three ways to answer "uma arma para mago iniciante". Text only (a TAG filter),
 * vector only (KNN over the embedding) and FT.HYBRID, which runs both and fuses the rankings with RRF.
 * Servers older than Redis 8.4 have no FT.HYBRID: the lab explains and still completes.
 */
public final class JedisLab implements Lab {

    @Override
    public void run(Ctx ctx) {
        String index = ItemsIndex.name(ctx);
        try (RedisClient jedis = Clients.jedis()) {
            ItemsIndex.requireSeed(jedis.exists(ctx.k("item", "espada-de-brasa")));

            ctx.out.step("O índice da casa de leilões (lição 201-01) precisa existir");
            ItemsIndex.ensureJedis(ctx, jedis);

            World.Query question = Questions.pick(ctx);
            byte[] blob = Vectors.toBlob(question.embedding());

            ctx.out.step("Só texto: FT.SEARCH " + Questions.TEXT_QUERY + " (um filtro, sem noção de 'parecido')");
            ctx.out.cmd("FT.SEARCH " + index + " \"" + Questions.TEXT_QUERY + "\" RETURN 3 name rarity level LIMIT 0 5");
            SearchResult text = jedis.ftSearch(index, Questions.TEXT_QUERY,
                    FTSearchParams.searchParams().returnFields("name", "rarity", "level").limit(0, 5));
            List<String[]> rows = new ArrayList<>();
            for (Document d : text.getDocuments()) {
                rows.add(new String[]{ItemsIndex.shortId(ctx, d.getId()), Table.str(d.get("name")),
                        Table.str(d.get("rarity")), Table.str(d.get("level"))});
            }
            Table.print(ctx.out, new String[]{"item", "name", "rarity", "level"}, rows);
            ctx.out.kv("total", text.getTotalResults());
            ctx.out.info("Todas as armas, todas com o mesmo score: o filtro TAG não sabe qual delas responde à pergunta.");

            ctx.out.step("Só vetor: KNN 5 pelo embedding da pergunta");
            ctx.out.cmd("FT.SEARCH " + index + " \"*=>[KNN 5 @embedding $vec AS score]\" PARAMS 2 vec <blob de "
                    + blob.length + " bytes> SORTBY score RETURN 3 name type score DIALECT 2");
            SearchResult knn = jedis.ftSearch(index, "*=>[KNN 5 @embedding $vec AS score]",
                    FTSearchParams.searchParams()
                            .addParam("vec", blob)
                            .sortBy("score", SortingOrder.ASC)
                            .returnFields("name", "type", "score")
                            .dialect(2));
            rows = new ArrayList<>();
            String knnTop = "-";
            for (Document d : knn.getDocuments()) {
                String id = ItemsIndex.shortId(ctx, d.getId());
                if (knnTop.equals("-")) knnTop = id;
                rows.add(new String[]{id, Table.str(d.get("name")), Table.str(d.get("type")),
                        Table.decimal(d.get("score"), 3), Questions.similarity(d.get("score"))});
            }
            Table.print(ctx.out, new String[]{"item", "name", "type", "distancia", "similaridade"}, rows);
            ctx.out.info("Só semântica: acha o que 'parece' com a pergunta, mas não sabe que você quer uma arma; pode vir manto ou poção.");
            ctx.out.info("O vetor vai como PARAMS em bytes FLOAT32 little-endian (Vectors.toBlob); a query só cita $vec. DIALECT 2 é obrigatório.");

            ctx.out.step("Híbrido: FT.HYBRID = SEARCH + VSIM, rankings fundidos por RRF");
            ctx.out.cmd("FT.HYBRID " + index + " SEARCH \"" + Questions.TEXT_QUERY + "\" VSIM @embedding $vec KNN 2 K 10"
                    + " COMBINE RRF 2 WINDOW 20 LOAD 5 @__key @__score @name @rarity @price LIMIT 0 5 PARAMS 2 vec <blob>");
            String hybridStatus;
            String hybridTop = "-";
            try {
                HybridResult hybrid = jedis.ftHybrid(index, FTHybridParams.builder()
                        .search(FTHybridSearchParams.builder().query(Questions.TEXT_QUERY).build())
                        .vectorSearch(FTHybridVectorParams.builder()
                                .field("@embedding")
                                .vector("$vec")
                                .method(FTHybridVectorParams.Knn.of(10))
                                .build())
                        .combine(Combiners.rrf().window(20))
                        .postProcessing(FTHybridPostProcessingParams.builder()
                                .load("@__key", "@__score", "@name", "@rarity", "@price")
                                .limit(Limit.of(0, 5))
                                .build())
                        .param("vec", blob)
                        .build());
                rows = new ArrayList<>();
                for (Document d : hybrid.getDocuments()) {
                    // Jedis maps the loaded __key and __score onto Document.getId() and Document.getScore()
                    String key = d.getId() != null ? d.getId() : Table.str(d.get("__key"));
                    Object score = d.getScore() != null ? d.getScore() : d.get("__score");
                    String id = ItemsIndex.shortId(ctx, key);
                    if (hybridTop.equals("-")) hybridTop = id;
                    rows.add(new String[]{id, Table.str(d.get("name")), Table.str(d.get("rarity")),
                            Table.money(d.get("price")), Table.decimal(score, 4)});
                }
                Table.print(ctx.out, new String[]{"item", "name", "rarity", "price", "rrf"}, rows);
                ctx.out.kv("total_results", hybrid.getTotalResults());
                ctx.out.info("RRF soma 1/(60 + posição) em cada ranking: quem vai bem no texto E no vetor sobe; quem só aparece em um fica atrás.");
                ctx.out.info("WINDOW 20 limita quantos candidatos de cada lado entram na fusão; LOAD traz os campos (com @) e __score é a nota final.");
                hybridStatus = "ok";
            } catch (JedisDataException e) {
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
}
