---
title: Jedis ou Lettuce?
---

# Jedis ou Lettuce?

Os dois são clients open source oficiais, mantidos pela Redis. A escolha considera o modelo de programação, a topologia e a maturidade das APIs de que você precisa. Este curso fixa **Jedis 8.0.1** e **Lettuce 7.7.0**; não confunda comportamento de uma versão futura com o que o lab executa.

| | Jedis 8 | Lettuce 7 |
|---|---|---|
| Modelo | Síncrono, bloqueante, pool de conexões (`RedisClient`) | Netty, uma conexão multiplexada compartilhada; sync, async (futures) e reactive (Reactor) |
| Protocolo padrão | Negocia RESP3, com fallback para RESP2 | Negocia RESP3, com fallback para RESP2 |
| Cluster OSS / OSS Cluster API | `redis.clients.jedis.RedisClusterClient` | `io.lettuce.core.cluster.RedisClusterClient` |
| Curva de aprendizado | Menor: parece redis-cli em Java | Maior: pensar em conexões compartilhadas e futures |
| Threads | Uma conexão do pool por comando em voo | Muitas threads na mesma conexão; comandos bloqueantes pedem conexão dedicada |
| Spring | Suportado | Padrão do Spring Data Redis e do Spring Boot |
| Client-side caching | Sim (RESP3 + `CacheConfig`) | `ClientSideCaching` + `CacheFrontend` na versão 7.7.0 do curso |
| Smart client handoffs (manutenção do Redis Cloud) | Ainda não (8.0.1) | Sim, ligado por padrão em RESP3 (7.0+) |
| Failover geográfico no client (Active-Active) | `MultiDbClient` **experimental**, com circuit breaker | `MultiDbClient` em preview (7.7+); o Redis Cloud também redireciona endpoints do lado do servidor |
| Search, JSON, Streams, vector sets, Bloom | Sim | Sim |
| TimeSeries | Sim | Sem API nativa (comando customizado) |

## Regras de bolso

- **Aplicação Spring Boot**: fique com o Lettuce, que já vem. Aprenda a regra da conexão dedicada para comandos bloqueantes (lição 102-04).
- **Serviço sem Spring, código direto, time que quer previsibilidade**: Jedis. Dimensione o pool pela concorrência real mais os comandos bloqueantes.
- **Failover entre regiões no client**: `MultiDbClient` é experimental no Jedis e preview no Lettuce. Valide falha, recuperação e consistência antes de adotar. O client não replica dados; essa é a função do Active-Active no servidor.
- **Manutenções do Redis Cloud/Software**: Lettuce suporta smart client handoffs quando client, servidor e tipo de conexão atendem aos requisitos. Isso reduz o impacto da manutenção, sem prometer ausência de erro.

O curso mantém as duas versões dos labs. Na trilha de uma hora, Jedis é o caminho principal e a comparação Lettuce é conduzida pelo apresentador.

Referências: [migração Jedis 7 → 8](https://redis.github.io/jedis/migration-guides/v7-to-v8/) e [failover experimental do Jedis](https://redis.github.io/jedis/failover/).
