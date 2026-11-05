package br.gestaofinanceira.app.ui.common;

import android.content.Context;
import android.graphics.Paint;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import br.gestaofinanceira.app.R;
import br.gestaofinanceira.app.data.remote.dto.Alerta;
import br.gestaofinanceira.app.data.remote.dto.Lancamento;

/** Cores por tipo, situação e nível de alerta, iguais em todas as telas. */
public final class Cores {

    private Cores() {
    }

    public static int situacao(Context ctx, String situacao) {
        int cor;
        if (Lancamento.REALIZADO.equals(situacao)) cor = R.color.normal;
        else if (Lancamento.PREVISTO.equals(situacao)) cor = R.color.alerta_atencao;
        else cor = R.color.cancelado;
        return ContextCompat.getColor(ctx, cor);
    }

    public static int tipo(Context ctx, String tipo) {
        return ContextCompat.getColor(ctx, Lancamento.RECEITA.equals(tipo) ? R.color.receita : R.color.despesa);
    }

    public static int alerta(Context ctx, Alerta alerta) {
        if (alerta != null && Alerta.CRITICO.equals(alerta.nivel)) return ContextCompat.getColor(ctx, R.color.alerta_critico);
        if (alerta != null && Alerta.ATENCAO.equals(alerta.nivel)) return ContextCompat.getColor(ctx, R.color.alerta_atencao);
        return ContextCompat.getColor(ctx, R.color.normal);
    }

    /** Etiqueta colorida de situação (texto e fundo translúcido na mesma cor). */
    public static void etiquetaSituacao(TextView etiqueta, String situacao) {
        int cor = situacao(etiqueta.getContext(), situacao);
        etiqueta.setText(Formatos.situacao(situacao));
        etiqueta.setTextColor(cor);
        etiqueta.getBackground().mutate().setTint((cor & 0x00FFFFFF) | 0x26000000);
    }

    /** Valor riscado quando o lançamento foi cancelado (não entra nos totais). */
    public static void riscar(TextView texto, boolean riscar) {
        int flags = texto.getPaintFlags();
        texto.setPaintFlags(riscar ? flags | Paint.STRIKE_THRU_TEXT_FLAG : flags & ~Paint.STRIKE_THRU_TEXT_FLAG);
    }
}
