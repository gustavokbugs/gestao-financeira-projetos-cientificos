package br.gestaofinanceira.app.data.remote;

import java.util.List;
import java.util.Map;

import br.gestaofinanceira.app.data.remote.dto.Comprovante;
import br.gestaofinanceira.app.data.remote.dto.Dashboard;
import br.gestaofinanceira.app.data.remote.dto.Lancamento;
import br.gestaofinanceira.app.data.remote.dto.LancamentoRequest;
import br.gestaofinanceira.app.data.remote.dto.LoginRequest;
import br.gestaofinanceira.app.data.remote.dto.LoginResponse;
import br.gestaofinanceira.app.data.remote.dto.Movimentacao;
import br.gestaofinanceira.app.data.remote.dto.PaginaLancamentos;
import br.gestaofinanceira.app.data.remote.dto.Projeto;
import br.gestaofinanceira.app.data.remote.dto.ProjetoRequest;
import br.gestaofinanceira.app.data.remote.dto.Rubrica;
import br.gestaofinanceira.app.data.remote.dto.RubricaRequest;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.QueryMap;
import retrofit2.http.Streaming;

/** Endpoints consumidos pelo app. Contrato completo em docs/api-endpoints.md. */
public interface ApiService {

    // GP-01
    @POST("auth/login")
    Call<LoginResponse> login(@Body LoginRequest corpo);

    // GP-01 / GP-02
    @GET("projetos")
    Call<List<Projeto>> listarProjetos();

    @GET("projetos/{id}")
    Call<Projeto> projeto(@Path("id") long projetoId);

    @POST("projetos")
    Call<Projeto> criarProjeto(@Body ProjetoRequest corpo);

    @PUT("projetos/{id}")
    Call<Projeto> editarProjeto(@Path("id") long projetoId, @Body ProjetoRequest corpo);

    // GP-06 / GP-10
    @GET("projetos/{id}/dashboard")
    Call<Dashboard> dashboard(@Path("id") long projetoId);

    // GP-03
    @GET("projetos/{id}/rubricas")
    Call<List<Rubrica>> rubricas(@Path("id") long projetoId);

    @POST("projetos/{id}/rubricas")
    Call<Rubrica> criarRubrica(@Path("id") long projetoId, @Body RubricaRequest corpo);

    @PUT("rubricas/{id}")
    Call<Rubrica> editarRubrica(@Path("id") long rubricaId, @Body RubricaRequest corpo);

    // GP-04 / GP-07: filtros tipo, rubricaId, situacao, de, ate, busca, pagina, tamanho
    @GET("projetos/{id}/lancamentos")
    Call<PaginaLancamentos> lancamentos(@Path("id") long projetoId, @QueryMap Map<String, String> filtros);

    @POST("projetos/{id}/lancamentos")
    Call<Lancamento> criarLancamento(@Path("id") long projetoId, @Body LancamentoRequest corpo);

    @GET("lancamentos/{id}")
    Call<Lancamento> lancamento(@Path("id") long lancamentoId);

    @PUT("lancamentos/{id}")
    Call<Lancamento> editarLancamento(@Path("id") long lancamentoId, @Body LancamentoRequest corpo);

}
