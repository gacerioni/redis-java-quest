---
lesson: 101-05
title: "Sorted Set: o ranking do servidor"
minutes: 8
kind: lab
---

# Sorted Set: o ranking do servidor

<p class="lesson-meta">Lição 101-05 · Lab · 8 min</p>

## Por que isso importa

Todo servidor do Ember Realm tem um placar: quem tem mais XP aparece em cima, e cada masmorra fechada mexe na ordem. Fazer isso com `ORDER BY` a cada consulta custa caro quando milhares de jogadores ganham XP por segundo. O Sorted Set resolve: cada membro é único e carrega um score; o Redis mantém a ordem a cada escrita, e ler o top 10, a posição de alguém ou uma faixa de score é O(log N).

## O que você vai fazer

- Ler o top 10 do ranking `rank:xp` com `ZREVRANGE ... WITHSCORES`
- Descobrir a posição da Vesper com `ZREVRANK` e o XP dela com `ZSCORE`
- Dar 5000 XP para ela com `ZINCRBY` e ver o ranking se ajustar sozinho
- Montar um grupo por faixa de XP com `ZCOUNT` e `ZRANGEBYSCORE`
- Conferir com `./quest check 101-05`

## Rode

```bash
./quest run 101-05 jedis
./quest run 101-05 lettuce
./quest check 101-05
```

## O código

=== "Jedis"

    ```java
    String rank = ctx.k("rank", "xp");
    try (RedisClient jedis = Clients.jedis()) {
        jedis.zadd(rank, 900, "vesper");                           // back to the seed value

        List<Tuple> top = jedis.zrevrangeWithScores(rank, 0, 9);   // top 10, highest first
        for (Tuple t : top) System.out.println(t.getElement() + " " + (long) t.getScore());

        Long before = jedis.zrevrank(rank, "vesper");              // 11 -> 12th place (zero based)
        Double xp = jedis.zscore(rank, "vesper");                  // 900.0

        double newXp = jedis.zincrby(rank, 5000, "vesper");        // 5900.0, atomic
        Long after = jedis.zrevrank(rank, "vesper");
        Tuple rival = jedis.zrevrangeWithScores(rank, after - 1, after - 1).get(0);   // who is right above

        long inBracket = jedis.zcount(rank, 100_000, 600_000);
        List<Tuple> bracket = jedis.zrangeByScoreWithScores(rank, 100_000, 600_000);
    }
    ```

=== "Lettuce"

    ```java
    try (StatefulRedisConnection<String, String> connection = Clients.lettuceConnection()) {
        RedisCommands<String, String> redis = connection.sync();
        redis.zadd(rank, 900, "vesper");

        List<ScoredValue<String>> top = redis.zrevrangeWithScores(rank, 0, 9);
        for (ScoredValue<String> sv : top) System.out.println(sv.getValue() + " " + (long) sv.getScore());

        Long before = redis.zrevrank(rank, "vesper");
        Double xp = redis.zscore(rank, "vesper");

        Double newXp = redis.zincrby(rank, 5000, "vesper");
        Long after = redis.zrevrank(rank, "vesper");
        ScoredValue<String> rival = redis.zrevrangeWithScores(rank, after - 1, after - 1).get(0);

        Range<Double> range = Range.create(100_000.0, 600_000.0);
        Long inBracket = redis.zcount(rank, range);
        List<ScoredValue<String>> bracket = redis.zrangebyscoreWithScores(rank, range);
    }
    ```

## O que olhar no Redis Insight

No **Browser**, abra `seu-prefixo:rank:xp`. O Insight mostra os membros já ordenados por score; Vesper aparece com 5900 no fim da lista, logo abaixo de Marisol (6400). Rode a lição de novo e o valor continua 5900: o lab reaplica o XP do seed antes de incrementar.

No **Workbench**, experimente `ZRANGE seu-prefixo:rank:xp 0 9 REV WITHSCORES` (a forma moderna do `ZREVRANGE`) e `ZRANGEBYSCORE seu-prefixo:rank:xp 100000 600000 WITHSCORES`.

No **Profiler**, veja o `ZINCRBY` passar como um comando só: não há `GET`, soma em Java e `SET` de volta, por isso ele é atômico.

## Por dentro

| Comando | O que faz |
|---|---|
| `ZADD key score membro` | Insere ou atualiza o score de um membro. `NX`, `XX`, `GT` e `LT` controlam quando gravar |
| `ZINCRBY key incr membro` | Soma ao score de forma atômica e devolve o novo valor. Cria o membro se não existe |
| `ZSCORE key membro` | Score de um membro (ou nil) |
| `ZCARD key` | Quantos membros |
| `ZREVRANGE key start stop [WITHSCORES]` | Fatia por posição, do maior para o menor. `0 9` é o top 10 |
| `ZRANGE key start stop REV WITHSCORES` | A mesma coisa na sintaxe unificada do Redis 6.2+ |
| `ZREVRANK key membro` | Posição do maior para o menor, base zero: 11 significa 12º lugar |
| `ZRANK key membro` | Posição do menor para o maior |
| `ZCOUNT key min max` | Quantos membros têm score na faixa |
| `ZRANGEBYSCORE key min max [WITHSCORES] [LIMIT off n]` | Membros por faixa de score. `(100` exclui a borda; `-inf` e `+inf` abrem a faixa |

Empates: membros com o mesmo score ficam em ordem lexicográfica. Se o placar precisa desempatar por "quem chegou primeiro", coloque essa informação no próprio score (veja abaixo).

## Em produção

- O score é um double de 64 bits: inteiros até 2^53 são exatos. Para desempatar por tempo, use a parte fracionária, por exemplo `xp + (1 - timestamp / 1e13)`: mesmo XP, quem chegou antes fica acima.
- Placar por temporada: uma chave por semana (`rank:xp:2026-w37`) com `EXPIRE`, em vez de zerar o ranking global. `ZUNIONSTORE` junta temporadas quando você precisar do acumulado.
- Se só o topo interessa, não deixe o ranking crescer sem limite: `ZREMRANGEBYRANK key 0 -1001` mantém os 1000 melhores. Leituras são O(log N + M), mas `ZRANGE 0 -1` em milhões de membros é um comando lento como qualquer `*RANGE` sem limite.

## Desafio

Vesper ganhou 5000 XP e continuou em 12º: Marisol tem 6400. Troque `BONUS_XP` para `6000` no lab e rode de novo: `ZREVRANK` passa a devolver 10 (11º lugar) e o "logo acima" vira Dorian. Depois volte para 5000, porque o check espera exatamente 5900.
