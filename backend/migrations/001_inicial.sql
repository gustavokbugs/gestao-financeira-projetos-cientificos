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
