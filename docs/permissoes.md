# Perfis de acesso e permissões do dispositivo

## Perfis (GP-01)

| Perfil | O que pode fazer |
|---|---|
| **Coordenador** | Tudo no projeto: consultar, lançar, editar o projeto, gerenciar rubricas. |
| **Gestor** | Consultar, criar e editar lançamentos, anexar comprovantes. |
| **Consulta** | Somente leitura: dashboard, lançamentos, histórico e relatórios. |

O perfil é definido **por projeto** e vem da API (`papel` em `GET /projetos`). A autorização vale sempre no
servidor; o app só esconde as ações que o perfil não permite. Tabela por rota em [api-endpoints.md](api-endpoints.md#perfis).

## Permissões do Android

| Permissão | Situação | Uso |
|---|---|---|
| `INTERNET` | declarada | Comunicação com a API |
| Câmera | **não declarar** | GP-05: usar `ActivityResultContracts.TakePicture` (o app de câmera do sistema tira a foto, então não precisa de `CAMERA`) |
| Arquivos/fotos | **não declarar** | GP-05: usar o Photo Picker / `OpenDocument`, que não pedem permissão de armazenamento |

## Proteção de dados

- **Tráfego:** release só com HTTPS (`res/xml/network_security_config.xml`). HTTP só no build debug e só para
  `10.0.2.2`/`localhost` (`src/debug/res/xml/network_security_config.xml`).
- **Token JWT:** cifrado com AES-256-GCM usando uma chave do Android Keystore (`SessionManager`). Ao expirar,
  é descartado.
- **Senha:** enviada só no login, nunca armazenada.
- **Logs:** o log HTTP existe só no debug e no nível `BASIC` (sem cabeçalhos nem corpo).
- **Backup:** `allowBackup="false"`, para a sessão não ir para backups na nuvem.
