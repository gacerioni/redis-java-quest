package com.emberrealm.quest.lessons.l201_01;

import com.emberrealm.quest.core.Clients;
import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Lab;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.args.SortingOrder;
import redis.clients.jedis.search.Document;
import redis.clients.jedis.search.FTSearchParams;
import redis.clients.jedis.search.SearchResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 201-01 (Jedis): the auction house. One FT.CREATE over the 42 JSON items, then FT.SEARCH with
 * TEXT, TAG and NUMERIC filters, fuzzy and prefix matching, sorting and RETURN.
 */
public final class JedisLab implements Lab {

    @Override
    public void run(Ctx ctx) {
        String index = ItemsIndex.name(ctx);
        try (RedisClient jedis = Clients.jedis()) {
            ItemsIndex.requireSeed(jedis.exists(ctx.k("item", "espada-de-brasa")));

            ctx.out.step("A casa de leilões abre: um índice sobre os 42 itens JSON do seed");
            ItemsIndex.dropJedis(ctx, jedis);
            ItemsIndex.createJedis(ctx, jedis);
            ctx.out.cmd("FT.INFO " + index);
            Map<String, Object> info = jedis.ftInfo(index);
            long numDocs = ItemsIndex.numDocs(info);
            ctx.out.kv("num_docs", numDocs);
            ctx.out.info("Todo JSON.SET em " + ItemsIndex.prefix(ctx) + "* entra no índice sozinho: sem reindexação, sem job noturno.");

            ctx.out.step("TEXT: quem vende espadas? @name:espada");
            search(ctx, jedis, index, "@name:espada", "RETURN 3 name rarity price",
                    FTSearchParams.searchParams().returnFields("name", "rarity", "price"),
                    "name", "rarity", "price");
            ctx.out.info("Campos TEXT são tokenizados e passam por stemming: 'espada' acha 'Espada de Brasa' e 'Espada Curta de Ferro'.");

            ctx.out.step("TAG + NUMERIC: raros ou épicos entre os níveis 20 e 40");
            search(ctx, jedis, index, "@rarity:{raro|epico} @level:[20 40]", "RETURN 3 name rarity level",
                    FTSearchParams.searchParams().returnFields("name", "rarity", "level"),
                    "name", "rarity", "level");
            ctx.out.info("TAG compara o valor exato ({raro|epico} é OU); NUMERIC filtra por faixa [min max].");

            ctx.out.step("A loja do mago iniciante: @classes:{mago} @price:[0 1000]");
            search(ctx, jedis, index, "@classes:{mago} @price:[0 1000]", "RETURN 3 name type price",
                    FTSearchParams.searchParams().returnFields("name", "type", "price"),
                    "name", "type", "price");
            ctx.out.info("$.classes[*] virou uma TAG com vários valores por documento: basta um deles bater.");

            ctx.out.step("Digitou errado? Fuzzy %espda% (distância de Levenshtein 1)");
            SearchResult fuzzy = search(ctx, jedis, index, "%espda%", "RETURN 1 name DIALECT 2",
                    FTSearchParams.searchParams().returnFields("name").dialect(2), "name");
            ctx.out.info("Um % de cada lado tolera 1 edição; %%termo%% toleraria 2. Vale para qualquer campo TEXT, inclusive a descrição.");

            ctx.out.step("Prefixo: poc* contra poç*");
            SearchResult prefixAscii = search(ctx, jedis, index, "poc*", "RETURN 1 name DIALECT 2",
                    FTSearchParams.searchParams().returnFields("name").dialect(2), "name");
            ctx.out.info("Zero resultados: o token indexado é 'poção', com cedilha. Prefixo compara caracteres e não normaliza acentos.");
            SearchResult prefixAccent = search(ctx, jedis, index, "poç*", "RETURN 1 name DIALECT 2",
                    FTSearchParams.searchParams().returnFields("name").dialect(2), "name");
            ctx.out.info("Com a cedilha certa o prefixo acha as poções. Em produção, normalize a entrada do usuário antes de montar a query.");

            ctx.out.step("Os cinco itens mais caros do reino: SORTBY price DESC LIMIT 0 5");
            search(ctx, jedis, index, "*", "SORTBY price DESC LIMIT 0 5 RETURN 3 name rarity price",
                    FTSearchParams.searchParams().sortBy("price", SortingOrder.DESC).limit(0, 5)
                            .returnFields("name", "rarity", "price"),
                    "name", "rarity", "price");
            ctx.out.info("SORTBY em campo NUMERIC ou TAG; RETURN devolve só o que a tela precisa, e não o JSON inteiro com o embedding.");

            ctx.done("num_docs", String.valueOf(numDocs),
                    "queries", "7",
                    "fuzzy_hits", String.valueOf(fuzzy.getTotalResults()),
                    "prefix_hits", String.valueOf(prefixAscii.getTotalResults() + prefixAccent.getTotalResults()),
                    "ran_" + ctx.client, "1");
        }
    }

    /** Runs one FT.SEARCH, shows the command and prints the result as a table. */
    static SearchResult search(Ctx ctx, RedisClient jedis, String index, String query, String shownArgs,
                               FTSearchParams params, String... columns) {
        ctx.out.cmd("FT.SEARCH " + index + " \"" + query + "\" " + shownArgs);
        SearchResult result = jedis.ftSearch(index, query, params);
        List<String[]> rows = new ArrayList<>();
        for (Document doc : result.getDocuments()) {
            String[] row = new String[columns.length + 1];
            row[0] = ItemsIndex.shortId(ctx, doc.getId());
            for (int i = 0; i < columns.length; i++) {
                Object value = doc.get(columns[i]);
                row[i + 1] = columns[i].equals("price") ? Table.money(value) : Table.str(value);
            }
            rows.add(row);
        }
        String[] headers = new String[columns.length + 1];
        headers[0] = "item";
        System.arraycopy(columns, 0, headers, 1, columns.length);
        Table.print(ctx.out, headers, rows);
        ctx.out.kv("total", result.getTotalResults());
        if (rows.size() < result.getTotalResults()) {
            ctx.out.info(shownArgs.contains("LIMIT")
                    ? "Mostrando " + rows.size() + " de " + result.getTotalResults() + " (paginação pelo LIMIT)."
                    : "Mostrando " + rows.size() + " de " + result.getTotalResults() + ": sem LIMIT o padrão é LIMIT 0 10.");
        }
        return result;
    }
}
