# Arquitetura

```
┌──────────────────────┐   HTTPS + JWT    ┌───────────────────────────┐       ┌──────────────────┐
│  App Android (Java)  │ ───────────────→ │ API de gestão financeira  │ ────→ │  PostgreSQL      │
│  android/            │   REST / JSON    │ backend/ (Node.js)        │       │  18 (Docker)     │
└──────────────────────┘                  │ atrás do Caddy (HTTPS)    │       │  dados + anexos  │
                                          └───────────────────────────┘       └──────────────────┘
        produção: PC servidor (servidor/docker-compose.yml)  ·  desenvolvimento: docker compose da raiz, localhost:3000
```

## Camadas do app

| Pacote | Responsabilidade |
|---|---|
| `GestaoFinanceiraApp` | Cria e guarda `SessionManager` e `ApiService`; volta ao login quando a API responde 401 |
| `data.remote` | `ApiClient` (OkHttp: timeouts, interceptor do JWT, log só no debug), `ApiService` (endpoints Retrofit), `ApiErros` (mensagens de erro), `dto/` (modelos JSON) |
| `data.local` | `SessionManager`: token cifrado com o Keystore, validade e dados do usuário |
| `ui.common` | `EstadoTela` (carregando/erro/vazio), `Formatos` (moeda, datas e percentuais em pt-BR) |
| `ui.login`, `ui.projetos`, `ui.dashboard` | Uma Activity por tela |

A URL da API fica em `BuildConfig.API_BASE_URL`, definida por tipo de build em `android/app/build.gradle`.
Os valores monetários usam `BigDecimal` nos DTOs, para não perder precisão.

## Backend

| Arquivo | Responsabilidade |
|---|---|
| `backend/src/server.js` | Rotas, autenticação JWT, permissões por projeto, validações, cálculos e relatórios |
| `backend/src/db.js` | Pool de conexões, transações e migrações (aplicadas ao iniciar) |
| `backend/migrations/*.sql` | Esquema do banco, versionado |
| `backend/src/seed.js` | Dados de demonstração (banco vazio + `SEED_DEMO=true`) |
| `backend/src/pdf.js` | Gerador de PDF do relatório (sem dependências) |

Tabelas: `usuarios`, `projetos`, `membros` (papel por projeto), `rubricas` (dois níveis), `lancamentos`,
`comprovantes` (arquivo em `BYTEA`) e `historico` (auditoria com os campos alterados). Toda escrita grava o
histórico na mesma transação.
