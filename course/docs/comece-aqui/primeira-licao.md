---
title: Rode a primeira lição
---

# Rode a primeira lição

<p class="lesson-meta">Passo 3 · 5 min</p>

## Semeie o mundo

```bash
./quest seed
```

Isso carrega o Ember Realm no seu Redis: 42 itens como documentos JSON, 12 personagens como hashes, o ranking de XP como sorted set, as zonas como hashes e um índice geográfico. Tudo com o prefixo `quest:`.

## Veja o mapa das lições

```bash
./quest list
```

## Rode a lição 100-01 com os dois clients

```bash
./quest run 100-01 jedis
./quest run 100-01 lettuce
./quest check 100-01
```

Leia a saída com calma. Cada linha que começa com `>` é o comando Redis que o client enviou. Cada linha `[OK]` é uma etapa que deu certo. O `check` mostra o que ficou registrado no Redis.

## Abra o Redis Insight

No Redis Cloud, clique em **Launch Redis Insight web** na página do banco. Com Docker, abra `http://localhost:5540`. Filtre por `quest:*` e navegue pelas chaves que o seed criou. Você vai voltar aqui em toda lição.

## Siga para o curso

Agora sim: [Fundamentos, lição 01: o mapa do mundo](../fundamentos/01-mapa-do-mundo.md). No fim de cada lição, marque como concluída para acompanhar o progresso.

!!! tip "Recomeçar do zero"
    `./quest reset --yes` apaga todas as chaves do seu prefixo. Depois, `./quest seed` recarrega o mundo.
