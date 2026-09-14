---
lesson: 101-01
title: "String: sessão, cooldown e contador"
minutes: 15
kind: lab
---

# String: sessão, cooldown e contador

<p class="lesson-meta">Trilha do workshop · Passo 4 de 6 · ~15 min · <a href="../101-tipos/01-string/">versão completa</a></p>

Uma String é um valor por chave — mas os detalhes resolvem três clássicos de backend: sessão que expira (`SET EX`), checar-e-gravar sem condição de corrida (`SET NX EX`) e contador atômico (`INCR`). No fim deste passo você escreve código pela primeira vez.

## Faça agora

```bash
./quest run 101-01 jedis
./quest verify 101-01     # vai marcar o lab verde e a "Sua vez" em vermelho — é esperado
```

## O que aconteceu

Cinco padrões em uma lição (o código está em `l101_01/JedisLab.java`):

```java
jedis.set(session, token, SetParams.setParams().ex(1800));   // sessão com prazo
jedis.set(cooldown, "1", SetParams.setParams().nx().ex(5));  // "OK" ou null — atômico
jedis.incr(kills);                                            // contador sem GET+SET
jedis.getDel(redeem);                                         // lê e apaga numa viagem só
jedis.mget(session, cooldown, kills);                         // várias chaves numa ida
```

- `SET ... EX`: a sessão nasce com TTL — dispensou o job de limpeza.
- `SET ... NX EX`: o segundo lançamento volta `nil` — checar e gravar em um único comando. Dois servidores tentando ao mesmo tempo: só um ganha.
- `INCR`/`INCRBY`: a soma acontece no servidor, atômica. Nada de `GET`, somar em Java, `SET` de volta.

**Antes da sua vez, mexa no Redis à mão:** no Workbench do Insight, some 10 ao contador que o lab criou:

```redis
INCRBY quest:kills:kaelith 10
GET quest:kills:kaelith     # o Redis fez a conta em cima da String
```

## Sua vez: `castHeal`

A primeira vez que você escreve código. A missão: um cooldown de 8 segundos — a cura só sai se a chave não existir, e cada cura que sai conta +1.

1. Abra `src/main/java/com/emberrealm/quest/lessons/l101_01/JedisExercise.java`
2. Implemente o método `castHeal` (hoje é um `throw new Todo(...)`):

| Regra | Como o check confere |
|---|---|
| Só cura se `quest:cooldown:kaelith:heal` não existir | duas curas seguidas: só a primeira sai |
| Ao curar, a chave nasce com TTL de 8 s | `TTL` entre 0 e 8 |
| Ao curar, `quest:heals:kaelith` cresce em 1 | o contador vale exatamente 1 |

3. Rode e confira:

```bash
./quest exercise 101-01 jedis
./quest verify 101-01
```

O resto do arquivo é o arnês: limpa as chaves, chama `castHeal` duas vezes e registra o resultado. Travou? `./quest solve 101-01 --yes` copia a solução de referência por cima do seu arquivo.

??? note "Ver a solução de referência"

    ```java
    static boolean castHeal(RedisClient jedis, String cooldownKey, String healsKey) {
        String reply = jedis.set(cooldownKey, "1",
                SetParams.setParams().nx().ex(HEAL_COOLDOWN_SECONDS));
        if (reply == null) return false;    // já existia: em cooldown
        jedis.incr(healsKey);
        return true;
    }
    ```

??? note "E o Lettuce?"
    Mesma lógica, outra assinatura: `redis.set(key, "1", SetArgs.Builder.nx().ex(8))` no lugar de `SetParams`. O exercício em `LettuceExercise.java` e `./quest exercise 101-01 lettuce`.

??? tip "Para ir além"
    - Faça `castHeal` devolver também quantos segundos faltam para curar de novo (`TTL`).
    - `SET NX EX` é o lock mais simples do Redis; em produção, libere apagando só se o valor ainda for o seu — detalhes na [lição completa 101-01](../101-tipos/01-string.md).

<div class="quest-complete" data-lesson="101-01" data-next-url="trilha/05-hash/" data-next-title="Hash: campos que mudam sozinhos"></div>
