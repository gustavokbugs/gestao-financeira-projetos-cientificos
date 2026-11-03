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
