package br.gestaofinanceira.app.data.remote;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.Reader;

import br.gestaofinanceira.app.data.remote.dto.ErroApi;
import okhttp3.ResponseBody;
import retrofit2.Response;

/** Converte falhas da API e de rede em mensagens para o usuário. */
public final class ApiErros {

    private static final Gson GSON = new Gson();

    private ApiErros() {
    }

    public static String mensagem(Response<?> resposta) {
        ResponseBody corpo = resposta.errorBody();
        if (corpo != null) {
            try (Reader leitor = corpo.charStream()) {
                ErroApi erro = GSON.fromJson(leitor, ErroApi.class);
                if (erro != null && erro.mensagem != null && !erro.mensagem.isEmpty()) {
                    return erro.mensagem;
                }
            } catch (Exception ignorado) {
                // corpo fora do formato esperado: usa a mensagem genérica abaixo
            }
        }
        switch (resposta.code()) {
            case 401:
                return "Sessão expirada. Faça login novamente.";
            case 403:
                return "Você não tem permissão para esta operação.";
            case 404:
                return "Registro não encontrado.";
            case 503:
                return "Serviço temporariamente indisponível. Tente novamente em instantes.";
            default:
                return "Erro no servidor (" + resposta.code() + "). Tente novamente.";
        }
    }

    public static String mensagem(Throwable falha) {
        if (falha instanceof IOException) {
            return "Sem conexão com o servidor. Verifique a internet e tente novamente.";
        }
        return "Erro inesperado ao processar a resposta do servidor.";
    }
}
