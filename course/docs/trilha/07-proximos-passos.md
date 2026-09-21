---
title: "E agora?"
---

# E agora?

<p class="lesson-meta">Workshop guiado · Fechamento · 55-60 min</p>

Com o ambiente preparado, você conectou Java ao Redis, mudou um valor, observou expiração e implementou um cooldown. Hash, ranking e a comparação com Lettuce foram demonstrações para abrir os próximos caminhos. Se começou sem preparo, consolide primeiro **PONG, SET/GET e TTL** e retome o exercício no seu ritmo.

## O que levar para sua aplicação

- Escolha o client conforme a topologia: standalone, Cluster OSS ou proxy do Cloud/Software. OSS Cluster API é uma opção explícita.
- Reutilize o client. Compartilhar uma conexão Lettuce funciona para operações comuns; uma operação bloqueante pede conexão e timeout próprios.
- Crie dados temporários com TTL e use comandos atômicos para a decisão que precisa ser indivisível.
- Timeout não prova que a escrita falhou: repita somente quando a operação puder ser repetida com segurança.

| Você viu | Continue no curso completo |
|---|---|
| Conexão e topologia | [Fundamentos](../fundamentos/index.md): Insight, SCAN, pipeline e MULTI |
| TTL, String, Hash e ranking | [Tipos](../101-tipos/index.md): também List, Set e Bloom |
| Comandos em uma conexão | [Eventos](../102-eventos/index.md): Pub/Sub, Streams, consumer groups e conexões bloqueantes |
| Perfis e documentos | [Busca](../201-busca/index.md): JSON, índices, filtros e similaridade |
| Comportamento do client | [Produção](../301-producao/index.md): timeouts, retry, client-side caching, TLS, SCH e failover |

## Jedis ou Lettuce?

Jedis favorece código síncrono e direto; Lettuce oferece sync, async e reactive e é o padrão no Spring Data Redis. Você pode ter acompanhado a comparação sem executar ambos: rode a segunda versão quando quiser explorar a diferença. [Comparação e versões do curso](../referencia/jedis-vs-lettuce.md).

Se houver tempo nos minutos 50-55, a [demo de client-side caching](../301-producao/02-client-side-caching.md) mostra leituras atendidas na memória da aplicação e invalidação após uma escrita. Ela é uma extensão opcional da hora guiada.

## Continue de onde parou

```bash
./quest list
./quest next
```

O CLI usa as verificações guardadas no seu Redis. O botão das páginas é um registro pessoal de estudo no navegador: marcar aqui não executa `verify` nem certifica uma habilidade. As páginas curtas e completas compartilham esse registro por lição.

**Pergunta para levar:** onde no seu backend uma expiração, uma decisão atômica ou uma estrutura já ordenada poderia simplificar o código?
