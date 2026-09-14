---
title: "E agora?"
---

# E agora?

<p class="lesson-meta">Trilha do workshop · Fechamento</p>

Em uma hora você conectou o Java ao Redis com dois clients, gravou e leu dados de verdade, viu chaves expirarem sozinhas, escreveu seu primeiro código contra o banco e usou três tipos além de cache. O que cada tema vira quando você aprofunda:

| Você viu | No curso completo |
|---|---|
| SET/GET e a anatomia da URL | Topologias (OSS, Cluster, proxy do Cloud), Redis Insight a fundo, pipeline e MULTI — [Fundamentos](../fundamentos/index.md) |
| `INCRBY` à mão, TTL, String, Hash, Sorted Set | List (fila de jobs), Set (conjuntos e sorteio), Bloom (já vi isso?) — [Tipos](../101-tipos/index.md) |
| Comandos simples | Pub/Sub, Streams e consumer groups — [Eventos](../102-eventos/index.md) |
| Hash vs JSON | Documentos JSON com índice, FT.SEARCH, agregações e busca por similaridade — [Busca](../201-busca/index.md) |
| Um client por aplicação | Timeouts, pool e retry, client-side caching, TLS, Active-Active — [Produção](../301-producao/index.md) |

## Jedis ou Lettuce?

Você rodou os dois. A escolha prática: Jedis quando a base é síncrona e simples; Lettuce quando você já vive de `CompletableFuture`, Reactor ou Spring WebFlux (o Spring Data Redis usa Lettuce por baixo). Comparação completa em [Jedis ou Lettuce?](../referencia/jedis-vs-lettuce.md).

## Continuar no seu ritmo

```bash
./quest list        # as 24 lições e seu progresso
./quest next        # vai para a primeira lição com passo pendente
```

O progresso que você marcou nesta trilha já conta nas lições completas — elas são as mesmas lições, com mais contexto, referência de comandos e desafios.

!!! tip "Pergunta para levar para casa"
    Onde no seu aplicativo de hoje uma sessão com TTL, um contador atômico ou um ranking resolveriam algo que você faz "na mão" no banco relacional?
