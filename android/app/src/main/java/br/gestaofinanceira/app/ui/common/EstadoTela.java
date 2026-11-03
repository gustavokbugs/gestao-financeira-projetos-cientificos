package br.gestaofinanceira.app.ui.common;

import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import br.gestaofinanceira.app.R;

/** Controla a camada de carregando / erro / vazio de uma tela (layout view_estado.xml). */
public class EstadoTela {

    private final View raiz;
    private final View progresso;
    private final TextView mensagem;
    private final Button tentarNovamente;

    public EstadoTela(View raiz, Runnable aoTentarNovamente) {
        this.raiz = raiz;
        this.progresso = raiz.findViewById(R.id.estado_progresso);
        this.mensagem = raiz.findViewById(R.id.estado_mensagem);
        this.tentarNovamente = raiz.findViewById(R.id.estado_tentar);
        tentarNovamente.setOnClickListener(v -> aoTentarNovamente.run());
    }

    public void carregando() {
        raiz.setVisibility(View.VISIBLE);
        progresso.setVisibility(View.VISIBLE);
        mensagem.setVisibility(View.GONE);
        tentarNovamente.setVisibility(View.GONE);
    }

    public void erro(String texto) {
        raiz.setVisibility(View.VISIBLE);
        progresso.setVisibility(View.GONE);
        mensagem.setText(texto);
        mensagem.setVisibility(View.VISIBLE);
        tentarNovamente.setVisibility(View.VISIBLE);
    }

    public void vazio(String texto) {
        raiz.setVisibility(View.VISIBLE);
        progresso.setVisibility(View.GONE);
        mensagem.setText(texto);
        mensagem.setVisibility(View.VISIBLE);
        tentarNovamente.setVisibility(View.GONE);
    }

    public void esconder() {
        raiz.setVisibility(View.GONE);
    }
}
