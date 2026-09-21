---
title: "Knowledge check: Fundamentos"
course: "100"
---

# Knowledge check: Fundamentos

Cinco perguntas rápidas. Acerte quatro para concluir este knowledge check; os labs têm verificações separadas.

<div class="quest-quiz" data-course="100"></div>

<script type="application/json" id="quest-quiz-data">
[
  {
    "q": "Por que usamos RedisClient, e não RedisClusterClient, no modo padrão de acesso ao Redis Cloud?",
    "options": [
      "Porque o Redis Cloud não suporta mais de um shard por banco",
      "Porque um proxy fica na frente dos shards e o client vê um endpoint único; o client de cluster só faz sentido com a OSS Cluster API habilitada",
      "Porque os clients de cluster não aceitam senha",
      "Porque o Redis Cloud só fala RESP2"
    ],
    "answer": 1,
    "why": "No Redis Cloud o proxy roteia cada comando para o shard certo, então a aplicação usa o mesmo RedisClient de um Redis standalone; só com a OSS Cluster API ligada o client precisa calcular slots."
  },
  {
    "q": "Quantos io.lettuce.core.RedisClient uma aplicação Java deve criar?",
    "options": [
      "Um por thread, para evitar concorrência",
      "Um por requisição, fechando logo depois",
      "Um por aplicação, reutilizando as conexões adequadas ao trabalho",
      "Um por chave acessada"
    ],
    "answer": 2,
    "why": "O RedisClient do Lettuce é dono das threads do netty e deve ser único; as conexões saem dele com connect() e uma conexão compartilhada atende muitas threads."
  },
  {
    "q": "TTL quest:session:abc devolveu -2. O que isso significa?",
    "options": [
      "A chave existe e nunca expira",
      "A chave não existe",
      "Faltam 2 segundos para a chave expirar",
      "A chave está travada por um WATCH"
    ],
    "answer": 1,
    "why": "TTL devolve -2 para chave inexistente e -1 para chave que existe sem prazo; qualquer valor positivo é o tempo restante em segundos."
  },
  {
    "q": "Por que KEYS quest:* é proibido em produção enquanto SCAN é aceitável?",
    "options": [
      "KEYS só funciona em bancos do Redis Cloud",
      "KEYS varre o banco inteiro em uma única execução e bloqueia o servidor para todos; SCAN devolve páginas com um cursor",
      "SCAN devolve resultados mais precisos que KEYS",
      "KEYS exige RESP3 e a maioria dos clients usa RESP2"
    ],
    "answer": 1,
    "why": "KEYS faz a varredura numa execução; SCAN divide o trabalho em chamadas com cursor. COUNT é uma sugestão, não uma garantia de tamanho ou latência."
  },
  {
    "q": "Qual é a diferença entre um pipeline e um MULTI/EXEC?",
    "options": [
      "Nenhuma, são dois nomes para a mesma coisa",
      "O pipeline é atômico e o MULTI/EXEC não",
      "O pipeline economiza idas e voltas na rede sem garantir atomicidade; o MULTI/EXEC executa os comandos em bloco, sem outro cliente no meio",
      "O MULTI/EXEC é sempre mais rápido que um pipeline"
    ],
    "answer": 2,
    "why": "Pipeline é uma técnica do client para enviar vários comandos antes de ler as respostas; MULTI/EXEC é o servidor executando a fila de uma vez, sem intercalar comandos de outros clientes."
  }
]
</script>
