package com.emberrealm.quest.lessons.l201_01;

import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Lab;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.search.SearchReply;
import io.lettuce.core.search.arguments.QueryDialects;
import io.lettuce.core.search.arguments.SearchArgs;
import io.lettuce.core.search.arguments.SortByArgs;

import java.util.ArrayList;
import java.util.List;

/** 201-01 (Lettuce): same auction house, same index, same seven queries, now with SearchArgs builders. */
public final class LettuceLab implements Lab {

    @Override
    public void run(Ctx ctx) {
        String index = ItemsIndex.name(ctx);
        try (StatefulRedisConnection<String, String> connection = Clients.lettuceConnection()) {
            RedisCommands<String, String> redis = connection.sync();
            ItemsIndex.requireSeed(redis.exists(ctx.k("item", "espada-de-brasa")) == 1);

            ctx.out.step("A casa de leilões abre: um índice sobre os 42 itens JSON do seed");
            ItemsIndex.dropLettuce(ctx, redis);
            ItemsIndex.createLettuce(ctx, redis);
            ctx.out.cmd("FT._LIST");
            ctx.out.kv("índices", redis.ftList());
            ctx.out.info("O Lettuce 7.7 não expõe FT.INFO; FT._LIST confirma que o índice existe (o check usa FT.INFO pelo Jedis).");

            ctx.out.step("TEXT: quem vende espadas? @name:espada");
            search(ctx, redis, index, "@name:espada", "RETURN 3 name rarity price",
                    returning("name", "rarity", "price").build(), "name", "rarity", "price");
            ctx.out.info("Campos TEXT são tokenizados e passam por stemming: 'espada' acha 'Espada de Brasa' e 'Espada Curta de Ferro'.");

            ctx.out.step("TAG + NUMERIC: raros ou épicos entre os níveis 20 e 40");
            search(ctx, redis, index, "@rarity:{raro|epico} @level:[20 40]", "RETURN 3 name rarity level",
                    returning("name", "rarity", "level").build(), "name", "rarity", "level");
            ctx.out.info("TAG compara o valor exato ({raro|epico} é OU); NUMERIC filtra por faixa [min max].");

            ctx.out.step("A loja do mago iniciante: @classes:{mago} @price:[0 1000]");
            search(ctx, redis, index, "@classes:{mago} @price:[0 1000]", "RETURN 3 name type price",
                    returning("name", "type", "price").build(), "name", "type", "price");
            ctx.out.info("$.classes[*] virou uma TAG com vários valores por documento: basta um deles bater.");

            ctx.out.step("Digitou errado? Fuzzy %espda% (distância de Levenshtein 1)");
            SearchReply<String, String> fuzzy = search(ctx, redis, index, "%espda%", "RETURN 1 name DIALECT 2",
                    returning("name").dialect(QueryDialects.DIALECT2).build(), "name");
            ctx.out.info("Um % de cada lado tolera 1 edição; %%termo%% toleraria 2. Vale para qualquer campo TEXT, inclusive a descrição.");

            ctx.out.step("Prefixo: poc* contra poç*");
            SearchReply<String, String> prefixAscii = search(ctx, redis, index, "poc*", "RETURN 1 name DIALECT 2",
                    returning("name").dialect(QueryDialects.DIALECT2).build(), "name");
            ctx.out.info("Zero resultados: o token indexado é 'poção', com cedilha. Prefixo compara caracteres e não normaliza acentos.");
            SearchReply<String, String> prefixAccent = search(ctx, redis, index, "poç*", "RETURN 1 name DIALECT 2",
                    returning("name").dialect(QueryDialects.DIALECT2).build(), "name");
            ctx.out.info("Com a cedilha certa o prefixo acha as poções. Em produção, normalize a entrada do usuário antes de montar a query.");

            ctx.out.step("Os cinco itens mais caros do reino: SORTBY price DESC LIMIT 0 5");
            search(ctx, redis, index, "*", "SORTBY price DESC LIMIT 0 5 RETURN 3 name rarity price",
                    returning("name", "rarity", "price")
                            .sortBy(SortByArgs.<String>builder().attribute("price").descending().build())
                            .limit(0, 5)
                            .build(),
                    "name", "rarity", "price");
            ctx.out.info("SORTBY em campo NUMERIC ou TAG; RETURN devolve só o que a tela precisa, e não o JSON inteiro com o embedding.");

            ctx.done("num_docs", String.valueOf(ItemsIndex.DOCS),
                    "queries", "7",
                    "fuzzy_hits", String.valueOf(fuzzy.getCount()),
                    "prefix_hits", String.valueOf(prefixAscii.getCount() + prefixAccent.getCount()),
                    "ran_" + ctx.client, "1");
        }
    }

    /** SearchArgs builder with RETURN fields already set; callers add SORTBY, LIMIT or DIALECT. */
    static SearchArgs.Builder<String, String> returning(String... fields) {
        SearchArgs.Builder<String, String> builder = SearchArgs.builder();
        for (String field : fields) builder.returnField(field);
        return builder;
    }

    /** Runs one FT.SEARCH, shows the command and prints the result as a table. */
    static SearchReply<String, String> search(Ctx ctx, RedisCommands<String, String> redis, String index, String query,
                                              String shownArgs, SearchArgs<String, String> args, String... columns) {
        ctx.out.cmd("FT.SEARCH " + index + " \"" + query + "\" " + shownArgs);
        SearchReply<String, String> reply = redis.ftSearch(index, query, args);
        List<String[]> rows = new ArrayList<>();
        for (SearchReply.SearchResult<String, String> hit : reply.getResults()) {
            String[] row = new String[columns.length + 1];
            row[0] = ItemsIndex.shortId(ctx, hit.getId());
            for (int i = 0; i < columns.length; i++) {
                String value = hit.getFields().get(columns[i]);
                row[i + 1] = columns[i].equals("price") ? Table.money(value) : Table.str(value);
            }
            rows.add(row);
        }
        String[] headers = new String[columns.length + 1];
        headers[0] = "item";
        System.arraycopy(columns, 0, headers, 1, columns.length);
        Table.print(ctx.out, headers, rows);
        ctx.out.kv("total", reply.getCount());
        if (rows.size() < reply.getCount()) {
            ctx.out.info(shownArgs.contains("LIMIT")
                    ? "Mostrando " + rows.size() + " de " + reply.getCount() + " (paginação pelo LIMIT)."
                    : "Mostrando " + rows.size() + " de " + reply.getCount() + ": sem LIMIT o padrão é LIMIT 0 10.");
        }
        return reply;
    }
}
