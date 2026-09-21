---
title: "Knowledge check: Produção"
course: "301"
---

# Knowledge check: Produção

Cinco perguntas rápidas. Acerte quatro para concluir este knowledge check; os labs têm verificações separadas.

<div class="quest-quiz" data-course="301"></div>

<script type="application/json" id="quest-quiz-data">
[
  {
    "q": "O que um timeout de escrita permite concluir?",
    "options": [
      "O servidor certamente não aplicou a escrita",
      "A resposta não chegou dentro do prazo; o efeito no servidor pode ter ocorrido",
      "É sempre seguro repetir INCR",
      "O pool precisa ser ilimitado"
    ],
    "answer": 1,
    "why": "Timeout não é confirmação de falha da operação. Repita apenas operações idempotentes ou use um mecanismo apropriado para identificar e reconciliar o efeito."
  },
  {
    "q": "O que o client-side caching exige no Redis Cloud e no Redis Software?",
    "options": [
      "RESP2 e o modo BCAST",
      "Banco na versão 7.4 ou superior e RESP3, no modo padrão de tracking",
      "Duas conexões, uma delas em modo REDIRECT",
      "Um TTL local configurado em cada chave"
    ],
    "answer": 1,
    "why": "Esses produtos pedem versão 7.4+ e RESP3; o modo REDIRECT não é suportado neles e o Jedis não implementa BCAST, OPTIN nem OPTOUT."
  },
  {
    "q": "O que é necessário para o lab TLS comprovar uma conexão segura?",
    "options": [
      "Qualquer URL basta se o PING responder",
      "rediss://, cadeia confiável e hostname do certificado correspondente ao endpoint",
      "RESP3 e nenhum certificado",
      "Desligar a verificação do peer"
    ],
    "answer": 1,
    "why": "O lab valida a identidade do servidor e registra a evidência atual. Sem REDIS_TLS_URL ele fica pendente. O Cloud free não oferece TLS; use um plano com TLS ou ambiente local configurado."
  },
  {
    "q": "Sobre smart client handoffs (SCH), qual afirmação é verdadeira?",
    "options": [
      "O Jedis 8.0.1 negocia SCH sozinho no handshake",
      "SCH funciona em conexões bloqueantes como BLPOP e em pub/sub",
      "O Lettuce 7.0+ negocia SCH em RESP3, ligado por padrão no Redis Cloud, e conexões bloqueantes e pub/sub ficam de fora",
      "SCH só existe no Redis Open Source"
    ],
    "answer": 2,
    "why": "SCH é do Redis Cloud e do Redis Software, exige RESP3, o Lettuce 7 suporta, o Jedis 8.0.1 ainda não, e conexões bloqueantes dependem do autoReconnect."
  },
  {
    "q": "No MultiDbClient do Jedis, para que serve o peso (weight) de cada banco?",
    "options": [
      "Dividir a carga proporcionalmente entre as regiões",
      "Definir a ordem de preferência: a região saudável de maior peso atende, e o failback volta para ela quando se recupera",
      "Controlar o tamanho do pool de cada região",
      "Ajustar o timeout de cada conexão"
    ],
    "answer": 1,
    "why": "O peso ordena os endpoints; não há balanceamento de carga, e a replicação entre regiões é papel do Active-Active com CRDT."
  }
]
</script>
