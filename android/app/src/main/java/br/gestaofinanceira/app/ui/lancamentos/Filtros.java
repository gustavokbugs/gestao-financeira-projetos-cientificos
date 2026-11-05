package br.gestaofinanceira.app.ui.lancamentos;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GP-07: filtros combináveis da lista de lançamentos. O projeto é a própria tela; período,
 * rubrica (inclui as subrubricas, no servidor), situação, tipo e busca viram query params.
 * Os mesmos filtros servem para o relatório (GP-08).
 */
public class Filtros implements Serializable {

    public String de;
    public String ate;
    public Long rubricaId;
    public String rubricaNome;
    public String situacao;
    public String tipo;
    public String busca;

    public boolean algumAtivo() {
        return de != null || ate != null || rubricaId != null || situacao != null || tipo != null
                || (busca != null && !busca.isEmpty());
    }

    public void limpar() {
        de = ate = situacao = tipo = busca = rubricaNome = null;
        rubricaId = null;
    }

    public Map<String, String> paraConsulta() {
        Map<String, String> q = new LinkedHashMap<>();
        if (tipo != null) q.put("tipo", tipo);
        if (rubricaId != null) q.put("rubricaId", String.valueOf(rubricaId));
        if (situacao != null) q.put("situacao", situacao);
        if (de != null) q.put("de", de);
        if (ate != null) q.put("ate", ate);
        if (busca != null && !busca.trim().isEmpty()) q.put("busca", busca.trim());
        return q;
    }

    public Map<String, String> paraPagina(int pagina, int tamanho) {
        Map<String, String> q = paraConsulta();
        q.put("pagina", String.valueOf(pagina));
        q.put("tamanho", String.valueOf(tamanho));
        return q;
    }
}
