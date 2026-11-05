# Plano e casos de teste

Testes exigidos pelo enunciado: navegação, autenticação, integração, tratamento de falhas e compatibilidade.

**Legenda:** ✅ passou · 🤖 automatizado (Espresso ou JUnit) · ⬜ não executado.
**Execução de 05/11/2026:** API local (`docker compose up -d`, usuários no [README](../README.md#como-rodar)),
emuladores **Pixel API 36 (Android 16)** e **Pixel API 26 (Android 8.0, versão mínima)**.

## Como rodar

```bash
cd android
export JAVA_HOME=~/android-studio/jbr
./gradlew testDebugUnitTest            # 31 testes unitários (JVM)
./gradlew connectedDebugAndroidTest    # 8 testes Espresso em cada emulador/aparelho conectado
```

Os testes Espresso usam uma API falsa (`MockWebServer`, em `app/src/androidTest/.../ApiFalsa.java`), então não
dependem do backend nem dos dados do banco. O `connectedDebugAndroidTest` desinstala o app no fim; rode
`./gradlew installDebug` antes de testar à mão.

## Autenticação (GP-01)

| # | Passos | Esperado | Resultado |
|---|---|---|---|
| A1 | Login com e-mail inválido | Erro "Informe um e-mail válido" no campo; nenhuma requisição à API | ✅ 🤖 |
| A2 | Login com senha errada | Mensagem da API "E-mail ou senha incorretos" | ✅ 🤖 |
| A3 | Login, fechar o app (force stop) e reabrir | Abre direto em "Selecione o projeto" | ✅ |
| A4 | Projetos → Sair | Confirmação; volta ao login | ✅ |
| A5 | API responde 401 (token expirado) em qualquer tela | Volta ao login com "Sua sessão expirou. Entre novamente." | ✅ 🤖 |

## Controle de acesso (GP-01, CA-02)

| # | Passos | Esperado | Resultado |
|---|---|---|---|
| P1 | Login como `gestor` | Só o projeto 1, papel "Gestor"; vê "Novo lançamento"; não vê "Nova rubrica" nem "Editar" no projeto; tocar na rubrica abre os lançamentos | ✅ |
| P2 | Login como `consulta` | Sem "Novo projeto", "Novo lançamento", editar, cancelar ou anexar | ✅ 🤖 |
| P3 | Via API: gestor cria rubrica; consulta cria lançamento; gestor abre projeto 2 | 403, 403 e 404 | ✅ |
| P4 | Login como `coordenador` | Vê "Novo lançamento", "Nova rubrica", editar projeto e "Novo projeto" | ✅ 🤖 |

## Dashboard e coerência com a API (GP-06, GP-10, CA-01)

| # | Passos | Esperado | Resultado |
|---|---|---|---|
| D1 | Dashboard do projeto 1 (dados originais) | Receita R$ 20.000,00, despesa R$ 19.380,00, saldo R$ 620,00, 64,6% executado | ✅ |
| D2 | Dashboard do projeto 3 (painel do enunciado) | Receita R$ 559.453,02, despesa R$ 96.078,83, saldo R$ 463.374,19 | ✅ |
| D3 | Gráfico de participação | Rosca e legenda com valor e percentual por rubrica (6,6% · 12,1% · 6,5% · 2,6% · 72,2%) | ✅ |
| D4 | Resumo financeiro | Tabela tipo / tipo de despesa / valor, com receita total, cada rubrica, despesa total e saldo | ✅ |
| D5 | Alertas e regras | Passagens e Passagens aéreas críticas; Diárias e Material permanente em atenção; texto com as regras do servidor (80% / 100%) | ✅ 🤖 |
| D6 | Criar receita realizada de R$ 1.000 e voltar ao dashboard | Receita R$ 21.000,00 e saldo R$ 1.620,00 sem precisar atualizar | ✅ |
| D7 | Projeto novo, sem lançamentos | Valores zerados e "Ainda não há despesas realizadas" | ✅ |

## Lançamentos (GP-04, GP-09, GP-10, CA-02)

| # | Passos | Esperado | Resultado |
|---|---|---|---|
| L1 | Salvar formulário vazio | "Informe a data", "Informe a descrição", "Informe a rubrica da despesa", "Informe o valor unitário" | ✅ 🤖 |
| L2 | Calendário da data | Só permite datas dentro da vigência do projeto | ⬜ configurado (`CalendarConstraints`), não conferido na tela |
| L3 | Despesa válida (Diárias, 1 × R$ 400) | Diálogo de confirmação com o resumo; total do servidor exibido | ✅ |
| L4 | Mesma despesa estourando a rubrica | Depois de salvar: "Saldo insuficiente na rubrica" com a mensagem do servidor | ✅ |
| L5 | Receita sem rubrica | Aceita; rubrica aparece como "Sem rubrica" | ✅ |
| L6 | Editar quantidade 1 → 2 | Total R$ 800,00; histórico "Quantidade: 1 → 2 / Valor total: R$ 400,00 → R$ 800,00" | ✅ |
| L7 | Editar enquanto outra pessoa altera o mesmo lançamento | "Alterado por outra pessoa" → Recarregar traz a versão nova | ✅ |
| L8 | Cancelar lançamento | Confirmação; situação "Cancelado", valor riscado, aviso de que não entra nos totais | ✅ |
| L9 | Sair do formulário com texto digitado | Pergunta "Descartar alterações?" | ✅ |
| L10 | Girar a tela com o formulário preenchido | O texto continua lá | ✅ |
| L11 | Erros de validação de JVM (vigência, limites do banco, obrigatórios) | `ValidadorLancamentoTest` | ✅ 🤖 |

## Filtros e histórico (GP-07, CA-03)

| # | Passos | Esperado | Resultado |
|---|---|---|---|
| F1 | Tipo Despesa + rubrica Passagens | 2 lançamentos (aérea e rodoviária: a rubrica inclui as subrubricas) | ✅ |
| F2 | Situação Previsto | 3 lançamentos | ✅ |
| F3 | Situação Previsto + busca "Notebook" | 1 lançamento | ✅ |
| F4 | Limpar | Volta a todos os lançamentos e esvazia a busca | ✅ |
| F5 | Histórico do projeto | Ações com autor, data e campos alterados; tocar abre o lançamento | ✅ |
| F6 | Montagem dos parâmetros dos filtros | `FiltrosTest` | ✅ 🤖 |

## Comprovantes (GP-05)

| # | Passos | Esperado | Resultado |
|---|---|---|---|
| C1 | Tirar foto (câmera do sistema) | Enviada como JPEG reduzido; aparece na lista e no histórico ("Comprovante anexado") | ✅ |
| C2 | Imagem da galeria (seletor de fotos) | Enviada como JPEG (PNG de 226 KB → 95 KB) | ✅ |
| C3 | Documento PDF | Enviado como está | ✅ |
| C4 | Tocar no comprovante | Abre no app padrão (Fotos / leitor de PDF) | ✅ (Android 16 e 8.0) |

## Rubricas e projeto (GP-02, GP-03)

| # | Passos | Esperado | Resultado |
|---|---|---|---|
| R1 | Nova subrubrica de R$ 5.000 em Diárias (R$ 1.600) | Erro do servidor no campo orçamento: soma das subrubricas ultrapassa o orçamento | ✅ |
| R2 | Mesma subrubrica com R$ 200 | Criada e listada abaixo de Diárias | ✅ |
| R3 | Editar orçamento R$ 200 → R$ 150 | Confirmação "R$ 200,00 → R$ 150,00"; lista atualizada | ✅ |
| J1 | Detalhe do projeto | Código, vigência, fonte de recursos, responsáveis, orçamento, perfil | ✅ |
| J2 | Editar projeto com orçamento abaixo da soma das rubricas | Erro do servidor no campo orçamento | ✅ |
| J3 | Novo projeto vazio → preencher → salvar | Validação local de todos os campos; confirmação; abre o dashboard do projeto criado | ✅ |

## Relatórios (GP-08)

| # | Passos | Esperado | Resultado |
|---|---|---|---|
| RL1 | Dashboard → Download do relatório → PDF → Abrir | PDF com resumo, execução por rubrica e lançamentos | ✅ |
| RL2 | Lista com filtro Receita → Exportar → Planilha | Título "(com os filtros atuais)"; CSV só com receitas | ✅ |
| RL3 | Compartilhar | Abre a tela de compartilhamento do Android com o arquivo | ✅ |

## Falhas de rede (CA-03)

| # | Passos | Esperado | Resultado |
|---|---|---|---|
| N1 | API parada ao abrir uma tela | "Sem conexão com o servidor…" e "Tentar novamente"; ao religar, recupera | ✅ 🤖 |
| N2 | API com `MOCK_DELAY_MS=2500` | Indicador de carregamento enquanto espera | ✅ |
| N3 | API com `MOCK_FAIL_RATE=1` e puxar para atualizar | Snackbar "Serviço temporariamente indisponível" com "Tentar novamente"; dados continuam na tela | ✅ |
| N4 | Mensagens por código e timeout | `ApiErrosTest` | ✅ 🤖 |

Para N2/N3 sem mudar o compose: `docker compose stop api && docker compose run --rm --service-ports -e MOCK_DELAY_MS=2500 api`.

## Compatibilidade

| Caso | Resultado |
|---|---|
| Android 16 (API 36), Pixel 1080×2400 | ✅ todos os casos acima |
| Android 8.0 (API 26), versão mínima | ✅ Espresso 8/8 + roteiro de fumaça (login, dashboard, lista, detalhe, abrir PDF) |
| Tema escuro | ✅ cores próprias, gráfico com a paleta clara |
| Fonte 1,3× | ✅ cartões se ajustam; legenda sem sobreposição |
| Rotação | ✅ estado mantido (`configChanges`) |

Problemas encontrados e corrigidos nesta rodada: moeda sem espaço no Android 8.0 ("R$21.000,00"); nome do
comprovante vindo do seletor de fotos só com o número da mídia ("20.jpg" → "imagem-20.jpg"); coluna de
percentual da legenda apertada com fonte grande.
