package br.gestaofinanceira.app.ui.common;

import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Filter;

import java.util.ArrayList;
import java.util.List;

import br.gestaofinanceira.app.data.remote.dto.Rubrica;

/** Lista achatada de rubricas e subrubricas para um campo de seleção (ExposedDropdownMenu). */
public final class OpcoesRubrica {

    private final List<Rubrica> itens = new ArrayList<>();
    private final List<String> rotulos = new ArrayList<>();

    /**
     * @param rotuloNenhuma se não for null, vira a primeira opção (id null), por exemplo "Todas as rubricas".
     * @param soPrincipais  só rubricas de primeiro nível (para escolher a rubrica principal de uma subrubrica).
     */
    public OpcoesRubrica(List<Rubrica> rubricas, String rotuloNenhuma, boolean soPrincipais) {
        if (rotuloNenhuma != null) {
            itens.add(null);
            rotulos.add(rotuloNenhuma);
        }
        if (rubricas == null) return;
        for (Rubrica r : rubricas) {
            itens.add(r);
            rotulos.add(r.nome);
            if (!soPrincipais && r.subrubricas != null) {
                for (Rubrica s : r.subrubricas) {
                    itens.add(s);
                    rotulos.add("    ↳ " + s.nome);
                }
            }
        }
    }

    /**
     * O ArrayAdapter padrão filtra as opções pelo texto do campo: com uma opção já escolhida,
     * o menu mostraria só ela. Este adapter sempre mostra a lista inteira.
     */
    public void ligar(Context ctx, AutoCompleteTextView campo) {
        campo.setAdapter(new ArrayAdapter<String>(ctx, android.R.layout.simple_list_item_1, rotulos) {
            private final Filter semFiltro = new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence texto) {
                    FilterResults r = new FilterResults();
                    r.values = rotulos;
                    r.count = rotulos.size();
                    return r;
                }

                @Override
                protected void publishResults(CharSequence texto, FilterResults r) {
                    notifyDataSetChanged();
                }
            };

            @Override
            public Filter getFilter() {
                return semFiltro;
            }
        });
    }

    public String[] rotulos() {
        return rotulos.toArray(new String[0]);
    }

    public Long idNaPosicao(int posicao) {
        Rubrica r = posicao >= 0 && posicao < itens.size() ? itens.get(posicao) : null;
        return r == null ? null : r.id;
    }

    /** Texto a exibir no campo quando o id já está escolhido (sem o recuo das subrubricas). */
    public String rotulo(Long id) {
        for (int i = 0; i < itens.size(); i++) {
            Rubrica r = itens.get(i);
            if ((r == null && id == null) || (r != null && id != null && r.id == id)) {
                return rotulos.get(i).trim().replace("↳ ", "");
            }
        }
        return "";
    }

    public boolean vazia() {
        for (Rubrica r : itens) if (r != null) return false;
        return true;
    }
}
