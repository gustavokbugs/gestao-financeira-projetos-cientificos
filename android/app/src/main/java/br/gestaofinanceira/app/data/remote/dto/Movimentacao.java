package br.gestaofinanceira.app.data.remote.dto;

import com.google.gson.JsonElement;

import java.util.List;

/** Item do histórico (GP-07 e GP-09): quem fez o quê, quando e quais campos mudaram. */
public class Movimentacao {
    public long id;
    public long projetoId;
    public String entidade;
    public long entidadeId;
    public String acao;
    public Lancamento.Autor usuario;
    public String data;
    public List<Alteracao> alteracoes;

    public static class Alteracao {
        public String campo;
        /** Pode ser texto, número, nulo ou lista (responsáveis), por isso JsonElement. */
        public JsonElement de;
        public JsonElement para;
    }
}
