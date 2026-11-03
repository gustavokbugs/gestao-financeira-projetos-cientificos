package br.gestaofinanceira.app.data.remote.dto;

import java.math.BigDecimal;
import java.util.List;

public class Rubrica {
    public long id;
    public String nome;
    public Long rubricaPaiId;
    public BigDecimal orcamento;
    public BigDecimal executado;
    public BigDecimal previsto;
    public BigDecimal saldo;
    public Double percentualComprometido;
    public Alerta alerta;
    public List<Rubrica> subrubricas;
}
