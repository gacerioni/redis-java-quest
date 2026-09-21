---
lesson: 102-01
title: "Pub/Sub: o chat da zona"
minutes: 8
kind: lab
---

# Pub/Sub: o chat da zona

<p class="lesson-meta">Lição 102-01 · Lab · 8 min</p>

`PUBLISH` entrega a mensagem para quem estiver assinando o canal naquele instante e não guarda nada: é a ferramenta certa para avisos ao vivo (chat, presença, invalidação de cache) e a errada para qualquer coisa que não pode se perder. O lab mostra os dois lados; uma mensagem perdida de propósito e cinco entregues.

## O que o lab faz

- Publicar em `{p}:chat:zone:floresta-de-cinzas` antes de alguém assinar e ver `PUBLISH` devolver 0 receptores
- Assinar o canal em uma conexão dedicada (Jedis: `JedisPubSub` em uma thread; Lettuce: `connectPubSub()`)
- Publicar cinco mensagens e conferir que cada uma teve exatamente 1 receptor
- Sair do canal com `UNSUBSCRIBE` e confirmar com `PUBSUB NUMSUB` que ninguém ficou pendurado

## Faça agora

```bash
./quest run 102-01 jedis
./quest run 102-01 lettuce    # opcional: mesmo lab, outro client
./quest verify 102-01
```

## O código

=== "Jedis"

    ```java
    String channel = ctx.k("chat", "zone", "floresta-de-cinzas");
    long receivers = jedis.publish(channel, "Thane: alguém aí?");   // 0: ninguém ouviu, mensagem perdida

    CountDownLatch subscribed = new CountDownLatch(1);
    List<String> inbox = new CopyOnWriteArrayList<>();
    JedisPubSub listener = new JedisPubSub() {
        @Override public void onSubscribe(String ch, int count) { subscribed.countDown(); }
        @Override public void onMessage(String ch, String message) { inbox.add(message); }
    };
    // subscribe(...) só retorna depois do unsubscribe(): precisa de uma thread própria
    Thread subscriber = new Thread(() -> jedis.subscribe(listener, channel));
    subscriber.start();
    subscribed.await(3, TimeUnit.SECONDS);

    for (String line : LINES) jedis.publish(channel, line);          // 1 receptor cada

    listener.unsubscribe();   // devolve a conexão ao pool e encerra a thread
    subscriber.join(3000);
    ```

=== "Lettuce"

    ```java
    RedisCommands<String, String> redis = connection.sync();
    long receivers = redis.publish(channel, "Thane: alguém aí?");   // 0: ninguém ouviu, mensagem perdida

    try (StatefulRedisPubSubConnection<String, String> pubsub = Clients.lettuce().connectPubSub()) {
        pubsub.addListener(new RedisPubSubAdapter<>() {
            @Override public void subscribed(String ch, long count) { subscribed.countDown(); }
            @Override public void message(String ch, String message) { inbox.add(message); }
        });
        pubsub.sync().subscribe(channel);
        subscribed.await(3, TimeUnit.SECONDS);

        for (String line : LINES) redis.publish(channel, line);      // 1 receptor cada

        pubsub.sync().unsubscribe(channel);
    }   // close() fecha a conexão Pub/Sub
    ```

## No Redis Insight

Pub/Sub não cria chave: o Browser não mostra nada em `{p}:chat:*`. Use a aba Pub/Sub do Insight (menu lateral), assine `{p}:chat:zone:floresta-de-cinzas` e rode a lição de novo: as mensagens aparecem em tempo real na tela e o `PUBLISH` passa a devolver 2 receptores (a sua thread e o Insight). No Profiler dá para ver o `SUBSCRIBE` de um lado e os cinco `PUBLISH` do outro. Saia do canal no Insight antes de rodar o `verify`: a lição espera que a primeira mensagem não tenha ninguém ouvindo.

??? note "Por dentro"

    | Comando | O que faz |
    |---|---|
    | `PUBLISH canal msg` | Entrega para todos os assinantes do canal naquele instante; devolve quantos receberam |
    | `SUBSCRIBE canal` | Coloca a conexão em modo assinante; ela só recebe mensagens até o `UNSUBSCRIBE` |
    | `UNSUBSCRIBE canal` | Sai do canal e devolve a conexão ao uso normal |
    | `PUBSUB NUMSUB canal` | Quantos assinantes o canal tem agora |
    | `PSUBSCRIBE {p}:chat:zone:*` | Assina por padrão: todas as zonas de uma vez |

    No Jedis, `subscribe` bloqueia a thread que chamou e segura uma conexão do pool até o `unsubscribe()`. No Lettuce, `connectPubSub()` abre uma conexão separada e o listener roda na thread de I/O do netty: não bloqueie dentro dele, entregue para uma fila ou um executor.

??? tip "Em produção"

    - Pub/Sub é at-most-once: sem assinante, sem retry, sem histórico. Para eventos que precisam de garantia, use [Streams](02-streams.md).
    - Cada `SUBSCRIBE` segura uma conexão o tempo todo. No plano free (30 conexões) isso conta; veja [Conexões bloqueantes](04-conexoes-bloqueantes.md).
    - Mensagem grande com assinante lento estoura o `client-output-buffer-limit` de Pub/Sub e o servidor derruba o assinante: payloads pequenos, e em bancos com vários shards considere sharded Pub/Sub (`SPUBLISH`/`SSUBSCRIBE`).

??? tip "Desafio"

    Troque `subscribe` por `psubscribe` com o padrão `{p}:chat:zone:*` (Jedis: `jedis.psubscribe(listener, pattern)` e `onPMessage`; Lettuce: `pubsub.sync().psubscribe(pattern)` e `message(pattern, channel, message)`) e publique em duas zonas diferentes. `PUBSUB NUMSUB` continua mostrando 0 assinantes diretos, enquanto `PUBSUB NUMPAT` passa a mostrar 1, e as duas zonas chegam no mesmo listener.
