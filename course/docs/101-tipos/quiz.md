---
title: "Knowledge check: Tipos"
course: "101"
---

# Knowledge check: Tipos

Cinco perguntas rápidas. Acerte quatro para fechar o curso.

<div class="quest-quiz" data-course="101"></div>

<script type="application/json" id="quest-quiz-data">
[
  {"q": "Kaelith lança Bola de Fogo e o lab roda SET cooldown:kaelith:fireball 1 NX EX 5. Dois segundos depois ela lança de novo e o SET devolve null. O que aconteceu?", "options": ["A chave expirou e foi apagada", "NX só grava se a chave não existe: a habilidade ainda está em cooldown", "O Redis rejeitou o EX porque o valor não é numérico", "O SET devolveu null porque a String estava vazia"], "answer": 1, "why": "Com NX o SET não grava quando a chave já existe e responde nil: o cooldown de 5 segundos ainda está valendo."},
  {"q": "A ficha do personagem (hp, mana, gold) precisa de um incremento atômico só no gold, e a fila da dungeon precisa manter a ordem de chegada, entrando por um lado e saindo pelo outro. Que par de tipos você usa?", "options": ["String para a ficha e Set para a fila", "Hash para a ficha e List para a fila", "List para a ficha e Hash para a fila", "Hash para a ficha e Sorted Set para a fila"], "answer": 1, "why": "O Hash guarda campos independentes e incrementa um deles com HINCRBY; a List entrega FIFO com RPUSH e LPOP (e BRPOP quando alguém quer esperar)."},
  {"q": "Brom e Thane têm suas conquistas em dois Sets. Qual comando devolve só o que os dois têm em comum, sem trazer os Sets para a JVM?", "options": ["SUNION brom thane", "SDIFF brom thane", "SINTER brom thane", "SMEMBERS nos dois e um retainAll em Java"], "answer": 2, "why": "SINTER calcula a interseção no servidor e responde apenas com os membros compartilhados."},
  {"q": "No ranking rank:xp, ZREVRANK rank:xp vesper devolve 11. Em que lugar Vesper está?", "options": ["11º, porque o rank começa em 1", "12º, porque ZREVRANK conta a partir de zero, do maior score para o menor", "Depende do ZSCORE dela", "11º contando a partir do menor score"], "answer": 1, "why": "ZREVRANK é base zero e ordena do maior para o menor: 11 significa onze jogadores acima dela, logo ela é a 12ª."},
  {"q": "BF.EXISTS chests:opened chest-777 respondeu 0 e BF.EXISTS chests:opened chest-001 respondeu 1. O que dá para afirmar com certeza?", "options": ["chest-777 nunca entrou no filtro; chest-001 provavelmente entrou", "Os dois baús foram abertos", "chest-001 com certeza entrou; chest-777 pode ter entrado", "Nada: o Bloom filter é probabilístico nos dois sentidos"], "answer": 0, "why": "Um Bloom filter não tem falsos negativos, então 0 é certeza; o 1 pode ser um falso positivo, por isso é só 'provavelmente'."}
]
</script>
