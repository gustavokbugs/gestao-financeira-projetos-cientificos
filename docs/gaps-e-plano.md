# Gaps e plano de implementação

Levantamento feito em **03/11/2026** a partir do checklist
[requisitos_opcao1_gestao_financeira.md](referencias/requisitos_opcao1_gestao_financeira.md), comparado com o
código atual (`android/` e `backend/`). Atualizado depois da troca da API simulada pela API com PostgreSQL.

**Legenda:** ✅ atendido · 🟡 parcial ou implementado sem teste · ❌ falta

## Atualização de 05/11/2026: fases 0 a 5 implementadas no app

Todas as telas do plano abaixo foram feitas e testadas no emulador (Pixel, API 36) contra a API local:

| Área | Situação |
|---|---|
| GP-01 a GP-10 | ✅ no app e na API |
| CA-01 (valores coerentes com a API) | ✅ dashboard, lista e relatório batem com a API |
| CA-02 (registrar/consultar por projeto e rubrica com controle de acesso) | ✅ testado com coordenador e consulta (sem botões de escrita) e com a API recusando por perfil |
| CA-03 (dashboard, filtros, indisponibilidade de rede) | ✅ API parada → mensagem com "Tentar novamente"; com dados na tela → Snackbar |
| Conflito de edição (409) | ✅ diálogo "Alterado por outra pessoa" → Recarregar |
| Testes unitários (E-11/E-13) | ✅ 30 testes JUnit: `Formatos`, `ApiErros`, `Permissoes`, `ValidadorLancamento`, `Filtros`, `TextoMovimentacao` |
| AAB (E-05) | ✅ `bundleRelease` assinado com `keystore.properties` (fora do git) |
| Rotação (T-07) | ✅ `configChanges` nas Activities: girar não recria a tela nem perde o que foi digitado |

**Ainda pendente** (depende de fora do código ou ficou fora do escopo):

- Instalar o servidor HTTPS no PC do grupo e trocar `apiUrlRelease` pelo endereço real (tarefa 5.1a); o AAB atual aponta para o endereço de exemplo.
- Testes instrumentados com Espresso (5.3) e teste no Android 8.0 / API 26 (5.4): só há AVD da API 36 nesta máquina.
- Capturas de tela em `telas-e-fluxos.md` e homologação com o professor (fase 6).

As seções abaixo são o levantamento original de 03/11/2026, mantido como registro.

---

## 1. Resumo

| Área | Situação | Principal gap |
|---|---|---|
| Premissas (P-01 a P-08) | 🟡 | AAB não gerado; HTTP no desenvolvimento; erros e loading só nas 3 telas existentes |
| GP-01 Login e perfis | 🟡 | Esconder ações por perfil (não há telas de escrita ainda); logout/401 sem teste |
| GP-02 Projetos | 🟡 | Sem tela de detalhe, cadastro e edição |
| GP-03 Rubricas | ❌ app / ✅ API | Nenhuma tela |
| GP-04 Lançamentos | ❌ app / ✅ API | Nenhuma tela; **bloqueia o CA-02** |
| GP-05 Comprovantes | ❌ app / ✅ API | Câmera, seletor de arquivo e upload |
| GP-06 Dashboard | 🟡 | Falta o gráfico de participação e os elementos do painel de referência |
| GP-07 Filtros e histórico | ❌ app / ✅ API | Nenhuma tela; **bloqueia o CA-03** |
| GP-08 Relatórios | ❌ app / ✅ API | Baixar e compartilhar PDF/CSV |
| GP-09 Validação e auditoria | 🟡 | Validação só no login; falta confirmação e exibição da autoria |
| GP-10 Alertas | ✅ | Falta o alerta no momento de lançar (a API já devolve) |
| Transversais (T-01 a T-08) | 🟡 | HTTPS no desenvolvimento; mensagens de timeout e 422 |
| Critérios de aceite | ✅ CA-01 · ❌ CA-02 · 🟡 CA-03 | |
| Entregáveis (E-01 a E-15) | 🟡 | AAB, testes, homologação |

A API (`backend/`, com PostgreSQL) já atende **todos** os GP-01 a GP-10. Quase todo o trabalho que falta está no app.

---

## 2. Gaps detalhados

### 2.1 Premissas

