---
lesson: 201-04
title: "Vector sets: itens parecidos"
minutes: 10
kind: lab
---

# Vector sets: itens parecidos

<p class="lesson-meta">Lição 201-04 · Lab · 10 min</p>

## Por que isso importa

"Quem comprou a Espada de Brasa também olhou..." não precisa de índice, schema nem documento JSON: precisa de um conjunto de vetores e de uma pergunta de vizinhança. Vector set é um tipo de dado nativo do Redis 8, como Set ou Sorted Set: uma chave, elementos com um vetor e um JSON de atributos, `VADD` para inserir e `VSIM` para achar os parecidos, com filtro opcional sobre os atributos. É a ferramenta certa quando a única pergunta é "o que parece com isto?".

## O que você vai fazer

- Criar `{p}:vs:items` com `VADD` dos 42 itens, cada um com atributos `{"type":..., "rarity":..., "level":..., "price":...}`
- Conferir `VCARD` (42) e `VDIM` (384)
- Buscar parecidos com a Espada de Brasa pelo elemento: `VSIM ... ELE espada-de-brasa COUNT 6 WITHSCORES`
- Restringir com `FILTER '.rarity == "epico"'`
- Buscar com o vetor da pergunta "uma arma para mago iniciante" e ler os atributos com `VGETATTR`

## Rode

```bash
./quest run 201-04 jedis
./quest run 201-04 lettuce
./quest check 201-04
```

## O código

=== "Jedis"

    ```java
    String key = ctx.k("vs", "items");
    jedis.unlink(key);
    for (World.Item item : World.items()) {
        String attrs = Neighbors.attributes(item);   // {"type":"arma","rarity":"raro","level":22,"price":2400}
        jedis.vadd(key, item.embedding(), item.id(), new VAddParams().setAttr(attrs));
    }
    long card = jedis.vcard(key);   // 42
    long dim = jedis.vdim(key);     // 384

    Map<String, Double> similar = jedis.vsimByElementWithScores(key, "espada-de-brasa",
            new VSimParams().count(6));
    Map<String, Double> epics = jedis.vsimByElementWithScores(key, "espada-de-brasa",
            new VSimParams().count(5).filter(".rarity == \"epico\""));
    Map<String, Double> byText = jedis.vsimWithScores(key, question.embedding(),
            new VSimParams().count(5));
    String attrs = jedis.vgetattr(key, "espada-de-brasa");
    ```

=== "Lettuce"

    ```java
    String key = ctx.k("vs", "items");
    redis.unlink(key);
    for (World.Item item : World.items()) {
        redis.vadd(key, item.id(), new VAddArgs().attributes(Neighbors.attributes(item)),
                Vectors.toDoubles(item.embedding()));
    }
    Long card = redis.vcard(key);   // 42
    Long dim = redis.vdim(key);     // 384

    Map<String, Double> similar = redis.vsimWithScore(key, new VSimArgs().count(6L), "espada-de-brasa");
    Map<String, Double> epics = redis.vsimWithScore(key,
            new VSimArgs().count(5L).filter(".rarity == \"epico\""), "espada-de-brasa");
    Map<String, Double> byText = redis.vsimWithScore(key, new VSimArgs().count(5L),
            Vectors.toDoubles(question.embedding()));
    String attrs = redis.vgetattr(key, "espada-de-brasa");
    ```

!!! tip "float[] ou Double..."
    O Jedis aceita o vetor como `float[]` (e `vaddFP32` com o blob binário); o Lettuce recebe `Double...`, por isso `Vectors.toDoubles`. Os dois viram `VADD key VALUES 384 ...` no fio, e o servidor guarda igual.

## O que olhar no Redis Insight

No **Browser**, `{p}:vs:items` aparece como uma chave do tipo vector set (o Insight mostra o tipo; para ver o conteúdo use o Workbench). No **Workbench**, rode `VCARD {p}:vs:items`, `VDIM {p}:vs:items`, `VINFO {p}:vs:items` (quantização `int8`, parâmetros do HNSW, tamanho) e `VSIM {p}:vs:items ELE espada-de-brasa COUNT 5 WITHSCORES`. Compare `MEMORY USAGE {p}:vs:items` com o tamanho dos 42 documentos JSON: o vector set guarda só vetor quantizado mais atributos.

## Por dentro

| Comando | O que faz |
|---|---|
| `VADD key VALUES 384 v1 ... v384 elem SETATTR json` | insere ou atualiza o vetor do elemento; a chave nasce no primeiro VADD e a dimensão fica fixa |
| `VCARD key` | quantos elementos |
| `VDIM key` | dimensão dos vetores |
| `VSIM key ELE elem COUNT n WITHSCORES` | vizinhos de um elemento que já está no set; ele mesmo vem primeiro, com score 1.0 |
| `VSIM key VALUES 384 ... COUNT n FILTER expr` | vizinhos de um vetor externo; FILTER usa os atributos JSON |
| `VGETATTR key elem`, `VSETATTR key elem json` | lê e troca os atributos |
| `VEMB key elem` | devolve o vetor guardado (já quantizado) |
| `VREM key elem` | remove um elemento |
| `VINFO key` | quantização, parâmetros HNSW, tamanho |

O score do `VSIM` é similaridade entre 0 e 1 (1.0 é idêntico), o contrário da distância que o `FT.SEARCH` KNN devolve (0 é idêntico). Expressões de `FILTER` leem o JSON de atributos: `.rarity == "epico"`, `.level >= 40 and .price < 20000`, `.type in ["arma", "armadura"]`.

Vector set ou Query Engine? Depende da pergunta:

| Você precisa de | Vector set (`VADD`, `VSIM`) | Query Engine (`FT.*`) |
|---|---|---|
| só "parecidos com X": recomendação, deduplicação, cache semântico simples | sim, uma chave e dois comandos | funciona, mas exige índice e schema |
| filtro por atributos do próprio elemento | `FILTER` sobre o JSON de atributos | `@tag:{...}` e `@num:[...]` na query |
| texto (TEXT, fuzzy, prefixo) junto com vetor | não | `FT.HYBRID` ou KNN com pré-filtro |
| agregações, GROUPBY, SORTBY, paginação | não | `FT.AGGREGATE`, `SORTBY`, `LIMIT` |
| documentos JSON ou Hash já existentes, indexados sozinhos | não: você faz o VADD | sim, pelo `PREFIX` do índice |
| quantização embutida e memória mínima | padrão `Q8`; `BIN` para ir além | `FLOAT32` ou `FLOAT16` no campo VECTOR |

## Em produção

- `Q8` (padrão) usa 4x menos memória que FLOAT32 com perda mínima; `NOQUANT` quando a exatidão importa e `BIN` para conjuntos enormes onde recall pode cair.
- `VSIM` é aproximado (HNSW): `EF` sobe a precisão ao custo de CPU e `TRUTH` força a varredura exata, útil para comparar em desenvolvimento.
- Um vector set é uma chave como outra qualquer: replicada, persistida, com TTL se você quiser, e inteira num só shard. Se crescer para milhões de elementos, particione por categoria ou por tenant.
- O `FILTER` roda durante a caminhada no grafo; se o filtro for muito seletivo, aumente `FILTER-EF` para não voltar com menos resultados que o `COUNT`.

## Desafio

Troque o filtro dos épicos por `.type == "arma" and .level <= 25` com `COUNT 3`. Esperado: a própria Espada de Brasa (nível 22, score 1,000), a Espada Curta de Ferro e a Varinha de Faísca. Rode `./quest check 201-04` de novo: continua verde, porque só a consulta mudou e o set segue com 42 elementos.
