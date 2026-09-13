---
title: Comandos do quest
---

# Comandos do quest

O `quest` é um wrapper fino sobre `java -jar target/quest.jar`. Ele compila o projeto quando algo mudou e lê o `.env` da raiz.

| Comando | O que faz |
|---|---|
| `./quest start <id>` | Prepara a lição (semeia se preciso, roda o setup dos passos) e mostra os passos com o próximo a fazer |
| `./quest verify <id>` | Confere cada passo no seu Redis, marca os concluídos e aponta o que falta (`verify all` para tudo) |
| `./quest solve <id> [passo]` | Faz o próximo passo por você (roda o lab, executa o comando ou copia a solução do exercício e roda) |
| `./quest skip <id> [passo]` | Igual ao `solve`, mas marca o passo como pulado |
| `./quest next` | Vai para a primeira lição com passo pendente |
| `./quest run <id> jedis` ou `lettuce` ou `both` | Roda o lab pronto da lição |
| `./quest exercise <id> jedis` ou `lettuce` | Roda o SEU código do exercício (`JedisExercise` / `LettuceExercise`) |
| `./quest list` | Lições por curso com os passos concluídos |
| `./quest seed` | Carrega o mundo Ember Realm no seu prefixo (idempotente) |
| `./quest reset --yes` | Apaga todas as chaves do seu prefixo |
| `./quest doctor` | Relatório de conexão |

Ciclo de vida de cada passo, no estilo dos labs guiados: `start` prepara, você age, `verify` confere, `solve` faz por você quando travar, `skip` pula. O progresso dos passos fica no hash `{prefixo}:steps:<id>` do seu Redis.

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
