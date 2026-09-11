---
title: "Knowledge check: Eventos"
course: "102"
---

# Knowledge check: Eventos

Cinco perguntas rápidas. Acerte quatro para fechar o curso.

<div class="quest-quiz" data-course="102"></div>

<script type="application/json" id="quest-quiz-data">
[
  {"q": "Você faz PUBLISH em um canal sem nenhum assinante. O que acontece com a mensagem?", "options": ["Fica guardada até alguém assinar", "É descartada e PUBLISH devolve 0", "Vai para um stream de backlog", "O comando falha com erro"], "answer": 1, "why": "Pub/Sub é fogo e esquece: sem assinante naquele instante, ninguém recebe e o Redis não guarda nada."},
  {"q": "O que significa o id 1789151634753-2 de uma entrada de Stream?", "options": ["Um hash do conteúdo e o número de campos", "Milissegundos do servidor ao gravar e a terceira entrada naquele milissegundo", "O número da entrada e a versão do stream", "Um UUID encurtado"], "answer": 1, "why": "O id é <ms>-<sequência>: o timestamp do servidor e um contador que recomeça em 0 a cada milissegundo."},
  {"q": "Em um consumer group, um worker leu 5 entradas com XREADGROUP e caiu antes do XACK. Onde elas estão?", "options": ["Voltaram para o stream como entradas novas", "Foram apagadas do stream", "Na PEL do worker, visíveis no XPENDING e recuperáveis com XAUTOCLAIM", "Em um consumer especial chamado dead-letter"], "answer": 2, "why": "Entregas sem XACK ficam na pending entries list do consumer até serem confirmadas ou reivindicadas por outro consumer."},
  {"q": "No plano free do Redis Cloud (30 conexões), o que acontece quando o 31º cliente tenta conectar?", "options": ["Entra em uma fila de espera até sobrar vaga", "Recebe ERR max number of clients reached e a conexão é fechada", "O cliente mais antigo é derrubado para abrir espaço", "Conecta em modo somente leitura"], "answer": 1, "why": "O servidor aceita o TCP, responde o erro e fecha: pools, conexões dedicadas e Insight precisam caber nas 30."},
  {"q": "Sua aplicação Lettuce usa uma conexão compartilhada e uma thread chama BRPOP fila 5 nela. O que acontece com os outros comandos?", "options": ["Nada, o Lettuce abre outra conexão sozinho", "Ficam na fila atrás do BRPOP até ele voltar, por até 5 s", "Falham na hora com erro de conexão ocupada", "São redirecionados para uma réplica"], "answer": 1, "why": "A conexão é uma fila ordenada: um comando bloqueante segura tudo que vier atrás, por isso bloqueante pede conexão dedicada."}
]
</script>
