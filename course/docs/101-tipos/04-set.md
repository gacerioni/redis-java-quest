---
lesson: 101-04
title: "Set: conquistas e loot aleatório"
minutes: 8
kind: lab
---

# Set: conquistas e loot aleatório

<p class="lesson-meta">Lição 101-04 · Lab · 8 min</p>

Uma conquista não tem ordem e não se repete: ou o jogador tem, ou não tem. Isso é um Set; coleção de strings únicas, sem ordem, com teste de pertinência em O(1) e operações de conjunto (interseção, diferença, união) rodando no servidor. Dá para cruzar os troféus de dois jogadores e sortear um item de uma tabela de loot, um comando cada.

## O que o lab faz

- Desbloquear uma conquista com `SADD` e provar que repetir o comando não duplica nada
- Perguntar "tem essa conquista?" com `SISMEMBER` e `SMISMEMBER`, e contar com `SCARD`
- Comparar os troféus de Brom e Thane com `SINTER`, `SDIFF` e `SUNION`
- Montar uma tabela de loot com 6 itens e sortear com `SRANDMEMBER`
- Conferir tudo com `./quest verify 101-04`

## Faça agora

```bash
./quest run 101-04 jedis
./quest run 101-04 lettuce    # opcional: mesmo lab, outro client
./quest verify 101-04
```

## O código

=== "Jedis"

    ```java
    String marisol = ctx.k("player", "marisol", "achievements");
    String brom = ctx.k("player", "brom", "achievements");
    String thane = ctx.k("player", "thane", "achievements");
    String loot = ctx.k("loot", "table");

    try (RedisClient jedis = Clients.jedis()) {
        jedis.srem(marisol, "primeiro-sangue");               // idempotent start
        jedis.unlink(loot);

        long added = jedis.sadd(marisol, "primeiro-sangue");   // 1
        long again = jedis.sadd(marisol, "primeiro-sangue");   // 0: already there
        boolean has = jedis.sismember(marisol, "primeiro-sangue");
        long count = jedis.scard(marisol);

        Set<String> common = jedis.sinter(brom, thane);        // trophies both have
        Set<String> onlyThane = jedis.sdiff(thane, brom);      // thane minus brom
        Set<String> union = jedis.sunion(brom, thane);

        jedis.sadd(loot, "pocao-de-vida-menor", "pocao-de-mana", "anel-de-cobre",
                "espada-curta-de-ferro", "botas-do-viajante", "arco-de-teixo");
        String drop = jedis.srandmember(loot);                 // one random item, set untouched
        List<String> distinct = jedis.srandmember(loot, 3);    // 3 distinct items
        List<String> rolls = jedis.srandmember(loot, -3);      // 3 rolls, repeats allowed
    }
    ```

=== "Lettuce"

    ```java
    try (StatefulRedisConnection<String, String> connection = Clients.lettuceConnection()) {
        RedisCommands<String, String> redis = connection.sync();
        redis.srem(marisol, "primeiro-sangue");
        redis.unlink(loot);

        Long added = redis.sadd(marisol, "primeiro-sangue");   // 1
        Long again = redis.sadd(marisol, "primeiro-sangue");   // 0
        Boolean has = redis.sismember(marisol, "primeiro-sangue");
        Long count = redis.scard(marisol);

        Set<String> common = redis.sinter(brom, thane);
        Set<String> onlyThane = redis.sdiff(thane, brom);
        Set<String> union = redis.sunion(brom, thane);

        redis.sadd(loot, "pocao-de-vida-menor", "pocao-de-mana", "anel-de-cobre",
                "espada-curta-de-ferro", "botas-do-viajante", "arco-de-teixo");
        String drop = redis.srandmember(loot);
        List<String> distinct = redis.srandmember(loot, 3);
        List<String> rolls = redis.srandmember(loot, -3);
    }
    ```

## No Redis Insight

No **Browser**, filtre por `seu-prefixo:player:marisol:achievements`: um Set com um único membro, `primeiro-sangue`. Abra `seu-prefixo:player:brom:achievements` e `seu-prefixo:player:thane:achievements` lado a lado e confira a interseção que o lab imprimiu. Em `seu-prefixo:loot:table` estão os 6 itens do baú.

No **Workbench**, rode `SRANDMEMBER seu-prefixo:loot:table 3` algumas vezes: o resultado muda, o Set não. Depois rode `SINTER seu-prefixo:player:brom:achievements seu-prefixo:player:thane:achievements` e veja que o Redis responde só com os quatro membros em comum.

No **Profiler**, repare que o `SINTER` envia apenas os nomes das chaves: a comparação inteira acontece no servidor.

??? note "Por dentro"

    | Comando | O que faz |
    |---|---|
    | `SADD key m [m ...]` | Adiciona membros e devolve quantos eram novos. Repetir é seguro: um Set não duplica |
    | `SREM key m [m ...]` | Remove membros (ignora os que não estão lá) |
    | `SISMEMBER key m` | 1 se o membro está no Set, 0 se não. O(1) |
    | `SMISMEMBER key m [m ...]` | O mesmo, para vários membros em uma viagem |
    | `SCARD key` | Quantidade de membros (cardinalidade), O(1) |
    | `SINTER k1 k2 ...` | Membros presentes em todos os Sets |
    | `SDIFF k1 k2 ...` | Membros do primeiro Set que não estão nos outros (a ordem dos argumentos importa) |
    | `SUNION k1 k2 ...` | Todos os membros, sem repetição |
    | `SRANDMEMBER key [count]` | Sorteia sem remover. Count positivo: membros distintos; negativo: pode repetir |
    | `SPOP key [count]` | Sorteia e remove. Bom para "tirar uma carta do baralho", ruim para uma tabela de loot que precisa durar |
    | `SMEMBERS key` | Lista tudo. Só em Sets pequenos; nos grandes, use `SSCAN` |

??? tip "Em produção"

    - `SMEMBERS`, `SINTER`, `SUNION` e `SDIFF` são O(N). Em Sets com milhões de membros, liste com `SSCAN` e responda "existe algo em comum?" com `SINTERCARD ... LIMIT 1`, sem trazer os membros.
    - Quando o banco tem mais de um shard (Redis OSS Cluster, ou um Redis Cloud maior), um comando multi-chave como `SINTER` exige que todas as chaves caiam no mesmo slot. Agrupe as chaves que você compara sob a mesma hash tag, por exemplo `{servidor-1}:player:brom:achievements`. No plano free, com um shard só, isso não aparece.
    - Um Set de ids é ótimo para relações: quem está online, quem está na guilda, tags de um item. Se você precisa de ordem ou de um peso por membro, a próxima lição ([Sorted Set](05-sorted-set.md)) é o tipo certo.

??? tip "Desafio"

    Troque `srandmember(loot)` por `spop(loot)` no lab, rode de novo e depois rode `./quest verify 101-04`. O verify reclama que a tabela ficou com 5 itens: `SPOP` sorteia e remove. Volte para `SRANDMEMBER`, rode a lição de novo e o verify passa.
