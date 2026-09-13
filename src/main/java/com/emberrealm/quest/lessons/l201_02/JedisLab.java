package com.emberrealm.quest.lessons.l201_02;

import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Lab;
import com.emberrealm.quest.lessons.l201_01.ItemsIndex;
import com.emberrealm.quest.lessons.l201_01.Table;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.search.aggr.AggregationBuilder;
import redis.clients.jedis.search.aggr.AggregationResult;
import redis.clients.jedis.search.aggr.Reducers;
import redis.clients.jedis.search.aggr.Row;
import redis.clients.jedis.search.aggr.SortedField;

import java.util.ArrayList;
import java.util.List;

/**
 * 201-02 (Jedis): the realm's economy with FT.AGGREGATE. GROUPBY + REDUCE for statistics per rarity
 * and per type, APPLY for a computed sale price, FILTER for high-level items. The server computes,
 * the JVM only prints.
 */
public final class JedisLab implements Lab {

    @Override
    public void run(Ctx ctx) {
        String index = ItemsIndex.name(ctx);
        try (RedisClient jedis = Clients.jedis()) {
            ItemsIndex.requireSeed(jedis.exists(ctx.k("item", "espada-de-brasa")));

            ctx.out.step("O índice da casa de leilões (lição 201-01) precisa existir");
            ItemsIndex.ensureJedis(ctx, jedis);

            ctx.out.step("Quanto custa cada raridade? GROUPBY rarity com COUNT e AVG");
            ctx.out.cmd("FT.AGGREGATE " + index + " \"*\" GROUPBY 1 @rarity REDUCE COUNT 0 AS n"
                    + " REDUCE AVG 1 @price AS avg_price SORTBY 2 @avg_price DESC");
            AggregationResult byRarity = jedis.ftAggregate(index, new AggregationBuilder("*")
                    .groupBy("@rarity", Reducers.count().as("n"), Reducers.avg("@price").as("avg_price"))
                    .sortBy(SortedField.desc("@avg_price")));
            table(ctx, byRarity, "rarity", "n", "avg_price");
            int groups = byRarity.getRows().size();
            ctx.out.info("Cada linha é um grupo, não um documento: o servidor agrupou e calculou; a JVM só imprimiu.");

            ctx.out.step("O item mais caro de cada tipo: GROUPBY type REDUCE MAX price");
            ctx.out.cmd("FT.AGGREGATE " + index + " \"*\" GROUPBY 1 @type REDUCE MAX 1 @price AS max_price"
                    + " REDUCE COUNT 0 AS n SORTBY 2 @max_price DESC");
            AggregationResult byType = jedis.ftAggregate(index, new AggregationBuilder("*")
                    .groupBy("@type", Reducers.max("@price").as("max_price"), Reducers.count().as("n"))
                    .sortBy(SortedField.desc("@max_price")));
            table(ctx, byType, "type", "max_price", "n");
            ctx.out.info("Vários REDUCE no mesmo GROUPBY: MAX, COUNT, SUM, MIN, AVG, COUNT_DISTINCT, QUANTILE.");

            ctx.out.step("Liquidação de armas: APPLY \"@price * 0.9\" AS sale_price, as cinco mais caras");
            ctx.out.cmd("FT.AGGREGATE " + index + " \"@type:{arma}\" LOAD 2 @name @price"
                    + " APPLY \"@price * 0.9\" AS sale_price SORTBY 2 @price DESC LIMIT 0 5");
            AggregationResult sale = jedis.ftAggregate(index, new AggregationBuilder("@type:{arma}")
                    .load("@name", "@price")
                    .apply("@price * 0.9", "sale_price")
                    .sortBy(SortedField.desc("@price"))
                    .limit(0, 5));
            table(ctx, sale, "name", "price", "sale_price");
            ctx.out.info("LOAD traz campos do documento para o pipeline; APPLY cria um campo calculado por linha.");

            ctx.out.step("Só para veteranos: FILTER \"@level >= 40\"");
            ctx.out.cmd("FT.AGGREGATE " + index + " \"*\" LOAD 3 @name @level @rarity"
                    + " FILTER \"@level >= 40\" SORTBY 2 @level DESC LIMIT 0 20");
            AggregationResult veterans = jedis.ftAggregate(index, new AggregationBuilder("*")
                    .load("@name", "@level", "@rarity")
                    .filter("@level >= 40")
                    .sortBy(SortedField.desc("@level"))
                    .limit(0, 20));
            table(ctx, veterans, "name", "level", "rarity");
            ctx.out.info("FILTER roda sobre as linhas do pipeline, depois de LOAD e APPLY; @level:[40 +inf] na query filtraria antes, pelo índice.");

            ctx.done("groups", String.valueOf(groups),
                    "types", String.valueOf(byType.getRows().size()),
                    "veterans", String.valueOf(veterans.getRows().size()),
                    "ran_" + ctx.client, "1");
        }
    }

    /** Prints aggregation rows as a table; prices with grouping, averages with two decimals. */
    static void table(Ctx ctx, AggregationResult result, String... columns) {
        List<String[]> rows = new ArrayList<>();
        for (Row row : result.getRows()) {
            String[] cells = new String[columns.length];
            for (int i = 0; i < columns.length; i++) cells[i] = format(columns[i], row.getString(columns[i]));
            rows.add(cells);
        }
        Table.print(ctx.out, columns, rows);
        ctx.out.kv("linhas", result.getRows().size());
    }

    static String format(String column, String value) {
        if (column.equals("avg_price") || column.equals("sale_price")) return Table.decimal(value, 2);
        if (column.endsWith("price")) return Table.money(value);
        return Table.str(value);
    }
}
