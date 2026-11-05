package br.gestaofinanceira.app.ui.common;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import br.gestaofinanceira.app.R;
import br.gestaofinanceira.app.data.remote.dto.Movimentacao;
import br.gestaofinanceira.app.ui.historico.TextoMovimentacao;

/** Monta as linhas das telas de detalhe: pares rótulo/valor e itens do histórico. */
public final class Detalhes {

    private Detalhes() {
    }

    public static void campo(ViewGroup pai, String rotulo, CharSequence valor) {
        View linha = LayoutInflater.from(pai.getContext()).inflate(R.layout.item_campo, pai, false);
        ((TextView) linha.findViewById(R.id.rotulo)).setText(rotulo);
        ((TextView) linha.findViewById(R.id.valor)).setText(valor == null || valor.length() == 0 ? "—" : valor);
        pai.addView(linha);
    }

    public static View movimentacao(ViewGroup pai, Movimentacao m, boolean mostrarEntidade) {
        View v = LayoutInflater.from(pai.getContext()).inflate(R.layout.item_movimentacao, pai, false);
        preencherMovimentacao(v, m, mostrarEntidade);
        return v;
    }

    public static void preencherMovimentacao(View v, Movimentacao m, boolean mostrarEntidade) {
        String acao = Formatos.acao(m.acao);
        if (mostrarEntidade) acao = Formatos.entidade(m.entidade) + " nº " + m.entidadeId + " · " + acao;
        ((TextView) v.findViewById(R.id.acao)).setText(acao);
        ((TextView) v.findViewById(R.id.data)).setText(Formatos.dataHora(m.data));
        ((TextView) v.findViewById(R.id.autor)).setText(v.getContext().getString(R.string.por_formato,
                m.usuario == null ? "" : m.usuario.nome));
        TextView alteracoes = v.findViewById(R.id.alteracoes);
        String texto = TextoMovimentacao.alteracoes(m);
        alteracoes.setText(texto);
        alteracoes.setVisibility(texto.isEmpty() ? View.GONE : View.VISIBLE);
    }
}
