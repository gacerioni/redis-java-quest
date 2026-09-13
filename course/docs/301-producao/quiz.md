---
title: "Knowledge check: Produção"
course: "301"
---

# Knowledge check: Produção

Cinco perguntas rápidas. Acerte quatro para fechar o curso.

<div class="quest-quiz" data-course="301"></div>

<script type="application/json" id="quest-quiz-data">
[
  {"q": "Na lição 301-01, o que o connect timeout de 500 ms fez na conexão com 10.255.255.1?", "options": ["Fez o Redis responder mais rápido", "Fez a tentativa falhar em cerca de meio segundo, em vez de esperar o sistema operacional desistir", "Desligou o retry com backoff", "Aumentou o tamanho do pool para 8"], "answer": 1, "why": "Um endereço que engole o pacote só falha quando o client desiste; sem connect timeout isso leva mais de um minuto e prende a thread."},
  {"q": "O que o client-side caching exige no Redis Cloud e no Redis Software?", "options": ["RESP2 e o modo BCAST", "Banco na versão 7.4 ou superior e RESP3, no modo padrão de tracking", "Duas conexões, uma delas em modo REDIRECT", "Um TTL local configurado em cada chave"], "answer": 1, "why": "Esses produtos pedem versão 7.4+ e RESP3; o modo REDIRECT não é suportado neles e o Jedis não implementa BCAST, OPTIN nem OPTOUT."},
  {"q": "Por que a lição 301-03 se marca como pulada quando REDIS_TLS_URL não está definida?", "options": ["Porque o Jedis não suporta TLS", "Porque o plano free do Redis Cloud (30 MB) não oferece TLS; é preciso um plano pago", "Porque TLS exige RESP3", "Porque o Lettuce não aceita certificados em PEM"], "answer": 1, "why": "TLS está nos planos pagos Essentials e Pro; o bundle redis_ca.pem inclui uma raiz GlobalSign que a JVM já confia."},
  {"q": "Sobre smart client handoffs (SCH), qual afirmação é verdadeira?", "options": ["O Jedis 8.0.1 negocia SCH sozinho no handshake", "SCH funciona em conexões bloqueantes como BLPOP e em pub/sub", "O Lettuce 7.0+ negocia SCH em RESP3, ligado por padrão no Redis Cloud, e conexões bloqueantes e pub/sub ficam de fora", "SCH só existe no Redis Open Source"], "answer": 2, "why": "SCH é do Redis Cloud e do Redis Software, exige RESP3, o Lettuce 7 suporta, o Jedis 8.0.1 ainda não, e conexões bloqueantes dependem do autoReconnect."},
  {"q": "No MultiDbClient do Jedis, para que serve o peso (weight) de cada banco?", "options": ["Dividir a carga proporcionalmente entre as regiões", "Definir a ordem de preferência: a região saudável de maior peso atende, e o failback volta para ela quando se recupera", "Controlar o tamanho do pool de cada região", "Ajustar o timeout de cada conexão"], "answer": 1, "why": "O peso ordena os endpoints; não há balanceamento de carga, e a replicação entre regiões é papel do Active-Active com CRDT."}
]
</script>
