package br.gestaofinanceira.app.ui.common;

import android.app.Activity;

import br.gestaofinanceira.app.data.remote.ApiErros;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Envolve o Callback do Retrofit: ignora respostas que chegam depois de a tela fechar e
 * separa sucesso, erro da API (com o corpo já interpretado) e falha de rede.
 */
public final class Chamadas {

    public interface Sucesso<T> {
        void ok(T corpo);
    }

    public interface Erro {
        void falhou(String mensagem, ApiErros.Falha falhaApi);
    }

    private Chamadas() {
    }

    public static <T> Call<T> enfileirar(Activity tela, Call<T> chamada, Sucesso<T> sucesso, Erro erro) {
        chamada.enqueue(new Callback<T>() {
            @Override
            public void onResponse(Call<T> call, Response<T> resposta) {
                if (tela.isFinishing() || tela.isDestroyed()) return;
                if (resposta.isSuccessful()) {
                    sucesso.ok(resposta.body());
                } else {
                    ApiErros.Falha falha = ApiErros.interpretar(resposta);
                    erro.falhou(falha.mensagem, falha);
                }
            }

            @Override
            public void onFailure(Call<T> call, Throwable falha) {
                if (call.isCanceled() || tela.isFinishing() || tela.isDestroyed()) return;
                erro.falhou(ApiErros.mensagem(falha), null);
            }
        });
        return chamada;
    }
}
