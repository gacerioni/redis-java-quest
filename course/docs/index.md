---
title: Redis Java Quest
hide:
  - navigation
  - toc
---

<div class="quest-hero" markdown>

<p class="kicker">Aprenda Redis fazendo</p>

# Redis Java Quest

<p class="lead">Aprenda Redis com Java, Jedis e Lettuce: conecte ao Redis Cloud, veja dados mudarem e implemente padrões de backend. Comece pelo workshop guiado de 60 minutos e continue no seu ritmo pelo curso completo.</p>

<div class="quest-stats">
<div><strong>5</strong><span>cursos</span></div>
<div><strong>24</strong><span>lições</span></div>
<div><strong>2</strong><span>clients Java</span></div>
<div><strong>0</strong><span>reais pra começar</span></div>
</div>

<a class="quest-btn" href="trilha/">Começar: workshop guiado de 60 min</a>

</div>

<p>Os 60 minutos pressupõem o <a href="trilha/01-setup/">preparo antecipado</a>. Sem preparo, o objetivo ao vivo é conectar, gravar, ler e observar um TTL; os demais temas ficam como demonstração.</p>

<p class="quest-track">Curso completo · continue no seu ritmo</p>

<div class="quest-courses">
<a class="course-card" data-course="100" href="fundamentos/">
<span class="code">Fundamentos</span>
<h3>Fundamentos</h3>
<p>Redis Open Source, Redis Cloud e Redis Software. Quem roteia até os shards, como escolher o client, conectar, observar e reduzir viagens de rede.</p>
<div class="meta"><span>Setup + 5 lições</span><span class="badge">Não iniciado</span></div>
<div class="course-progress"><span></span></div>
</a>
<a class="course-card" data-course="101" href="101-tipos/">
<span class="code">101</span>
<h3>Tipos de dados</h3>
<p>Sessões com TTL, contadores atômicos, perfis, filas, conjuntos, rankings e filtros probabilísticos com String, Hash, List, Set, Sorted Set e Bloom.</p>
<div class="meta"><span>6 lições</span><span class="badge">Não iniciado</span></div>
<div class="course-progress"><span></span></div>
</a>
<a class="course-card" data-course="102" href="102-eventos/">
<span class="code">102</span>
<h3>Eventos</h3>
<p>Pub/Sub, Streams, consumer groups e o efeito de uma conexão bloqueante sobre o restante da aplicação.</p>
<div class="meta"><span>4 lições</span><span class="badge">Não iniciado</span></div>
<div class="course-progress"><span></span></div>
</a>
<a class="course-card" data-course="201" href="201-busca/">
<span class="code">201</span>
<h3>Busca</h3>
<p>Documentos JSON, filtros, agregações, busca híbrida com FT.HYBRID e similaridade com vector sets.</p>
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

1. **Prepare o ambiente antes do encontro.** Java 21+, Git, projeto e um Redis Cloud free ou Docker. O [preparo](trilha/01-setup.md) termina com `PONG` e já baixa as dependências.
2. **Veja, altere, observe.** Os labs prontos mostram comandos e respostas; a lição String inclui um exercício Java para você implementar. Use Jedis como caminho principal e compare com Lettuce quando indicado.
3. **Confira a evidência.** `./quest verify` verifica resultados no Redis; ele distingue o que passou do que não pôde ser verificado. O botão da página é seu registro pessoal de estudo, salvo no navegador.

O dataset é um pequeno jogo fictício, **Ember Realm**: personagens, itens e rankings dão dados consistentes aos exemplos. O foco é o padrão de backend que você pode levar para a sua aplicação.

!!! tip "O curso continua depois da hora guiada"
    As 24 lições aprofundam fundamentos, tipos, eventos, busca e produção. Os tempos de cada página são estimativas de estudo, além do workshop; recursos como TLS e failover pedem ambiente próprio.
