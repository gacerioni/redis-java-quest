---
lesson: 101-01
title: "String: sessão, cooldown e contador"
minutes: 12
kind: lab
no_steps: true
next_url: trilha/05-hash/
next_title: "Hash: campos que mudam sozinhos"
state_text: "Lab verde e castHeal implementado? Registre seu estudo."
---

# String: sessão, cooldown e contador

<p class="lesson-meta">Ao vivo · 30-42 min · <a href="../../101-tipos/01-string/">versão completa</a></p>

Uma String é um valor por chave; mas os detalhes resolvem três clássicos de backend: sessão que expira (`SET EX`), checar-e-gravar sem condição de corrida (`SET NX EX`) e contador atômico (`INCR`). No fim deste passo você implementa sua primeira regra usando Redis.

## Faça agora

```bash
./quest run 101-01 jedis
./quest verify 101-01     # vai marcar o lab verde e a "Sua vez" em vermelho; é esperado
```

## O que aconteceu

No lab completo há cinco padrões. Na hora guiada, concentre-se em `SET NX EX` e `INCR`; `GETDEL` e `MGET` ficam para explorar depois (código em `l101_01/JedisLab.java`):

```java
jedis.set(session, token, SetParams.setParams().ex(1800));   // sessão com prazo
jedis.set(cooldown, "1", SetParams.setParams().nx().ex(5));  // "OK" ou null; atômico
jedis.incr(kills);                                            // contador sem GET+SET
jedis.getDel(redeem);                                         // lê e apaga numa viagem só
jedis.mget(session, cooldown, kills);                         // várias chaves numa ida
```

- `SET ... EX`: a sessão nasce com TTL; dispensou o job de limpeza.
- `SET ... NX EX`: o segundo lançamento volta `nil`; checar e gravar em um único comando. Dois servidores tentando ao mesmo tempo: só um ganha.
- `INCR`/`INCRBY`: a soma acontece no servidor, atômica. Nada de `GET`, somar em Java, `SET` de volta.

**Antes da sua vez, mexa no Redis à mão:** no Workbench do Insight, some 10 ao contador que o lab criou:

```redis
INCRBY quest:kills:kaelith 10
GET quest:kills:kaelith     # o Redis fez a conta em cima da String
```

## Sua vez: `castHeal`

Agora você implementa uma regra de backend: um cooldown de 8 segundos; a cura só sai se a chave não existir, e cada cura que sai conta +1.

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

O restante do arquivo prepara e verifica a execução: limpa as chaves, chama `castHeal` duas vezes e registra o resultado. Travou? Depois de verificar os passos anteriores, `./quest solve 101-01 --yes` aplica a solução de referência no arquivo.

O check chama o método duas vezes em sequência. Para evitar a corrida entre várias instâncias, a decisão de aceitar o cooldown precisa estar no **único `SET NX EX`**; não faça `GET` seguido de `SET`. O `INCR` seguinte é outro comando: cooldown e contagem juntos não formam uma transação.

Nos comandos manuais, substitua `quest` pelo prefixo mostrado no `doctor`, caso seja diferente.

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
    - `SET NX EX` é o lock mais simples do Redis; em produção, libere apagando só se o valor ainda for o seu; detalhes na [lição completa 101-01](../101-tipos/01-string.md).
