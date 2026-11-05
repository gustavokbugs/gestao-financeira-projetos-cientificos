package br.gestaofinanceira.app.ui.lancamentos;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import br.gestaofinanceira.app.R;
import br.gestaofinanceira.app.data.remote.dto.Lancamento;
import br.gestaofinanceira.app.ui.common.Cores;
import br.gestaofinanceira.app.ui.common.Formatos;

class LancamentosAdapter extends RecyclerView.Adapter<LancamentosAdapter.ViewHolder> {

    interface AoSelecionar {
        void selecionado(Lancamento lancamento);
    }

    private final List<Lancamento> itens = new ArrayList<>();
    private final AoSelecionar aoSelecionar;

    LancamentosAdapter(AoSelecionar aoSelecionar) {
        this.aoSelecionar = aoSelecionar;
    }

    void substituir(List<Lancamento> novos) {
        itens.clear();
        itens.addAll(novos);
        notifyDataSetChanged();
    }

    void acrescentar(List<Lancamento> mais) {
        int inicio = itens.size();
        itens.addAll(mais);
        notifyItemRangeInserted(inicio, mais.size());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_lancamento, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        Lancamento l = itens.get(position);
        Context ctx = h.itemView.getContext();
        boolean receita = Lancamento.RECEITA.equals(l.tipo);
        boolean cancelado = Lancamento.CANCELADO.equals(l.situacao);

        h.descricao.setText(l.descricao);
        h.valor.setText((receita ? "+ " : "− ") + Formatos.moeda(l.valorTotal));
        h.valor.setTextColor(cancelado ? ContextCompat.getColor(ctx, R.color.cancelado) : Cores.tipo(ctx, l.tipo));
        Cores.riscar(h.valor, cancelado);

        String rubrica = l.rubrica != null ? l.rubrica.nome : ctx.getString(R.string.sem_rubrica);
        h.detalhes.setText(ctx.getString(R.string.lancamento_detalhes_formato,
                Formatos.data(l.data), Formatos.tipo(l.tipo), rubrica));
        Cores.etiquetaSituacao(h.situacao, l.situacao);

        if (l.quantidadeComprovantes > 0) {
            h.anexos.setVisibility(View.VISIBLE);
            h.anexos.setText(ctx.getResources().getQuantityString(R.plurals.comprovantes, l.quantidadeComprovantes, l.quantidadeComprovantes));
        } else {
            h.anexos.setVisibility(View.GONE);
        }
        h.itemView.setOnClickListener(v -> aoSelecionar.selecionado(l));
    }

    @Override
    public int getItemCount() {
        return itens.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView descricao;
        final TextView valor;
        final TextView detalhes;
        final TextView situacao;
        final TextView anexos;

        ViewHolder(View view) {
            super(view);
            descricao = view.findViewById(R.id.descricao);
            valor = view.findViewById(R.id.valor);
            detalhes = view.findViewById(R.id.detalhes);
            situacao = view.findViewById(R.id.situacao);
            anexos = view.findViewById(R.id.anexos);
        }
    }
}
