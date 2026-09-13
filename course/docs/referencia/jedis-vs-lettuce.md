---
title: Jedis ou Lettuce?
---

# Jedis ou Lettuce?

Os dois são clients oficiais, mantidos pela Redis, com suporte a todos os tipos de dados e ao Redis Cloud. A escolha é sobre modelo de programação, não sobre features do servidor.

| | Jedis 8 | Lettuce 7 |
|---|---|---|
| Modelo | Síncrono, bloqueante, pool de conexões (`RedisClient`) | Netty, uma conexão multiplexada compartilhada; sync, async (futures) e reactive (Reactor) |
| Curva de aprendizado | Menor: parece redis-cli em Java | Maior: pensar em conexões compartilhadas e futures |
| Threads | Uma conexão do pool por comando em voo | Muitas threads na mesma conexão; comandos bloqueantes pedem conexão dedicada |
| Spring | Suportado | Padrão do Spring Data Redis e do Spring Boot |
| Client-side caching | Sim (RESP3 + `CacheConfig`) | API `ClientSideCaching` marcada como legada a partir da 7.8 |
| Smart client handoffs (manutenção do Redis Cloud) | Ainda não (8.0.1) | Sim, ligado por padrão em RESP3 (7.0+) |
| Failover geográfico no client (Active-Active) | `MultiDbClient` com circuit breaker (Jedis 7+) | `MultiDbClient` em preview (7.7+); o Redis Cloud também redireciona endpoints do lado do servidor |
| Search, JSON, Streams, vector sets, Bloom | Sim | Sim |
| TimeSeries | Sim | Sem API nativa (comando customizado) |

## Regras de bolso

- **Aplicação Spring Boot**: fique com o Lettuce, que já vem. Aprenda a regra da conexão dedicada para comandos bloqueantes (lição 102-04).
- **Serviço sem Spring, código direto, time que quer previsibilidade**: Jedis. Dimensione o pool pela concorrência real mais os comandos bloqueantes.
- **Ativo-ativo entre regiões com failover no client**: Jedis tem a peça estável; no Lettuce ela ainda é preview.
- **Manutenções do Redis Cloud sem soluço**: Lettuce hoje tem a peça pronta.

O curso mostra as duas versões de cada lição justamente para você escolher com base no que viu rodando, não no que leu.
