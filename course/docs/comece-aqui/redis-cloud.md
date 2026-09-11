---
title: Crie seu Redis Cloud
---

# Crie seu Redis Cloud

<p class="lesson-meta">Passo 1 · 5 min</p>

O plano free do Redis Cloud tem 30 MB, roda na região de São Paulo e traz tudo que o curso usa: JSON, Search, Streams, vector sets, Bloom e client-side caching. Sem cartão de crédito.

## Passo a passo

1. Abra [redis.io/try-free](https://redis.io/try-free/) e entre com Google, GitHub ou e-mail.
2. Confirme o e-mail de ativação. Você cai na tela **Create your database** com o plano **Free** já selecionado.
3. Dê um nome (ou aceite o gerado).
4. Em **Database version**, escolha a mais nova disponível. A lição de busca híbrida (201-03) precisa de Redis 8.4 ou mais novo; as demais funcionam em qualquer 8.x.
5. Em **Cloud vendor**, escolha **AWS**, e em **Region**, **South America (São Paulo)**, `sa-east-1`. Latência de um dígito para quem está no Brasil.
6. Clique em **Create database** e espere o ícone ficar verde.

## Pegue a URL

1. Na página do banco, seção **Security**, clique no olho ao lado de **Default user password** e copie a senha.
2. Em **General**, copie o **Public endpoint**, algo como `redis-12345.c308.sa-east-1-1.ec2.redns.redis-cloud.com:12345`.
3. Monte a URL no formato abaixo e guarde. Ela vai no arquivo `.env` do projeto no próximo passo.

```text
redis://default:SUA_SENHA@redis-12345.c308.sa-east-1-1.ec2.redns.redis-cloud.com:12345
```

!!! tip "Redis Insight no navegador"
    Na mesma página do banco, **Launch Redis Insight web** abre o Redis Insight sem instalar nada. Você vai usar isso na lição 100-03 para ver as chaves nascendo.

## Os limites do free, e por que eles ajudam

| Limite | Valor | Onde aparece no curso |
|---|---|---|
| Memória | 30 MB | O mundo inteiro do Ember Realm ocupa menos de 1 MB |
| Conexões simultâneas | 30 | Vira a lição 102-04, sobre conexões bloqueantes |
| Throughput | 100 ops/s (aviso antes de limitar) | Os labs foram calibrados para ficar abaixo disso |
| TLS | não disponível no free | A lição 301-03 explica e mostra o código; roda em planos pagos |
| Bancos free por conta | 1 | Um por pessoa é o suficiente |

Se você preferir não criar conta agora, o repositório traz um `docker compose up -d` com Redis 8 e Redis Insight locais. Tudo funciona igual, só a URL muda para `redis://localhost:6379`.
