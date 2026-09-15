---
lesson: 201-03
title: "FT.HYBRID: busca híbrida"
minutes: 12
kind: lab
---

# FT.HYBRID: busca híbrida

<p class="lesson-meta">Lição 201-03 · Lab · 12 min</p>

"Uma arma para mago iniciante": texto puro (`@type:{arma}`) devolve as 15 armas sem saber qual responde à pergunta; busca vetorial pura (KNN sobre o embedding) entende a intenção, mas devolve um manto porque "parece" com a frase. `FT.HYBRID` (Redis 8.4+) roda as duas buscas no servidor e funde os rankings com RRF — quem vai bem nas duas listas sobe. Os embeddings dos itens já estão no seed e a pergunta vem com o vetor pronto: a lição roda sem modelo instalado.

## O que o lab faz

- Reusar o índice `{p}:idx:items` com o campo `VECTOR` da lição 201-01
- Rodar a busca só texto (`@type:{arma}`) e a só vetor (`*=>[KNN 5 @embedding $vec AS score]`)
- Passar o vetor como parâmetro binário FLOAT32 little-endian (`Vectors.toBlob`)
- Rodar `FT.HYBRID ... SEARCH ... VSIM ... COMBINE RRF` e comparar as três tabelas
- Ver a lição seguir de pé num servidor sem `FT.HYBRID`: aviso, dica e marcador `hybrid=unsupported`

## Faça agora

```bash
./quest run 201-03 jedis
./quest run 201-03 lettuce    # opcional: mesmo lab, outro client
./quest verify 201-03
```

Variações: `QUEST_QUERY=q3 ./quest run 201-03 jedis` usa outra pergunta pré-computada (q1 a q4). Com um Ollama local (`ollama pull all-minilm`), `QUEST_QUESTION="um escudo barato para paladino" ./quest run 201-03 lettuce` gera o embedding da sua própria pergunta.

## O código

=== "Jedis"

    ```java
    byte[] blob = Vectors.toBlob(question.embedding());   // FLOAT32 little-endian, 1536 bytes

    // so vetor: KNN 5, distancia de cosseno em "score" (menor = mais parecido)
    SearchResult knn = jedis.ftSearch(index, "*=>[KNN 5 @embedding $vec AS score]",
            FTSearchParams.searchParams()
                    .addParam("vec", blob)
                    .sortBy("score", SortingOrder.ASC)
                    .returnFields("name", "type", "score")
                    .dialect(2));

    // hibrido: texto + vetor, fundidos por RRF
    HybridResult hybrid = jedis.ftHybrid(index, FTHybridParams.builder()
            .search(FTHybridSearchParams.builder().query("@type:{arma}").build())
            .vectorSearch(FTHybridVectorParams.builder()
                    .field("@embedding").vector("$vec")
                    .method(FTHybridVectorParams.Knn.of(10)).build())
            .combine(Combiners.rrf().window(20))
            .postProcessing(FTHybridPostProcessingParams.builder()
                    .load("@__key", "@__score", "@name", "@rarity", "@price")
                    .limit(Limit.of(0, 5)).build())
            .param("vec", blob)
            .build());
    for (Document d : hybrid.getDocuments())
        System.out.println(d.getId() + " " + d.getScore() + " " + d.get("name"));
    ```

=== "Lettuce"

    ```java
    byte[] blob = Vectors.toBlob(question.embedding());

    // KNN puro: SearchArgs.param(K, V) usa o codec da conexao; para mandar bytes,
    // abra uma conexao com valores byte[] a partir do mesmo RedisClient
    try (StatefulRedisConnection<String, byte[]> binary =
                 Clients.lettuce().connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE))) {
        SearchReply<String, byte[]> knn = binary.sync().ftSearch(index,
                "*=>[KNN 5 @embedding $vec AS score]".getBytes(StandardCharsets.UTF_8),
                SearchArgs.<String, byte[]>builder()
                        .param("vec", blob)
                        .sortBy(SortByArgs.<String>builder().attribute("score").build())
                        .returnField("name").returnField("type").returnField("score")
                        .dialect(QueryDialects.DIALECT2).build());
    }

    // hibrido: HybridArgs ja tem param(K, byte[])
    HybridReply<String, String> hybrid = redis.ftHybrid(index, HybridArgs.<String, String>builder()
            .search(HybridSearchArgs.<String, String>builder().query("@type:{arma}").build())
            .vectorSearch(HybridVectorArgs.<String, String>builder()
                    .field("@embedding").vector("$vec")
                    .method(HybridVectorArgs.Knn.of(10)).build())
            .combine(Combiners.<String>rrf().window(20))
            .postProcessing(PostProcessingArgs.<String, String>builder()
                    .load("@__key", "@__score", "@name", "@rarity", "@price")
                    .limit(Limit.<String, String>of(0, 5)).build())
            .param("vec", blob)
            .build());
    for (Map<String, String> row : hybrid.getResults())
        System.out.println(row.get("__key") + " " + row.get("__score") + " " + row.get("name"));
    ```

