# Gestão Financeira de Projetos Científicos

Trabalho de faculdade: **Opção 1, Aplicativo de gestão financeira de projetos científicos**, do documento
[Requisitos para desenvolvimento de aplicativos Android](docs/referencias/Requisitos_Tres_Opcoes_Apps_Android.pdf).

O app Android deixa pesquisadores, coordenadores e equipes autorizadas consultar e registrar a execução
orçamentária de projetos de pesquisa, por rubricas e demonstrativos.

## Integrantes

- Alec Kniphoff da Cruz
- Gustavo Kardauke Bugs
- Pedro Henrique Hermes

## Tecnologia

| Item | Escolha |
|---|---|
| App | Android nativo em **Java** (Android Gradle Plugin 9.5, Gradle 9.8) |
| SDK | compileSdk/targetSdk 36, **minSdk 26 (Android 8.0)**, proposta a confirmar (ver [decisões](docs/decisoes.md)) |
| Rede | Retrofit 3 + OkHttp, JSON, **JWT** no cabeçalho `Authorization: Bearer` |
| UI | AppCompat + Material Components 3 |
| Credenciais | token cifrado com AES-GCM via Android Keystore; a senha nunca é salva |
| API | [`backend/`](backend/): Node.js 24 + **PostgreSQL** (driver `pg`), em Docker |
| Servidor | PC próprio com Docker: PostgreSQL + API + Caddy (HTTPS) + backup diário, ver [servidor próprio](docs/servidor-proprio.md) |

Pelas premissas do PDF, o app **não acessa banco de dados diretamente**: tudo passa pela API REST autenticada
(contrato em [docs/api-endpoints.md](docs/api-endpoints.md)). O enunciado trata o backend como **entrega separada**
do aplicativo; ele está neste repositório, na pasta `backend/`, para o app ter um servidor e um banco reais.

## Estrutura

```
.
├── android/              Projeto Android Studio (app Java)
│   └── app/src/main/java/br/gestaofinanceira/app/
│       ├── data/remote/  Retrofit: ApiClient, ApiService, DTOs, tratamento de erros
│       ├── data/local/   SessionManager (token JWT cifrado)
│       └── ui/           login, projetos, dashboard, common
├── backend/              API REST (Node.js) + migrações do PostgreSQL + dados de demonstração
├── docker-compose.yml    PostgreSQL + API para desenvolver (HTTP, localhost:3000)
├── servidor/             Produção no PC servidor: compose com Caddy (HTTPS) e backup
├── docs/                 Requisitos, telas e fluxos, endpoints, permissões, testes, decisões
│   └── referencias/      PDF do enunciado e imagens de exemplo (planilha e painel)
└── scripts/rodar.sh      Sobe banco + API, o emulador e instala o app
```

## Como rodar

Pré-requisitos: Android Studio (traz JDK e SDK), um emulador (AVD) e Docker.

**Tudo de uma vez:**

```bash
./scripts/rodar.sh
```

**Passo a passo:**

1. Banco + API: `docker compose up -d --build` (API em `localhost:3000`, Postgres em `localhost:5432`).
2. Abrir a pasta `android/` no Android Studio, criar ou escolher um emulador e clicar em *Run*.
   Pela linha de comando: `cd android && ./gradlew installDebug`.
3. No build debug, o app usa `http://10.0.2.2:3000/api/v1/` (o computador visto de dentro do emulador).
   Em aparelho físico, rode `adb reverse tcp:3000 tcp:3000` e use `-PapiUrlDebug=http://localhost:3000/api/v1/`.
   Para usar o servidor, veja [docs/servidor-proprio.md](docs/servidor-proprio.md#5-apontar-o-app-para-o-servidor).

**Usuários de teste** (senha `senha123`):

| E-mail | Perfil |
|---|---|
| `coordenador@exemplo.com` | coordenador nos projetos 1, 2 e 3 (pode criar projetos) |
| `gestor@exemplo.com` | gestor só no projeto 1 |
| `consulta@exemplo.com` | consulta (somente leitura) nos projetos 1, 2 e 3 |

O projeto 1 reproduz a planilha de orçamento do enunciado; o projeto 3, os valores do painel financeiro (CA-01).

Para testar o tratamento de falhas, suba a API com `MOCK_DELAY_MS=1500` (atraso) ou `MOCK_FAIL_RATE=0.3` (30% das
requisições falham com 503), ou pare a API (`docker compose stop api`), ou ative o modo avião no emulador.

## Gerar APK e AAB

```bash
cd android
./gradlew assembleDebug    # APK de teste: app/build/outputs/apk/debug/app-debug.apk
./gradlew bundleRelease    # AAB: app/build/outputs/bundle/release/ (precisa de keystore de assinatura)
```

O build release só aceita **HTTPS** (`network_security_config`) e usa `apiUrlRelease` de `android/gradle.properties`.

## Situação dos requisitos

| Código | Requisito | Situação |
|---|---|---|
| GP-01 | Login, seleção de projeto e perfis | ✅ App + API |
| GP-02 | Cadastro e consulta de projetos | 🟡 API pronta; telas pendentes |
| GP-03 | Rubricas e subrubricas | 🟡 API pronta; telas pendentes |
| GP-04 | Lançamento de receitas e despesas | 🟡 API pronta; telas pendentes |
| GP-05 | Comprovantes (foto ou arquivo) | 🟡 API pronta; telas pendentes |
| GP-06 | Dashboard por projeto | ✅ App + API (falta o gráfico de participação) |
| GP-07 | Filtros, detalhamento e histórico | 🟡 API pronta; telas pendentes |
| GP-08 | Relatórios PDF e planilha | 🟡 API pronta; telas pendentes |
| GP-09 | Validações, confirmação e autoria | 🟡 API pronta; confirmações nas telas pendentes |
| GP-10 | Alertas de rubrica | ✅ App + API |

Detalhes e critérios de aceite em [docs/requisitos.md](docs/requisitos.md). O que falta, e em que ordem, está em [docs/gaps-e-plano.md](docs/gaps-e-plano.md).
