---
lesson: 102-02
title: "Streams: o diário de combate"
minutes: 10
kind: lab
---

# Streams: o diário de combate

<p class="lesson-meta">Lição 102-02 · Lab · 10 min</p>

## Por que isso importa

O chat pode se perder; o diário de combate não. Cada golpe precisa ficar registrado em ordem, com um id que ninguém repete, para ser relido pelo sistema de loot, pelo anti-cheat e pelo painel de estatísticas, cada um no seu ritmo. Um Stream do Redis é um log só de anexar: `XADD` grava, o servidor cunha o id `<ms>-<seq>`, e os leitores percorrem o histórico por faixa de ids (`XRANGE`) ou como cursor (`XREAD`) sem apagar nada. `MAXLEN` mantém o tamanho sob controle.

## O que você vai fazer

- Limpar `{p}:events:combat` e gravar 20 golpes com `XADD ... MAXLEN ~ 1000 *`
- Ler o começo com `XRANGE - + COUNT 3` e o fim com `XREVRANGE + - COUNT 3`
- Percorrer o diário com `XREAD COUNT 5 STREAMS ... 0-0` e entender o cursor
- Decifrar o id `<milissegundos>-<sequência>`

## Rode

```bash
./quest run 102-02 jedis
./quest run 102-02 lettuce
./quest check 102-02
```

## O código

=== "Jedis"

    ```java
    String stream = ctx.k("events", "combat");
    jedis.unlink(stream);
    XAddParams params = XAddParams.xAddParams().maxLen(1000).approximateTrimming();
    for (Map<String, String> event : CombatEvents.events()) {       // attacker, target, damage, zone
        StreamEntryID id = jedis.xadd(stream, params, event);       // 1789151634753-0
    }
    long length = jedis.xlen(stream);                                 // 20

    List<StreamEntry> oldest = jedis.xrange(stream, "-", "+", 3);
    List<StreamEntry> newest = jedis.xrevrange(stream, "+", "-", 3);

    List<Map.Entry<String, List<StreamEntry>>> batch = jedis.xread(
            XReadParams.xReadParams().count(5),
            Map.of(stream, new StreamEntryID("0-0")));                // cursor: começa do zero
    for (StreamEntry entry : batch.get(0).getValue()) {
        entry.getID();        // o último id lido é o próximo cursor
        entry.getFields();    // Map<String, String>
    }
    ```

=== "Lettuce"

    ```java
    RedisCommands<String, String> redis = connection.sync();
    redis.unlink(stream);
    XAddArgs args = new XAddArgs().maxlen(1000).approximateTrimming();
    for (Map<String, String> event : CombatEvents.events()) {
        String id = redis.xadd(stream, args, event);                  // 1789151635353-0
    }
    Long length = redis.xlen(stream);                                 // 20

    List<StreamMessage<String, String>> oldest = redis.xrange(stream, Range.unbounded(), Limit.from(3));
    List<StreamMessage<String, String>> newest = redis.xrevrange(stream, Range.unbounded(), Limit.from(3));

    List<StreamMessage<String, String>> batch = redis.xread(
            XReadArgs.Builder.count(5),
            XReadArgs.StreamOffset.from(stream, "0-0"));
    for (StreamMessage<String, String> message : batch) {
        message.getId();      // próximo cursor
        message.getBody();    // Map<String, String>
    }
    ```

## O que olhar no Redis Insight

No Browser, abra `{p}:events:combat`: o Insight reconhece o tipo Stream e mostra uma tabela com id e campos de cada entrada; a aba Consumer Groups ainda está vazia (isso vem na [próxima lição](03-consumer-groups.md)). Repare que os ids crescem com o tempo e que golpes gravados no mesmo milissegundo ganham `-0`, `-1`, `-2`. No Workbench, `XINFO STREAM {p}:events:combat` mostra `length`, `first-entry`, `last-entry` e `entries-added`.

## Por dentro

| Comando | O que faz |
|---|---|
| `XADD key MAXLEN ~ 1000 * campo valor ...` | Anexa uma entrada; `*` pede um id ao servidor; `MAXLEN ~` poda o excesso em blocos |
| `XLEN key` | Quantas entradas o stream tem |
| `XRANGE key - + COUNT n` | Entradas em ordem crescente de id; `-` e `+` são o menor e o maior id possíveis |
| `XREVRANGE key + - COUNT n` | O mesmo, de trás para frente: as últimas entradas |
| `XREAD COUNT n STREAMS key id` | Entradas com id maior que `id`; passe o último id lido para continuar |
| `XREAD BLOCK ms STREAMS key $` | Espera por entradas novas segurando a conexão (veja a [lição 102-04](04-conexoes-bloqueantes.md)) |
| `XTRIM key MAXLEN ~ 1000` | Poda fora do `XADD` |

O id `1789151634753-0` é o timestamp em milissegundos de quando o servidor gravou, mais uma sequência que recomeça em 0 a cada milissegundo. Ele é sempre crescente, mesmo se o relógio do servidor voltar: nesse caso o Redis mantém o timestamp anterior e só avança a sequência.

## Em produção

- Stream é armazenamento: sem `MAXLEN` (ou `MINID`) ele cresce para sempre. Escolha o teto pelo tempo que o leitor mais lento precisa para alcançar, e prefira `~`: a poda exata trabalha entrada a entrada em cada `XADD`.
- Campos e valores são strings pequenas; para payloads grandes grave a referência (a chave de um Hash ou JSON) e não o objeto inteiro.
- Em bancos com vários shards, um stream vive em um só shard; para volume alto, particione por zona ou por tipo (`{p}:events:combat:{zona}`).

## Desafio

Troque `maxLen(1000)` por `maxLen(10)` (Lettuce: `maxlen(10)`) e rode de novo: o `XLEN` continua 20. A poda aproximada só remove nós internos inteiros (100 entradas por nó, por padrão), então com 20 entradas não há nada para remover. Troque `approximateTrimming()` por `exactTrimming()` e o `XLEN` cai para 10.
