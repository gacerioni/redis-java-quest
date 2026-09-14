---
title: "Trilha do workshop"
---

# Trilha do workshop

A versão curta do curso, pensada para quem veio do workshop: **seis passos, cerca de uma hora**, do `PING` até um ranking de verdade. Você conecta o Java ao Redis, grava e lê dados e sai conhecendo três tipos além de cache: String, Hash e Sorted Set.

Tudo roda contra o seu próprio Redis (de graça) com o projeto `redis-java-quest`. Os exemplos usam **Jedis** como caminho principal; em cada página o bloco "E o Lettuce?" mostra a mesma operação no outro client, para comparar quando quiser.

<ul class="quest-lessons">
<li data-lesson="setup"><span class="mark"></span><span class="num">1</span><a href="01-setup/">Setup: do zero ao PONG</a><span class="min">15 min</span></li>
<li data-lesson="100-02"><span class="mark"></span><span class="num">2</span><a href="02-conectar/">Conectar e o primeiro SET/GET</a><span class="min">10 min</span></li>
<li data-lesson="100-04"><span class="mark"></span><span class="num">3</span><a href="03-ttl/">Chaves que expiram sozinhas (TTL)</a><span class="min">10 min</span></li>
<li data-lesson="101-01"><span class="mark"></span><span class="num">4</span><a href="04-string/">String: sessão, cooldown e contador</a><span class="min">15 min</span></li>
<li data-lesson="101-02"><span class="mark"></span><span class="num">5</span><a href="05-hash/">Hash: campos que mudam sozinhos</a><span class="min">10 min</span></li>
<li data-lesson="101-05"><span class="mark"></span><span class="num">6</span><a href="06-sorted-set/">Sorted Set: ranking sem ORDER BY</a><span class="min">10 min</span></li>
</ul>

## Como o `./quest` funciona

Cada lição é um programa Java de verdade que roda contra o seu Redis. O `./quest` é o controle remoto — você só precisa destes:

| Comando | O que faz |
|---|---|
| `./quest doctor` | Testa a conexão: `PING → PONG`, seu prefixo de chaves e a latência |
| `./quest seed` | Carrega o mundo Ember Realm no seu Redis (uma vez só) |
| `./quest run <lição> jedis` | Executa o **lab pronto** — você não escreve nada, só lê a saída |
| `./quest verify <lição>` | Olha o seu Redis e confere: verde = feito, vermelho = o que falta |
| `./quest exercise <lição> jedis` | Roda o **seu código** — o arquivo `JedisExercise` da lição |
| `./quest solve <lição> --yes` | Travou? Copia a solução de referência por cima do seu arquivo |

Três coisas para saber:

1. **A primeira execução demora** — o `./quest` compila o projeto e baixa as dependências; depois fica instantâneo, exceto quando você edita um `Exercise` (ele recompila antes de rodar).
2. **A saída mostra o fio** — linhas que começam com `>` são o comando Redis que o client enviou; a linha seguinte é a resposta.
3. **O `verify` olha o Redis, não o seu código** — se a chave está certa no banco, ele passa, não importa como você escreveu.

Pode ignorar por enquanto: `start`, `steps`, `skip`, `next`, `reset`, `progress` e `check` (sinônimo de `verify`) — eles servem ao curso completo.

!!! note "Antes de começar"
    Você precisa de Java 21+, Git e um Redis: Redis Cloud free (recomendado) ou Docker. O passo 1 cobre os dois caminhos.

!!! tip "O mundo Ember Realm"
    O `./quest seed` carrega um mini-mundo de jogo no seu Redis: itens, personagens, ranking. Ele existe para dar dados de verdade às lições — as chaves são `quest:*` e os conceitos são os mesmos do seu aplicativo do dia a dia.

Terminou? [E agora?](07-proximos-passos.md) mostra onde cada tema continua no curso completo: eventos, busca e produção.
