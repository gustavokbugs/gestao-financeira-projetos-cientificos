package br.gestaofinanceira.app.data.remote.dto;

/** Alerta calculado pelo servidor (GP-10). nivel: ATENCAO ou CRITICO. */
public class Alerta {
    public static final String ATENCAO = "ATENCAO";
    public static final String CRITICO = "CRITICO";

    public Long rubricaId;
    public String rubrica;
    public String nivel;
    public String mensagem;
}
