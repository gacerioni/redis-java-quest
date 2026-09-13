---
title: "Knowledge check: Busca"
course: "201"
---

# Knowledge check: Busca

Cinco perguntas rápidas. Acerte quatro para fechar o curso.

<div class="quest-quiz" data-course="201"></div>

<script type="application/json" id="quest-quiz-data">
[
  {"q": "Na casa de leilões, você quer filtrar itens cuja raridade seja exatamente \"epico\". Qual tipo de campo usar no FT.CREATE?", "options": ["TEXT, porque raridade é uma palavra", "NUMERIC, guardando um código por raridade", "TAG, comparado com @rarity:{epico}", "VECTOR, para achar raridades parecidas"], "answer": 2, "why": "TAG compara o valor exato, sem tokenização nem stemming; TEXT serve para linguagem natural, com fuzzy e prefixo."},
  {"q": "O que FT.AGGREGATE idx \"*\" GROUPBY 1 @rarity REDUCE COUNT 0 AS n devolve?", "options": ["Uma linha por raridade, com a quantidade de itens em n", "Um documento por item, com um campo n igual a 1", "O item mais caro de cada raridade", "A lista de raridades, sem contagem"], "answer": 0, "why": "GROUPBY agrupa os documentos por raridade e cada REDUCE calcula um valor por grupo; o resultado são linhas, não documentos."},
  {"q": "Por que a consulta KNN precisa de DIALECT 2 e de PARAMS 2 vec <blob>?", "options": ["Para o Redis aceitar acentos na query", "Para acelerar a busca em campos FLAT", "Porque o índice foi criado ON JSON", "Porque a sintaxe *=>[KNN 5 @embedding $vec] e os parâmetros $nome existem só no dialeto 2, e o vetor vai em bytes FLOAT32"], "answer": 3, "why": "O dialeto 2 introduziu =>[KNN ...] e os $params; o vetor de consulta viaja como blob binário little-endian, nunca como texto."},
  {"q": "No FT.HYBRID, o que o COMBINE RRF faz com os resultados do SEARCH e do VSIM?", "options": ["Soma as notas brutas de texto e de vetor", "Dá a cada documento 1/(k + posição) em cada ranking e soma, favorecendo quem vai bem nos dois", "Fica só com o ranking vetorial e usa o texto como filtro", "Devolve os dois rankings separados para a aplicação fundir"], "answer": 1, "why": "RRF usa posições, não notas, então texto e vetor entram em pé de igualdade; quem aparece bem nas duas listas sobe."},
  {"q": "Quando um vector set (VADD/VSIM) é a escolha certa em vez do Query Engine (FT.*)?", "options": ["Quando você precisa de busca por texto com fuzzy junto com o vetor", "Quando precisa de GROUPBY e médias por categoria", "Quando a única pergunta é \"o que parece com isto?\", com no máximo um filtro sobre atributos JSON do próprio elemento", "Quando os documentos JSON já existem e devem ser indexados automaticamente"], "answer": 2, "why": "Vector set é uma estrutura simples de vizinhança com FILTER por atributos; texto, agregações e indexação automática de documentos são do Query Engine."}
]
</script>
