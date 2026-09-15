---
lesson: 201-02
title: "FT.AGGREGATE: a economia do reino"
minutes: 10
kind: lab
---

# FT.AGGREGATE: a economia do reino

<p class="lesson-meta">Lição 201-02 · Lab · 10 min</p>

Preço médio por raridade, item mais caro por tipo, tabela de liquidação com desconto: fazer isso na aplicação significa puxar todos os documentos e somar em Java. `FT.AGGREGATE` roda o pipeline no servidor, sobre o mesmo índice da lição anterior: agrupa (`GROUPBY`), reduz (`REDUCE COUNT`, `AVG`, `MAX`), calcula campos (`APPLY`), filtra, ordena e pagina. Só as linhas finais viajam pela rede.

## O que o lab faz

- Garantir que o índice `{p}:idx:items` existe (a lição cria com o mesmo schema se faltar)
- Agrupar por raridade com `COUNT` e `AVG price`, ordenado pela média
- Achar o `MAX price` de cada tipo
- Calcular `sale_price = price * 0.9` com `APPLY` e paginar com `LIMIT`
- Filtrar linhas do pipeline com `FILTER "@level >= 40"`

## Faça agora

```bash
./quest run 201-02 jedis
./quest run 201-02 lettuce    # opcional: mesmo lab, outro client
./quest verify 201-02
```

## O código

=== "Jedis"

    ```java
    AggregationResult byRarity = jedis.ftAggregate(index, new AggregationBuilder("*")
            .groupBy("@rarity", Reducers.count().as("n"), Reducers.avg("@price").as("avg_price"))
            .sortBy(SortedField.desc("@avg_price")));
    for (Row row : byRarity.getRows())
        System.out.println(row.getString("rarity") + " " + row.getLong("n") + " " + row.getDouble("avg_price"));

    AggregationResult byType = jedis.ftAggregate(index, new AggregationBuilder("*")
            .groupBy("@type", Reducers.max("@price").as("max_price"), Reducers.count().as("n"))
            .sortBy(SortedField.desc("@max_price")));

    AggregationResult sale = jedis.ftAggregate(index, new AggregationBuilder("@type:{arma}")
            .load("@name", "@price")
            .apply("@price * 0.9", "sale_price")
            .sortBy(SortedField.desc("@price"))
            .limit(0, 5));

    AggregationResult veterans = jedis.ftAggregate(index, new AggregationBuilder("*")
            .load("@name", "@level", "@rarity")
            .filter("@level >= 40")
            .sortBy(SortedField.desc("@level"))
            .limit(0, 20));
    ```

=== "Lettuce"

    ```java
    AggregationReply<String, String> byRarity = redis.ftAggregate(index, "*",
            AggregateArgs.<String, String>builder()
                    .groupBy(GroupBy.<String, String>of("@rarity")
                            .reduce(Reducer.<String, String>count().as("n"))
                            .reduce(Reducer.<String, String>avg("@price").as("avg_price")))
                    .sortBy(SortBy.of("@avg_price", SortDirection.DESC))
                    .build());
    for (SearchReply<String, String> page : byRarity.getReplies())
        for (SearchReply.SearchResult<String, String> row : page.getResults())
            System.out.println(row.getFields());   // {rarity=lendario, n=2, avg_price=48000}

    redis.ftAggregate(index, "@type:{arma}", AggregateArgs.<String, String>builder()
            .load("@name").load("@price")
            .apply("@price * 0.9", "sale_price")
            .sortBy(SortBy.of("@price", SortDirection.DESC))
            .limit(0, 5)
            .build());

    redis.ftAggregate(index, "*", AggregateArgs.<String, String>builder()
            .load("@name").load("@level").load("@rarity")
            .filter("@level >= 40")
            .sortBy(SortBy.of("@level", SortDirection.DESC))
            .limit(0, 20)
            .build());
    ```

## No Redis Insight

Nada novo aparece no **Browser**: agregação lê o índice e os documentos, não escreve chave nenhuma. No **Workbench**, cole `FT.AGGREGATE {p}:idx:items "*" GROUPBY 1 @rarity REDUCE COUNT 0 AS n REDUCE AVG 1 @price AS avg_price SORTBY 2 @avg_price DESC`: o Insight mostra o resultado como tabela, uma linha por raridade. No **Profiler**, rode a lição e compare o pipeline que o builder montou com o comando mostrado no console: é o mesmo, argumento por argumento.

??? note "Por dentro"

    O pipeline roda na ordem em que você escreve as etapas:

    | Etapa | O que faz | Na lição |
    |---|---|---|
    | query | filtra documentos pelo índice, antes de tudo | `"*"`, `"@type:{arma}"` |
    | `LOAD n @campo ...` | traz campos do documento para o pipeline (só o que precisa) | `LOAD 2 @name @price` |
    | `GROUPBY n @campo REDUCE ...` | agrupa e reduz: `COUNT 0`, `AVG 1 @price`, `MAX`, `MIN`, `SUM`, `COUNT_DISTINCT`, `QUANTILE`, `TOLIST` | `GROUPBY 1 @rarity REDUCE COUNT 0 AS n REDUCE AVG 1 @price AS avg_price` |
    | `APPLY "expr" AS alias` | campo calculado por linha: aritmética, `round`, `upper`, funções de data | `APPLY "@price * 0.9" AS sale_price` |
    | `FILTER "expr"` | filtra linhas do pipeline, depois de LOAD, APPLY ou GROUPBY | `FILTER "@level >= 40"` |
    | `SORTBY n @campo DESC` | ordena as linhas; `MAX k` limita quantas ordenar | `SORTBY 2 @avg_price DESC` |
    | `LIMIT offset count` | pagina; sem LIMIT o padrão é `0 10` | `LIMIT 0 5`, `LIMIT 0 20` |

    O resultado são linhas (mapas campo, valor), não documentos: no Jedis, `AggregationResult.getRows()` e `Row.getString`, `getLong`, `getDouble`; no Lettuce, `AggregationReply.getReplies()` traz páginas de `SearchReply`, e cada resultado expõe `getFields()`.

    `FILTER "@level >= 40"` e `@level:[40 +inf]` na query dão o mesmo resultado aqui, mas não custam o mesmo: a query usa o índice e descarta documentos antes de carregar qualquer coisa; o FILTER avalia linha a linha depois do LOAD. Use FILTER para campos calculados (`@n > 8` depois de um GROUPBY) e deixe o resto para a query.

??? tip "Em produção"

    - Filtre na query sempre que o campo estiver no índice; reserve `FILTER` para o que só existe depois de `APPLY` ou `GROUPBY`.
    - Resultados grandes pedem cursor em vez de `LIMIT` gigante: `WITHCURSOR COUNT 500` e `FT.CURSOR READ` (Jedis `ftAggregateIterator` ou `cursor(...)`; Lettuce `withCursor(...)` e `ftCursorread`).
    - Agregação gasta CPU do shard. Um painel que roda o mesmo GROUPBY a cada segundo merece um cache com TTL (uma chave JSON ou STRING com o resultado) em vez de bater no índice toda vez.

??? tip "Desafio"

    Acrescente um terceiro reducer ao grupo por raridade, `Reducers.min("@price").as("min_price")` no Jedis ou `Reducer.<String, String>min("@price").as("min_price")` no Lettuce, e ordene por `@n` decrescente. Esperado: comum e raro empatam com 11 itens; o `min_price` de comum é 10 (Anel de Cobre) e o de lendario é 46000.