| # | Situação | O que existe / o que falta |
|---|---|---|
| P-01 | 🟡 | APK de debug gerado e testado em smartphone (Pixel 7, API 36). **Falta o AAB** (E-05). |
| P-02 | ✅ | Android nativo em Java. |
| P-03 | ✅ | Só Retrofit → API REST. Nenhum driver de banco no app. |
| P-04 | ✅ | JWT no `Authorization: Bearer`; perfis por projeto. |
| P-05 | 🟡 | O release só aceita HTTPS, mas **o ambiente local usa HTTP** (`docker compose`). O servidor de produção (`servidor/`) já serve HTTPS e foi testado com o app; falta instalar no PC servidor e apontar `apiUrlRelease` para ele ([servidor próprio](servidor-proprio.md)). Ver a tarefa 5.1. |
| P-06 | 🟡 | `ApiErros` + `EstadoTela` nas 3 telas. As telas novas precisam seguir o mesmo padrão. |
| P-07 | 🟡 | Loading nas 3 telas; idem. |
| P-08 | ✅ | Token cifrado com AES-GCM no Keystore; a senha não é salva; log HTTP só no debug e no nível BASIC; `allowBackup=false`. |

### 2.2 Requisitos funcionais

**GP-01: Login, seleção de projeto e permissões**

| Item | Situação | Observação |
|---|---|---|
| a) Login JWT | ✅ | `LoginActivity` |
| b) Seleção de projeto | ✅ | `ProjetosActivity` |
| c) Três perfis | ✅ | API: `coordenador`, `gestor`, `consulta` por projeto |
| d) Acesso na interface | ❌ | Ainda não há botão de criar/editar para esconder. Precisa de um helper central de permissões (tarefa 0.2) |
| e) Acesso na API | ✅ | 403 por perfil; 404 para projeto de que o usuário não participa |
| f) Logout / token expirado | 🟡 | Implementado (menu Sair com confirmação; 401 → login), **ainda não testado no app** |

**GP-02: Projetos**

| Item | Situação | Observação |
|---|---|---|
| a) Listagem | ✅ | |
| b) Detalhe (vigência, fonte, responsáveis, orçamento) | 🟡 | A lista mostra vigência e orçamento; **fonte de recursos e responsáveis não aparecem em nenhuma tela** |
| c) Cadastro | ❌ | Além disso, a sessão **não guarda `podeCriarProjetos`**, que vem no login |
| d) Edição com confirmação | ❌ | |

**GP-03: Rubricas e subrubricas:** ❌ no app (a–e). A API tem `GET/POST /projetos/{id}/rubricas` e `PUT /rubricas/{id}`, com dois níveis, orçamento e validação de soma. As cinco rubricas da planilha estão nos dados de exemplo; "outras configuráveis" são suportadas porque nada está fixo no código.

**GP-04: Lançamentos:** ❌ no app (a–f). A API calcula `valorTotal = quantidade × valorUnitario` no servidor, valida a vigência e exige rubrica na despesa.

**GP-05: Comprovantes:** ❌ no app (a, b, f). A API recebe o upload (c), associa ao lançamento (d) e o comprovante é opcional (e).

**GP-06: Dashboard**

| Item | Situação | Observação |
|---|---|---|
| a–d) Receita, despesa, saldo, previsto × executado | ✅ | |
| e) Participação por rubrica | 🟡 | Aparece só como texto ("6,6% das despesas"). **Falta o gráfico** |
| f) Dados da API | ✅ | O app não recalcula nada |
| g) Muda com o projeto | ✅ | |
| Painel de referência (seção 10.2) | ❌ | Faltam a tabela "Resumo Financeiro" (tipo / tipo de despesa / valor), os medidores e o botão **Download do relatório** |

**GP-07: Filtros e histórico:** ❌ no app (a–g). A API filtra por `tipo`, `rubricaId` (inclui as subrubricas), `situacao`, `de`, `ate` e `busca`, com paginação, e tem `GET /lancamentos/{id}` e `GET /projetos/{id}/historico`. O filtro "por projeto" (a) é a própria seleção de projeto.

**GP-08: Relatórios:** ❌ no app (a–d). A API gera PDF e CSV em `GET /projetos/{id}/relatorio?formato=pdf|csv`, com os mesmos filtros.

**GP-09: Validação e auditoria**

