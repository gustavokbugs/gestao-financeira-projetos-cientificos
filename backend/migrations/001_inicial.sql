-- Esquema inicial do banco da gestão financeira de projetos científicos.

CREATE TABLE usuarios (
    id                  SERIAL PRIMARY KEY,
    nome                TEXT        NOT NULL,
    email               TEXT        NOT NULL UNIQUE,
    senha_salt          TEXT        NOT NULL,
    senha_hash          TEXT        NOT NULL,
    pode_criar_projetos BOOLEAN     NOT NULL DEFAULT FALSE,
    criado_em           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE projetos (
    id                 SERIAL PRIMARY KEY,
    codigo             TEXT          NOT NULL UNIQUE,
    titulo             TEXT          NOT NULL,
    descricao          TEXT          NOT NULL DEFAULT '',
    vigencia_inicio    DATE          NOT NULL,
    vigencia_fim       DATE          NOT NULL,
    fonte_recursos     TEXT          NOT NULL,
    responsaveis       JSONB         NOT NULL DEFAULT '[]',
    orcamento_aprovado NUMERIC(14,2) NOT NULL CHECK (orcamento_aprovado > 0),
    CHECK (vigencia_fim >= vigencia_inicio)
);

-- Papel do usuário em cada projeto (GP-01).
CREATE TABLE membros (
    usuario_id INT  NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    projeto_id INT  NOT NULL REFERENCES projetos(id) ON DELETE CASCADE,
    papel      TEXT NOT NULL CHECK (papel IN ('coordenador', 'gestor', 'consulta')),
    PRIMARY KEY (usuario_id, projeto_id)
);

-- Rubricas em dois níveis: rubrica principal (rubrica_pai_id nulo) e subrubricas (GP-03).
CREATE TABLE rubricas (
    id             SERIAL PRIMARY KEY,
    projeto_id     INT           NOT NULL REFERENCES projetos(id) ON DELETE CASCADE,
    rubrica_pai_id INT           REFERENCES rubricas(id),
    nome           TEXT          NOT NULL,
    orcamento      NUMERIC(14,2) NOT NULL CHECK (orcamento >= 0)
);
CREATE UNIQUE INDEX rubricas_nome_unico ON rubricas (projeto_id, COALESCE(rubrica_pai_id, 0), lower(nome));

-- Lançamentos não são excluídos: o cancelamento é uma situação (GP-09).
CREATE TABLE lancamentos (
    id             SERIAL PRIMARY KEY,
    projeto_id     INT           NOT NULL REFERENCES projetos(id) ON DELETE CASCADE,
    tipo           TEXT          NOT NULL CHECK (tipo IN ('RECEITA', 'DESPESA')),
    data           DATE          NOT NULL,
    descricao      TEXT          NOT NULL,
    rubrica_id     INT           REFERENCES rubricas(id),
    quantidade     NUMERIC(12,3) NOT NULL CHECK (quantidade > 0),
    valor_unitario NUMERIC(14,2) NOT NULL CHECK (valor_unitario > 0),
    valor_total    NUMERIC(14,2) NOT NULL,
    situacao       TEXT          NOT NULL CHECK (situacao IN ('PREVISTO', 'REALIZADO', 'CANCELADO')),
    criado_por     INT           NOT NULL REFERENCES usuarios(id),
    criado_em      TIMESTAMPTZ   NOT NULL,
    atualizado_por INT           NOT NULL REFERENCES usuarios(id),
    atualizado_em  TIMESTAMPTZ   NOT NULL,
    CHECK (tipo = 'RECEITA' OR rubrica_id IS NOT NULL)
);
CREATE INDEX lancamentos_projeto_data ON lancamentos (projeto_id, data DESC);
CREATE INDEX lancamentos_rubrica ON lancamentos (rubrica_id);

-- Comprovantes guardados no próprio banco (BYTEA, até 10 MB cada): dispensa um serviço de arquivos à parte.
CREATE TABLE comprovantes (
    id            SERIAL PRIMARY KEY,
    lancamento_id INT         NOT NULL REFERENCES lancamentos(id) ON DELETE CASCADE,
    nome_arquivo  TEXT        NOT NULL,
    tipo          TEXT        NOT NULL,
    tamanho       INT         NOT NULL,
    conteudo      BYTEA       NOT NULL,
    enviado_por   INT         NOT NULL REFERENCES usuarios(id),
    enviado_em    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX comprovantes_lancamento ON comprovantes (lancamento_id);

-- Auditoria: quem fez o quê e quando, com os campos alterados (GP-07 e GP-09).
CREATE TABLE historico (
    id          BIGSERIAL PRIMARY KEY,
    projeto_id  INT         NOT NULL REFERENCES projetos(id) ON DELETE CASCADE,
    entidade    TEXT        NOT NULL,
    entidade_id INT         NOT NULL,
    acao        TEXT        NOT NULL,
    usuario_id  INT         NOT NULL REFERENCES usuarios(id),
    data        TIMESTAMPTZ NOT NULL DEFAULT now(),
    alteracoes  JSONB       NOT NULL DEFAULT '[]'
);
CREATE INDEX historico_projeto ON historico (projeto_id, data DESC);
CREATE INDEX historico_entidade ON historico (entidade, entidade_id);
