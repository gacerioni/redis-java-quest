---
lesson: 101-06
title: "Bloom: esse baú já foi aberto?"
minutes: 8
kind: lab
---

# Bloom: esse baú já foi aberto?

<p class="lesson-meta">Lição 101-06 · Lab · 8 min</p>

"Esse item já foi visto?"; responder isso milhões de vezes sem guardar a lista inteira em memória. Um Bloom filter responde com alguns KB: "não" é certeza absoluta; "sim" é "provavelmente", com uma taxa de falso positivo que você escolhe. É uma estrutura probabilística que vem no Redis 8 e no Redis Cloud, com comandos `BF.*`.

## O que o lab faz

- Reservar um filtro para 1000 baús com 1% de erro (`BF.RESERVE`)
- Marcar baús abertos com `BF.ADD` e `BF.MADD`
- Perguntar `BF.EXISTS` para um baú aberto e para um nunca aberto
- Testar 1000 baús que ninguém abriu e contar os falsos positivos
- Comparar a memória do filtro com a de um `SET` que guarda os mesmos ids

## Faça agora

```bash
./quest run 101-06 jedis
./quest run 101-06 lettuce    # opcional: mesmo lab, outro client
./quest verify 101-06
```

## O código

=== "Jedis"

    ```java
    String chests = ctx.k("chests", "opened");
    try (RedisClient jedis = Clients.jedis()) {
        jedis.unlink(chests);
        jedis.bfReserve(chests, 0.01, 1000);                   // 1% error, room for 1000 chests

        boolean fresh = jedis.bfAdd(chests, "chest-001");      // true: some bit was off
        boolean again = jedis.bfAdd(chests, "chest-001");      // false: all bits already on
        List<Boolean> added = jedis.bfMAdd(chests, "chest-002", "chest-003", "chest-004");

        boolean opened = jedis.bfExists(chests, "chest-001");  // true: probably opened
        boolean never = jedis.bfExists(chests, "chest-777");   // false: certainly not

        jedis.bfMAdd(chests, ids);                             // ids = chest-001 .. chest-1000, 250 per call
        List<Boolean> answers = jedis.bfMExists(chests, neverOpened);   // chest-5001 .. chest-6000
        int falsePositives = countTrue(answers);               // every true here is a false positive

        long card = jedis.bfCard(chests);                      // estimated items
        Map<String, Object> info = jedis.bfInfo(chests);       // Capacity, Size, Number of filters...
        Long bytes = jedis.memoryUsage(chests);
    }
    ```

=== "Lettuce"

    ```java
    try (StatefulRedisConnection<String, String> connection = Clients.lettuceConnection()) {
        RedisCommands<String, String> redis = connection.sync();
        redis.unlink(chests);
        redis.bfReserve(chests, 0.01, 1000);

        Boolean fresh = redis.bfAdd(chests, "chest-001");
        Boolean again = redis.bfAdd(chests, "chest-001");
        List<Boolean> added = redis.bfMAdd(chests, "chest-002", "chest-003", "chest-004");

        Boolean opened = redis.bfExists(chests, "chest-001");
        Boolean never = redis.bfExists(chests, "chest-777");

        redis.bfMAdd(chests, ids);
        List<Boolean> answers = redis.bfMExists(chests, neverOpened);
        int falsePositives = countTrue(answers);

        Long card = redis.bfCard(chests);
        BfInfoValue info = redis.bfInfo(chests);               // getCapacity(), getSize(), getNumberOfFilters()...
        Long bytes = redis.memoryUsage(chests);
    }
    ```

## No Redis Insight

No **Browser**, `seu-prefixo:chests:opened` aparece com o tipo do módulo (`MBbloom--`) e sem conteúdo navegável: um filtro não guarda os ids, só bits. Ao lado, `seu-prefixo:chests:opened:plain` é o SET com os mesmos 1000 ids; compare o tamanho das duas chaves na coluna de memória.

No **Workbench**, rode `BF.INFO seu-prefixo:chests:opened` e depois `BF.EXISTS seu-prefixo:chests:opened chest-5042`. Se vier 1, você encontrou um dos falsos positivos do lab; troque o número e tente de novo.

??? note "Por dentro"

    | Comando | O que faz |
    |---|---|
    | `BF.RESERVE key erro capacidade` | Cria o filtro dimensionado para a taxa de erro e a quantidade de itens esperada |
    | `BF.ADD key item` | Marca um item. Devolve 1 se algum bit mudou, 0 se todos já estavam acesos |
    | `BF.MADD key item [item ...]` | Vários itens em uma viagem |
    | `BF.EXISTS key item` | 0: com certeza não está. 1: provavelmente está |
    | `BF.MEXISTS key item [item ...]` | Vários itens em uma viagem |
    | `BF.INSERT key ITEMS item ...` | Cria o filtro (se não existe) e adiciona, no mesmo comando |
    | `BF.CARD key` | Estimativa de quantos itens entraram |
    | `BF.INFO key` | Capacidade, tamanho em bytes, número de sub-filtros, itens inseridos, fator de expansão |

    Como funciona: cada item passa por k funções de hash e acende k bits de um vetor. Consultar é conferir se os k bits estão acesos. Bits são compartilhados entre itens, daí o falso positivo; nenhum bit é apagado, daí o zero falso negativo.

    !!! tip "Quanto custa a certeza"
        O lab cria um SET com os mesmos 1000 ids e mede os dois com `MEMORY USAGE`. O filtro fica perto de 1 KB; o SET, dezenas de KB. A conta muda de patamar com milhões de baús por jogador.

??? tip "Em produção"

    - Dimensione a capacidade para o crescimento real. Passou da capacidade, o Redis cria sub-filtros (o fator `expansion`), e cada consulta passa a olhar todos eles: mais lento e menos preciso. Um `BF.RESERVE` generoso custa poucos KB.
    - Não dá para remover um item de um Bloom filter. Se precisa de remoção, use o Cuckoo filter (`CF.*`). Se precisa listar os itens, guarde a fonte da verdade em outro lugar (um SET, um Hash, o banco) e use o filtro como pré-checagem barata.
    - Casos clássicos: "esse usuário já viu esse item?", "esse e-mail já foi cadastrado?", deduplicação de eventos antes de gravar. Sempre onde um "talvez sim" seguido de uma checagem cara é aceitável, e um "não" barato evita a maioria das checagens.

??? tip "Desafio"

    Troque `ERROR_RATE` para `0.001` (0,1%) no lab e rode de novo: os falsos positivos caem para perto de 1 em 1000 e o `Size` do `BF.INFO` cresce cerca de 50%. Depois experimente `0.1` e veja o efeito contrário.
