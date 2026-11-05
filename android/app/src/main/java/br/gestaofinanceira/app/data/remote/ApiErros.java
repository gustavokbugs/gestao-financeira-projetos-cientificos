package br.gestaofinanceira.app.data.remote;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.Reader;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import br.gestaofinanceira.app.data.remote.dto.ErroApi;
import okhttp3.ResponseBody;
import retrofit2.Response;

/** Converte falhas da API e de rede em mensagens para o usuário. */
public final class ApiErros {

    private static final Gson GSON = new Gson();

    private ApiErros() {
    }

    /** Resposta de erro já interpretada. O corpo de erro só pode ser lido uma vez, por isso tudo sai daqui. */
    public static final class Falha {
        public final int codigo;
        public final String mensagem;
        /** Erros de validação (422) por campo, na ordem enviada pela API. */
        public final Map<String, String> campos;

        Falha(int codigo, String mensagem, Map<String, String> campos) {
            this.codigo = codigo;
            this.mensagem = mensagem;
            this.campos = Collections.unmodifiableMap(campos);
        }

        public boolean conflito() {
            return codigo == 409;
        }
    }

    public static Falha interpretar(Response<?> resposta) {
        ErroApi erro = null;
        ResponseBody corpo = resposta.errorBody();
        if (corpo != null) {
            try (Reader leitor = corpo.charStream()) {
                erro = GSON.fromJson(leitor, ErroApi.class);
            } catch (Exception ignorado) {
                // corpo fora do formato esperado: usa a mensagem genérica abaixo
            }
        }
        Map<String, String> campos = new LinkedHashMap<>();
        if (erro != null && erro.erros != null) {
            for (ErroApi.ErroCampo c : erro.erros) {
                // Se a API mandar duas mensagens para o mesmo campo, mostra a primeira.
                if (c != null && c.campo != null && c.mensagem != null && !campos.containsKey(c.campo)) {
                    campos.put(c.campo, c.mensagem);
                }
            }
        }
        String mensagem = erro != null && erro.mensagem != null && !erro.mensagem.isEmpty()
                ? erro.mensagem
                : mensagemPadrao(resposta.code());
        return new Falha(resposta.code(), mensagem, campos);
    }

    public static String mensagem(Response<?> resposta) {
        return interpretar(resposta).mensagem;
    }

    static String mensagemPadrao(int codigo) {
        switch (codigo) {
            case 401:
                return "Sessão expirada. Faça login novamente.";
            case 403:
                return "Você não tem permissão para esta operação.";
            case 404:
                return "Registro não encontrado.";
            case 409:
                return "O registro foi alterado por outra pessoa. Recarregue e tente novamente.";
            case 413:
                return "Arquivo grande demais. O limite é 10 MB.";
            case 415:
                return "Tipo de arquivo não aceito. Envie JPEG, PNG ou PDF.";
            case 422:
                return "Verifique os campos destacados.";
            case 429:
                return "Muitas tentativas. Aguarde alguns minutos e tente novamente.";
            case 501:
                return "Recurso indisponível no servidor.";
            case 503:
                return "Serviço temporariamente indisponível. Tente novamente em instantes.";
            default:
                return "Erro no servidor (" + codigo + "). Tente novamente.";
        }
    }

    public static String mensagem(Throwable falha) {
        if (falha instanceof SocketTimeoutException
                || (falha instanceof InterruptedIOException && "timeout".equals(falha.getMessage()))) {
            return "O servidor demorou para responder. Tente novamente.";
        }
        if (falha instanceof IOException) {
            return "Sem conexão com o servidor. Verifique a internet e tente novamente.";
        }
        return "Erro inesperado ao processar a resposta do servidor.";
    }
}
