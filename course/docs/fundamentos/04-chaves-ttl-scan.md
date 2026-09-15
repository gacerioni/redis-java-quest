---
lesson: 100-04
title: "Chaves, TTL e SCAN"
minutes: 8
kind: lab
---

# Chaves, TTL e SCAN

<p class="lesson-meta">Lição 100-04 · Lab · 8 min</p>

Uma sessão precisa existir por 30 minutos e depois sumir sozinha, sem nenhum job de limpeza. No Redis, o prazo de vida é uma propriedade da chave: `SET ... EX` cria, `TTL` consulta, `EXPIRE` renova. E para achar chaves pelo nome, nunca `KEYS` em produção: `SCAN` percorre o banco em páginas, sem travar o servidor.

## O que o lab faz

- Criar a sessão `quest:session:{token}` com `SET ... EX 1800` e ler `TTL`, `EXISTS` e `TYPE`
- Entender os dois valores especiais do `TTL`: -1 e -2
- Renovar o prazo com `EXPIRE`, remover com `PERSIST` e trocar o token com `RENAME`
- Percorrer todo o seu prefixo com `SCAN MATCH COUNT`, página por página, contando por tipo
- Adotar a convenção de nomes `app:entidade:id`

## Faça agora

```bash
./quest run 100-04 jedis
./quest run 100-04 lettuce    # opcional: mesmo lab, outro client
./quest verify 100-04
```

## O código

=== "Jedis"

    ```java
    String session = ctx.k("session", "7f3a9c");          // quest:session:7f3a9c
    String rotated = ctx.k("session", "b81d22");
    try (RedisClient jedis = Clients.jedis()) {
        jedis.unlink(session, rotated);                     // idempotent

        jedis.set(session, "kaelith", SetParams.setParams().ex(1800));   // SET ... EX 1800
        jedis.ttl(session);                                 // 1800
        jedis.exists(session);                              // true
        jedis.type(session);                                // "string"

        jedis.ttl(ctx.k("player", "kaelith"));              // -1: exists, never expires
        jedis.ttl(ctx.k("session", "inexistente"));         // -2: no such key

        jedis.expire(session, 3600);                        // 1: renewed
        jedis.persist(session);                             // 1: TTL removed (now -1)
        jedis.expire(session, 1800);                        // back to 30 minutes
        jedis.rename(session, rotated);                     // value and TTL travel with the key

        Map<String, Integer> byType = new TreeMap<>();
        ScanParams params = new ScanParams().match(ctx.keys.pattern()).count(100);
        String cursor = ScanParams.SCAN_POINTER_START;
        do {
            ScanResult<String> page = jedis.scan(cursor, params);
            for (String key : page.getResult()) byType.merge(jedis.type(key), 1, Integer::sum);
            cursor = page.getCursor();
        } while (!cursor.equals(ScanParams.SCAN_POINTER_START));
    }
    ```

=== "Lettuce"

    ```java
    String session = ctx.k("session", "7f3a9c");
    String rotated = ctx.k("session", "b81d22");
    try (StatefulRedisConnection<String, String> connection = Clients.lettuceConnection()) {
        RedisCommands<String, String> redis = connection.sync();
        redis.unlink(session, rotated);

        redis.set(session, "kaelith", SetArgs.Builder.ex(1800));   // SET ... EX 1800
        redis.ttl(session);                                 // 1800
        redis.exists(session);                              // 1 (Long, counts the keys that exist)
        redis.type(session);                                // "string"

        redis.ttl(ctx.k("player", "kaelith"));              // -1
        redis.ttl(ctx.k("session", "inexistente"));         // -2

        redis.expire(session, 3600);                        // true (Boolean)
        redis.persist(session);                             // true
        redis.expire(session, 1800);
        redis.rename(session, rotated);                     // "OK"

        Map<String, Integer> byType = new TreeMap<>();
        ScanArgs args = ScanArgs.Builder.matches(ctx.keys.pattern()).limit(100);
        ScanCursor cursor = ScanCursor.INITIAL;
        do {
            KeyScanCursor<String> page = redis.scan(cursor, args);
            for (String key : page.getKeys()) byType.merge(redis.type(key), 1, Integer::sum);
            cursor = page;
        } while (!cursor.isFinished());
    }
    ```

Repare nas diferenças de assinatura: o Jedis devolve `boolean` no `EXISTS` de uma chave e `long` (0 ou 1) no `EXPIRE`; o Lettuce devolve `Long` no `EXISTS` (quantas das chaves pedidas existem) e `Boolean` no `EXPIRE` e no `PERSIST`. Os comandos no fio são idênticos.