!!! note "Builders ou comando cru?"
    Os dois builders funcionaram como estão acima (Jedis 8.0.1 e Lettuce 7.7.0), sem precisar de `sendCommand` ou `dispatch`. Dois detalhes que o servidor exige: no `LOAD` do FT.HYBRID os campos vão com `@` (`@name`, e não `name`), e `__key` e `__score` são os nomes da chave e da nota fundida. O Jedis mapeia esses dois para `Document.getId()` e `Document.getScore()`; o Lettuce devolve os dois como campos do mapa. Já o Lettuce só aceita o vetor como bytes em `HybridArgs.param(K, byte[])`; no `FT.SEARCH` comum o `SearchArgs.param(K, V)` segue o codec da conexão, daí a segunda conexão com `ByteArrayCodec`.

## No Redis Insight

No **Workbench**, `FT.INFO {p}:idx:items` mostra o atributo `embedding` com `VECTOR`, `FLAT`, `FLOAT32`, `DIM 384`, `COSINE`. O Workbench não é bom lugar para colar um parâmetro binário, então use o **Profiler** enquanto a lição roda: você vê o `FT.SEARCH` com `PARAMS 2 vec` seguido de 1536 bytes binários e o `FT.HYBRID` com `SEARCH`, `VSIM`, `COMBINE RRF`, `LOAD`. Nada é escrito no banco.

??? note "Por dentro"

    Três buscas, três respostas para a mesma pergunta:

    | Busca | Comando | Entende | Não entende |
    |---|---|---|---|
    | Texto | `FT.SEARCH idx "@type:{arma}"` | filtros exatos, palavras | intenção, sinônimos |
    | Vetor | `FT.SEARCH idx "*=>[KNN 5 @embedding $vec AS score]" PARAMS 2 vec <blob> SORTBY score DIALECT 2` | semântica ("arma de mago" fica perto de "cajado") | restrições duras: pode devolver um manto |
    | Híbrida | `FT.HYBRID idx SEARCH "@type:{arma}" VSIM @embedding $vec KNN 2 K 10 COMBINE RRF 2 WINDOW 20 LOAD 5 @__key @__score @name @rarity @price` | os dois lados | nada de graça: são duas buscas por chamada |

    Peças da consulta vetorial e da híbrida:

    | Peça | Significado |
    |---|---|
    | `*=>[KNN 5 @embedding $vec AS score]` | 5 vizinhos mais próximos; o `*` pode virar um pré-filtro: `@type:{arma}=>[KNN 5 ...]` |
    | `PARAMS 2 vec <blob>` | o vetor em bytes FLOAT32 little-endian (`Vectors.toBlob`): 384 x 4 = 1536 bytes |
    | `SORTBY score` | distância de cosseno crescente: 0 é idêntico; similaridade = 1 - distância |
    | `DIALECT 2` | obrigatório para `=>[KNN]` e para `$params` |
    | `VSIM @embedding $vec KNN 2 K 10` | lado vetorial do FT.HYBRID; o 2 é a quantidade de argumentos que seguem |
    | `COMBINE RRF 2 WINDOW 20` | fusão por posição; `CONSTANT 60` é o k da fórmula |
    | `LOAD 5 @__key @__score @name ...` | campos com `@`; `__key` é a chave e `__score` a nota final |

    RRF (Reciprocal Rank Fusion) ignora as notas de cada busca e usa só as posições: cada documento recebe `1 / (k + posição)` em cada lista e as parcelas são somadas (k = 60 por padrão). Um item em 1º no texto e em 3º no vetor soma 1/61 + 1/63 = 0,0323; um item em 1º em uma lista só fica com 1/61 = 0,0164. Isso deixa texto e vetor em pé de igualdade sem normalizar escalas diferentes (BM25 de um lado, cosseno do outro). `WINDOW 20` diz quantos candidatos de cada lista entram na fusão. A alternativa é `COMBINE LINEAR 4 ALPHA 0.3 BETA 0.7`, uma soma ponderada das notas normalizadas, que dá mais controle e pede mais calibração.

    O pré-filtro `@type:{arma}=>[KNN 5 ...]` no FT.SEARCH também combina texto e vetor, mas de outro jeito: o texto só restringe os candidatos e o ranking é 100% vetorial. No FT.HYBRID as duas listas têm voz no resultado final.

??? tip "Em produção"

    - `FLAT` é busca exata e serve bem até dezenas de milhares de vetores; acima disso use `HNSW` (`M`, `EF_CONSTRUCTION`, `EF_RUNTIME`) e aceite resultados aproximados.
    - Gere o embedding da pergunta com o mesmo modelo e a mesma dimensão dos documentos; um índice não mistura modelos.
    - `FT.HYBRID` pede Redis 8.4 ou mais novo (o Redis Cloud já tem). Em servidor mais velho, rode as duas buscas e funda na aplicação: RRF são dez linhas de Java, e o lab mostra o caminho quando o comando não existe.
    - Memória: 384 floats x 4 bytes = 1,5 KB por documento mais o índice. `RETURN` e `LOAD` mantêm o embedding fora do fio.

??? tip "Desafio"

    Troque `Knn.of(10)` por `Knn.of(3)` e `window(20)` por `window(5)`, rode de novo e compare as duas tabelas híbridas: menos candidatos vetoriais entram na fusão e o `rrf` dos itens que só apareceram no texto muda. Depois rode com `QUEST_QUERY=q3` (armadura pesada para tanque) mantendo `@type:{arma}` no lado do texto e observe o conflito entre as duas listas; troque o texto para `@type:{armadura}` e veja as duas concordarem.
