package br.gestaofinanceira.app.ui.historico;

import static org.junit.Assert.assertEquals;

import com.google.gson.Gson;

import org.junit.Test;

import br.gestaofinanceira.app.data.remote.dto.Movimentacao;

public class TextoMovimentacaoTest {

    private static Movimentacao ler(String json) {
        return new Gson().fromJson(json, Movimentacao.class);
    }

    @Test
    public void alteracaoDeSituacaoEValor() {
        Movimentacao m = ler("{\"acao\":\"ALTERACAO\",\"alteracoes\":["
                + "{\"campo\":\"situacao\",\"de\":\"PREVISTO\",\"para\":\"REALIZADO\"},"
                + "{\"campo\":\"valorTotal\",\"de\":100,\"para\":null}]}");
        String texto = TextoMovimentacao.alteracoes(m).replace(' ', ' ');
        assertEquals("Situação: Previsto → Realizado\nValor total: R$ 100,00 → —", texto);
    }

    @Test
    public void anexoMostraSoONome() {
        Movimentacao m = ler("{\"acao\":\"ANEXO\",\"alteracoes\":[{\"campo\":\"comprovante\",\"de\":null,\"para\":\"nota.png\"}]}");
        assertEquals("Comprovante: nota.png", TextoMovimentacao.alteracoes(m));
    }

    @Test
    public void listaDeResponsaveis() {
        Movimentacao m = ler("{\"alteracoes\":[{\"campo\":\"responsaveis\",\"de\":[{\"nome\":\"Ana\",\"funcao\":\"Coord.\"}],"
                + "\"para\":[{\"nome\":\"Ana\",\"funcao\":\"\"},{\"nome\":\"Bia\"}]}]}");
        assertEquals("Responsáveis: Ana (Coord.) → Ana, Bia", TextoMovimentacao.alteracoes(m));
    }

    @Test
    public void semAlteracoes() {
        assertEquals("", TextoMovimentacao.alteracoes(ler("{\"acao\":\"CRIACAO\",\"alteracoes\":[]}")));
    }
}
