---
title: Comece aqui
---

# Comece aqui

Três passos e você roda a primeira lição contra um Redis de verdade.

<ul class="quest-lessons">
<li><span class="mark"></span><span class="num">1</span><a href="redis-cloud/">Crie seu Redis Cloud</a><span class="min">5 min</span></li>
<li><span class="mark"></span><span class="num">2</span><a href="maquina/">Prepare a máquina</a><span class="min">5 min</span></li>
<li><span class="mark"></span><span class="num">3</span><a href="primeira-licao/">Rode a primeira lição</a><span class="min">5 min</span></li>
</ul>

## O que você precisa

| Item | Detalhe |
|---|---|
| Java 21 ou mais novo | Qualquer distribuição (Temurin, Corretto, OpenJDK do brew) |
| Maven 3.9 | Ou o `mvnw` que vem no repositório, que baixa o Maven sozinho |
| Git | Para clonar o repositório |
| Um Redis | Redis Cloud free (recomendado) ou Docker local (`docker compose up -d`) |
| Uma IDE | IntelliJ, VS Code ou a que você já usa. Cada lição é uma classe com `main` |

## Como as lições funcionam

Cada lição tem três comandos, sempre iguais:

```bash
./quest run 101-02 jedis      # roda a lição com Jedis
./quest run 101-02 lettuce    # roda a mesma lição com Lettuce
./quest check 101-02          # inspeciona o seu Redis e diz o que falta
```

O código das duas versões fica em `src/main/java/com/emberrealm/quest/lessons/l101_02/`. Abra na IDE, mude, rode de novo. O `check` continua funcionando porque ele olha o estado no Redis, não o seu código.

!!! note "Progresso"
    O botão "Marcar como concluída" no fim de cada lição salva o progresso no seu navegador (localStorage). Nada sai da sua máquina.
