---
title: Limites do Redis Cloud free
---

# Limites do Redis Cloud free

O plano free (30 MB) é um plano Essentials. Os números abaixo são os oficiais na data em que o curso foi escrito; a página de planos do Redis Cloud é a fonte atual.

| Limite | Free (30 MB) | Como o curso lida |
|---|---|---|
| Memória | 30 MB | O Ember Realm inteiro, com 42 itens e vetores de 384 dimensões, ocupa menos de 1 MB |
| Conexões simultâneas | 30 | Nenhum lab passa de 20; a lição 102-04 mostra o que acontece no 31º |
| Throughput | 100 ops/s, com aviso antes de limitar | Labs curtos, sem loops de carga; o pipeline da 100-05 envia 100 comandos em uma rajada e pronto |
| Banda mensal | 5 GB | Irrelevante para o curso |
| Regras de CIDR | 1 | Não usamos allowlist no curso |
| TLS | não disponível | Lição 301-03 roda com `REDIS_TLS_URL` em um plano pago |
| Alta disponibilidade e persistência | não disponíveis | Assunto da trilha 301, em teoria |
| Bancos free por conta | 1 | Suficiente |

## Depois do curso

Os planos pagos Essentials começam em 250 MB e trazem TLS, replicação e backups. O Pro traz VPC peering, PrivateLink, Active-Active e o autoscaling por shard. O código do curso roda igual em qualquer um deles: só a URL muda.
