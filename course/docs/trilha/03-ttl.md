---
lesson: 100-04
title: "Chaves que expiram sozinhas (TTL)"
minutes: 10
kind: lab
no_steps: true
next_url: trilha/04-string/
next_title: "String: sessão, cooldown e contador"
state_text: "Rodou o lab e o verify passou? Marque como concluído."
---

# Chaves que expiram sozinhas (TTL)

<p class="lesson-meta">Trilha do workshop · Passo 3 de 6 · ~10 min · <a href="../fundamentos/04-chaves-ttl-scan/">versão completa</a></p>

Toda chave do Redis pode ter prazo de vida. Sessão que morre em 30 minutos, cache de uma hora, lock de 5 segundos: você cria com `SET ... EX`, consulta com `TTL`, renova com `EXPIRE`. Sem job de limpeza — o Redis apaga sozinho.

## Faça agora

**1. Veja a expiração com os próprios olhos** — no Workbench do Redis Insight (ou no `redis-cli`):

```redis
SET quest:session:demo "kaelith" EX 15
GET quest:session:demo     # "kaelith"
TTL quest:session:demo     # alguns segundos, contando
```

Espere 15 segundos e repita `GET` e `TTL`: voltam `nil` e `-2`. Ninguém apagou a chave.

**2. Rode o lab e confira:**

```bash
./quest run 100-04 jedis
./quest verify 100-04
```

## O que aconteceu

```java
try (RedisClient jedis = Clients.jedis()) {
    jedis.set(session, "kaelith", SetParams.setParams().ex(1800));  // nasce com prazo
    jedis.ttl(session);        // 1800
    jedis.ttl(semPrazo);       // -1: existe e nunca expira
    jedis.ttl(inexistente);    // -2: não existe

    jedis.expire(session, 3600);    // renova o prazo
    jedis.persist(session);         // remove o prazo (vira -1)
    jedis.rename(session, rotated); // valor E prazo migram para o novo nome
}
```

E no fim o lab percorre todas as suas chaves `quest:*` com `SCAN` — página por página, sem travar o servidor. `KEYS` faz a mesma busca em uma execução só e bloqueia o banco inteiro: só em desenvolvimento.

??? note "E o Lettuce?"

    Mesmos comandos, assinaturas um pouco diferentes: `redis.expire(...)` devolve `Boolean`, `redis.exists(...)` devolve `Long` (quantas das chaves pedidas existem), e o scan usa `ScanArgs`/`ScanCursor`. Rode `./quest run 100-04 lettuce` e compare.

??? tip "Ver no Redis Insight"
    Filtre por `quest:session:*`: a coluna **TTL** mostra o prazo caindo segundo a segundo, e dá para editar o TTL clicando na chave (é um `EXPIRE` por trás). Compare com `quest:player:kaelith`, que aparece como "No limit" — é o `-1` do console.

??? tip "Para ir além"
    - Convenção de nomes: `app:entidade:id` (`quest:session:7f3a9c`). O Insight agrupa por esses segmentos e `SCAN MATCH quest:session:*` acha só sessões.
    - Regra de produção: chave de sessão, cache ou lock **nasce com prazo**. Chave sem TTL só some se alguém apagar.
    - Detalhes de `EXPIRE ... NX/GT/LT`, `RENAME`, `UNLINK` e eviction: [lição completa 100-04](../fundamentos/04-chaves-ttl-scan.md).