| Item | Situação | Observação |
|---|---|---|
| a) Validação de preenchimento | 🟡 | Só no login. A API valida tudo e devolve 422 com `erros[]`, mas **o app não liga esses erros aos campos** |
| b) Confirmação | 🟡 | Só no "Sair" |
| c, d) Autoria e data registradas | ✅ | API: `criadoPor/criadoEm/atualizadoPor/atualizadoEm` + histórico com os campos alterados |
| e) Autoria e data visíveis | ❌ | Depende das telas de detalhe e histórico |

**GP-10: Alertas:** ✅ a–d. As regras ficam no servidor (`regrasAlerta`, `alerta.nivel`), com cor e texto. Melhoria: mostrar o `alertaRubrica` que a API devolve ao salvar um lançamento (tarefa 1.3).

### 2.3 Transversais

| # | Situação | Gap |
|---|---|---|
| T-01 Rede indisponível | 🟡 | Implementado (estado de erro + "Tentar novamente"; Snackbar quando já há conteúdo), **não testado** com modo avião |
| T-02 Loading em todas as chamadas | 🟡 | Seguir o padrão nas telas novas |
| T-03 Mensagens de erro | 🟡 | Timeout cai em "Sem conexão" (não há mensagem própria); 422 não é ligado ao campo; 409 sem tratamento |
| T-04 Somente HTTPS | 🟡 | Ver P-05 |
| T-05 Token seguro | ✅ | Keystore |
| T-06 Sem banco direto | ✅ | |
| T-07 Smartphone | 🟡 | Testado só em 1080×2400. Falta tela pequena, fonte grande e rotação (as telas recarregam ao girar porque não usam ViewModel) |
| T-08 R$ | ✅ | `Formatos.moeda` em pt-BR |

### 2.4 Critérios de aceite

| # | Situação | O que falta para comprovar |
|---|---|---|
| CA-01 | ✅ | Comprovado no app com o projeto 1 (R$ 20.000,00 − R$ 19.380,00 = R$ 620,00) e com o projeto 3, que reproduz o painel do enunciado (R$ 559.453,02 − R$ 96.078,83 = R$ 463.374,19) |
| CA-02 | ❌ | Telas de lançamento e filtro por rubrica, testadas com os 3 perfis |
| CA-03 | 🟡 | Dashboard ✅. **Filtros ❌.** Teste de rede com modo avião pendente |

### 2.5 Entregáveis

| # | Situação | Gap |
|---|---|---|
| E-01 a E-04 | ✅ | Código, projeto compilável, README e APK de debug |
| E-05 AAB | ❌ | Keystore + `signingConfig` + `bundleRelease` |
| E-06/E-07 Telas e fluxos | 🟡 | `telas-e-fluxos.md` cobre as telas atuais; atualizar e incluir capturas de tela |
| E-08 Endpoints | ✅ | `api-endpoints.md` |
| E-09 Permissões | 🟡 | Revisar quando a câmera e o seletor entrarem (GP-05) |
| E-10 a E-13 Testes | ❌ | Há só o plano manual (`testes.md`); nenhum teste automatizado |
| E-14 Compatibilidade | ❌ | Testado só no Android 16; falta a versão mínima (API 26) |
| E-15 Homologação | ❌ | Depende do professor (seção 6) |

### 2.6 Decisões (seção 9 do checklist)

| # | Situação |
|---|---|
| D-01 Opção | ✅ Opção 1 |
| D-02 Tecnologia | ✅ Java nativo |
| D-03 Perfis | ✅ |
| D-04 APIs | ✅ API própria com PostgreSQL (`backend/`); instalação no PC servidor pendente |
| D-05 Identidade visual | 🟡 Provisória |
| D-06 Android mínimo | 🟡 Proposta: API 26 (Android 8.0) |
| D-07 a D-10 | ❌ Privacidade, prazo, orçamento e homologação |

### 2.7 Gaps técnicos internos (não aparecem no checklist, mas atrapalham os itens acima)

1. **Sem ViewModel**: ao girar a tela, as Activities recarregam e os formulários perderiam o que foi digitado.
2. **`SessionManager` não guarda `podeCriarProjetos`**, então o app não sabe se pode mostrar "Novo projeto".
3. **`ApiErros` não expõe os erros por campo** (422) nem trata o 409 e o timeout separadamente.
4. **Dados de exemplo × planilha**: o projeto 1 usa 4 diárias em vez de 5 e não tem alguns itens da planilha
   (kit DELL, cabos VGA/HDMI, revisão/tradução). Foi de propósito, para gerar alertas, mas convém documentar
   ou cadastrar os itens que faltam como `PREVISTO`.
