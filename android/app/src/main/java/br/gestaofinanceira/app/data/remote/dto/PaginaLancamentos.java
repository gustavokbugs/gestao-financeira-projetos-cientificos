package br.gestaofinanceira.app.data.remote.dto;

import java.util.List;

public class PaginaLancamentos {
    public List<Lancamento> itens;
    public int total;
    public int pagina;
    public int tamanho;
}
