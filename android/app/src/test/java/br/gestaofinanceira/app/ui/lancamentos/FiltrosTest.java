package br.gestaofinanceira.app.ui.lancamentos;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Map;

public class FiltrosTest {

    @Test
    public void semFiltrosSoPaginacao() {
        Filtros f = new Filtros();
        assertFalse(f.algumAtivo());
        Map<String, String> q = f.paraPagina(2, 20);
        assertEquals(2, q.size());
        assertEquals("2", q.get("pagina"));
        assertEquals("20", q.get("tamanho"));
    }

    @Test
    public void filtrosCombinados() {
        Filtros f = new Filtros();
        f.tipo = "DESPESA";
        f.rubricaId = 3L;
        f.situacao = "REALIZADO";
        f.de = "2026-04-01";
        f.ate = "2026-04-30";
        f.busca = "  diárias ";
        assertTrue(f.algumAtivo());
        Map<String, String> q = f.paraConsulta();
        assertEquals("DESPESA", q.get("tipo"));
        assertEquals("3", q.get("rubricaId"));
        assertEquals("REALIZADO", q.get("situacao"));
        assertEquals("2026-04-01", q.get("de"));
        assertEquals("2026-04-30", q.get("ate"));
        assertEquals("diárias", q.get("busca"));
    }

    @Test
    public void buscaEmBrancoIgnorada() {
        Filtros f = new Filtros();
        f.busca = "   ";
        assertFalse(f.paraConsulta().containsKey("busca"));
    }

    @Test
    public void limpar() {
        Filtros f = new Filtros();
        f.tipo = "RECEITA";
        f.rubricaId = 1L;
        f.limpar();
        assertFalse(f.algumAtivo());
    }
}
