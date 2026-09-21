---
lesson: 301-04
title: "Smart client handoffs"
minutes: 8
kind: lab
---

# Smart client handoffs

<p class="lesson-meta">Lição 301-04 · Lab · 8 min</p>

Durante uma manutenção, Redis Cloud e Redis Software podem avisar o client antes de mover uma conexão. **Smart client handoffs (SCH)** permite relaxar timeouts e, onde suportado, reconectar ao novo endpoint antes do corte. Na versão do curso, Lettuce 7.7 oferece esse suporte em RESP3; Jedis 8.0.1 ainda não.

O lab verifica a configuração e executa **20 operações idempotentes**. Só há evidência de uma manutenção real quando os avisos aparecem durante a execução. Receber `OK` no pedido de notificações, sozinho, comprova a negociação, não um handoff ocorrido.

## Faça agora

```bash
./quest run 301-04 lettuce
./quest run 301-04 jedis    # comparação: pool + repetição segura, sem SCH nesta versão
./quest verify 301-04
```

No Redis Open Source, o pedido pode ser recusado por falta de suporte. Uma recusa também pode indicar permissão: leia a resposta antes de inferir qual produto está do outro lado. O lab ainda demonstra a parte de operações idempotentes.

## Por que SADD em vez de repetir INCR?

Se o servidor aplicou uma escrita mas a resposta se perdeu, repetir `INCR` incrementaria novamente. Aqui cada operação tem um ID fixo: `SADD quest:maint:operations op-1`, até `op-20`. Repetir o mesmo ID mantém um único membro. `SCARD` deve resultar em 20.

Isso demonstra uma operação naturalmente idempotente, não uma implementação universal de deduplicação de pagamentos ou de outros efeitos externos.

=== "Jedis"

    ```java
    ConnectionPoolConfig pool = new ConnectionPoolConfig();
    pool.setMaxTotal(8);
    pool.setTestWhileIdle(true);
    pool.setTimeBetweenEvictionRuns(Duration.ofSeconds(5));

    for (int i = 1; i <= 20; i++) {
        String operationId = "op-" + i;
        withRetry(3, 200, () -> jedis.sadd(operationsKey, operationId));
    }
    long applied = jedis.scard(operationsKey);   // 20 distinct IDs
    ```

    O lab limita timeouts e o pool e repete somente essa operação segura. Reutilizar um pool não elimina a necessidade de tratar resultado desconhecido após uma falha de rede.

=== "Lettuce"

    ```java
    ClientOptions options = ClientOptions.builder()
            .protocolVersion(ProtocolVersion.RESP3)
            .maintNotificationsConfig(MaintNotificationsConfig.enabled())
            .timeoutOptions(TimeoutOptions.builder()
                    .timeoutCommands(true)
                    .fixedTimeout(Duration.ofSeconds(2))
                    .relaxedTimeoutsDuringMaintenance(Duration.ofSeconds(10))
                    .build())
            .build();

    RedisClient client = RedisClient.create(uri);
    client.setOptions(options);
    try (StatefulRedisConnection<String, String> connection = client.connect()) {
        connection.addListener(message ->
                System.out.println("maintenance: " + message.getType()));
        RedisAsyncCommands<String, String> async = connection.async();
        List<CompletableFuture<Long>> pending = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            pending.add(async.sadd(operationsKey, "op-" + i).toCompletableFuture());
        }
        CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new))
                .get(12, TimeUnit.SECONDS);
    } finally {
        client.shutdown();
    }
    ```

    A espera externa de 12 segundos ultrapassa os 10 segundos do timeout relaxado. Uma espera externa de 2 segundos interromperia a aplicação antes do mecanismo que acabamos de configurar. O lab limpa o set antes da rodada para permitir repetição da demonstração.

## No Redis Insight

No Browser, `quest:maint:operations` termina com 20 membros e `quest:maint:clients` registra os clients usados. No Profiler aparecem os `SADD`s; no Lettuce eles saem sem esperar cada resposta individualmente. O marcador da lição também registra a resposta da negociação e quantos avisos de manutenção foram observados.

| Sinal | O que demonstra |
|---|---|
| `SCARD ... = 20` | Os 20 IDs distintos foram aplicados |
| `CLIENT MAINT_NOTIFICATIONS ON` aceito | O servidor aceitou enviar avisos nesta conexão |
| `MIGRATING` / `MIGRATED` | Avisos de início e fim de migração |
| `MOVING` | Aviso com novo endpoint para o handoff |
| Nenhum aviso durante o lab | Não foi observada manutenção nessa execução |

??? tip "Em produção"
    - SCH exige suporte do servidor e do client e RESP3. No Cloud, o suporte depende também do tipo de endpoint; PrivateLink e Private Service Connect não usam pre-handoff como um endpoint público. No Software, a configuração do cluster deve habilitar os avisos.
    - Timeouts relaxados do Lettuce valem nas APIs async/reactive. Conexões bloqueantes e Pub/Sub ficam fora desse mecanismo; use conexão dedicada e recuperação apropriada.
    - O modo de failover geográfico do client desabilita SCH nesta versão. Trate os dois recursos como configurações diferentes e consulte as [notificações de manutenção do Lettuce](https://redis.io/docs/latest/develop/clients/lettuce/connect/).
    - Mesmo com SCH, mantenha operações repetíveis ou trate explicitamente o resultado desconhecido. O suporte do client reduz o impacto da manutenção; não garante entrega exatamente uma vez.
