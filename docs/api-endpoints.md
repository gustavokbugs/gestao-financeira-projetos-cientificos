# Endpoints consumidos

Base: `/api/v1`. JSON em UTF-8. Todas as rotas, exceto `status` e `auth/login`, exigem
`Authorization: Bearer <JWT>`. Datas no formato `AAAA-MM-DD`; instantes em ISO-8601 UTC.
Valores monetários são números em reais (ex.: `1600.5`).

Implementado em [`backend/`](../backend/) (Node.js + PostgreSQL). Esquema do banco em
[`backend/migrations/`](../backend/migrations/).

## Erros

```json
{ "mensagem": "Dados do lançamento inválidos",
  "erros": [{ "campo": "data", "mensagem": "A data deve estar na vigência do projeto" }] }
```

| Status | Quando |
|---|---|
| 400 | JSON inválido |
| 401 | Token ausente, inválido ou expirado. O app limpa a sessão e volta ao login |
| 403 | O perfil não permite a operação |
| 404 | Recurso inexistente **ou projeto do qual o usuário não participa** |
| 409 | Conflito de edição (lançamento alterado por outra pessoa) |
| 413 / 415 | Arquivo grande demais (máx. 10 MB) ou tipo não aceito |
| 429 | Muitas tentativas de login (10 falhas em 15 min por IP + e-mail) |
| 422 | Falha de validação (lista em `erros`) |
| 503 | Serviço indisponível |

## Perfis

| Ação | coordenador | gestor | consulta |
|---|:-:|:-:|:-:|
| Consultar projeto, dashboard, lançamentos, histórico e relatórios | ✅ | ✅ | ✅ |
| Criar e editar lançamentos, enviar comprovantes | ✅ | ✅ | |
| Editar projeto, criar e editar rubricas | ✅ | | |
| Criar projeto | só usuários com `podeCriarProjetos` | | |

O papel é **por projeto**: a mesma pessoa pode ser coordenadora em um projeto e ter só consulta em outro.

## Rotas

| Método | Rota | Requisito | Descrição |
|---|---|---|---|
| GET | `/status` | - | Verifica se a API está no ar |
| POST | `/auth/login` | GP-01 | `{email, senha}` → `{token, tipo, expiraEm, usuario}` |
| GET | `/auth/me` | GP-01 | Usuário logado |
| GET | `/projetos` | GP-01/02 | Projetos do usuário, cada um com `papel` |
| POST | `/projetos` | GP-02 | Cria um projeto (o criador vira coordenador) |
| GET | `/projetos/{id}` | GP-02 | Detalhe do projeto |
| PUT | `/projetos/{id}` | GP-02 | Edita o projeto (coordenador) |
| GET | `/projetos/{id}/dashboard` | GP-06/10 | Totais, previsto x executado, participação por rubrica, rubricas e alertas |
| GET | `/projetos/{id}/rubricas` | GP-03 | Rubricas com subrubricas, orçado, executado, previsto, saldo e alerta |
| POST | `/projetos/{id}/rubricas` | GP-03 | `{nome, orcamento, rubricaPaiId?}` (coordenador) |
| PUT | `/rubricas/{id}` | GP-03 | `{nome?, orcamento?}` (coordenador) |
| GET | `/projetos/{id}/lancamentos` | GP-07 | Filtros: `tipo`, `rubricaId` (inclui as subrubricas), `situacao`, `de`, `ate`, `busca`, `pagina`, `tamanho` |
| POST | `/projetos/{id}/lancamentos` | GP-04 | Cria o lançamento; a resposta traz `alertaRubrica` |
| GET | `/lancamentos/{id}` | GP-07 | Detalhe com `comprovantes` e `historico` |
| PUT | `/lancamentos/{id}` | GP-04/09 | Edita; envie `atualizadoEm` para detectar conflito (409). Cancelar = `situacao: CANCELADO` |
| POST | `/lancamentos/{id}/comprovantes` | GP-05 | Corpo binário; `Content-Type` image/jpeg, image/png ou application/pdf; `X-Nome-Arquivo` (URL-encoded) |
| GET | `/comprovantes/{id}` | GP-05 | Baixa o arquivo |
| GET | `/projetos/{id}/historico` | GP-07/09 | Movimentações com autor, data e campos alterados |
| GET | `/projetos/{id}/relatorio?formato=pdf\|csv` | GP-08 | Relatório resumido (PDF) ou planilha de lançamentos (CSV separado por `;`, abre no Excel). Aceita os mesmos filtros dos lançamentos |

## Lançamento

```json
{
  "id": 3, "projetoId": 1, "tipo": "DESPESA", "data": "2026-04-15",
  "descricao": "Diárias nacionais - evento em Brasília",
  "rubricaId": 1, "rubrica": { "id": 1, "nome": "Diárias", "rubricaPaiId": null },
  "quantidade": 4, "valorUnitario": 320, "valorTotal": 1280,
  "situacao": "REALIZADO",
  "criadoPor": { "id": 2, "nome": "Gestão Exemplo" }, "criadoEm": "2026-04-15T12:00:00.000Z",
  "atualizadoPor": { "id": 2, "nome": "Gestão Exemplo" }, "atualizadoEm": "2026-04-15T12:00:00.000Z",
  "quantidadeComprovantes": 0
}
```

- `tipo`: `RECEITA` | `DESPESA`. `situacao`: `PREVISTO` | `REALIZADO` | `CANCELADO` (padrão `PREVISTO`).
- `valorTotal` é **calculado pelo servidor** (`quantidade × valorUnitario`).
- Despesa exige rubrica. A data precisa estar dentro da vigência do projeto.
- Os lançamentos não são excluídos, só cancelados, para manter o histórico (GP-09).

## Regras de cálculo (servidor)

- **Receita total / despesa total:** soma dos lançamentos `REALIZADO`.
- **Saldo disponível:** receita total − despesa total.
- **Orçamento previsto × executado:** `orcamentoAprovado` do projeto × despesa total.
- **Rubrica:** `executado` (realizado), `previsto`, `saldo = orçamento − executado − previsto`. A rubrica principal inclui as subrubricas.
- **Alertas (GP-10):** comprometido = executado + previsto. A partir de 80% do orçamento → `ATENCAO`; acima de 100% → `CRITICO`.
  Os limites são configuráveis no servidor (`ALERTA_ATENCAO_PERCENTUAL`, `ALERTA_CRITICO_PERCENTUAL`) e vêm em
  `regrasAlerta` no dashboard; o app só exibe.
- **Validação de orçamento:** a soma das rubricas não pode passar do orçamento do projeto, e a soma das subrubricas
  não pode passar do orçamento da rubrica principal.
