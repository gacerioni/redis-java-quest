---
lesson: 201-01
title: "JSON e FT.SEARCH: a casa de leilões"
minutes: 12
kind: lab
---

# JSON e FT.SEARCH: a casa de leilões

<p class="lesson-meta">Lição 201-01 · Lab · 12 min</p>

Responder "espadas raras até o nível 40, mais baratas primeiro" em milissegundos sobre documentos JSON; sem índice, a única saída seria varrer todas as chaves na aplicação. O Redis Query Engine cria um índice secundário: um `FT.CREATE` declara quais campos são texto, tag, número ou vetor, e cada `JSON.SET` alimenta o índice sozinho. `FT.SEARCH` filtra, ordena e devolve só os campos que a tela precisa.

## O que o lab faz

- Criar o índice `{p}:idx:items` sobre os 42 itens JSON, já com um campo `VECTOR` para as lições 201-03 e 201-04
- Buscar por texto (`@name:espada`) e por tag mais faixa numérica (`@rarity:{raro|epico} @level:[20 40]`)
- Tolerar erro de digitação com fuzzy (`%espda%`) e completar por prefixo (`poç*`)
- Ordenar por preço com `SORTBY` e devolver só três campos com `RETURN`
- Conferir com `FT.INFO` que os 42 documentos entraram no índice

## Faça agora

```bash
./quest run 201-01 jedis
./quest run 201-01 lettuce    # opcional: mesmo lab, outro client
./quest verify 201-01
```

## O código

=== "Jedis"

    ```java
    String index = ctx.k("idx", "items");
    List<SchemaField> schema = List.of(
            TextField.of("$.name").as("name"),
            TextField.of("$.description").as("description"),
            TagField.of("$.type").as("type"),
            TagField.of("$.rarity").as("rarity"),
            NumericField.of("$.level").as("level"),
            NumericField.of("$.price").as("price"),
            TagField.of("$.classes[*]").as("classes"),
            VectorField.builder().fieldName("$.embedding").as("embedding")
                    .algorithm(VectorField.VectorAlgorithm.FLAT)
                    .addAttribute("TYPE", "FLOAT32").addAttribute("DIM", 384)
                    .addAttribute("DISTANCE_METRIC", "COSINE").build());
    jedis.ftCreate(index,
            FTCreateParams.createParams().on(IndexDataType.JSON).addPrefix(ctx.k("item", "")),
            schema);

    SearchResult r = jedis.ftSearch(index, "@rarity:{raro|epico} @level:[20 40]",
            FTSearchParams.searchParams().returnFields("name", "rarity", "level"));
    for (Document d : r.getDocuments()) System.out.println(d.getId() + " " + d.get("name"));

    jedis.ftSearch(index, "%espda%", FTSearchParams.searchParams().returnFields("name").dialect(2));
    jedis.ftSearch(index, "*", FTSearchParams.searchParams()
            .sortBy("price", SortingOrder.DESC).limit(0, 5).returnFields("name", "rarity", "price"));
    ```

=== "Lettuce"

    ```java
    String index = ctx.k("idx", "items");
    List<FieldArgs<String>> schema = List.of(
            TextFieldArgs.<String>builder().name("$.name").as("name").build(),
            TextFieldArgs.<String>builder().name("$.description").as("description").build(),
            TagFieldArgs.<String>builder().name("$.type").as("type").build(),
            TagFieldArgs.<String>builder().name("$.rarity").as("rarity").build(),
            NumericFieldArgs.<String>builder().name("$.level").as("level").build(),
            NumericFieldArgs.<String>builder().name("$.price").as("price").build(),
            TagFieldArgs.<String>builder().name("$.classes[*]").as("classes").build(),
            VectorFieldArgs.<String>builder().name("$.embedding").as("embedding")
                    .flat().type(VectorFieldArgs.VectorType.FLOAT32).dimensions(384)
                    .distanceMetric(VectorFieldArgs.DistanceMetric.COSINE).build());
    redis.ftCreate(index, CreateArgs.<String, String>builder()
            .on(CreateArgs.TargetType.JSON).withPrefix(ctx.k("item", "")).build(), schema);

    SearchReply<String, String> r = redis.ftSearch(index, "@rarity:{raro|epico} @level:[20 40]",
            SearchArgs.<String, String>builder()
                    .returnField("name").returnField("rarity").returnField("level").build());
    for (SearchReply.SearchResult<String, String> hit : r.getResults())
        System.out.println(hit.getId() + " " + hit.getFields().get("name"));

    redis.ftSearch(index, "%espda%", SearchArgs.<String, String>builder()
            .returnField("name").dialect(QueryDialects.DIALECT2).build());
    redis.ftSearch(index, "*", SearchArgs.<String, String>builder()
            .sortBy(SortByArgs.<String>builder().attribute("price").descending().build())
            .limit(0, 5).returnField("name").returnField("rarity").returnField("price").build());
    ```

