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

## Telas

| Tela | Classe | Requisitos | Comportamento |
|---|---|---|---|
| Login | `ui.login.LoginActivity` | GP-01, GP-09 | Valida e-mail e senha antes de enviar; carregando; erros da API e de rede. Com sessão válida, abre direto a seleção de projeto. 401 em qualquer tela volta aqui com "sessão expirada". |
| Seleção de projeto | `ui.projetos.ProjetosActivity` | GP-01, GP-02 | Projetos do usuário com código, vigência, orçamento e **papel**. "Novo projeto" só para quem tem `podeCriarProjetos`. Sair com confirmação. |
| Dashboard | `ui.dashboard.DashboardActivity` | GP-06, GP-08, GP-10 | Receita, despesa e saldo; previsto × executado; gráfico de rosca da participação por rubrica; tabela "Resumo financeiro"; alertas (tocar abre os lançamentos da rubrica); rubricas com barra, saldo e alerta; regras de alerta do servidor; "Download do relatório". Menu: lançamentos, rubricas, projeto, histórico, relatório. |
| Lançamentos | `ui.lancamentos.LancamentosActivity` | GP-04, GP-07 | Lista paginada (carrega mais ao rolar) com filtros combináveis: período, rubrica (inclui subrubricas), tipo, situação e busca; "Limpar". Exporta relatório com os filtros. "Novo lançamento" só para coordenador e gestor. |
| Novo/editar lançamento | `ui.lancamentos.LancamentoFormActivity` | GP-04, GP-09, GP-10 | Data limitada à vigência; rubrica obrigatória na despesa; total estimado (o oficial é do servidor). Validação local, confirmação com resumo, erros 422 nos campos, conflito 409 com "Recarregar", alerta da rubrica depois de salvar. Pergunta antes de descartar. |
| Detalhe do lançamento | `ui.lancamentos.LancamentoDetalheActivity` | GP-05, GP-07, GP-09 | Todos os campos, criado e alterado por quem e quando; comprovantes (abrir, tirar foto, galeria, PDF); histórico do lançamento. Editar e cancelar (com confirmação) para quem pode lançar. |
| Rubricas | `ui.rubricas.RubricasActivity` | GP-03, GP-10 | Rubricas e subrubricas com orçado, executado, previsto, saldo e alerta. Coordenador cria e edita (diálogo com confirmação e erros do servidor); tocar abre os lançamentos da rubrica. |
| Projeto | `ui.projeto.ProjetoDetalheActivity` | GP-02 | Código, vigência, fonte de recursos, responsáveis, orçamento aprovado e perfil. Coordenador edita. |
| Novo/editar projeto | `ui.projeto.ProjetoFormActivity` | GP-02, GP-09 | Título, descrição, vigência, fonte, orçamento e lista editável de responsáveis; validação, confirmação e erros do servidor. |
| Histórico | `ui.historico.HistoricoActivity` | GP-07, GP-09 | Movimentações do projeto (mais recentes primeiro) com ação, autor, data e campos alterados (de → para). |
| Relatórios | `ui.relatorios.Relatorios` | GP-08 | PDF resumido ou planilha CSV gerados pela API; abrir ou compartilhar (`FileProvider`). |

Todas as telas usam a camada `view_estado.xml` (carregando / erro / vazio). Quando já há conteúdo e uma
atualização falha, o erro aparece em Snackbar com "Tentar novamente", sem apagar a tela. As ações de escrita
aparecem conforme o `papel` no projeto (`ui.common.Permissoes`); a API também bloqueia (403).

## Capturas de tela

Emulador Pixel, Android 16, com os dados de demonstração da API local (05/11/2026).

### Entrada e dashboard

