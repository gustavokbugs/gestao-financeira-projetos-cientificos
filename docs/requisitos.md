# Requisitos: Opção 1, gestão financeira de projetos científicos

Transcrição do enunciado ([PDF](referencias/Requisitos_Tres_Opcoes_Apps_Android.pdf)) com a situação de cada item.

**Objetivo:** permitir a pesquisadores, coordenadores e equipes autorizadas consultar e registrar a execução
orçamentária de projetos de pesquisa, com base nas rubricas e demonstrativos da planilha e do painel financeiro
fornecidos ([planilha](referencias/exemplo-planilha-orcamento.png), [painel](referencias/exemplo-painel-financeiro.png)).

## Premissas comuns (valem para o trabalho todo)

- [x] Produto principal: aplicativo Android instalável (APK/AAB), com interface para smartphone.
- [x] Tecnologia: Android nativo em Java (escolhida pelo grupo).
- [x] Backend consumido por APIs seguras, sem acesso direto do app ao banco de dados.
- [x] Autenticação e perfis de acesso com JWT.
- [x] Tráfego HTTPS. O release só aceita HTTPS; o HTTP local fica restrito ao build debug.
- [x] Tratamento de erros e indicadores de carregamento (telas atuais).
- [x] Proteção de credenciais: token cifrado no Keystore; a senha não é guardada; os logs não exibem o token.
- Fora do escopo do app: portais web, infraestrutura de nuvem e manutenção continuada.

## Requisitos funcionais

| Código | Requisito | App | API (`backend/`) |
|---|---|---|---|
| GP-01 | Login e seleção de projeto; permissões de coordenador, gestor e usuário de consulta. | ✅ | ✅ |
| GP-02 | Cadastro e consulta de projetos, vigência, fonte de recursos, responsáveis e orçamento aprovado. | ⬜ | ✅ |
| GP-03 | Cadastro de rubricas e subrubricas: diárias, passagens, material de consumo, serviços de terceiros, material permanente e outras configuráveis. | ⬜ | ✅ |
| GP-04 | Lançamento de receitas e despesas com data, descrição, rubrica, quantidade, valor unitário, valor total e situação. | ⬜ | ✅ |
| GP-05 | Inclusão opcional de comprovantes por foto ou arquivo, enviados à API e associados ao lançamento. | ⬜ | ✅ |
| GP-06 | Dashboard por projeto: receita total, despesa total, saldo disponível, orçamento previsto x executado e participação das despesas por rubrica. | ✅ (falta o gráfico de participação) | ✅ |
| GP-07 | Filtros por projeto, período, rubrica e situação; detalhamento de transações e histórico de movimentações. | ⬜ | ✅ |
| GP-08 | Relatórios financeiros resumidos e exportação/compartilhamento em PDF ou planilha, conforme endpoint disponível. | ⬜ | ✅ (PDF e CSV) |
| GP-09 | Validações de preenchimento, confirmação de alterações e registro da autoria/data das movimentações. | 🟡 (validação no login) | ✅ |
| GP-10 | Alertas visuais para rubricas próximas do limite ou com saldo insuficiente, de acordo com regras fornecidas pelo servidor. | ✅ | ✅ |

Legenda: ✅ feito · 🟡 parcial · ⬜ pendente

## Arquitetura de integração (dependência técnica)

- App → API REST/JSON autenticada → microsserviço de gestão financeira → banco relacional na nuvem (ex.: PostgreSQL).
- Serviços: autenticação/autorização, projetos, rubricas, lançamentos, agregações financeiras, anexos e relatórios.
- Infraestrutura: servidor, banco de dados, armazenamento de anexos, backups, logs e TLS/HTTPS.
- A implementação, a hospedagem e a operação do servidor e do banco são **entregas separadas** do aplicativo.
  O grupo implementou a API e o banco em `backend/` (Node.js + PostgreSQL), hospedados em um PC servidor do
  grupo com Docker, HTTPS e backup ([servidor próprio](servidor-proprio.md)).

## Critérios de aceite do aplicativo

- [x] Exibir valores e totais coerentes com os dados da API (receita, despesa e saldo). O app não recalcula, só exibe o que a API retorna.
- [ ] Registrar e consultar lançamentos por projeto e rubrica, com controle de acesso.
- [x] Dashboard funcional, filtros e tratamento de indisponibilidade de rede. Dashboard e rede já tratados; filtros pendentes.

## Entregáveis comuns

- [x] Código-fonte, projeto compilável e instruções de configuração e compilação (README).
- [x] APK para testes (`assembleDebug`). [ ] AAB assinado para distribuição.
- [x] Documentação de telas, fluxos, endpoints e permissões (em andamento, nesta pasta).
- [ ] Testes de navegação, autenticação, integração, falhas e compatibilidade ([plano](testes.md)).
- [ ] Homologação com credenciais e APIs do responsável pela plataforma.
