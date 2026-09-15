---
lesson: 101-02
title: "Hash: a ficha do personagem"
minutes: 8
kind: lab
---

# Hash: a ficha do personagem

<p class="lesson-meta">Lição 101-02 · Lab · 8 min</p>

Uma ficha com nome, classe, nível, HP, ouro: guardar como String JSON obriga ler e reescrever o documento inteiro para mudar um campo; uma chave por atributo espalha a ficha e multiplica viagens. O Hash fica no meio — uma chave, vários campos, cada campo lido, escrito ou incrementado sozinho, no servidor. Desde o Redis 7.4 um campo pode ter prazo próprio: o buff expira, o resto da ficha fica.

## O que o lab faz

- Ler a ficha inteira com `HGETALL` e só o necessário com `HGET` e `HMGET`
- Pagar a recompensa da missão com `HINCRBY gold 250`, sem ler antes
- Gravar título e montaria de uma vez com `HSET` de vários campos
- Conferir campos com `HEXISTS` e contar com `HLEN`
- Dar prazo a um buff com `HEXPIRE ... FIELDS 1 haste` e acompanhar com `HTTL`

## Faça agora

```bash
./quest run 101-02 jedis
./quest run 101-02 lettuce    # opcional: mesmo lab, outro client
./quest verify 101-02
```

## O código

=== "Jedis"

    ```java
    try (RedisClient jedis = Clients.jedis()) {
        Map<String, String> all = jedis.hgetAll(sheet);              // name, class, level, hp, ...
        String hp = jedis.hget(sheet, "hp");                         // "610"
        List<String> few = jedis.hmget(sheet, "name", "class", "level");

        long gold = jedis.hincrBy(sheet, "gold", 250);               // 8650, atomic on the server

        Map<String, String> extras = new LinkedHashMap<>();
        extras.put("title", "Guardiã das Brasas");
        extras.put("mount", "grifo-cinzento");
        long created = jedis.hset(sheet, extras);                    // 2 new fields

        boolean hasMount = jedis.hexists(sheet, "mount");            // true
        long fields = jedis.hlen(sheet);                             // 11

        // temporary buffs: only the haste field expires (Redis 7.4+)
        jedis.hset(buffs, Map.of("haste", "1", "shield", "1"));
        try {
            jedis.hexpire(buffs, 30, "haste");                       // [1]
            jedis.httl(buffs, "haste");                              // [30]
        } catch (Exception e) {
            // older server: explain instead of crashing
        }
    }
    ```

=== "Lettuce"

    ```java
    try (StatefulRedisConnection<String, String> connection = Clients.lettuceConnection()) {
        RedisCommands<String, String> redis = connection.sync();

        Map<String, String> all = redis.hgetall(sheet);
        String hp = redis.hget(sheet, "hp");                                     // "610"
        List<KeyValue<String, String>> few = redis.hmget(sheet, "name", "class", "level");

        Long gold = redis.hincrby(sheet, "gold", 250);                           // 8650

        Map<String, String> extras = new LinkedHashMap<>();
        extras.put("title", "Guardiã das Brasas");
        extras.put("mount", "grifo-cinzento");
        Long created = redis.hset(sheet, extras);                                // 2

        Boolean hasMount = redis.hexists(sheet, "mount");                        // true
        Long fields = redis.hlen(sheet);                                         // 11

        redis.hset(buffs, Map.of("haste", "1", "shield", "1"));
        try {
            redis.hexpire(buffs, 30, "haste");                                   // [1]
            redis.httl(buffs, "haste");                                          // [30]
        } catch (Exception e) {
            // older server: explain instead of crashing
        }
    }
    ```

## No Redis Insight

No Browser, abra `quest:player:kaelith`: o Insight mostra o Hash como uma tabela de campos e valores, com `gold` em 8650 e os campos novos `title` e `mount`. Dá para editar um valor ali mesmo. Depois abra `quest:player:kaelith:buffs`: nas versões recentes do Insight, o campo `haste` aparece com um TTL próprio contando para baixo e `shield` sem prazo; em 30 segundos `haste` some e a chave continua existindo. No Profiler, rode o lab e compare o tamanho da resposta do `HGETALL` com a do `HMGET`.

??? note "Por dentro"

    | Comando | O que faz |
    |---|---|
    | `HGETALL chave` | Todos os campos e valores; em hashes grandes a resposta cresce junto |
    | `HGET chave campo`, `HMGET chave c1 c2` | Um ou alguns campos; a resposta só carrega o que você pediu |
    | `HINCRBY chave campo 250` | Soma atômica num campo numérico; cria o campo (a partir de 0) se não existir |
    | `HSET chave c1 v1 c2 v2` | Grava vários campos; devolve quantos foram criados (atualizações contam zero) |
    | `HEXISTS chave campo`, `HLEN chave` | O campo existe? Quantos campos há? |
    | `HDEL chave campo` | Remove campos; o hash some quando o último campo sai |
    | `HEXPIRE chave 30 FIELDS 1 campo` | Prazo por campo (Redis 7.4+); `HTTL` lê o prazo, `HPERSIST` remove |

    ```mermaid
    flowchart LR
        S["quest:player:kaelith (Hash)"] --> a["name = Kaelith"]
        S --> b["gold = 8650"]
        S --> c["title = Guardiã das Brasas"]
        B["quest:player:kaelith:buffs (Hash)"] --> d["haste = 1, TTL 30 s"]
        B --> e["shield = 1, sem prazo"]
    ```

    !!! note "Por que o lab recoloca o ouro do seed antes de somar"
        O lab começa com `HSET gold 8400` (o valor do seed) e só então faz `HINCRBY gold 250`. Assim ele pode rodar quantas vezes você quiser e o resultado é sempre 8650, que é o que o verify confere. Em produção você não faria isso; é só para a lição ser repetível.

??? tip "Em produção"

    - Hash para objetos planos (ficha, perfil, configuração) e JSON quando há aninhamento ou quando você quer indexar e buscar pelos campos, como em [JSON e FT.SEARCH](../201-busca/01-json-search.md). Um hash pequeno (até 128 campos curtos, por padrão) usa uma codificação compacta e gasta pouquíssima memória.
    - Prefira `HMGET` a `HGETALL` no caminho quente: uma ficha com dezenas de campos vira dezenas de valores trafegando a cada requisição. Para percorrer hashes grandes sem travar o servidor, use `HSCAN`.
    - Prazo por campo substitui o padrão antigo de "uma chave por buff". Atenção à semântica: quando o último campo com prazo vence e não sobra nenhum, a chave inteira desaparece; e o TTL da chave, se houver, continua valendo para todos os campos.

??? tip "Desafio"

    Dê prazo ao `shield` também, com `HEXPIRE ... FIELDS 2 haste shield` (Jedis: `hexpire(buffs, 30, "haste", "shield")`; Lettuce: `hexpire(buffs, 30, "haste", "shield")`), e observe no Insight a chave `quest:player:kaelith:buffs` desaparecer inteira quando os dois campos vencerem. Rode o verify depois dos 30 segundos e veja o que ele diz. Depois experimente `HINCRBYFLOAT` num campo novo `luck` com `0.5` e veja o valor voltar como texto decimal.
