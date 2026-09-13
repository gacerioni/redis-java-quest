---
lesson: setup
title: "Do zero ao primeiro comando"
minutes: 15
kind: setup
---

# Do zero ao primeiro comando

<p class="lesson-meta">Lição 0 · Setup · 15 min</p>

Esta é a única lição em que você não escreve código. Ao fim dela você tem um Redis de verdade na nuvem, o projeto do curso rodando na sua máquina e o mundo Ember Realm carregado. Siga na ordem; cada passo mostra o que você deve ver.

## Passo 1: confira o Java

O curso usa Java 21 ou mais novo. No terminal:

```bash
java -version
```

Você deve ver algo como `openjdk version "21.0.2"`. Se aparecer erro ou uma versão menor que 21, instale pelo [SDKMAN](https://sdkman.io/) (`sdk install java 21-tem`), pelo [Adoptium Temurin 21](https://adoptium.net/) ou, no macOS, `brew install openjdk@21`. Git também precisa estar instalado (`git --version`). Maven não precisa: o projeto traz o `mvnw`, que baixa o Maven sozinho.

## Passo 2: crie seu Redis Cloud (grátis, sem cartão)

1. Abra [redis.io/try-free](https://redis.io/try-free/) e entre com Google, GitHub ou e-mail.
2. Confirme o e-mail de ativação. Você cai em **Create your database** com o plano **Free** já selecionado.
3. Dê um nome ao banco (ou aceite o gerado).
4. Em **Database version**, escolha a mais nova disponível. A lição de busca híbrida (201-03) precisa de Redis 8.4 ou mais novo; as demais funcionam em qualquer 8.x.
5. Em **Cloud vendor**, escolha **AWS**; em **Region**, **South America (São Paulo)**, `sa-east-1`.
6. Clique em **Create database** e espere o ícone ficar verde.

Agora pegue a URL:

1. Na página do banco, seção **Security**, clique no olho ao lado de **Default user password** e copie a senha.
2. Em **General**, copie o **Public endpoint**, algo como `redis-12345.c308.sa-east-1-1.ec2.redns.redis-cloud.com:12345`.
3. Monte a URL neste formato e guarde:

```text
redis://default:SUA_SENHA@redis-12345.c308.sa-east-1-1.ec2.redns.redis-cloud.com:12345
```

!!! tip "Sem conta agora?"
    O repositório traz um `docker compose up -d` que sobe Redis 8 e Redis Insight na sua máquina. Tudo funciona igual; a URL vira `redis://localhost:6379`. Você pode migrar para o Cloud depois só trocando a URL.

## Passo 3: clone o projeto

```bash
git clone https://github.com/gacerioni/redis-java-quest.git
cd redis-java-quest
```

## Passo 4: cole a URL no .env

```bash
cp .env.example .env
```

Abra o `.env` no editor e troque a linha `REDIS_URL=` pela sua URL do passo 2:

```text
REDIS_URL=redis://default:SUA_SENHA@redis-12345.c308.sa-east-1-1.ec2.redns.redis-cloud.com:12345
```

O `.env` está no `.gitignore`: a senha não vai para o git.

## Passo 5: rode o doctor

```bash
./quest doctor
```

A primeira execução compila o projeto e baixa as dependências, então leva cerca de um minuto. Depois é instantâneo. O resultado esperado:

```text
== 100-01 O mapa do mundo: OSS, Cluster e Redis Cloud  [jedis] ==
   Redis: redis://default:****@redis-12345....redis-cloud.com:12345   prefixo: quest
-> Um endpoint só: ...
   PING: PONG
   redis_version: 8.4.0
   RTT médio: 9.12 ms
   > CLUSTER INFO
   resposta: ERR ...
   dica: Sem cluster do lado do client: no Redis Cloud o proxy roteia para os shards.
[OK] Lição 100-01 concluída com jedis.
```

Se der erro de conexão, confira a URL (senha, host e porta) e se o banco está com o ícone verde no console. No Windows, use `quest.cmd doctor`.

## Passo 6: carregue o mundo

```bash
./quest seed
```

Isso grava o Ember Realm no seu Redis: 42 itens como documentos JSON, 12 personagens como hashes, o ranking de XP como sorted set, as zonas e um índice geográfico. Tudo com o prefixo `quest:`.

## Passo 7: veja o mundo no Redis Insight

Na página do banco no Redis Cloud, clique em **Launch Redis Insight web** (sem instalar nada). Com Docker local, abra `http://localhost:5540`. Filtre por `quest:*` e navegue pelas chaves que o seed criou. Você vai voltar aqui em toda lição.

## Como toda lição funciona daqui pra frente

```bash
./quest run 100-01 jedis      # roda a lição com Jedis
./quest run 100-01 lettuce    # roda a mesma lição com Lettuce
./quest check 100-01          # inspeciona o seu Redis e diz o que falta
```

O código das duas versões fica em `src/main/java/com/emberrealm/quest/lessons/l100_01/`. Abra na IDE, mude, rode de novo. O `check` continua valendo porque ele olha o estado no Redis, não o seu código. Na IDE, cada lição é uma classe com `main`; aponte `REDIS_URL` na configuração de execução ou deixe o `.env` na raiz, que o código também lê.

!!! note "Progresso"
    O botão no fim de cada lição salva o progresso no seu navegador (localStorage). Nada sai da sua máquina. Marque esta lição como concluída para o aviso de pré-requisito sumir das próximas.

Pronto. Siga para [Fundamentos, lição 01: o mapa do mundo](../fundamentos/01-mapa-do-mundo.md).
