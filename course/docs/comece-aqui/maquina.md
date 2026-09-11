---
title: Prepare a máquina
---

# Prepare a máquina

<p class="lesson-meta">Passo 2 · 5 min</p>

## Java 21

Confira a versão:

```bash
java -version
```

Se precisar instalar, o caminho mais simples é o [SDKMAN](https://sdkman.io/) (`sdk install java 21-tem`) ou o [Adoptium Temurin 21](https://adoptium.net/). No macOS com Homebrew, `brew install openjdk@21` também serve.

## Clone o repositório

```bash
git clone https://github.com/gacerioni/redis-java-quest.git
cd redis-java-quest
```

## Configure a URL do Redis

```bash
cp .env.example .env
```

Abra o `.env` e cole a URL que você montou no passo anterior:

```text
REDIS_URL=redis://default:SUA_SENHA@redis-12345.c308.sa-east-1-1.ec2.redns.redis-cloud.com:12345
```

O `.env` está no `.gitignore`. Sua senha não vai para o git.

## Verifique

```bash
./quest doctor
```

A primeira execução compila o projeto e baixa as dependências (Jedis, Lettuce e pouca coisa mais), então leva um minuto. Depois é instantâneo. Você deve ver a versão do Redis, a latência de ida e volta e a resposta do servidor à pergunta "você é um cluster?".

!!! note "Windows"
    Use `quest.cmd` no lugar de `./quest` no PowerShell ou no cmd. No Git Bash, `./quest` funciona normalmente.

## Na IDE

Abra a pasta como projeto Maven. Cada lição tem duas classes com `main`, `JedisLab` e `LettuceLab`, dentro de `src/main/java/com/emberrealm/quest/lessons/`. Para rodar pela IDE, aponte a variável de ambiente `REDIS_URL` na configuração de execução (ou deixe o `.env` na raiz do projeto, que o código também lê).