5. **Semântica do limite em 100%**: uma rubrica com exatamente 100% comprometido aparece como "próxima do
   limite" (ATENÇÃO), não como "saldo insuficiente". Confirmar a regra (pergunta 3 da seção 6).

---

## 3. Plano de implementação

As fases seguem a ordem de impacto nos critérios de aceite: **CA-02 e CA-03 primeiro**. Tamanho:
**P** ≈ até 2 h · **M** ≈ meio dia · **G** ≈ 1 dia ou mais.

### Fase 0: Base para as telas novas (pré-requisito)

| # | Tarefa | Arquivos | Tam. | Fecha |
|---|---|---|---|---|
| 0.1 | Adotar `ViewModel` + `LiveData` (`androidx.lifecycle`) para guardar o estado e as chamadas em andamento. Migrar as 3 telas existentes como modelo | `ui/*/…ViewModel.java`, `libs.versions.toml` | M | T-07 |
| 0.2 | Classe `Permissoes` (a partir do `papel`): `podeLancar()`, `podeGerenciar()`. Guardar `podeCriarProjetos` no `SessionManager` | `ui/common/Permissoes.java`, `SessionManager.java`, `LoginActivity.java` | P | GP-01 d, GP-02 c |
| 0.3 | `ApiErros`: `errosPorCampo(Response) → Map<String,String>`, mensagem própria para `SocketTimeoutException`, tratamento do 409 | `ApiErros.java` | P | T-03, GP-09 a |
| 0.4 | Navegação a partir do dashboard: menu ou bottom bar com **Lançamentos · Rubricas · Projeto · Histórico · Relatórios** | `activity_dashboard.xml`, `menu_dashboard.xml` | P | navegação |
| 0.5 | Helpers de formulário: `Formatos.parseMoeda("1.234,56") → BigDecimal`, `MaterialDatePicker` limitado à vigência, dropdown de rubricas (`ExposedDropdownMenu`) | `ui/common/` | M | GP-04, GP-07 |

### Fase 1: Lançamentos (CA-02 e CA-03)

| # | Tarefa | Endpoints | Tam. | Fecha |
|---|---|---|---|---|
| 1.1 | **Lista de lançamentos**: RecyclerView paginada (carrega mais ao rolar), puxar para atualizar, estados de vazio/erro. Cada item mostra data, descrição, rubrica, total, situação (chip colorido) e ícone de anexo | `GET /projetos/{id}/lancamentos` | M | GP-04 e, GP-07 |
| 1.2 | **Filtros combináveis**: período (date range picker), rubrica (dropdown, inclui subrubricas), situação e tipo (chips), busca por texto. Filtros ativos visíveis com "Limpar" | mesma rota, query params | M | GP-07 a–e, CA-03 |
| 1.3 | **Formulário de lançamento** (criar e editar): tipo, data (dentro da vigência), descrição, rubrica (obrigatória para despesa), quantidade, valor unitário; **total calculado na tela só para exibição** (o servidor é a referência), situação. Validação local, **diálogo de confirmação com resumo** antes de enviar, erros 422 ligados aos campos, 409 → "alterado por outra pessoa, recarregar". Depois de salvar, mostrar o `alertaRubrica` (GP-10) | `POST /projetos/{id}/lancamentos`, `PUT /lancamentos/{id}` | G | GP-04 a–d, f; GP-09 a, b |
| 1.4 | **Detalhe do lançamento**: todos os campos, criado e alterado por quem e quando, histórico do lançamento (de → para), comprovantes. Ações conforme o perfil: Editar, **Cancelar** (com confirmação; não há exclusão) | `GET /lancamentos/{id}`, `PUT` com `situacao: CANCELADO` | M | GP-07 f, GP-09 c–e |
| 1.5 | **Esconder ações por perfil**: botão "Novo lançamento" e Editar/Cancelar ocultos para `consulta` | – | P | GP-01 d |
| 1.6 | Testar o CA-02 com os 3 usuários e o CA-03 com modo avião; registrar em `testes.md` | – | P | CA-02, CA-03, T-01 |

