---
lesson: 100-03
title: "Redis Insight: enxergando o reino"
minutes: 6
kind: lab
---

# Redis Insight: enxergando o reino

<p class="lesson-meta">Lição 100-03 · Lab · 6 min</p>

O `./quest seed` colocou 42 itens, 12 personagens e 6 zonas no seu Redis, e o Redis Insight é a interface oficial para olhar tudo isso: o Browser mostra as chaves em árvore, o Workbench roda comandos com a documentação ao lado e o Profiler mostra, ao vivo, o que o seu client Java está mandando. O lab faz em Java o mesmo passeio do Browser (`SCAN` para achar as chaves, `TYPE` para ver o que mora em cada uma), para você comparar as duas visões.

## O que o lab faz

- Abrir o Redis Insight apontando para o mesmo banco do `REDIS_URL`
- Filtrar o Browser pelo seu prefixo e reconhecer os cinco tipos do seed
- Rodar o tour em Java: `SCAN` + `TYPE` e uma tabela por entidade
- Gravar o marco `quest:tour:visited` e achar a chave no Browser
- Ligar o Profiler e ver os comandos do lab passando

## Faça agora

```bash
./quest run 100-03 jedis
./quest run 100-03 lettuce    # opcional: mesmo lab, outro client
./quest verify 100-03
```

## O código

=== "Jedis"

    ```java
    String visited = ctx.k("tour", "visited");          // quest:tour:visited
    String pattern = ctx.keys.pattern();                 // quest:*
    try (RedisClient jedis = Clients.jedis()) {
        jedis.unlink(visited);                           // idempotent: clean our own marker first

        Set<String> keys = new TreeSet<>();
        ScanParams params = new ScanParams().match(pattern).count(100);
        String cursor = ScanParams.SCAN_POINTER_START;   // "0"
        do {
            ScanResult<String> page = jedis.scan(cursor, params);
            keys.addAll(page.getResult());
            cursor = page.getCursor();
        } while (!cursor.equals(ScanParams.SCAN_POINTER_START));

        Tour tour = new Tour(ctx.keys.prefix());
        for (String key : keys) tour.add(key, jedis.type(key));   // "ReJSON-RL", "hash", "set", "zset", "string"
        tour.print(ctx);

        jedis.set(visited, Instant.now().toString());
        ctx.done("keys", String.valueOf(tour.total()), "items", String.valueOf(tour.count("item", "ReJSON-RL")),
                "players", String.valueOf(tour.count("player", "hash")), "types", String.valueOf(tour.distinctTypes()));
    }
    ```

=== "Lettuce"

    ```java
    String visited = ctx.k("tour", "visited");
    String pattern = ctx.keys.pattern();
    try (StatefulRedisConnection<String, String> connection = Clients.lettuceConnection()) {
        RedisCommands<String, String> redis = connection.sync();
        redis.unlink(visited);

        Set<String> keys = new TreeSet<>();
        ScanArgs args = ScanArgs.Builder.matches(pattern).limit(100);
        ScanCursor cursor = ScanCursor.INITIAL;          // the cursor is an object here
        do {
            KeyScanCursor<String> page = redis.scan(cursor, args);
            keys.addAll(page.getKeys());
            cursor = page;                               // the reply is the next cursor
        } while (!cursor.isFinished());

        Tour tour = new Tour(ctx.keys.prefix());
        for (String key : keys) tour.add(key, redis.type(key));
        tour.print(ctx);

        redis.set(visited, Instant.now().toString());
        ctx.done("keys", String.valueOf(tour.total()), "items", String.valueOf(tour.count("item", "ReJSON-RL")),
                "players", String.valueOf(tour.count("player", "hash")), "types", String.valueOf(tour.distinctTypes()));
    }
    ```

A classe `Tour` é um ajudante do próprio pacote da lição: agrupa as chaves pelo segmento depois do prefixo (`item`, `player`, `zone`...) e pelo tipo, exatamente como a árvore do Browser faz. O resultado no console:

