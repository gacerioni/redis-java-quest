# Validação da revisão do workshop de 60 minutos

Validação realizada em 21 de setembro de 2026, com Java 21 e Redis 8.10.1 em instâncias locais isoladas. As soluções de referência foram executadas em cópias temporárias do projeto; os exercícios do aluno continuam sem implementação.

## Resultado

- 24 testes unitários: sem falhas, erros ou testes ignorados.
- 9 testes do launcher Unix: recompilação, fontes e recursos alterados, solução/skip, falha de build, falha de exercício e propagação de código parcial.
- Rodada de todas as 24 lições com Jedis e Lettuce: 73 etapas aprovadas, zero falhas e 3 resultados parciais esperados no failover sem interrupção provocada (duas execuções e uma verificação).
- Solução de referência do exercício 101-01 aprovada com ambos os clientes, incluindo o passo manual do contador.
- Build estrito do site e lint aprovados; 3.107 referências locais verificadas, sem destinos ausentes, incluindo links em HTML escrito dentro do Markdown.
- Navegação no navegador: home, trilha, conexão, link para a versão completa e registro/desmarcação de estudo conferidos.

## TLS e failover com ambiente real

Com ambos os clientes, o teste TLS conectou a um Redis com TLS obrigatório e CA local de teste, executou o lab e passou no `verify`. O mesmo teste recusou `redis://` e um certificado confiável cujo hostname não correspondia ao endpoint. Depois de cada tentativa inválida, `verify` recusou a evidência antiga de sucesso. Foram 12 verificações de execução/estado.

No teste de failover, um endpoint local foi pausado depois de escritas confirmadas. Após escritas no endpoint alternativo, o primeiro foi restaurado. Jedis e Lettuce comprovaram a sequência inicial, alternativo, inicial por escritas concluídas; `verify` passou. Uma nova execução sem interrupção retornou código 3 (parcial), e seu `verify` também ficou parcial.

## Limites desta validação

- Os dois endpoints locais de failover não replicam dados entre si. O teste comprova seleção e recuperação do client, não reconciliação CRDT do Redis Cloud Active-Active.
- Não foi provocada manutenção real no Redis Cloud. O lab de SCH distingue negociação/configuração de avisos observados; a operação de retry é idempotente, mas o teste não certifica um handoff de produção.
- O launcher Windows recebeu revisão estática. Não houve execução em Windows neste ambiente.
- A pontuação de estudo no navegador é um registro pessoal. A evidência técnica do lab vem do CLI e não equivale a uma certificação de segurança ou desempenho em produção.

Reprodução básica: `REDIS_URL=redis://localhost:6379 scripts/dod.sh`. Configure os endpoints opcionais TLS/failover para exercitá-los; indisponibilidade permanece explícita. O processo de publicação e reversão está em [release.md](release.md).
