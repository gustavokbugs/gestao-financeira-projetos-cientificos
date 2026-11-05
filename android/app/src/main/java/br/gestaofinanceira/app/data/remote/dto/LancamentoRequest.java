package br.gestaofinanceira.app.data.remote.dto;

import java.math.BigDecimal;

/** Corpo de POST /projetos/{id}/lancamentos e PUT /lancamentos/{id}. Campos null não são enviados. */
public class LancamentoRequest {
    public String tipo;
    public String data;
    public String descricao;
    public Long rubricaId;
    public BigDecimal quantidade;
    public BigDecimal valorUnitario;
    public String situacao;
    /** Na edição: o atualizadoEm conhecido, para a API detectar conflito (409). */
    public String atualizadoEm;
}
