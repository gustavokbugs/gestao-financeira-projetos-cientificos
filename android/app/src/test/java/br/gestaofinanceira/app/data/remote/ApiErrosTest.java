package br.gestaofinanceira.app.data.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.net.SocketTimeoutException;

import okhttp3.MediaType;
import okhttp3.ResponseBody;
import retrofit2.Response;

public class ApiErrosTest {

    private static Response<Object> erro(int codigo, String json) {
        return Response.error(codigo, ResponseBody.create(json, MediaType.get("application/json")));
    }

    @Test
    public void usaMensagemDaApi() {
        assertEquals("Credenciais inválidas", ApiErros.mensagem(erro(401, "{\"mensagem\":\"Credenciais inválidas\"}")));
    }

    @Test
    public void errosDeValidacaoPorCampo() {
        ApiErros.Falha f = ApiErros.interpretar(erro(422,
                "{\"mensagem\":\"Dados do lançamento inválidos\",\"erros\":["
                        + "{\"campo\":\"data\",\"mensagem\":\"A data deve estar na vigência do projeto\"},"
                        + "{\"campo\":\"data\",\"mensagem\":\"segunda mensagem\"},"
                        + "{\"campo\":\"rubricaId\",\"mensagem\":\"Informe a rubrica da despesa\"}]}"));
        assertEquals(422, f.codigo);
        assertEquals("Dados do lançamento inválidos", f.mensagem);
        assertEquals(2, f.campos.size());
        assertEquals("A data deve estar na vigência do projeto", f.campos.get("data"));
        assertEquals("Informe a rubrica da despesa", f.campos.get("rubricaId"));
    }

    @Test
    public void conflitoDeEdicao() {
        ApiErros.Falha f = ApiErros.interpretar(erro(409, "{\"mensagem\":\"Alterado por outra pessoa\"}"));
        assertTrue(f.conflito());
    }

    @Test
    public void corpoForaDoFormatoUsaMensagemPadrao() {
        assertEquals("Você não tem permissão para esta operação.", ApiErros.mensagem(erro(403, "<html>")));
        assertTrue(ApiErros.mensagem(erro(503, "")).contains("indisponível"));
        assertTrue(ApiErros.mensagem(erro(500, "{}")).contains("500"));
    }

    @Test
    public void falhasDeRede() {
        assertTrue(ApiErros.mensagem(new SocketTimeoutException()).contains("demorou"));
        assertTrue(ApiErros.mensagem(new IOException("rede")).contains("Sem conexão"));
        assertTrue(ApiErros.mensagem(new IllegalStateException()).contains("inesperado"));
    }
}
