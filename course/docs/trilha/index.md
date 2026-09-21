---
title: "Workshop guiado de 60 min"
---

# Workshop guiado de 60 min

Uma hora para conectar Java ao Redis Cloud, observar dados de verdade e implementar um cooldown com TTL. **Jedis é o caminho principal**; uma comparação com Lettuce mostra o que muda no código e nas conexões. O curso completo fica disponível para continuar depois.

!!! important "Antes do encontro: ambiente pronto até o PONG"
    Faça o [preparo antecipado](01-setup.md): Java 21+, Git, Redis, projeto e `./quest doctor`. Reserve cerca de 15 minutos, com margem para instalação e rede. Esse preparo fica **fora** dos 60 minutos.

    Se você chegar sem ambiente pronto, acompanhe a demonstração. Seu objetivo prático nessa hora será **PONG + SET/GET + TTL**; exercício e demais tipos ficam para depois, sem correr para acompanhar todos os passos.

## O roteiro ao vivo

Os horários incluem explicação e transições. As páginas abaixo são apoio; não é preciso ler o curso inteiro durante a apresentação.

| Minutos | O que acontece | Sua participação |
|---|---|---|
| 0-3 | Um ranking muda ao vivo no Redis Insight | Veja o resultado que vamos construir |
| 3-8 | Standalone, Cluster OSS e Cloud/Software | Identifique quem roteia os comandos até os shards |
| 8-18 | [Conexão + SET/GET](02-conectar.md) | Rode o lab e altere uma mensagem |
| 18-30 | [Chaves com TTL](03-ttl.md) | Grave uma chave, acompanhe o prazo e veja-a desaparecer |
| 30-42 | [String: sua vez](04-string.md) | Implemente o cooldown e confira o resultado |
| 42-50 | [Hash](05-hash.md), [ranking](06-sorted-set.md) e comparação Lettuce | Acompanhe a demo; altere um valor se já terminou |
| 50-55 | Boas práticas de conexão; client-side caching se houver tempo | Relacione o que viu com uma aplicação real |
| 55-60 | Perguntas e [próximos passos](07-proximos-passos.md) | Escolha o próximo tema do curso |

## Qual client para qual Redis?

Os clients são bibliotecas open source mantidas pela Redis. A aplicação escolhe a classe conforme **a topologia exposta pelo banco**, não pelo nome do host.

| Banco / modo de acesso | Quem roteia | Client Java |
|---|---|---|
| Redis Open Source standalone | Um endpoint do servidor | `RedisClient` do Jedis ou do Lettuce |
| Redis Open Source Cluster | O client mantém slots e trata redirecionamentos | `RedisClusterClient` do Jedis 8 ou do Lettuce |
| Redis Cloud / Redis Software, modo padrão | O proxy encaminha para os shards | `RedisClient` do Jedis ou do Lettuce |
| Cloud / Software com **OSS Cluster API habilitada** | O client descobre a topologia e acessa endpoints por shard, ainda através de proxies locais | `RedisClusterClient` |

Uma conexão recusada ou um erro em `CLUSTER INFO` não identifica sozinho o produto nem a topologia. Confira a configuração do banco. [Detalhes e diagrama no curso completo](../fundamentos/01-mapa-do-mundo.md).

**Regra para os dois clients:** um client por aplicação. Jedis usa um pool; Lettuce permite compartilhar uma conexão entre threads. Comandos bloqueantes precisam de conexão dedicada e timeout apropriado. RESP3, client-side caching e smart client handoffs são capacidades específicas; não significam que qualquer client descobre automaticamente qualquer topologia.

## Páginas de apoio

<ul class="quest-lessons">
<li data-lesson="setup"><span class="mark"></span><span class="num">0</span><a href="01-setup/">Preparo: do zero ao PONG</a><span class="min">~15 min antes</span></li>
<li data-lesson="100-02"><span class="mark"></span><span class="num">1</span><a href="02-conectar/">Conectar e alterar uma mensagem</a><span class="min">Ao vivo</span></li>
<li data-lesson="100-04"><span class="mark"></span><span class="num">2</span><a href="03-ttl/">Ver uma chave expirar</a><span class="min">Ao vivo</span></li>
<li data-lesson="101-01"><span class="mark"></span><span class="num">3</span><a href="04-string/">Implementar um cooldown</a><span class="min">Ao vivo</span></li>
<li data-lesson="101-02"><span class="mark"></span><span class="num">4</span><a href="05-hash/">Demo Hash: atualizar campos</a><span class="min">Demonstração</span></li>
<li data-lesson="101-05"><span class="mark"></span><span class="num">5</span><a href="06-sorted-set/">Demo Sorted Set: atualizar o ranking</a><span class="min">Demonstração</span></li>
</ul>

## Como o `./quest` funciona

Cada lab é código Java que roda contra o seu Redis. Para a hora guiada, use estes comandos:

| Comando | O que faz |
|---|---|
| `./quest doctor` | Confere a conexão e mostra `PING → PONG`, prefixo e latência |
| `./quest run <lição> jedis` | Executa o **lab pronto**; leia o código e a saída |
| `./quest verify <lição>` | Confere resultados no Redis e informa pendências |
| `./quest exercise 101-01 jedis` | Roda o **seu código** do exercício de String |
| `./quest seed` | Carrega os dados de exemplo, depois do primeiro SET/GET |

No Windows, use `quest.cmd`. A primeira execução baixa dependências e compila: faça isso no preparo. O projeto recompila quando o código muda.

Linhas com `>` mostram os comandos; as seguintes mostram as respostas. O `verify` verifica estado e evidências de execução, **não prova sozinho que o código é seguro sob concorrência ou pronto para produção**. Compare a solução e explique por que usou cada comando.

Travou no exercício? Depois de concluir os passos anteriores, `./quest solve 101-01 --yes` aplica a solução de referência no arquivo do exercício. O curso completo explica `start`, `steps`, `skip` e `next` na [referência do CLI](../referencia/cli.md).

O dataset **Ember Realm** fornece perfis, itens e ranking para os labs. `quest:` é o prefixo padrão; se o `doctor` mostrar outro, use o seu prefixo nos comandos manuais. O código Java faz isso automaticamente.
