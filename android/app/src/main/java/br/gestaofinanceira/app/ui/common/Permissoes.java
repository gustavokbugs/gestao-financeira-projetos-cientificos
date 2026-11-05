package br.gestaofinanceira.app.ui.common;

/**
 * GP-01: o que o papel do usuário no projeto permite fazer. Espelha a tabela de perfis de
 * docs/api-endpoints.md. A API continua sendo a autoridade (403); aqui só decidimos o que mostrar.
 */
public final class Permissoes {

    public static final String COORDENADOR = "coordenador";
    public static final String GESTOR = "gestor";
    public static final String CONSULTA = "consulta";

    private final String papel;

    public Permissoes(String papel) {
        this.papel = papel;
    }

    /** Criar e editar lançamentos, enviar comprovantes. */
    public boolean podeLancar() {
        return COORDENADOR.equals(papel) || GESTOR.equals(papel);
    }

    /** Editar o projeto, criar e editar rubricas. */
    public boolean podeGerenciar() {
        return COORDENADOR.equals(papel);
    }

    public String getPapel() {
        return papel;
    }
}