```text
entidade   tipo          qtd   exemplo
item       JSON           42   quest:item:adaga-do-silencio
player     hash           12   quest:player:brom
player     set            10   quest:player:brom:achievements
players    set             1   quest:players
rank       sorted set      1   quest:rank:xp
zone       hash            6   quest:zone:catacumbas-rubras
zone       sorted set      1   quest:zone:geo
zones      set             1   quest:zones
```

!!! note "Por que 10 sets de conquistas e não 12?"
    Marisol e Vesper acabaram de chegar ao reino e ainda não têm conquistas. Um set vazio não existe no Redis: a chave só aparece quando tem o primeiro membro.

## No Redis Insight

**Como abrir.** No Redis Cloud Essentials, entre no console, abra o seu banco e clique em **Launch Redis Insight web**: o Insight abre já conectado, sem instalar nada. Com o Docker do curso, o Insight está em [http://localhost:5540](http://localhost:5540); na primeira vez, adicione o banco com host `redis` e porta `6379` (é o nome do serviço no `docker-compose.yml`; `localhost` dentro do container do Insight não é o seu Redis). Para qualquer outro banco, clique em **Add Redis database** e cole a mesma URL do seu `REDIS_URL`.

**Browser.** Digite `quest:*` no filtro (troque `quest` pelo seu prefixo, o lab imprime qual é). Ative a visão em árvore: cada `:` vira um nível, e você vê a mesma tabela do console. Clique em `quest:item:espada-de-brasa` e o Insight mostra o documento JSON formatado, com o `embedding` de 384 números no fim. Clique em `quest:player:kaelith` e veja os campos do hash; em `quest:rank:xp`, os membros e os scores do sorted set. Depois de rodar o lab, procure `quest:tour:visited`: uma STRING com o instante da visita.

**Workbench.** Rode você mesmo, um por vez: `SCAN 0 MATCH quest:* COUNT 100`, `TYPE quest:item:espada-de-brasa`, `JSON.GET quest:item:espada-de-brasa $.name $.price`. O painel da direita traz a documentação de cada comando.

**Profiler.** Clique em **Profiler** no rodapé, em **Start Profiler**, e rode o lab de novo em outro terminal. Você vai ver o `UNLINK`, as páginas do `SCAN` (repare nos cursores que mudam), os `TYPE` um atrás do outro e o `SET` final. É a forma mais rápida de descobrir o que uma biblioteca faz por baixo dos panos.

??? note "Por dentro"

    | Comando | O que faz |
    |---|---|
    | `SCAN cursor MATCH padrão COUNT n` | Devolve uma página de chaves e o próximo cursor; cursor `0` de volta encerra a iteração. `COUNT` é uma dica de tamanho de página, não um limite exato |
    | `TYPE chave` | Diz o tipo: `string`, `hash`, `set`, `zset`, `list`, `stream`, e nomes internos de módulos como `ReJSON-RL` (JSON) |
    | `UNLINK chave` | Apaga a chave devolvendo a memória em segundo plano; é o `DEL` que não bloqueia o servidor em chaves grandes |
    | `SET chave valor` | Grava uma STRING; aqui, o instante da visita |
    | `JSON.GET chave $.campo` | Lê um pedaço de um documento JSON (no Workbench, para espiar um item) |

??? tip "Em produção"

    - Conecte o Insight à produção com um usuário ACL só de leitura. O Browser é seguro (ele usa `SCAN` com páginas pequenas), mas um clique errado em **Delete** apaga chave de verdade.
    - O Profiler é um `MONITOR` por baixo: ele recebe uma cópia de cada comando do servidor. Ligue por alguns segundos para investigar e desligue; nunca deixe rodando em um banco movimentado.
    - Um `TYPE` por chave, como o lab fez, custa uma ida e volta por chave. Para milhares de chaves, junte os `TYPE` em um pipeline (lição [100-05](05-pipeline.md)) ou use o **Database analysis** do Insight, que amostra o banco em vez de varrer tudo.

??? tip "Desafio"

    Mude o `COUNT` do `SCAN` no lab (o `count(100)` do Jedis ou o `limit(100)` do Lettuce) para `10` e rode de novo: a linha "chaves com o prefixo" vai mostrar muito mais páginas. Agora troque para `1000`: quase sempre uma página só. O total de chaves não muda; só o número de idas e voltas até o cursor voltar a zero.
