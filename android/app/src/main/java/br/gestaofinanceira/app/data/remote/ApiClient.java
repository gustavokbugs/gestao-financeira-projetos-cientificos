package br.gestaofinanceira.app.data.remote;

import java.util.concurrent.TimeUnit;

import br.gestaofinanceira.app.BuildConfig;
import br.gestaofinanceira.app.data.local.SessionManager;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/** Monta o cliente HTTP: timeouts, token JWT no cabeçalho e tratamento de sessão expirada. */
public final class ApiClient {

    private ApiClient() {
    }

    public static ApiService criar(String baseUrl, SessionManager sessao, Runnable aoExpirarSessao) {
        OkHttpClient.Builder http = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS) // relatórios e comprovantes podem demorar em redes lentas
                .writeTimeout(60, TimeUnit.SECONDS)
                .addInterceptor(chain -> {
                    Request requisicao = chain.request();
                    String token = sessao.getToken();
                    if (token != null && requisicao.header("Authorization") == null) {
                        requisicao = requisicao.newBuilder()
                                .header("Authorization", "Bearer " + token)
                                .build();
                    }
                    Response resposta = chain.proceed(requisicao);
                    if (resposta.code() == 401 && token != null) {
                        sessao.limpar();
                        aoExpirarSessao.run();
                    }
                    return resposta;
                });

        if (BuildConfig.DEBUG) {
            // BASIC registra só método, URL e status: nunca cabeçalhos (token) nem corpo (senha).
            HttpLoggingInterceptor log = new HttpLoggingInterceptor();
            log.setLevel(HttpLoggingInterceptor.Level.BASIC);
            http.addInterceptor(log);
        }

        return new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(http.build())
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService.class);
    }
}
