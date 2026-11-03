package br.gestaofinanceira.app;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import br.gestaofinanceira.app.data.local.SessionManager;
import br.gestaofinanceira.app.data.remote.ApiClient;
import br.gestaofinanceira.app.data.remote.ApiService;
import br.gestaofinanceira.app.ui.login.LoginActivity;

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
        api = ApiClient.criar(BuildConfig.API_BASE_URL, sessao, this::voltarParaLogin);
    }

    public SessionManager getSessao() {
        return sessao;
    }

    public ApiService getApi() {
        return api;
    }

    /** Chamado quando a API responde 401: o token expirou ou foi invalidado. */
    private void voltarParaLogin() {
        new Handler(Looper.getMainLooper()).post(() -> {
            Intent intent = new Intent(this, LoginActivity.class);
            intent.putExtra(LoginActivity.EXTRA_SESSAO_EXPIRADA, true);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });
    }
}
