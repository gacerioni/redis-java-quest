---
title: Comandos do quest
---

# Comandos do quest

O `quest` é um wrapper fino sobre `java -jar target/quest.jar`. Ele compila o projeto quando algo mudou e lê o `.env` da raiz.

| Comando | O que faz |
|---|---|
| `./quest list` | Lista as lições por curso e marca as concluídas (lê os marcadores no Redis) |
| `./quest seed` | Carrega o mundo Ember Realm no seu prefixo (idempotente) |
| `./quest run <id> jedis` | Roda a lição com Jedis |
| `./quest run <id> lettuce` | Roda a lição com Lettuce |
| `./quest run <id> both` | Roda com os dois, em sequência |
| `./quest check <id>` | Inspeciona o Redis e diz o que está pronto e o que falta |
| `./quest check all` | Roda todos os checks |
| `./quest progress` | Mostra as lições com marcador gravado |
| `./quest reset --yes` | Apaga todas as chaves do seu prefixo |
| `./quest doctor` | Relatório de conexão (o mesmo que `run 100-01 jedis`) |

No Windows, troque `./quest` por `quest.cmd`.

## Variáveis de ambiente (ou `.env`)

| Variável | Padrão | Uso |
|---|---|---|
| `REDIS_URL` | `redis://localhost:6379` | URL do Redis: `redis://usuario:senha@host:porta` ou `rediss://` com TLS |
| `QUEST_PREFIX` | usuário da URL, ou `quest` | Prefixo de todas as chaves. Em um banco compartilhado, um por pessoa |
| `REDIS_TLS_URL` | vazio | Lição 301-03. URL `rediss://` de um banco pago |
| `OLLAMA_URL` | `http://localhost:11434` | Lição 201-03. Ollama local para vetorizar perguntas novas |
| `QUEST_WAITERS` | `5` | Lição 102-04. Quantos jogadores esperam bloqueados na fila |
| `QUEST_EAST_URL` e `QUEST_WEST_URL` | `redis://localhost:6391` e `6392` | Lição 301-05. Os dois "datacenters" do failover |
| `NO_COLOR` | vazio | Desliga as cores da saída |

## Rodando pela IDE

Cada lição é uma classe com `main` em `src/main/java/com/emberrealm/quest/lessons/l<id>/`. A classe de entrada é `com.emberrealm.quest.core.Main`, com os argumentos acima (`run 101-02 jedis`). O `.env` da raiz é lido automaticamente.
