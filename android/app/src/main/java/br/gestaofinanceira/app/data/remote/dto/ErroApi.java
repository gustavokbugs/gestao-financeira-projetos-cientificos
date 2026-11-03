package br.gestaofinanceira.app.data.remote.dto;

import java.util.List;

/** Corpo de erro padrão da API: {"mensagem": "...", "erros": [{"campo": "...", "mensagem": "..."}]}. */
public class ErroApi {
    public String mensagem;
    public List<ErroCampo> erros;

    public static class ErroCampo {
        public String campo;
        public String mensagem;
    }
}
