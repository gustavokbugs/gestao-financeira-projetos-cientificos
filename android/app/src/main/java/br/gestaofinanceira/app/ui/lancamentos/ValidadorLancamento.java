package br.gestaofinanceira.app.ui.lancamentos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import br.gestaofinanceira.app.data.remote.dto.Lancamento;

/**
 * GP-09: validação local do lançamento, com as mesmas regras da API (que continua validando).
 * Java puro para poder ser testado sem aparelho. Chaves = nomes dos campos na API.
 */
public final class ValidadorLancamento {

    /** Limites das colunas do banco: NUMERIC(12,3) na quantidade e NUMERIC(14,2) nos valores. */
    static final BigDecimal MAXIMO_QUANTIDADE = new BigDecimal("999999999.999");
    static final BigDecimal MAXIMO_VALOR = new BigDecimal("999999999999.99");

    private ValidadorLancamento() {
    }

    public static Map<String, String> validar(String tipo, String data, String descricao, Long rubricaId,
                                              BigDecimal quantidade, BigDecimal valorUnitario,
                                              String vigenciaInicio, String vigenciaFim) {
        Map<String, String> erros = new LinkedHashMap<>();
        if (!Lancamento.RECEITA.equals(tipo) && !Lancamento.DESPESA.equals(tipo)) {
            erros.put("tipo", "Escolha receita ou despesa");
        }
        LocalDate dia = null;
        try {
            dia = data == null || data.isEmpty() ? null : LocalDate.parse(data);
        } catch (Exception ignorado) {
            // tratado abaixo
        }
        if (dia == null) {
            erros.put("data", "Informe a data");
        } else if (foraDaVigencia(dia, vigenciaInicio, vigenciaFim)) {
            erros.put("data", "A data deve estar na vigência do projeto");
        }
        if (descricao == null || descricao.trim().isEmpty()) {
            erros.put("descricao", "Informe a descrição");
        }
        if (Lancamento.DESPESA.equals(tipo) && rubricaId == null) {
            erros.put("rubricaId", "Informe a rubrica da despesa");
        }
        if (quantidade == null) {
            erros.put("quantidade", "Informe a quantidade");
        } else if (quantidade.signum() <= 0) {
            erros.put("quantidade", "Quantidade deve ser maior que zero");
        } else if (quantidade.compareTo(MAXIMO_QUANTIDADE) > 0) {
            erros.put("quantidade", "Quantidade muito grande");
        }
        if (valorUnitario == null) {
            erros.put("valorUnitario", "Informe o valor unitário");
        } else if (valorUnitario.signum() <= 0) {
            erros.put("valorUnitario", "Valor unitário deve ser maior que zero");
        } else if (valorUnitario.compareTo(MAXIMO_VALOR) > 0) {
            erros.put("valorUnitario", "Valor muito grande");
        }
        return erros;
    }

    private static boolean foraDaVigencia(LocalDate dia, String inicio, String fim) {
        try {
            if (inicio != null && dia.isBefore(LocalDate.parse(inicio))) return true;
            if (fim != null && dia.isAfter(LocalDate.parse(fim))) return true;
        } catch (Exception ignorado) {
            // vigência desconhecida: a API valida
        }
        return false;
    }
}
