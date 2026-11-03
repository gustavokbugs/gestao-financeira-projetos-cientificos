package br.gestaofinanceira.app.data.remote;

import br.gestaofinanceira.app.data.remote.dto.LoginRequest;
import br.gestaofinanceira.app.data.remote.dto.LoginResponse;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

/**
 * Endpoints consumidos pelo app. Contrato completo em docs/api-endpoints.md;
 * os demais (rubricas, lançamentos, comprovantes, relatórios) entram aqui
 * conforme as telas forem implementadas.
 */
public interface ApiService {

    @POST("auth/login")
    Call<LoginResponse> login(@Body LoginRequest corpo);
}
