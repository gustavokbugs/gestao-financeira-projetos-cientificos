package br.gestaofinanceira.app.ui.common;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** GP-01: espelha a tabela de perfis de docs/api-endpoints.md. */
public class PermissoesTest {

    @Test
    public void coordenadorPodeTudo() {
        Permissoes p = new Permissoes(Permissoes.COORDENADOR);
        assertTrue(p.podeLancar());
        assertTrue(p.podeGerenciar());
    }

    @Test
    public void gestorLancaMasNaoGerencia() {
        Permissoes p = new Permissoes(Permissoes.GESTOR);
        assertTrue(p.podeLancar());
        assertFalse(p.podeGerenciar());
    }

    @Test
    public void consultaSoLe() {
        Permissoes p = new Permissoes(Permissoes.CONSULTA);
        assertFalse(p.podeLancar());
        assertFalse(p.podeGerenciar());
    }

    @Test
    public void papelDesconhecidoNaoPodeNada() {
        assertFalse(new Permissoes(null).podeLancar());
        assertFalse(new Permissoes("admin").podeGerenciar());
    }
}
