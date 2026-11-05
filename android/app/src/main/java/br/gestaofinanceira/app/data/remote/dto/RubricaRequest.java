package br.gestaofinanceira.app.data.remote.dto;

import java.math.BigDecimal;

public class RubricaRequest {
    public String nome;
    public BigDecimal orcamento;
    /** Só na criação; null = rubrica principal. */
    public Long rubricaPaiId;
}
