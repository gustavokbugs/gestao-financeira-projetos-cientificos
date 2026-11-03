# Servidor próprio (PC dedicado)

O banco e a API rodam em um PC do grupo, com Docker. Tudo fica em [`servidor/`](../servidor/):

```
Celular (app) ──HTTPS:443──► Caddy ──► API (Node.js) ──► PostgreSQL
                             │                              ▲
                             └ certificado e redirecionamento│
                               HTTP → HTTPS          backup diário (pg_dump)
```

| Serviço | Função | Exposto na rede? |
|---|---|---|
| `caddy` | HTTPS (gera e renova o certificado sozinho), redireciona HTTP → HTTPS | Sim: portas 80 e 443 |
| `api` | API REST (`backend/`) | Não, só via Caddy |
| `db` | PostgreSQL 18, dados em volume Docker | Não |
| `backup` | `pg_dump` diário em `servidor/backups/`, guarda 14 dias | Não |

Isso cobre a infraestrutura pedida no enunciado: servidor, banco relacional, armazenamento de anexos (no próprio
Postgres), backups, logs e TLS/HTTPS. Testado neste repositório: login pelo app via HTTPS com certificado próprio,
HTTP redirecionando para HTTPS, banco inacessível de fora e backup gerado.

## 1. Escolha como o app vai alcançar o servidor

| Cenário | `SITE_ENDERECO` | `TLS` | Requisitos |
|---|---|---|---|
| **Rede local** (celular e servidor no mesmo Wi-Fi) | IP do servidor na rede, ex.: `192.168.0.50` | `internal` | Nada além do PC |
| **Internet, só com IP** | IP público, ex.: `200.100.50.25` | `internal` | IP público fixo + redirecionamento da porta 443 no roteador |
| **Internet, com domínio** | `gestao.seudominio.com` | seu e-mail | Domínio apontando para o IP + portas 80 e 443 redirecionadas |

**Certificado:**

- **`internal`**: o Caddy cria a própria autoridade certificadora. O app passa a confiar nela com o script
  `exportar-certificado.sh` (passo 4). O navegador mostra aviso de certificado, o que é esperado.
- **E-mail** (Let's Encrypt): certificado reconhecido por qualquer aparelho, sem passo extra no app. Exige domínio.

**Antes de escolher "Internet", confira dois pontos:**

- **CGNAT:** muitos provedores não dão IP público. Compare o IP WAN mostrado no painel do roteador com o IP que
  aparece em um site como "meu ip". Se forem diferentes, você está atrás de CGNAT e precisa pedir IP público ao
  provedor; sem isso, só o cenário de rede local funciona.
- **IP dinâmico:** se o IP público mudar, a URL gravada no app deixa de funcionar e é preciso recompilar. Peça IP
  fixo ao provedor ou use um domínio.

## 2. Preparar o PC servidor

Recomendado: **Ubuntu Server 24.04 LTS** (ou outro Linux). Também funciona no Windows com Docker Desktop.

```bash
# Docker + Compose (Ubuntu)
sudo apt update && sudo apt install -y docker.io docker-compose-v2 git
sudo systemctl enable --now docker            # Docker sobe junto com o sistema
sudo usermod -aG docker $USER                 # saia e entre de novo na sessão

# Firewall: só SSH, HTTP e HTTPS
sudo ufw allow OpenSSH && sudo ufw allow 80/tcp && sudo ufw allow 443/tcp && sudo ufw enable
```

Também:

- Desligue a suspensão automática do PC.
- No roteador, reserve um IP fixo para o servidor (reserva DHCP).
- Para o cenário Internet, redirecione as portas **443** (e **80**, se usar domínio) para esse IP.
  **Nunca** redirecione a porta 5432 do banco.

## 3. Subir o servidor

```bash
git clone https://github.com/gustavokbugs/gestao-financeira-projetos-cientificos.git
cd gestao-financeira-projetos-cientificos/servidor
cp .env.example .env
nano .env        # preencha POSTGRES_PASSWORD, JWT_SECRET, SITE_ENDERECO, TLS e SEED_SENHA
chmod 600 .env
docker compose up -d --build
```

Para gerar as senhas: `openssl rand -base64 48`.

Teste em outro computador da rede: `curl -k https://SITE_ENDERECO/api/v1/status` deve responder
`{"status":"ok","banco":"ok",...}`. Com `SEED_DEMO=true`, o primeiro start cria os usuários de demonstração com a
senha de `SEED_SENHA`.

## 4. Fazer o app confiar no servidor (só com `TLS=internal`)

No PC servidor, dentro de `servidor/`:

```bash
./exportar-certificado.sh
```

O script copia o certificado raiz do Caddy para `android/app/src/main/res/raw/servidor_ca.crt` e ativa a linha
`SERVIDOR_CA` nos dois `network_security_config.xml`. Faça commit desses arquivos. O certificado é público, não é
segredo. O certificado raiz vale 10 anos; o certificado do site é renovado pelo Caddy automaticamente.

> Se o volume `caddy-dados` for apagado, o Caddy cria uma autoridade nova: rode o script de novo e recompile o app.

## 5. Apontar o app para o servidor

Em `android/gradle.properties`:

```properties
apiUrlRelease=https://SITE_ENDERECO/api/v1/
```

Para testar no emulador ou no celular sem gerar release:

```bash
cd android
./gradlew installDebug -PapiUrlDebug=https://SITE_ENDERECO/api/v1/
```

## Operação

| Tarefa | Comando (em `servidor/`) |
|---|---|
| Ver se está tudo no ar | `docker compose ps` |
| Logs da API / do HTTPS | `docker compose logs -f api` / `docker compose logs -f caddy` |
| Atualizar depois de um push | `git pull && docker compose up -d --build` |
| Backup manual agora | `docker compose restart backup` (gera um novo `.dump`) |
| Restaurar um backup | `docker compose exec -T db pg_restore -U gestao -d gestao --clean --if-exists < backups/ARQUIVO.dump` |
| Abrir o banco (psql) | `docker compose exec db psql -U gestao gestao` |
| Parar tudo | `docker compose down` (os dados ficam nos volumes) |

As migrações do banco (`backend/migrations/`) são aplicadas sozinhas quando a API inicia. Os containers voltam
sozinhos depois de reiniciar o PC (`restart: unless-stopped`).

Os backups ficam no mesmo PC. Copie `servidor/backups/` periodicamente para outro lugar (um pendrive ou outro
computador), senão um defeito no disco leva o banco e os backups juntos.

## Segurança

- `.env` fora do git (já está no `.gitignore`) e com `chmod 600`.
- Depois da apresentação ou da homologação, troque `SEED_DEMO=false` e altere as senhas dos usuários de demonstração.
- Acesso ao PC por SSH com chave, sem login de root por senha.
- Mantenha o sistema atualizado (`sudo apt upgrade`) e as imagens também (`docker compose pull && docker compose up -d`).
- A API já limita tentativas de login (10 falhas em 15 minutos por IP + e-mail) e não expõe o banco.

## Desenvolvimento local

Para programar, use o `docker-compose.yml` da raiz (Postgres + API em HTTP, `localhost:3000`, sem Caddy).
Detalhes no [README](../README.md#como-rodar).
