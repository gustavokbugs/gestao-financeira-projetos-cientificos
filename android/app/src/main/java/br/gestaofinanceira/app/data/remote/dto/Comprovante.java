package br.gestaofinanceira.app.data.remote.dto;

public class Comprovante {
    public long id;
    public long lancamentoId;
    public String nomeArquivo;
    public String tipo;
    public long tamanho;
    public Lancamento.Autor enviadoPor;
    public String enviadoEm;
}