### Fase 2: Projetos e rubricas

| # | Tarefa | Endpoints | Tam. | Fecha |
|---|---|---|---|---|
| 2.1 | **Detalhe do projeto**: código, título, descrição, vigência, **fonte de recursos**, **responsáveis**, orçamento aprovado | `GET /projetos/{id}` | P | GP-02 b |
| 2.2 | **Cadastro e edição de projeto** (coordenador; "Novo projeto" só com `podeCriarProjetos`): responsáveis em lista editável; confirmação; 422 nos campos (ex.: orçamento menor que a soma das rubricas) | `POST /projetos`, `PUT /projetos/{id}` | M | GP-02 c, d |
| 2.3 | **Rubricas**: lista em árvore (rubrica → subrubricas) com orçado/executado/saldo/alerta, reaproveitando o `RubricasAdapter` | `GET /projetos/{id}/rubricas` | P | GP-03 a, b, e |
| 2.4 | **Criar e editar rubrica** (coordenador): nome, orçamento, rubrica principal opcional (só primeiro nível); confirmação; mostrar o erro de soma da API | `POST /projetos/{id}/rubricas`, `PUT /rubricas/{id}` | M | GP-03 a, b, d |

### Fase 3: Dashboard completo e relatórios

| # | Tarefa | Endpoints | Tam. | Fecha |
|---|---|---|---|---|
| 3.1 | **Gráfico de participação por rubrica**: rosca ou barras em uma `View` própria desenhada com `Canvas` (sem dependência externa, já que MPAndroidChart exige o JitPack), com legenda e percentuais | `participacaoPorRubrica` | M | GP-06 e |
| 3.2 | **Tabela "Resumo Financeiro"** como no painel (tipo / tipo de despesa / valor). Sugestão: a API passa a devolver `resumoFinanceiro[]` no dashboard. Medidores (gauges) são opcionais; os cartões atuais já cobrem receita, despesa e saldo | `GET /projetos/{id}/dashboard` (estender) | M | seção 10.2 |
| 3.3 | ~~Projeto 3 nos dados de exemplo com os valores do painel~~ **Feito** | `backend/src/seed.js` | - | CA-01 |
| 3.4 | **Relatórios**: escolher PDF ou planilha (aplicando os filtros atuais) → baixar com `@Streaming` → salvar no `cacheDir` → compartilhar com `FileProvider` + `ACTION_SEND` (ou abrir com `ACTION_VIEW`). Botão **"Download do relatório"** no dashboard. Tratar 404/501 com "relatório indisponível" | `GET /projetos/{id}/relatorio` | M | GP-08 a–d |
| 3.5 | **Histórico do projeto**: lista de movimentações (ação, entidade, usuário, data, campos alterados) | `GET /projetos/{id}/historico` | P | GP-07 g, GP-09 e |

### Fase 4: Comprovantes

| # | Tarefa | Detalhes | Tam. | Fecha |
|---|---|---|---|---|
| 4.1 | **Foto pela câmera** com `ActivityResultContracts.TakePicture`, salvando em uma Uri do `FileProvider`. O app de câmera do sistema tira a foto, então **não é preciso declarar `CAMERA`** | Reduzir para ≤ 1600 px, JPEG 80%, para ficar abaixo de 10 MB | M | GP-05 a, f |
| 4.2 | **Arquivo** com o Photo Picker (`PickVisualMedia`) para imagens e `OpenDocument` para PDF. Nenhum dos dois pede permissão de armazenamento | – | P | GP-05 b, f |
| 4.3 | **Upload** em `POST /lancamentos/{id}/comprovantes` (corpo binário, `Content-Type`, `X-Nome-Arquivo`), com progresso e nova tentativa em caso de falha. O lançamento salva normalmente sem anexo | – | M | GP-05 c, d, e |
| 4.4 | **Ver comprovante**: baixar `GET /comprovantes/{id}` e abrir com `ACTION_VIEW` | – | P | GP-05 d |
| 4.5 | Atualizar `permissoes.md` | – | P | E-09 |

### Fase 5: Qualidade, segurança e entregáveis

