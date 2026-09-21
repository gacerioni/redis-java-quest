# Publicação do curso

A publicação atualiza somente os arquivos estáticos em `https://platformengineer.io/redisjava/`. Não altera bancos Redis nem credenciais de alunos.

## Antes de publicar

1. Executar `scripts/dod.sh` contra um Redis de laboratório isolado, com `REDIS_URL` e `QUEST_PREFIX` explícitos. O smoke usa uma cópia temporária e soluções de referência; os exercícios do aluno permanecem intactos. Recursos indisponíveis ficam identificados como pendentes.
2. Validar separadamente TLS e failover quando esses labs mudarem. Os Redis independentes do lab de failover não demonstram replicação Active-Active.
3. Conferir as páginas alteradas em navegador, inclusive os links entre a trilha e o curso completo.
4. Revisar o diff, incluir o `steps.js` gerado pelo build, fazer commit e push. O script de publicação exige a árvore limpa antes e depois do build.
5. Executar `scripts/deploy_platformengineer.sh` e guardar o commit e o caminho de backup impressos no final.

O build recompila o Java antes de gerar os passos e valida referências internas de HTML, CSS, JavaScript e páginas. `version.json` informa o commit publicado e o identificador da publicação.

## Verificação e reversão

O destino ativo é `/opt/redisjava/www/redisjava`. Cada publicação preserva a árvore anterior em `/opt/redisjava/www/redisjava.rollback-<release>`. A versão anterior a esta revisão correspondia ao código `f2ced2b`.

**Gatilhos de reversão:** falha HTTP em uma das páginas/recursos verificados pelo script, commit diferente em `version.json`, ou falha na navegação principal observada na conferência em navegador. O script reverte automaticamente as duas primeiras condições. O responsável pela publicação reverte a terceira.

Se precisar reverter depois da checagem automática, use o caminho exato de backup informado naquela publicação, no mesmo host configurado pelo script:

```bash
sudo mv /opt/redisjava/www/redisjava /opt/redisjava/www/redisjava.failed-<release>
sudo mv /opt/redisjava/www/redisjava.rollback-<release> /opt/redisjava/www/redisjava
```

Substituir `<release>` pelo identificador real; não apagar o backup antes da conferência. Em seguida, abrir a home, a trilha e uma lição, conferindo HTTP e conteúdo. O commit de código continua disponível no GitHub para diagnóstico; a reversão do site não reescreve o histórico Git.

Não há migração de dados nesta publicação. A comunicação de sucesso ou reversão é feita nesta tarefa com Gabriel; não há envio automático a terceiros. A estratégia se aplica a um site estático, sem feature flags, tráfego de aplicação ou canário de bancos.
