---
lesson: setup
title: "Preparo antes do workshop: do zero ao PONG"
minutes: 15
kind: setup
next_url: trilha/02-conectar/
next_title: "Conectar e o primeiro SET/GET"
state_text: "PONG no terminal? Registre seu estudo."
---

# Preparo antes do workshop: do zero ao PONG

<p class="lesson-meta">Antes do workshop · Reserve ~15 min, além dos 60 min ao vivo</p>

Faça este preparo antes do encontro. No fim, você tem um Redis, o projeto compilado e `PONG` no terminal. Instalação, download e acesso à rede podem pedir mais tempo. Ainda não é necessário carregar o dataset.

## Faça agora

**1. Confira o Java.** O curso usa Java 21 ou mais novo:

```bash
java -version    # esperado: openjdk version "21..." ou maior
git --version
```

Sem Java 21+? Instale o JDK 21 pelo [Adoptium Temurin](https://adoptium.net/), abra um terminal novo e confira `java -version` novamente. Maven não precisa: o projeto traz o wrapper `mvnw`.

**2. Clone o projeto e crie o `.env`:**

```bash
git clone https://github.com/gacerioni/redis-java-quest.git
cd redis-java-quest
cp .env.example .env      # Windows PowerShell: Copy-Item .env.example .env
```

**3. Tenha um Redis.** Escolha um:

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
    docker compose up -d    # dentro da pasta do projeto: sobe Redis 8 + Redis Insight
    ```

    A URL é `redis://localhost:6379`. Tudo funciona igual; dá para migrar para o Cloud depois só trocando a URL.

**4. Preencha o `.env`:**

Abra o `.env` e preencha `REDIS_URL=` com a sua URL do passo 3. O `.env` está no `.gitignore`: a senha não vai para o git.

**5. Rode o doctor:**

```bash
./quest doctor            # Windows: .\quest.cmd doctor
```

A primeira execução compila o projeto e baixa as dependências (~1 min). O que você quer ver:

```text
PING: PONG
RTT médio: 9.12 ms
```

**6. Deixe o Redis Insight pronto.** No Redis Cloud: **Launch Redis Insight web** na página do banco. Com Docker: [localhost:5540](http://localhost:5540), adicionando o banco com host `redis` e porta `6379` (nomes dentro da rede do Docker). Conecte ao mesmo banco; o Workbench será usado para alterar e observar dados.

Pronto: ambiente preparado. O primeiro `SET/GET` vem na [próxima página](02-conectar.md), durante o encontro. Só depois dele carregaremos o dataset com `./quest seed`. Para seguir sozinho, pode continuar agora.

## Deu errado?

| Sintoma | Próximo passo |
|---|---|
| `java` não roda | Confira Java 21+ e se o terminal está na pasta do projeto |
| Conexão recusada / timeout | Reveja host, porta, rede/VPN e se o banco está com ícone verde |
| Erro de autenticação / `NOPERM` | Confira a URL completa (usuário, senha, `redis://` vs `rediss://`) |
| `PONG` funciona, `seed` falha | O banco precisa ser Redis 8 com JSON/Search (o free tier e o Docker do curso são) |

Confira a [agenda ao vivo e os comandos essenciais](index.md). Se o preparo ainda não funcionar, acompanhe a demonstração e priorize PONG, SET/GET e TTL; o curso permanece disponível depois.