!!! tip "Convenção de nomes: app:entidade:id"
    `quest:session:7f3a9c` se lê assim: `quest` é o prefixo da aplicação (isola o seu mundo de qualquer outro no mesmo banco), `session` diz o que a chave é, `7f3a9c` diz qual. Minúsculas, dois-pontos como separador, do mais geral para o mais específico. O Browser do Redis Insight agrupa por esses segmentos, o `SCAN MATCH quest:session:*` acha só as sessões, e um colega entende a chave sem abrir o código. No lab os tokens são fixos para a lição ser repetível; na vida real, `SecureRandom`.

## No Redis Insight

No **Browser**, filtre por `quest:session:*`. A coluna **TTL** de `quest:session:b81d22` mostra o prazo caindo segundo a segundo; abra a chave e você pode editar o TTL ali mesmo (é um `EXPIRE` por trás). Volte em 30 minutos e a chave terá sumido sem ninguém apagar. Compare com `quest:player:kaelith`, cujo TTL aparece como "No limit": é o -1 do console.

No **Profiler**, rode o lab e acompanhe as páginas do `SCAN`: cada chamada leva o cursor da anterior (`SCAN 252 MATCH quest:* COUNT 100`) até voltar `0`. Depois vêm os `TYPE`, um por chave.

??? note "Por dentro"

    | Comando | O que faz |
    |---|---|
    | `SET chave valor EX segundos` | Cria a STRING já com prazo. Variações: `PX` (milissegundos), `EXAT` (instante Unix), `NX` (só se não existir, lição [101-01](../101-tipos/01-string.md)), `KEEPTTL` (sobrescreve o valor mantendo o prazo) |
    | `TTL chave` / `PTTL chave` | Segundos (ou milissegundos) restantes. `-1`: existe e não expira. `-2`: não existe |
    | `EXISTS chave [chave ...]` | Quantas das chaves existem |
    | `TYPE chave` | O tipo da chave (`string`, `hash`, `set`, `zset`, `list`, `stream`, `ReJSON-RL`...) |
    | `EXPIRE chave segundos` | Define ou renova o prazo. Opções do Redis 7: `NX` (só se não tiver prazo), `GT` (só se aumentar), `LT` (só se diminuir) |
    | `PERSIST chave` | Remove o prazo; a chave vira permanente |
    | `RENAME origem destino` | Troca o nome mantendo valor e TTL; sobrescreve o destino se existir (`RENAMENX` não sobrescreve) |
    | `SCAN cursor MATCH padrão COUNT n` | Uma página de chaves e o próximo cursor. Pode repetir uma chave entre páginas (por isso o lab junta tudo em um `Set`) e `COUNT` é só uma dica |
    | `KEYS padrão` | Devolve tudo de uma vez varrendo o banco inteiro em uma única execução: bloqueia o servidor para todos os outros clientes. Só em desenvolvimento |
    | `UNLINK chave [chave ...]` | Apaga chaves liberando a memória em segundo plano; prefira ao `DEL` |

??? tip "Em produção"

    - Toda chave de sessão, cache ou lock nasce com prazo. Uma chave sem TTL só some se alguém apagar; com milhões de sessões esquecidas, a memória acaba e a política de eviction começa a apagar o que ela achar melhor (`volatile-lru` só toca em chaves com TTL; `allkeys-lru` toca em qualquer uma).
    - Sliding expiration: renove com `EXPIRE` a cada acesso, ou grave de novo com `SET ... KEEPTTL` quando só o valor muda. Com `EXPIRE ... GT` você nunca encurta um prazo sem querer.
    - `KEYS` em um banco com milhões de chaves é incidente: segundos de bloqueio para todo mundo. Use `SCAN` com `COUNT` entre 100 e 1000, e lembre que o cursor é sem estado no servidor: a iteração pode durar quanto for preciso sem ocupar memória lá. No Redis Cloud, a lista de comandos bloqueados do plano pode incluir `KEYS`.

??? tip "Desafio"

    Troque `SESSION_SECONDS` no `JedisLab` de `1800` para `5` e, logo depois do `RENAME`, coloque um `Thread.sleep(6000)` seguido de `jedis.exists(rotated)`. Rode e veja o `EXISTS` responder `false` e o `TTL` responder `-2`: a chave morreu sozinha. O `./quest verify 100-04` vai falhar com a dica de rodar a lição de novo; volte o valor para `1800` e ele passa.
