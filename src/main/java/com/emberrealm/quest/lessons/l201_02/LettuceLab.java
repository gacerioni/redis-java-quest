package com.emberrealm.quest.lessons.l201_02;

import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Lab;
import com.emberrealm.quest.lessons.l201_01.ItemsIndex;
import com.emberrealm.quest.lessons.l201_01.Table;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.search.AggregationReply;
import io.lettuce.core.search.SearchReply;
import io.lettuce.core.search.arguments.AggregateArgs;
import io.lettuce.core.search.arguments.AggregateArgs.GroupBy;
import io.lettuce.core.search.arguments.AggregateArgs.Reducer;
import io.lettuce.core.search.arguments.AggregateArgs.SortBy;
import io.lettuce.core.search.arguments.AggregateArgs.SortDirection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 201-02 (Lettuce): the same four aggregations with AggregateArgs, GroupBy, Reducer, SortBy. */
public final class LettuceLab implements Lab {

    @Override
    public void run(Ctx ctx) {
        String index = ItemsIndex.name(ctx);
        try (StatefulRedisConnection<String, String> connection = Clients.lettuceConnection()) {
            RedisCommands<String, String> redis = connection.sync();
            ItemsIndex.requireSeed(redis.exists(ctx.k("item", "espada-de-brasa")) == 1);

            ctx.out.step("O índice da casa de leilões (lição 201-01) precisa existir");
            ItemsIndex.ensureLettuce(ctx, redis);

            ctx.out.step("Quanto custa cada raridade? GROUPBY rarity com COUNT e AVG");
            ctx.out.cmd("FT.AGGREGATE " + index + " \"*\" GROUPBY 1 @rarity REDUCE COUNT 0 AS n"
                    + " REDUCE AVG 1 @price AS avg_price SORTBY 2 @avg_price DESC");
            List<Map<String, String>> byRarity = rows(redis.ftAggregate(index, "*", AggregateArgs.<String, String>builder()
                    .groupBy(GroupBy.<String, String>of("@rarity")
                            .reduce(Reducer.<String, String>count().as("n"))
                            .reduce(Reducer.<String, String>avg("@price").as("avg_price")))
                    .sortBy(SortBy.of("@avg_price", SortDirection.DESC))
                    .build()));
            table(ctx, byRarity, "rarity", "n", "avg_price");
            int groups = byRarity.size();
            ctx.out.info("Cada linha é um grupo, não um documento: o servidor agrupou e calculou; a JVM só imprimiu.");

            ctx.out.step("O item mais caro de cada tipo: GROUPBY type REDUCE MAX price");
            ctx.out.cmd("FT.AGGREGATE " + index + " \"*\" GROUPBY 1 @type REDUCE MAX 1 @price AS max_price"
                    + " REDUCE COUNT 0 AS n SORTBY 2 @max_price DESC");
            List<Map<String, String>> byType = rows(redis.ftAggregate(index, "*", AggregateArgs.<String, String>builder()
                    .groupBy(GroupBy.<String, String>of("@type")
                            .reduce(Reducer.<String, String>max("@price").as("max_price"))
                            .reduce(Reducer.<String, String>count().as("n")))
                    .sortBy(SortBy.of("@max_price", SortDirection.DESC))
                    .build()));
            table(ctx, byType, "type", "max_price", "n");
            ctx.out.info("Vários REDUCE no mesmo GROUPBY: MAX, COUNT, SUM, MIN, AVG, COUNT_DISTINCT, QUANTILE.");

            ctx.out.step("Liquidação de armas: APPLY \"@price * 0.9\" AS sale_price, as cinco mais caras");
            ctx.out.cmd("FT.AGGREGATE " + index + " \"@type:{arma}\" LOAD 2 @name @price"
                    + " APPLY \"@price * 0.9\" AS sale_price SORTBY 2 @price DESC LIMIT 0 5");
            List<Map<String, String>> sale = rows(redis.ftAggregate(index, "@type:{arma}", AggregateArgs.<String, String>builder()
                    .load("@name").load("@price")
                    .apply("@price * 0.9", "sale_price")
                    .sortBy(SortBy.of("@price", SortDirection.DESC))
                    .limit(0, 5)
                    .build()));
            table(ctx, sale, "name", "price", "sale_price");
            ctx.out.info("LOAD traz campos do documento para o pipeline; APPLY cria um campo calculado por linha.");

            ctx.out.step("Só para veteranos: FILTER \"@level >= 40\"");
            ctx.out.cmd("FT.AGGREGATE " + index + " \"*\" LOAD 3 @name @level @rarity"
                    + " FILTER \"@level >= 40\" SORTBY 2 @level DESC LIMIT 0 20");
            List<Map<String, String>> veterans = rows(redis.ftAggregate(index, "*", AggregateArgs.<String, String>builder()
                    .load("@name").load("@level").load("@rarity")
                    .filter("@level >= 40")
                    .sortBy(SortBy.of("@level", SortDirection.DESC))
                    .limit(0, 20)
                    .build()));
            table(ctx, veterans, "name", "level", "rarity");
            ctx.out.info("FILTER roda sobre as linhas do pipeline, depois de LOAD e APPLY; @level:[40 +inf] na query filtraria antes, pelo índice.");

            ctx.done("groups", String.valueOf(groups),
                    "types", String.valueOf(byType.size()),
                    "veterans", String.valueOf(veterans.size()),
                    "ran_" + ctx.client, "1");
        }
    }

    /** Lettuce wraps aggregation rows in SearchReply objects; flatten them into plain maps. */
    static List<Map<String, String>> rows(AggregationReply<String, String> reply) {
        List<Map<String, String>> rows = new ArrayList<>();
        for (SearchReply<String, String> page : reply.getReplies()) {
            for (SearchReply.SearchResult<String, String> hit : page.getResults()) rows.add(hit.getFields());
        }
        return rows;
    }

    static void table(Ctx ctx, List<Map<String, String>> rows, String... columns) {
        List<String[]> cells = new ArrayList<>();
        for (Map<String, String> row : rows) {
            String[] line = new String[columns.length];
            for (int i = 0; i < columns.length; i++) line[i] = JedisLab.format(columns[i], row.get(columns[i]));
            cells.add(line);
        }
        Table.print(ctx.out, columns, cells);
        ctx.out.kv("linhas", rows.size());
    }
}
