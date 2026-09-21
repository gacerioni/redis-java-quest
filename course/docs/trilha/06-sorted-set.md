---
lesson: 101-05
title: "Sorted Set: ranking sem ORDER BY"
minutes: 8
kind: lab
no_steps: true
next_url: trilha/07-proximos-passos/
next_title: "E agora?"
state_text: "Rodou o lab e o verify passou? Registre seu estudo."
---

# Sorted Set: ranking sem ORDER BY

<p class="lesson-meta">Demo junto com Hash · 42-50 min · <a href="../../101-tipos/05-sorted-set/">versão completa</a></p>

Cada membro de um Sorted Set é único e carrega um score; o Redis mantém a ordem a cada escrita. Posição de alguém custa O(log N); retornar uma faixa custa O(log N + M), sendo M a quantidade de resultados. O índice já mantém a ordenação, sem `ORDER BY` a cada consulta. É o tipo por trás de rankings, filas priorizadas e séries temporais simples.

## Acompanhe a demonstração

```bash
./quest run 101-05 jedis
./quest verify 101-05
```

## O que aconteceu

O placar `quest:rank:xp` em operações (o código está em `l101_05/JedisLab.java`):

```java
List<Tuple> top = jedis.zrevrangeWithScores(rank, 0, 9);  // top 10, maior primeiro
Long pos = jedis.zrevrank(rank, "vesper");                // posição (base zero)
Double xp = jedis.zscore(rank, "vesper");                 // o score dela

jedis.zincrby(rank, 5000, "vesper");                      // +5000 XP, atômico,
                                                         // e o ranking se reordena sozinho

long faixa = jedis.zcount(rank, 100_000, 600_000);        // quantos nessa faixa de XP
List<Tuple> grupo = jedis.zrangeByScoreWithScores(rank, 100_000, 600_000);
```

`ZINCRBY` passa no fio como um comando só; não existe leitura, soma e reescrita. Por isso milhares de updates por segundo não criam condição de corrida.

??? note "E o Lettuce?"

    Mesmas operações; o tipo de retorno é `ScoredValue<String>` no lugar de `Tuple`, e faixas usam `Range.create(min, max)`. Rode `./quest run 101-05 lettuce` e compare.

??? tip "Ver no Redis Insight"
    Abra `quest:rank:xp`: os membros já aparecem ordenados por score. No Workbench, experimente `ZRANGE quest:rank:xp 0 9 REV WITHSCORES` (a forma moderna do `ZREVRANGE`).

??? tip "Para ir além"
    - Empate no score? A ordem é lexicográfica por bytes (invertida com `REV`). Um desempate por horário exige modelar o score ou o membro com cuidado e considerar a precisão do double.
    - Ranking por temporada: uma chave por semana (`rank:xp:2026-w37`) com `EXPIRE`, em vez de zerar o global.
    - Score é double: inteiros exatos até 2^53. Mais detalhes na [lição completa 101-05](../101-tipos/05-sorted-set.md).
