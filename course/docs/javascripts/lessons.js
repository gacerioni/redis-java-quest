/* Static catalog used by progress.js and quiz.js. URLs are relative to the site root. */
window.QUEST = window.QUEST || {};
window.QUEST.courses = [
  { id: "100", name: "Fundamentos", url: "fundamentos/", quiz: "fundamentos/quiz/", next: "101" },
  { id: "101", name: "Tipos", url: "101-tipos/", quiz: "101-tipos/quiz/", next: "102" },
  { id: "102", name: "Eventos", url: "102-eventos/", quiz: "102-eventos/quiz/", next: "201" },
  { id: "201", name: "Busca", url: "201-busca/", quiz: "201-busca/quiz/", next: "301" },
  { id: "301", name: "Produção", url: "301-producao/", quiz: "301-producao/quiz/", next: null }
];
window.QUEST.lessons = [
  { id: "setup", course: "100", title: "Do zero ao primeiro comando", url: "comece-aqui/", minutes: 15 },
  { id: "100-01", course: "100", title: "O mapa do mundo", url: "fundamentos/01-mapa-do-mundo/", minutes: 8 },
  { id: "100-02", course: "100", title: "Conectar com Jedis e Lettuce", url: "fundamentos/02-conectar/", minutes: 8 },
  { id: "100-03", course: "100", title: "Redis Insight", url: "fundamentos/03-redis-insight/", minutes: 6 },
  { id: "100-04", course: "100", title: "Chaves, TTL e SCAN", url: "fundamentos/04-chaves-ttl-scan/", minutes: 8 },
  { id: "100-05", course: "100", title: "Pipeline", url: "fundamentos/05-pipeline/", minutes: 8 },
  { id: "101-01", course: "101", title: "String", url: "101-tipos/01-string/", minutes: 8 },
  { id: "101-02", course: "101", title: "Hash", url: "101-tipos/02-hash/", minutes: 8 },
  { id: "101-03", course: "101", title: "List", url: "101-tipos/03-list/", minutes: 8 },
  { id: "101-04", course: "101", title: "Set", url: "101-tipos/04-set/", minutes: 8 },
  { id: "101-05", course: "101", title: "Sorted Set", url: "101-tipos/05-sorted-set/", minutes: 8 },
  { id: "101-06", course: "101", title: "Bloom", url: "101-tipos/06-bloom/", minutes: 8 },
  { id: "102-01", course: "102", title: "Pub/Sub", url: "102-eventos/01-pubsub/", minutes: 8 },
  { id: "102-02", course: "102", title: "Streams", url: "102-eventos/02-streams/", minutes: 10 },
  { id: "102-03", course: "102", title: "Consumer groups", url: "102-eventos/03-consumer-groups/", minutes: 12 },
  { id: "102-04", course: "102", title: "Conexões bloqueantes", url: "102-eventos/04-conexoes-bloqueantes/", minutes: 12 },
  { id: "201-01", course: "201", title: "JSON e FT.SEARCH", url: "201-busca/01-json-search/", minutes: 12 },
  { id: "201-02", course: "201", title: "FT.AGGREGATE", url: "201-busca/02-aggregate/", minutes: 10 },
  { id: "201-03", course: "201", title: "FT.HYBRID", url: "201-busca/03-hybrid/", minutes: 12 },
  { id: "201-04", course: "201", title: "Vector sets", url: "201-busca/04-vector-sets/", minutes: 10 },
  { id: "301-01", course: "301", title: "Timeouts, pool e retry", url: "301-producao/01-timeouts-pool-retry/", minutes: 12 },
  { id: "301-02", course: "301", title: "Client-side caching", url: "301-producao/02-client-side-caching/", minutes: 10 },
  { id: "301-03", course: "301", title: "TLS", url: "301-producao/03-tls/", minutes: 8 },
  { id: "301-04", course: "301", title: "Smart client handoffs", url: "301-producao/04-smart-client-handoffs/", minutes: 8 },
  { id: "301-05", course: "301", title: "Active-Active", url: "301-producao/05-active-active/", minutes: 12 }
];
