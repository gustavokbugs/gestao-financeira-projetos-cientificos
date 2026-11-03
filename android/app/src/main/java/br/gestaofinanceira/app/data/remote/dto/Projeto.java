package br.gestaofinanceira.app.data.remote.dto;

import java.math.BigDecimal;
import java.util.List;

public class Projeto {
    public long id;
    public String codigo;
    public String titulo;
    public String descricao;
    public String vigenciaInicio;
    public String vigenciaFim;
    public String fonteRecursos;
    public List<Responsavel> responsaveis;
    public BigDecimal orcamentoAprovado;
    /** Papel do usuário logado neste projeto: coordenador, gestor ou consulta. */
    public String papel;

    public static class Responsavel {
        public String nome;
        public String funcao;
    }
}
