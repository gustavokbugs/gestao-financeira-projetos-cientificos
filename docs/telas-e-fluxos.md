# Telas e fluxos de navegação

## Fluxo principal

```
Abrir app
  └─ sessão válida? ── não ─→ [Login] ──(sucesso)──┐
          │ sim                                     │
          └──────────────→ [Seleção de projeto] ←───┘
                                │ toca no projeto
                                ▼
                          [Dashboard do projeto]
                           ├─→ [Lançamentos] (filtros) ─→ [Detalhe do lançamento] ─→ [Comprovante]
                           │        └─→ [Novo/editar lançamento] → confirmação → API
                           ├─→ [Rubricas] ─→ [Nova/editar rubrica]       (coordenador)
                           ├─→ [Projeto] ─→ [Editar projeto]             (coordenador)
                           ├─→ [Histórico de movimentações]
                           └─→ [Relatórios] → PDF/CSV → compartilhar

Resposta 401 em qualquer tela → limpa a sessão → [Login] com aviso "sessão expirada"
"Sair" → confirmação → limpa a sessão → [Login]
```

## Telas implementadas

| Tela | Classe | Requisitos | Comportamento |
|---|---|---|---|
| Login | `ui.login.LoginActivity` | GP-01, GP-09 | Valida o formato do e-mail e a senha preenchida; indicador de carregamento; mensagens de erro da API e de falta de rede. Com sessão válida, abre direto a seleção de projeto. |
| Seleção de projeto | `ui.projetos.ProjetosActivity` | GP-01, GP-02 | Lista só os projetos do usuário, com código, título, vigência, orçamento e **papel**. Puxar para atualizar; estados de vazio, erro e "tentar novamente". Menu "Sair" com confirmação. |
| Dashboard | `ui.dashboard.DashboardActivity` | GP-06, GP-10 | Cartões de receita, despesa e saldo (saldo negativo em vermelho); barra de orçamento previsto × executado; lista de alertas (amarelo = atenção, vermelho = crítico); rubricas e subrubricas com barra de execução, saldo, participação nas despesas e alerta; data da última atualização. |

Todas as telas usam a camada `view_estado.xml` (carregando / erro / vazio). Quando já há conteúdo e uma
atualização falha, o erro aparece em Snackbar com "Tentar novamente", sem apagar a tela.

## Telas pendentes

| Tela | Requisitos | Observações |
|---|---|---|
| Lançamentos (lista) | GP-07 | Filtros por período, rubrica, situação e tipo; paginação. |
| Novo/editar lançamento | GP-04, GP-09, GP-10 | Validação local antes de enviar; diálogo de confirmação; exibir `alertaRubrica` da resposta; tratar 409. |
| Detalhe do lançamento | GP-05, GP-07, GP-09 | Comprovantes (câmera ou arquivo) e histórico com autor e data. |
| Rubricas | GP-03 | Somente coordenador cria e edita. |
| Projeto (detalhe/edição/novo) | GP-02 | Vigência, fonte de recursos, responsáveis e orçamento aprovado. |
| Histórico do projeto | GP-07, GP-09 | |
| Relatórios | GP-08 | Baixar PDF/CSV e compartilhar via `Intent.ACTION_SEND` + `FileProvider`. |
| Gráfico de participação por rubrica | GP-06 | Gráfico de pizza ou barras no dashboard. |

Os botões de ação de cada tela devem aparecer conforme o `papel` (ex.: consulta não vê "Novo lançamento"). Mesmo
assim, a API também bloqueia (403).
