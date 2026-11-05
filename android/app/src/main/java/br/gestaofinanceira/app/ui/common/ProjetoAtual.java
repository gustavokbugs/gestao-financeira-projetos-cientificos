package br.gestaofinanceira.app.ui.common;

import android.content.Intent;

import br.gestaofinanceira.app.data.remote.dto.Projeto;

/** Dados do projeto selecionado que passam de uma tela para outra (id, título, papel e vigência). */
public final class ProjetoAtual {

    private static final String ID = "projeto_id";
    private static final String TITULO = "projeto_titulo";
    private static final String PAPEL = "papel";
    private static final String INICIO = "vigencia_inicio";
    private static final String FIM = "vigencia_fim";

    public final long id;
    public final String titulo;
    public final String papel;
    public final String vigenciaInicio;
    public final String vigenciaFim;
    public final Permissoes permissoes;

    public ProjetoAtual(long id, String titulo, String papel, String vigenciaInicio, String vigenciaFim) {
        this.id = id;
        this.titulo = titulo;
        this.papel = papel;
        this.vigenciaInicio = vigenciaInicio;
        this.vigenciaFim = vigenciaFim;
        this.permissoes = new Permissoes(papel);
    }

    public static ProjetoAtual de(Projeto p) {
        return new ProjetoAtual(p.id, p.titulo, p.papel, p.vigenciaInicio, p.vigenciaFim);
    }

    public static ProjetoAtual ler(Intent intent) {
        return new ProjetoAtual(intent.getLongExtra(ID, -1), intent.getStringExtra(TITULO),
                intent.getStringExtra(PAPEL), intent.getStringExtra(INICIO), intent.getStringExtra(FIM));
    }

    public static boolean presente(Intent intent) {
        return intent != null && intent.hasExtra(ID);
    }

    public Intent colocar(Intent intent) {
        return intent.putExtra(ID, id).putExtra(TITULO, titulo).putExtra(PAPEL, papel)
                .putExtra(INICIO, vigenciaInicio).putExtra(FIM, vigenciaFim);
    }
}
