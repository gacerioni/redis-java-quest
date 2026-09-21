---
lesson: 101-02
title: "Hash: campos que mudam sozinhos"
minutes: 8
kind: lab
no_steps: true
next_url: trilha/06-sorted-set/
next_title: "Sorted Set: ranking sem ORDER BY"
state_text: "Rodou o lab e o verify passou? Registre seu estudo."
---

# Hash: campos que mudam sozinhos

<p class="lesson-meta">Demo junto com ranking · 42-50 min · <a href="../../101-tipos/02-hash/">versão completa</a></p>

Um Hash é uma chave com vários campos; nome, nível, saldo, status. Diferente de uma String com JSON dentro, cada campo é lido, escrito ou incrementado sozinho, no servidor: mudar o saldo não exige ler e regravar o objeto inteiro.

## Acompanhe a demonstração

```bash
./quest run 101-02 jedis
./quest verify 101-02
```

## O que aconteceu

A ficha de `quest:player:kaelith` em operações (o código está em `l101_02/JedisLab.java`):

```java
Map<String, String> all = jedis.hgetAll(sheet);                  // a ficha inteira
String hp = jedis.hget(sheet, "hp");                             // um campo só
List<String> few = jedis.hmget(sheet, "name", "class", "level"); // alguns campos

long gold = jedis.hincrBy(sheet, "gold", 250);   // soma no servidor, atômico

jedis.hset(sheet, Map.of("title", "Guardiã das Brasas", "mount", "grifo-cinzento"));
jedis.hexists(sheet, "mount");                                    // true
jedis.hlen(sheet);                                                // quantos campos

jedis.hexpire(buffs, 30, "haste");   // prazo por campo (Redis 7.4+):
                                    // o buff expira, o resto da ficha fica
```

- `HINCRBY` é o mesmo truque do `INCR`, mas dentro de um campo: sem `HGET`, somar em Java e `HSET` de volta.
- `HEXPIRE` dá prazo a um campo específico; o padrão antigo de "uma chave por buff" vira um campo com TTL.

??? note "E o Lettuce?"

    Mesmos comandos, retornos `Long`/`Boolean` em vez de primitivos, e `hmget` devolve `List<KeyValue<String,String>>`. Rode `./quest run 101-02 lettuce` e compare.

??? tip "Ver no Redis Insight"
    Abra `quest:player:kaelith`: o Hash vira uma tabela de campos e valores; dá para editar um campo ali mesmo. Em `quest:player:kaelith:buffs`, o campo `haste` aparece com TTL próprio e some em 30 s enquanto a chave continua existindo.

??? tip "Para ir além"
    - Hash para objetos planos (perfil, configuração); JSON quando há aninhamento. O Query Engine indexa campos de ambos; usamos JSON em [JSON e FT.SEARCH](../201-busca/01-json-search.md).
    - No caminho quente, prefira `HMGET` a `HGETALL`: a resposta só trafega o que você pediu.
    - Mais detalhes: [lição completa 101-02](../101-tipos/02-hash.md).
