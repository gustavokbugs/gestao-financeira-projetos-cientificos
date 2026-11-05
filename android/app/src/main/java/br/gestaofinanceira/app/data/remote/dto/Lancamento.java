package br.gestaofinanceira.app.data.remote.dto;

import java.math.BigDecimal;
import java.util.List;

/** Lançamento (GP-04). valorTotal é calculado pelo servidor. */
public class Lancamento {
    public static final String RECEITA = "RECEITA";
    public static final String DESPESA = "DESPESA";
    public static final String PREVISTO = "PREVISTO";
    public static final String REALIZADO = "REALIZADO";
    public static final String CANCELADO = "CANCELADO";

    public long id;
    public long projetoId;
    public String tipo;
    public String data;
    public String descricao;
    public Long rubricaId;
    public RubricaResumo rubrica;
    public BigDecimal quantidade;
    public BigDecimal valorUnitario;
    public BigDecimal valorTotal;
    public String situacao;
    public Autor criadoPor;
    public String criadoEm;
    public Autor atualizadoPor;
    public String atualizadoEm;
    public int quantidadeComprovantes;

    /** Só no detalhe (GET /lancamentos/{id}). */
    public List<Comprovante> comprovantes;
    public List<Movimentacao> historico;

    /** Só na resposta de criar/editar: alerta da rubrica depois do lançamento (GP-10). */
    public Alerta alertaRubrica;

    public static class RubricaResumo {
        public long id;
        public String nome;
        public Long rubricaPaiId;
    }

    public static class Autor {
        public long id;
        public String nome;
    }
}
