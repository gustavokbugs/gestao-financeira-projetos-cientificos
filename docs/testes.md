# Plano de testes

Testes básicos exigidos pelo enunciado: navegação, autenticação, integração, tratamento de falhas e compatibilidade.
Legenda: ✅ verificado · 🟡 parcial · ⬜ a verificar. Executar com a API local (`docker compose up -d`; usuários em [README](../README.md#como-rodar)).

## Autenticação

| # | Passos | Esperado | Situação |
|---|---|---|---|
| A1 | Login com e-mail inválido ou senha vazia | Erro no campo; nada é enviado à API | ⬜ |
| A2 | Login com senha errada | "E-mail ou senha incorretos" | ✅ API / ⬜ app |
| A3 | Login correto, fechar e reabrir o app | Abre direto na seleção de projeto | ⬜ |
| A4 | Sair | Confirmação e volta ao login; reabrir pede login | ⬜ |
| A5 | Token expirado/reiniciar a API e atualizar uma tela | Volta ao login com "sessão expirada" | ⬜ |

## Controle de acesso

| # | Passos | Esperado | Situação |
|---|---|---|---|
| P1 | Login como `gestor` | Lista só o projeto 1, com papel "Gestor" | ✅ |
| P2 | Login como `consulta` | Projetos 1 e 2 com papel "Consulta"; sem ações de escrita | 🟡 (telas de escrita pendentes) |
| P3 | Gestor tenta criar rubrica (via API) | 403 | ✅ API |

## Integração e coerência dos valores

| # | Passos | Esperado | Situação |
|---|---|---|---|
| I1 | Dashboard do projeto 1 | Receita R$ 20.000,00, despesa R$ 19.380,00, saldo R$ 620,00, 64,6% executado | ✅ |
| I2 | Alertas do projeto 1 | Passagens e Passagens aéreas críticas; Diárias, Rodoviárias e Material permanente em atenção | ✅ |
| I3 | Criar lançamento e voltar ao dashboard | Totais atualizados | ⬜ |
| I4 | Dashboard do projeto 3 (painel do enunciado, CA-01) | Receita R$ 559.453,02, despesa R$ 96.078,83, saldo R$ 463.374,19 | ✅ |
| I5 | Reiniciar a API (`docker compose restart api`) | Dados continuam no banco | ✅ API |

## Falhas de rede

| # | Passos | Esperado | Situação |
|---|---|---|---|
| F1 | API desligada ao abrir uma tela | Mensagem "Sem conexão..." e botão "Tentar novamente" | ⬜ |
| F2 | API com `MOCK_DELAY_MS=1500` | Indicador de carregamento visível; botão desabilitado no login | ⬜ |
| F3 | API com `MOCK_FAIL_RATE=0.3` e puxar para atualizar | Snackbar com erro; conteúdo anterior continua na tela | ⬜ |

## Compatibilidade

| Versão | Situação |
|---|---|
| Android 16 (API 36), emulador Pixel 7 | ✅ testado |
| Android 8.0 (API 26), versão mínima | ⬜ |
| Tema escuro | ⬜ |

## Automatizados (a fazer)

- Testes unitários (`app/src/test`) para `Formatos` e o mapeamento de erros de `ApiErros`.
- Testes instrumentados com Espresso para o fluxo login → projeto → dashboard.
