package br.gestaofinanceira.app.ui.projetos;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import br.gestaofinanceira.app.R;
import br.gestaofinanceira.app.data.remote.dto.Projeto;
import br.gestaofinanceira.app.ui.common.Formatos;

class ProjetosAdapter extends RecyclerView.Adapter<ProjetosAdapter.ViewHolder> {

    interface AoSelecionar {
        void selecionado(Projeto projeto);
    }

    private final List<Projeto> projetos = new ArrayList<>();
    private final AoSelecionar aoSelecionar;

    ProjetosAdapter(AoSelecionar aoSelecionar) {
        this.aoSelecionar = aoSelecionar;
    }

    void atualizar(List<Projeto> novos) {
        projetos.clear();
        projetos.addAll(novos);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_projeto, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Projeto projeto = projetos.get(position);
        holder.titulo.setText(projeto.titulo);
        holder.codigo.setText(projeto.codigo);
        holder.papel.setText(Formatos.papel(projeto.papel));
        holder.vigencia.setText(holder.itemView.getContext().getString(R.string.vigencia_formato,
                Formatos.data(projeto.vigenciaInicio), Formatos.data(projeto.vigenciaFim)));
        holder.orcamento.setText(holder.itemView.getContext().getString(R.string.orcamento_aprovado_formato,
                Formatos.moeda(projeto.orcamentoAprovado)));
        holder.itemView.setOnClickListener(v -> aoSelecionar.selecionado(projeto));
    }

    @Override
    public int getItemCount() {
        return projetos.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView titulo;
        final TextView codigo;
        final TextView papel;
        final TextView vigencia;
        final TextView orcamento;

        ViewHolder(View view) {
            super(view);
            titulo = view.findViewById(R.id.titulo);
            codigo = view.findViewById(R.id.codigo);
            papel = view.findViewById(R.id.papel);
            vigencia = view.findViewById(R.id.vigencia);
            orcamento = view.findViewById(R.id.orcamento);
        }
    }
}
