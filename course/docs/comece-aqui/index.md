---
lesson: setup
title: "Do zero ao primeiro comando"
minutes: 15
kind: setup
---

# Do zero ao primeiro comando

<p class="lesson-meta">Lição 0 · Setup · 15 min</p>

Ao fim deste preparo você tem um Redis na nuvem, o projeto compilado e sua primeira leitura e escrita. Faça o preparo antes da hora guiada do workshop. Siga na ordem; cada passo mostra o que você deve ver.

## Passo 1: confira o Java

O curso usa Java 21 ou mais novo. No terminal:

```bash
java -version
```

Você deve ver algo como `openjdk version "21.0.2"`. Se aparecer erro ou uma versão menor que 21, instale o JDK 21 pelo [Adoptium Temurin](https://adoptium.net/) e confira de novo em um terminal novo. Se usar SDKMAN, consulte `sdk list java` para escolher um identificador Temurin 21 disponível. Git também precisa estar instalado (`git --version`). Maven não precisa: o projeto traz o `mvnw`, que baixa o Maven sozinho.

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
PING: PONG
RTT médio: 9.12 ms
prefixo das suas chaves: quest
```

A latência é apenas um exemplo. Versão, permissões e resposta de `CLUSTER INFO` variam; o diagnóstico não identifica automaticamente qual produto está por trás do endpoint.

Se der erro de conexão, confira a URL (senha, host e porta) e se o banco está com o ícone verde no console. No Windows, use `quest.cmd doctor`.

## Passo 6: sua primeira leitura e escrita

```bash
./quest run 100-02 jedis
./quest verify 100-02
```

Leia a mensagem gravada e devolvida pelo Redis. Na [trilha guiada](../trilha/02-conectar.md), você também altera essa mensagem no Java. O seed ainda não é necessário.

## Passo 7: carregue os dados de exemplo

```bash
./quest seed
```

Isso grava o Ember Realm no seu Redis: 42 itens como documentos JSON, 12 personagens como hashes, o ranking de XP como sorted set, as zonas e um índice geográfico. Tudo com o prefixo `quest:`.

## Passo 8: veja os dados no Redis Insight

Na página do banco no Redis Cloud, clique em **Launch Redis Insight web** (sem instalar nada). Com Docker local, abra `http://localhost:5540`. Filtre por `quest:*` e navegue pelas chaves que o seed criou. Você vai voltar aqui em toda lição.

## Como toda lição funciona daqui pra frente

```bash
./quest run 100-01 jedis      # roda a lição com Jedis
./quest run 100-01 lettuce    # roda a mesma lição com Lettuce
./quest verify 100-01          # inspeciona o seu Redis e diz o que falta
```

O código das duas versões fica em `src/main/java/com/emberrealm/quest/lessons/l100_01/`. Abra na IDE, mude, rode de novo. O `verify` continua valendo porque ele olha o estado no Redis, não o seu código. Na IDE, execute `com.emberrealm.quest.core.Main` com argumentos como `run 100-02 jedis` e diretório de trabalho na raiz do projeto. As classes `JedisLab`/`LettuceLab` implementam `Lab`; não possuem um `main` próprio. O `.env` da raiz é lido automaticamente.

!!! note "Progresso"
    O botão no fim de cada página registra seu estudo no navegador (localStorage); ele não executa o check nem sincroniza com o CLI. O CLI guarda as evidências verificadas no seu Redis. Registre o preparo para o aviso de pré-requisito sumir das próximas páginas.

Pronto. Siga para [Fundamentos, lição 01: o mapa do mundo](../fundamentos/01-mapa-do-mundo.md).
