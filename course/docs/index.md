---
title: Redis Java Quest
hide:
  - navigation
  - toc
---

<div class="quest-hero" markdown>

<p class="kicker">Aprenda Redis fazendo</p>

# Redis Java Quest

<p class="lead">Um curso self-paced de Redis para devs Java, com Jedis e Lettuce lado a lado. Você constrói o servidor de um MMORPG fictício, o Ember Realm, e aprende cache, sessão, filas, eventos, busca e produção no seu próprio Redis Cloud, de graça.</p>

<div class="quest-stats">
<div><strong>5</strong><span>cursos</span></div>
<div><strong>24</strong><span>lições</span></div>
<div><strong>2</strong><span>clients Java</span></div>
<div><strong>0</strong><span>reais pra começar</span></div>
</div>

<a class="quest-btn" href="comece-aqui/">Comece aqui: do zero ao primeiro comando</a>
<a class="quest-btn secondary" href="trilha/">Veio do workshop? Trilha de ~1 hora</a>

</div>

<p class="quest-track">Trilha principal</p>

<div class="quest-courses">
<a class="course-card" data-course="100" href="fundamentos/">
<span class="code">Fundamentos</span>
<h3>Fundamentos</h3>
<p>OSS, Cluster e Redis Cloud. Por que o client não precisa saber de shard. Conectar com Jedis e Lettuce, Redis Insight, chaves, TTL, SCAN e pipeline.</p>
<div class="meta"><span>Setup + 5 lições</span><span class="badge">Não iniciado</span></div>
<div class="course-progress"><span></span></div>
</a>
<a class="course-card" data-course="101" href="101-tipos/">
<span class="code">101</span>
<h3>Tipos de dados</h3>
<p>String, Hash, List, Set, Sorted Set e Bloom filter contados pela ficha do personagem, a fila da dungeon, o ranking do servidor e o baú já aberto.</p>
<div class="meta"><span>6 lições</span><span class="badge">Não iniciado</span></div>
<div class="course-progress"><span></span></div>
</a>
<a class="course-card" data-course="102" href="102-eventos/">
<span class="code">102</span>
<h3>Eventos</h3>
<p>Pub/Sub no chat da zona, Streams no diário de combate, consumer groups processando loot e a lição sobre conexões bloqueantes.</p>
<div class="meta"><span>4 lições</span><span class="badge">Não iniciado</span></div>
<div class="course-progress"><span></span></div>
</a>
<a class="course-card" data-course="201" href="201-busca/">
<span class="code">201</span>
<h3>Busca</h3>
<p>Documentos JSON, FT.SEARCH e FT.AGGREGATE na casa de leilões, busca híbrida com FT.HYBRID e itens parecidos com vector sets.</p>
<div class="meta"><span>4 lições</span><span class="badge">Não iniciado</span></div>
<div class="course-progress"><span></span></div>
</a>
<a class="course-card" data-course="301" href="301-producao/">
<span class="code">301</span>
<h3>Produção</h3>
<p>Timeouts, pool e retry, client-side caching, TLS, smart client handoffs e Active-Active com failover geográfico no client.</p>
<div class="meta"><span>5 lições</span><span class="badge">Não iniciado</span></div>
<div class="course-progress"><span></span></div>
</a>
</div>

<p class="quest-track">Em breve</p>

<div class="quest-courses">
<a class="course-card" href="referencia/jedis-vs-lettuce/">
<span class="code">Extra</span>
<h3>Spring Data Redis</h3>
<p>Lettuce por baixo do Spring Boot, cache com anotações e sessão HTTP no Redis.</p>
<div class="meta"><span>Em preparação</span><span class="badge soon">Em breve</span></div>
</a>
</div>

## Como funciona

1. **Faça a lição 0** (15 minutos): Java, Redis Cloud free sem cartão, clone do projeto, `.env` e o primeiro comando. Tudo roda contra um Redis de verdade, na nuvem, na região de São Paulo.
2. **Clone o repositório e rode `./quest`.** Cada lição é um programa Java que você executa com Jedis e depois com Lettuce. A saída mostra o comando Redis enviado e o resultado.
3. **Siga as lições e confira com `./quest check`.** O check inspeciona o seu Redis e diz o que está pronto e o que falta. Marque a lição como concluída: o progresso fica salvo no seu navegador.

!!! tip "Uma hora para começar, o resto no seu ritmo"
    Fundamentos e Tipos cabem em uma hora. Eventos, Busca e Produção são a lição de casa: cada uma leva entre 30 e 60 minutos.
