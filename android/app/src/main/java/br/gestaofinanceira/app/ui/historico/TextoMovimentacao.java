package br.gestaofinanceira.app.ui.historico;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import br.gestaofinanceira.app.data.remote.dto.Movimentacao;
import br.gestaofinanceira.app.ui.common.Formatos;

/** Texto legível das alterações registradas no histórico (GP-09): "Situação: Realizado → Cancelado". */
public final class TextoMovimentacao {

    private TextoMovimentacao() {
    }

    public static String alteracoes(Movimentacao m) {
        if (m.alteracoes == null || m.alteracoes.isEmpty()) return "";
        List<String> linhas = new ArrayList<>();
        for (Movimentacao.Alteracao a : m.alteracoes) {
            if ("comprovante".equals(a.campo)) {
                linhas.add(Formatos.campo(a.campo) + ": " + valor(a.campo, a.para));
            } else {
                linhas.add(Formatos.campo(a.campo) + ": " + valor(a.campo, a.de) + " → " + valor(a.campo, a.para));
            }
        }
        return String.join("\n", linhas);
    }

    static String valor(String campo, JsonElement v) {
        if (v == null || v.isJsonNull()) return "—";
        if (v.isJsonArray()) return lista(v.getAsJsonArray());
        if (!v.isJsonPrimitive()) return v.toString();
        String texto = v.getAsString();
        switch (campo == null ? "" : campo) {
            case "valorUnitario":
            case "valorTotal":
            case "orcamento":
            case "orcamentoAprovado":
                try {
                    return Formatos.moeda(new BigDecimal(texto));
                } catch (NumberFormatException e) {
                    return texto;
                }
            case "data":
            case "vigenciaInicio":
            case "vigenciaFim":
                return Formatos.data(texto);
            case "situacao":
                return Formatos.situacao(texto);
            case "tipo":
                return Formatos.tipo(texto);
            case "rubricaId":
                return "nº " + texto;
            default:
                return texto;
        }
    }

    /** Lista de responsáveis: [{nome, funcao}] → "Fulana (Coordenadora), Beltrano". */
    private static String lista(JsonArray itens) {
        List<String> nomes = new ArrayList<>();
        for (JsonElement e : itens) {
            if (e.isJsonObject()) {
                JsonObject o = e.getAsJsonObject();
                String nome = o.has("nome") && !o.get("nome").isJsonNull() ? o.get("nome").getAsString() : "";
                String funcao = o.has("funcao") && !o.get("funcao").isJsonNull() ? o.get("funcao").getAsString() : "";
                nomes.add(funcao.isEmpty() ? nome : nome + " (" + funcao + ")");
            } else {
                nomes.add(e.isJsonPrimitive() ? e.getAsString() : e.toString());
            }
        }
        return nomes.isEmpty() ? "—" : String.join(", ", nomes);
    }
}