<table>
<tr><td align="center" valign="top"><img src="capturas/01-login.png" width="240" alt="Login"><br><sub>Login</sub></td><td align="center" valign="top"><img src="capturas/02-projetos.png" width="240" alt="Seleção de projeto"><br><sub>Seleção de projeto</sub></td><td align="center" valign="top"><img src="capturas/03-dashboard.png" width="240" alt="Dashboard"><br><sub>Dashboard</sub></td></tr>
<tr><td align="center" valign="top"><img src="capturas/04-dashboard-resumo.png" width="240" alt="Gráfico e resumo financeiro"><br><sub>Gráfico e resumo financeiro</sub></td><td align="center" valign="top"><img src="capturas/05-dashboard-rubricas-alertas.png" width="240" alt="Rubricas e alertas"><br><sub>Rubricas e alertas</sub></td><td align="center" valign="top"><img src="capturas/14-menu-dashboard.png" width="240" alt="Menu do dashboard"><br><sub>Menu do dashboard</sub></td></tr>
</table>

### Lançamentos

<table>
<tr><td align="center" valign="top"><img src="capturas/06-lancamentos.png" width="240" alt="Lista de lançamentos"><br><sub>Lista de lançamentos</sub></td><td align="center" valign="top"><img src="capturas/07-lancamentos-filtros.png" width="240" alt="Filtros: despesa + Passagens"><br><sub>Filtros: despesa + Passagens</sub></td><td align="center" valign="top"><img src="capturas/08-lancamento-rubricas.png" width="240" alt="Escolha da rubrica"><br><sub>Escolha da rubrica</sub></td></tr>
<tr><td align="center" valign="top"><img src="capturas/09-lancamento-formulario.png" width="240" alt="Novo lançamento"><br><sub>Novo lançamento</sub></td><td align="center" valign="top"><img src="capturas/10-lancamento-confirmacao.png" width="240" alt="Confirmação"><br><sub>Confirmação</sub></td><td align="center" valign="top"><img src="capturas/11-lancamento-alerta.png" width="240" alt="Alerta da rubrica ao salvar"><br><sub>Alerta da rubrica ao salvar</sub></td></tr>
<tr><td align="center" valign="top"><img src="capturas/12-lancamento-detalhe.png" width="240" alt="Detalhe"><br><sub>Detalhe</sub></td><td align="center" valign="top"><img src="capturas/13-lancamento-comprovantes-historico.png" width="240" alt="Comprovantes e histórico"><br><sub>Comprovantes e histórico</sub></td></tr>
</table>

### Rubricas, projeto e histórico

<table>
<tr><td align="center" valign="top"><img src="capturas/15-rubricas.png" width="240" alt="Rubricas"><br><sub>Rubricas</sub></td><td align="center" valign="top"><img src="capturas/16-rubrica-nova.png" width="240" alt="Nova rubrica"><br><sub>Nova rubrica</sub></td><td align="center" valign="top"><img src="capturas/17-projeto-detalhe.png" width="240" alt="Projeto"><br><sub>Projeto</sub></td></tr>
<tr><td align="center" valign="top"><img src="capturas/18-projeto-edicao.png" width="240" alt="Editar projeto"><br><sub>Editar projeto</sub></td><td align="center" valign="top"><img src="capturas/19-historico.png" width="240" alt="Histórico do projeto"><br><sub>Histórico do projeto</sub></td></tr>
</table>

### Relatórios

<table>
<tr><td align="center" valign="top"><img src="capturas/20-relatorio-formato.png" width="240" alt="Escolha do formato"><br><sub>Escolha do formato</sub></td><td align="center" valign="top"><img src="capturas/21-relatorio-pronto.png" width="240" alt="Relatório pronto"><br><sub>Relatório pronto</sub></td><td align="center" valign="top"><img src="capturas/22-relatorio-pdf.png" width="240" alt="PDF aberto"><br><sub>PDF aberto</sub></td></tr>
</table>

### Perfil consulta e tema escuro

<table>
<tr><td align="center" valign="top"><img src="capturas/23-consulta-lancamentos.png" width="240" alt="Consulta: sem "Novo lançamento""><br><sub>Consulta: sem "Novo lançamento"</sub></td><td align="center" valign="top"><img src="capturas/24-consulta-detalhe.png" width="240" alt="Consulta: sem editar nem anexar"><br><sub>Consulta: sem editar nem anexar</sub></td><td align="center" valign="top"><img src="capturas/25-dashboard-escuro.png" width="240" alt="Tema escuro"><br><sub>Tema escuro</sub></td></tr>
</table>
