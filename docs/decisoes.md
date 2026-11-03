# Decisões

O enunciado lista as decisões necessárias antes da implementação. Situação de cada uma:

| Decisão | Situação |
|---|---|
| Opção escolhida | ✅ Opção 1: gestão financeira de projetos científicos |
| Tecnologia | ✅ Android nativo em Java |
| Perfis de usuário | ✅ coordenador, gestor e consulta, por projeto ([permissoes.md](permissoes.md)) |
| Disponibilidade e documentação das APIs | ✅ API própria em `backend/` com PostgreSQL ([api-endpoints.md](api-endpoints.md)); em produção num PC servidor do grupo ([servidor próprio](servidor-proprio.md)) |
| Identidade visual | ⬜ Provisória (azul `#1F5FAD`, ícone de barras) |
| Versão mínima do Android | 🟡 Proposta: Android 8.0 (API 26); confirmar com o professor |
| Política de acesso e privacidade | ⬜ |
| Prazo | ⬜ |
| Orçamento do aplicativo | ⬜ Não se aplica (trabalho acadêmico)? Confirmar |
| Homologação e suporte | ⬜ |

## Decisões técnicas tomadas

- **Backend próprio (`backend/`)**: o enunciado trata o backend como entrega separada, mas o grupo precisa de um
  servidor e de um banco reais. Node.js + PostgreSQL.
- **Servidor próprio em vez de serviços de nuvem**: um PC do grupo com Docker (decisão do grupo). O Caddy cuida do
  HTTPS (Let's Encrypt com domínio, ou certificado próprio confiado pelo app) e um container faz backup diário.
- **Comprovantes no próprio Postgres (`BYTEA`)**: dispensa um serviço de armazenamento à parte. Limite de 10 MB
  por arquivo.
- **Cálculos no servidor**: totais, saldos e alertas vêm prontos da API; o app não recalcula (critério de aceite:
  "valores coerentes com dados retornados pela API").
- **Lançamentos não são excluídos**, só cancelados, para preservar a autoria e o histórico (GP-09).
- **Upload de comprovante em corpo binário** em vez de multipart: mais simples no app e na API.
