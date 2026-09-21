---
lesson: 102-03
title: "Consumer groups: guildas processando o loot"
minutes: 12
kind: lab
---

# Consumer groups: guildas processando o loot

<p class="lesson-meta">Lição 102-03 · Lab · 12 min</p>

Quando o processamento é pesado demais para um worker só, `XREAD` não basta; todo leitor vê tudo. O que você quer é dividir: cada evento para um worker, nenhum esquecido se um worker cair. Consumer groups fazem isso dentro do Redis: o grupo lembra até onde entregou, cada entrega fica pendente até o `XACK`, e `XPENDING` + `XAUTOCLAIM` recuperam o que um worker morto deixou pela metade. É a base de uma fila de trabalho confiável.

## O que o lab faz

- Criar o grupo `loot-workers` no diário `{p}:events:combat` a partir do id 0, com `MKSTREAM`
- Ler com dois workers, `XREADGROUP ... COUNT 5 ... >`, e confirmar com `XACK`
- Deixar dois golpes sem `XACK` de propósito (o worker-2 "caiu") e enxergá-los no `XPENDING`
- Passar os órfãos para o worker-3 com `XAUTOCLAIM`, confirmar e ver o `XPENDING` zerar
- Ler a foto do grupo com `XINFO GROUPS`

## Faça agora

```bash
./quest run 102-03 jedis
./quest run 102-03 lettuce    # opcional: mesmo lab, outro client
./quest verify 102-03
```

## O código

=== "Jedis"

    ```java
    String stream = ctx.k("events", "combat");
    String group = "loot-workers";
    jedis.xgroupCreate(stream, group, new StreamEntryID("0-0"), true);       // 0 = desde o início, MKSTREAM

    List<Map.Entry<String, List<StreamEntry>>> reply = jedis.xreadGroup(group, "worker-1",
            XReadGroupParams.xReadGroupParams().count(5),
            Map.of(stream, StreamEntryID.XREADGROUP_UNDELIVERED_ENTRY));      // ">"
    for (StreamEntry entry : reply.get(0).getValue()) {
        // processa o loot...
        jedis.xack(stream, group, entry.getID());                            // um por um ou em lote
    }

    StreamPendingSummary summary = jedis.xpending(stream, group);            // total, faixa, por consumer
    List<StreamPendingEntry> details = jedis.xpending(stream, group,
            XPendingParams.xPendingParams("-", "+", 10));                     // id, dono, ocioso, entregas

    Map.Entry<StreamEntryID, List<StreamEntry>> claimed = jedis.xautoclaim(stream, group, "worker-3",
            0, new StreamEntryID("0-0"), XAutoClaimParams.xAutoClaimParams().count(10));
    for (StreamEntry entry : claimed.getValue()) jedis.xack(stream, group, entry.getID());

    for (StreamGroupInfo info : jedis.xinfoGroups(stream)) {
        info.getName(); info.getConsumers(); info.getPending(); info.getLastDeliveredId();
    }
    ```

=== "Lettuce"

    ```java
    RedisCommands<String, String> redis = connection.sync();
    redis.xgroupCreate(XReadArgs.StreamOffset.from(stream, "0"), group, new XGroupCreateArgs().mkstream(true));

    List<StreamMessage<String, String>> entries = redis.xreadgroup(
            Consumer.from(group, "worker-1"),
            XReadArgs.Builder.count(5),
            XReadArgs.StreamOffset.lastConsumed(stream));                     // ">"
    for (StreamMessage<String, String> message : entries) {
        // processa o loot...
        redis.xack(stream, group, message.getId());
    }

    PendingMessages summary = redis.xpending(stream, group);                  // getCount(), getConsumerMessageCount()
    List<PendingMessage> details = redis.xpending(stream, group, Range.unbounded(), Limit.from(10));

    ClaimedMessages<String, String> claimed = redis.xautoclaim(stream, new XAutoClaimArgs<String>()
            .consumer(Consumer.from(group, "worker-3")).minIdleTime(0).startId("0-0").count(10));
    for (StreamMessage<String, String> message : claimed.getMessages()) redis.xack(stream, group, message.getId());

    List<Object> groups = redis.xinfoGroups(stream);                          // name, consumers, pending, lag
    ```

## No Redis Insight

Browser, chave `{p}:events:combat`, aba Consumer Groups: o grupo `loot-workers` com `pending`, `last-delivered-id` e `lag`. Clique no grupo para ver `worker-1`, `worker-2` e `worker-3` com o número de pendentes de cada um. Para enxergar o estado intermediário, comente o bloco do `XAUTOCLAIM` no lab e rode de novo: os dois golpes órfãos aparecem no worker-2 e o `verify` reclama deles. No Workbench, `XPENDING {p}:events:combat loot-workers - + 10` mostra a mesma coisa em texto.

??? note "Por dentro"

    | Comando | O que faz |
    |---|---|
    | `XGROUP CREATE key grupo 0 MKSTREAM` | Cria o grupo; `0` entrega desde o começo, `$` só o que chegar depois; `MKSTREAM` cria o stream se faltar |
    | `XREADGROUP GROUP grupo consumer COUNT n STREAMS key >` | Entrega até n entradas que ninguém do grupo recebeu; elas entram na PEL do consumer |
    | `XACK key grupo id ...` | Remove da PEL: processamento concluído |
    | `XPENDING key grupo` | Resumo: total pendente, faixa de ids, contagem por consumer |
    | `XPENDING key grupo - + n` | Detalhe: id, dono, tempo ocioso, número de entregas |
    | `XAUTOCLAIM key grupo consumer min-idle 0-0 COUNT n` | Transfere para `consumer` as pendentes ociosas há mais de `min-idle` ms; devolve o cursor para continuar |
    | `XINFO GROUPS key` | Foto dos grupos: consumers, pending, last-delivered-id, lag |

    `>` versus um id: `>` entrega novidades; passar `0` no lugar de `>` reentrega ao mesmo consumer o que ele já tem pendente, o jeito certo de um worker retomar depois de reiniciar.

??? tip "Em produção"

    - `min-idle` é a sua definição de "morto": use algo maior que o tempo normal de processamento (60 s, por exemplo) e rode o `XAUTOCLAIM` periodicamente em qualquer worker. O `0` da aula só serve para a aula.
    - Entrega at-least-once: uma entrada pode chegar duas vezes (o dono caiu depois de processar e antes do `XACK`). Faça o efeito idempotente e olhe `entregas` no `XPENDING`; acima de um limite, mande para uma dead letter (outro stream) e dê `XACK`.
    - `XREADGROUP ... BLOCK 2000` é o jeito eficiente de esperar trabalho, mas cada worker bloqueado segura uma conexão: a [próxima lição](04-conexoes-bloqueantes.md) é sobre isso.

??? tip "Desafio"

    Faça o worker-1 "reiniciar": depois do primeiro lote, chame `XREADGROUP` de novo com o id `0` no lugar de `>` (Jedis: `new StreamEntryID("0-0")`; Lettuce: `XReadArgs.StreamOffset.from(stream, "0")`) antes do `XACK`. Ele recebe de volta as entradas que ainda estão pendentes com ele, sem tocar no que é dos outros. Depois `XACK` e confira o `XPENDING`.
