---
lesson: setup
title: "Setup: do zero ao PONG"
minutes: 15
kind: setup
next_url: trilha/02-conectar/
next_title: "Conectar e o primeiro SET/GET"
state_text: "PONG no terminal? Marque como concluído."
---

# Setup: do zero ao PONG

<p class="lesson-meta">Trilha do workshop · Passo 1 de 6 · ~15 min</p>

Único passo sem código da trilha. No fim dele você tem um Redis de verdade, o projeto rodando e a resposta `PONG` no terminal.

## Faça agora

**1. Confira o Java** — o curso usa Java 21 ou mais novo:

```bash
java -version    # esperado: openjdk version "21..." ou maior
git --version
```

Sem Java 21+? Instale pelo [Adoptium Temurin 21](https://adoptium.net/) (ou `brew install openjdk@21` no macOS, `sdk install java 21-tem` com SDKMAN). Maven não precisa: o projeto traz o wrapper `mvnw`.

**2. Tenha um Redis** — escolha um:

=== "Redis Cloud (recomendado)"

    1. Abra [redis.io/try-free](https://redis.io/try-free/) e entre com Google, GitHub ou e-mail (grátis, sem cartão).
    2. Em **Create database**, deixe o plano **Free** e escolha **AWS / South America (São Paulo)**.
    3. Espere o ícone ficar verde. Na página do banco, copie a senha do **Default user** e o **Public endpoint** (`redis-12345....redis-cloud.com:12345`).
    4. Monte a URL assim e guarde:

    ```text
    redis://default:SUA_SENHA@redis-12345.c308.sa-east-1-1.ec2.redns.redis-cloud.com:12345
    ```

=== "Docker local"

    ```bash
    docker compose up -d    # dentro da pasta do projeto (passo 3): sobe Redis 8 + Redis Insight
    ```

    A URL é `redis://localhost:6379`. Tudo funciona igual; dá para migrar para o Cloud depois só trocando a URL.

**3. Clone o projeto e preencha o `.env`:**

```bash
git clone https://github.com/gacerioni/redis-java-quest.git
cd redis-java-quest
cp .env.example .env      # Windows: Copy-Item .env.example .env
```

Abra o `.env` e preencha `REDIS_URL=` com a sua URL do passo 2. O `.env` está no `.gitignore`: a senha não vai para o git.

**4. Rode o doctor:**

```bash
./quest doctor            # Windows: .\quest.cmd doctor
```

A primeira execução compila o projeto e baixa as dependências (~1 min). O que você quer ver:

```text
PING: PONG
RTT médio: 9.12 ms
```

**5. Carregue o mundo:**

```bash
./quest seed
```

Isso grava o Ember Realm no seu Redis: itens em JSON, personagens em hash, ranking em sorted set — tudo sob o prefixo `quest:` (o `doctor` mostra o seu prefixo; com usuário ACL próprio ele é o seu nome de usuário).

**6. (Opcional) Abra o Redis Insight** — no Redis Cloud: **Launch Redis Insight web** na página do banco. Com Docker: [localhost:5540](http://localhost:5540). Filtre por `quest:*` e olhe as chaves que o seed criou. Você vai voltar aqui em todos os passos.

## Deu errado?

| Sintoma | Próximo passo |
|---|---|
| `java` não roda | Confira Java 21+ e se o terminal está na pasta do projeto |
| Conexão recusada / timeout | Reveja host, porta, rede/VPN e se o banco está com ícone verde |
| Erro de autenticação / `NOPERM` | Confira a URL completa (usuário, senha, `redis://` vs `rediss://`) |
| `PONG` funciona, `seed` falha | O banco precisa ser Redis 8 com JSON/Search (o free tier e o Docker do curso são) |
