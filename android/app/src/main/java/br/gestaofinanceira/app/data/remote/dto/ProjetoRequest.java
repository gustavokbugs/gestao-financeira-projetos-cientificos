package br.gestaofinanceira.app.data.remote.dto;

import java.math.BigDecimal;
import java.util.List;

public class ProjetoRequest {
    public String codigo;
    public String titulo;
    public String descricao;
    public String vigenciaInicio;
    public String vigenciaFim;
    public String fonteRecursos;
    public List<Projeto.Responsavel> responsaveis;
    public BigDecimal orcamentoAprovado;
}
