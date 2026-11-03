package br.gestaofinanceira.app;

import android.app.Application;
import android.content.Context;

import br.gestaofinanceira.app.data.local.SessionManager;
import br.gestaofinanceira.app.data.remote.ApiClient;
import br.gestaofinanceira.app.data.remote.ApiService;

/** Guarda as dependências compartilhadas do app (sessão e cliente da API). */
public class GestaoFinanceiraApp extends Application {

    private SessionManager sessao;
    private ApiService api;

    public static GestaoFinanceiraApp de(Context context) {
        return (GestaoFinanceiraApp) context.getApplicationContext();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sessao = new SessionManager(this);
        api = ApiClient.criar(BuildConfig.API_BASE_URL, sessao, () -> { });
    }

    public SessionManager getSessao() {
        return sessao;
    }

    public ApiService getApi() {
        return api;
    }

}
