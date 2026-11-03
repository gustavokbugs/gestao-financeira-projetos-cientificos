package br.gestaofinanceira.app.data.remote.dto;

import java.math.BigDecimal;
import java.util.List;

/** Resposta de GET /projetos/{id}/dashboard (GP-06). Todos os totais vêm calculados da API. */
public class Dashboard {
    public long projetoId;
    public BigDecimal receitaTotal;
    public BigDecimal despesaTotal;
    public BigDecimal saldoDisponivel;
    public BigDecimal orcamentoPrevisto;
    public BigDecimal orcamentoExecutado;
    public Double percentualExecutado;
    public List<Participacao> participacaoPorRubrica;
    public List<Rubrica> rubricas;
    public List<Alerta> alertas;
    public String atualizadoEm;

    public static class Participacao {
        public long rubricaId;
        public String rubrica;
        public BigDecimal valor;
        public double percentual;
    }
}
