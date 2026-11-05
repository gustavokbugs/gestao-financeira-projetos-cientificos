package br.gestaofinanceira.app.ui.lancamentos;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.math.BigDecimal;
import java.util.Map;

public class ValidadorLancamentoTest {

    private static final String INICIO = "2026-03-01";
    private static final String FIM = "2027-02-28";

    private static Map<String, String> validar(String tipo, String data, String desc, Long rubrica, String qtd, String valor) {
        return ValidadorLancamento.validar(tipo, data, desc, rubrica,
                qtd == null ? null : new BigDecimal(qtd), valor == null ? null : new BigDecimal(valor), INICIO, FIM);
    }

    @Test
    public void despesaValida() {
        assertTrue(validar("DESPESA", "2026-04-15", "Diárias", 1L, "4", "320").isEmpty());
    }

    @Test
    public void receitaNaoPrecisaDeRubrica() {
        assertTrue(validar("RECEITA", "2026-03-01", "Repasse", null, "1", "10000").isEmpty());
    }

    @Test
    public void despesaExigeRubrica() {
        assertEquals("Informe a rubrica da despesa", validar("DESPESA", "2026-04-15", "x", null, "1", "1").get("rubricaId"));
    }

    @Test
    public void dataForaDaVigencia() {
        assertTrue(validar("DESPESA", "2026-02-28", "x", 1L, "1", "1").containsKey("data"));
        assertTrue(validar("DESPESA", "2027-03-01", "x", 1L, "1", "1").containsKey("data"));
        assertTrue(validar("DESPESA", "2027-02-28", "x", 1L, "1", "1").isEmpty());
    }

    @Test
    public void camposObrigatoriosEValoresPositivos() {
        Map<String, String> erros = validar(null, null, "  ", null, "0", "-1");
        assertTrue(erros.containsKey("tipo"));
        assertTrue(erros.containsKey("data"));
        assertTrue(erros.containsKey("descricao"));
        assertTrue(erros.containsKey("quantidade"));
        assertTrue(erros.containsKey("valorUnitario"));
        assertTrue(validar("DESPESA", "2026-04-15", "x", 1L, null, null).containsKey("valorUnitario"));
    }

    @Test
    public void limitesDoBanco() {
        assertTrue(validar("DESPESA", "2026-04-15", "x", 1L, "1000000000", "1").containsKey("quantidade"));
        assertTrue(validar("DESPESA", "2026-04-15", "x", 1L, "1", "1000000000000").containsKey("valorUnitario"));
    }
}
