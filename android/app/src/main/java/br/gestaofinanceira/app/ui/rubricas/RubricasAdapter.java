package br.gestaofinanceira.app.ui.rubricas;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import br.gestaofinanceira.app.R;
import br.gestaofinanceira.app.data.remote.dto.Rubrica;
import br.gestaofinanceira.app.ui.common.Cores;
import br.gestaofinanceira.app.ui.common.Formatos;

/** Lista rubricas e subrubricas com orçado x executado, saldo e alerta (GP-03, GP-06 e GP-10). */
public class RubricasAdapter extends RecyclerView.Adapter<RubricasAdapter.ViewHolder> {

    public interface AoSelecionar {
        void selecionada(Rubrica rubrica);
    }

    private final List<Rubrica> itens = new ArrayList<>();
    private final AoSelecionar aoSelecionar;
    private Map<Long, Double> participacao;

    public RubricasAdapter() {
        this(null);
    }

    public RubricasAdapter(AoSelecionar aoSelecionar) {
        this.aoSelecionar = aoSelecionar;
    }

    public void atualizar(List<Rubrica> rubricas, Map<Long, Double> participacaoPorRubrica) {
        itens.clear();
        for (Rubrica r : rubricas) {
            itens.add(r);
            if (r.subrubricas != null) itens.addAll(r.subrubricas);
        }
        participacao = participacaoPorRubrica;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_rubrica, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        Rubrica r = itens.get(position);
        Context ctx = h.itemView.getContext();
        boolean sub = r.rubricaPaiId != null;

        int recuo = sub ? ctx.getResources().getDimensionPixelSize(R.dimen.recuo_subrubrica) : 0;
        h.itemView.setPaddingRelative(recuo, h.itemView.getPaddingTop(), 0, h.itemView.getPaddingBottom());

        h.nome.setText(r.nome);
        h.valores.setText(ctx.getString(R.string.rubrica_valores_formato,
                Formatos.moeda(r.executado), Formatos.moeda(r.previsto), Formatos.moeda(r.orcamento)));
        h.saldo.setText(ctx.getString(R.string.rubrica_saldo_formato, Formatos.moeda(r.saldo)));

        Double part = participacao == null ? null : participacao.get(r.id);
        if (!sub && part != null) {
            h.participacao.setVisibility(View.VISIBLE);
            h.participacao.setText(ctx.getString(R.string.participacao_formato, Formatos.percentual(part)));
        } else {
            h.participacao.setVisibility(View.GONE);
        }

        double percentual = r.percentualComprometido == null ? 0 : r.percentualComprometido;
        h.progresso.setProgressCompat((int) Math.min(100, Math.round(percentual)), false);

        int cor = Cores.alerta(ctx, r.alerta);
        h.progresso.setIndicatorColor(cor);

        if (r.alerta != null) {
            h.alerta.setVisibility(View.VISIBLE);
            h.alerta.setText(r.alerta.mensagem);
            h.alerta.setTextColor(cor);
        } else {
            h.alerta.setVisibility(View.GONE);
        }

        if (aoSelecionar != null) {
            h.itemView.setOnClickListener(v -> aoSelecionar.selecionada(r));
        } else {
            h.itemView.setOnClickListener(null);
            h.itemView.setClickable(false);
        }
    }

    @Override
    public int getItemCount() {
        return itens.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView nome;
        final TextView valores;
        final TextView saldo;
        final TextView participacao;
        final TextView alerta;
        final LinearProgressIndicator progresso;

        ViewHolder(View view) {
            super(view);
            nome = view.findViewById(R.id.nome);
            valores = view.findViewById(R.id.valores);
            saldo = view.findViewById(R.id.saldo);
            participacao = view.findViewById(R.id.participacao);
            alerta = view.findViewById(R.id.alerta);
            progresso = view.findViewById(R.id.progresso);
        }
    }
}