| # | Tarefa | Detalhes | Tam. | Fecha |
|---|---|---|---|---|
| 5.1 | **HTTPS em todo lugar**: (a) instalar o servidor no PC ([servidor próprio](servidor-proprio.md)), rodar `exportar-certificado.sh` e usar a URL HTTPS na demonstração; (b) opcional: Caddy também no compose de desenvolvimento, para remover o cleartext do debug | (a) resolve a demonstração | P | P-05, T-04 |
| 5.2 | **Testes unitários** (JUnit): `Formatos`, `ApiErros`, `Permissoes` e um validador de lançamento em Java puro, extraído do formulário | `app/src/test` | M | E-11, E-13 |
| 5.3 | **Testes instrumentados** (Espresso + OkHttp `MockWebServer`): login ok e com erro, login → projeto → dashboard, consulta sem botões de escrita, falha de rede. Exige poder trocar a URL base nos testes (ex.: setter em `GestaoFinanceiraApp`) | `app/src/androidTest` | G | E-10, E-11, E-12, E-13 |
| 5.4 | **Compatibilidade**: AVD API 26 (`system-images;android-26;google_apis;x86_64`), roteiro manual de fumaça, tema escuro, fonte grande, tela pequena, rotação | `testes.md` | M | E-14, T-07 |
| 5.5 | **AAB assinado**: `keytool` → `keystore.properties` (no `.gitignore`) → `signingConfigs` → `./gradlew bundleRelease`. Depende do servidor instalado (`apiUrlRelease`) | `app/build.gradle` | P | E-05, P-01 |
| 5.6 | **Documentação**: atualizar `telas-e-fluxos.md` com capturas de tela, `requisitos.md` e este arquivo | `docs/` | P | E-06, E-07 |

### Fase 6: Homologação e decisões

| # | Tarefa | Fecha |
|---|---|---|
| 6.1 | Levar ao professor as perguntas da seção 6 | D-04, D-06 a D-10 |
| 6.2 | Se houver API oficial: ajustar `API_BASE_URL` do release, comparar o contrato com `api-endpoints.md` e adaptar o `ApiService` | E-15 |
| 6.3 | Definir a identidade visual (cores, ícone, nome) | D-05 |

---

## 4. Sugestão de divisão (3 frentes em paralelo)

Depois da Fase 0, que deve ser feita junto ou por uma pessoa antes das outras, as frentes quase não se cruzam:

| Frente | Fases | Foco |
|---|---|---|
| A | 1 | Lançamentos, filtros e detalhe (o núcleo do CA-02 e do CA-03) |
| B | 2 + 3.5 + 4 | Projetos, rubricas, histórico e comprovantes |
| C | 3.1–3.4 + 5 | Dashboard, relatórios, HTTPS, testes e AAB |

Os nomes (Alec, Gustavo, Pedro) ficam a critério do grupo.

## 5. Ordem mínima para cumprir os critérios de aceite

Se o prazo apertar, este é o caminho mais curto para os três CAs:

1. Fase 0 (0.2, 0.3, 0.5)
2. 1.1 → 1.2 → 1.3 → 1.5 → 1.6 (**CA-02 e CA-03**)
3. Instalar o servidor (5.1a)
4. 3.1 e 3.4 (gráfico e relatório: GP-06 e GP-08)
5. Fase 4, Fase 2 e Fase 5

## 6. Perguntas para o professor

Vêm da seção 11 do checklist, com o que já foi decidido no projeto:

1. **Backend**: haverá API de homologação para a Opção 1? *O grupo implementou a própria API com PostgreSQL
   (`backend/`, contrato em `api-endpoints.md`), hospedada em um PC servidor do grupo.* (O link da Defesa Civil e a tabela Normal/Atenção/Alerta/Emergência do PDF são da Opção 3.)
2. **GP-08**: PDF, planilha ou os dois? *A API oferece os dois (PDF e CSV).*
3. **GP-10**: quais são as regras de "próximo do limite"? *Hoje: atenção a partir de 80% e crítico acima de 100%
   do orçamento comprometido (executado + previsto), configuradas no servidor.* 100% exato é atenção ou crítico?
4. **Offline**: confirmar que não é exigido na Opção 1 (só o tratamento de indisponibilidade).
5. **Android mínimo**: a API 26 (Android 8.0) é aceita?
6. **HTTP na demonstração local**: é aceito, ou o HTTPS vale também para a demonstração (tarefa 5.1)?