!!! note "Um índice, dois clients"
    Os dois labs criam exatamente o mesmo índice (mesmo nome, mesmo schema). O Lettuce 7.7 não expõe `FT.INFO`; ele confirma o índice com `FT._LIST`, e o `verify` usa `FT.INFO` pelo Jedis.

## No Redis Insight

No **Browser**, filtre por `{p}:item:*` e abra `{p}:item:espada-de-brasa`: é um JSON com `name`, `type`, `rarity`, `level`, `price`, `classes` e um array `embedding` de 384 números. O índice não aparece como chave. No **Workbench**, rode `FT._LIST`, depois `FT.INFO {p}:idx:items` e procure `num_docs` (42), `indexing` (0, terminou) e `hash_indexing_failures` (0). Repita uma consulta da lição, por exemplo `FT.SEARCH {p}:idx:items "@classes:{mago} @price:[0 1000]" RETURN 3 name type price`. No **Profiler**, rode a lição e veja os `FT.SEARCH` saindo com os argumentos exatos que os builders montaram.

??? note "Por dentro"

    Tipos de campo e o que cada um entende na query:

    | Tipo | Serve para | Na query |
    |---|---|---|
    | `TEXT` | linguagem natural: tokeniza, tira stopwords, aplica stemming | `@name:espada`, fuzzy `%espda%`, prefixo `esp*` |
    | `TAG` | valor exato: categorias, ids, listas (`$.classes[*]`) | `@rarity:{epico}`, `@classes:{mago}` |
    | `NUMERIC` | faixas e ordenação | `@level:[20 40]`, `@price:[0 1000]` |
    | `VECTOR` | similaridade de embeddings | `*=>[KNN 5 @embedding $vec]` (lição 201-03) |

    Em TAG, vários valores entre chaves são OU: `@rarity:{raro|epico}` traz raros e épicos. Dois termos separados por espaço são E: `@rarity:{raro|epico} @level:[20 40]`.

    Sintaxe da consulta:

    | Sintaxe | Efeito |
    |---|---|
    | `termo` | procura o termo em todos os campos TEXT, com stemming |
    | `@campo:termo` | restringe a um campo |
    | `%termo%` | fuzzy, distância de Levenshtein 1; `%%termo%%` tolera 2 |
    | `termo*` | prefixo, compara caracteres (por isso `poc*` não acha `poção`, mas `poç*` acha) |
    | `@num:[min max]` | faixa inclusiva; `-inf` e `+inf` valem; `(20` exclui a borda |
    | `SORTBY campo DESC` | ordena por NUMERIC ou TAG |
    | `LIMIT 0 5` | paginação; sem LIMIT o padrão é `0 10`, por isso a consulta com 12 resultados mostra 10 |
    | `RETURN 3 name rarity price` | devolve só esses campos em vez do JSON inteiro |
    | `DIALECT 2` | sintaxe atual: obrigatória para KNN e `$params`, recomendada sempre |

    Comandos da lição:

    | Comando | O que faz |
    |---|---|
    | `FT.CREATE idx ON JSON PREFIX 1 {p}:item: SCHEMA ...` | cria o índice e indexa em background as chaves que já existem |
    | `FT.SEARCH idx "query" ...` | busca e devolve documentos (ou só campos, com RETURN) |
    | `FT.INFO idx` | `num_docs`, campos, `hash_indexing_failures` |
    | `FT.DROPINDEX idx` | apaga só o índice; com `DD` apagaria os documentos também |
    | `FT._LIST` | lista os índices do banco |

??? tip "Em produção"

    - O índice é derivado: recriar é barato e os documentos ficam. Para trocar o schema sem parar a aplicação, crie `idx_v2`, aponte um alias com `FT.ALIASUPDATE` e apague o antigo depois.
    - Use `RETURN` (ou `LOAD` no aggregate) sempre: sem isso cada resultado traz o JSON inteiro, e o `embedding` sozinho tem 1,5 KB.
    - Normalize a entrada do usuário (acentos, caixa) antes de montar `%fuzzy%` e `prefixo*`, e escape `@`, `{`, `[`, `:` e `|` antes de concatenar texto de fora na query.

??? tip "Desafio"

    Acrescente `@type:{arma}` à consulta da loja do mago e troque o `SORTBY` para `price` crescente. Resultado esperado: só o Cajado de Aprendiz (25 de ouro) e a Varinha de Faísca (180), nessa ordem. Rode `./quest verify 201-01` de novo: continua verde, porque o índice e os 42 documentos não mudaram.
