package br.gestaofinanceira.app;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.net.InetAddress;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;

/**
 * API falsa para os testes instrumentados: responde como o backend real (docs/api-endpoints.md),
 * com um projeto de exemplo. Roda em localhost, que o build debug aceita por HTTP.
 */
public class ApiFalsa {

    public final MockWebServer servidor = new MockWebServer();
    /** Papel do usuário logado no projeto 1: coordenador, gestor ou consulta. */
    public volatile String papel = "coordenador";
    /** Quando true, toda requisição falha como se a rede tivesse caído. */
    public volatile boolean semRede;
    /** Quando true, as rotas autenticadas respondem 401, como com um token expirado. */
    public volatile boolean tokenExpirado;

    public String iniciar() throws IOException {
        servidor.setDispatcher(new Dispatcher() {
            @NonNull
            @Override
            public MockResponse dispatch(@NonNull RecordedRequest r) {
                return responder(r);
            }
        });
        servidor.start(InetAddress.getByName("localhost"), 0);
        return servidor.url("/api/v1/").toString();
    }

    public void parar() throws IOException {
        servidor.shutdown();
    }

    private static MockResponse json(int codigo, String corpo) {
        return new MockResponse().setResponseCode(codigo)
                .setHeader("Content-Type", "application/json; charset=utf-8").setBody(corpo);
    }

    private MockResponse responder(RecordedRequest r) {
        if (semRede) return new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START);
        String caminho = r.getPath() == null ? "" : r.getPath().replaceFirst("^/api/v1", "");
        String metodo = r.getMethod();

        if (caminho.equals("/auth/login")) {
            String corpo = r.getBody().readUtf8();
            if (!corpo.contains("\"senha\":\"senha123\"")) {
                return json(401, "{\"mensagem\":\"E-mail ou senha incorretos\"}");
            }
            String expira = Instant.now().plus(8, ChronoUnit.HOURS).toString();
            return json(200, "{\"token\":\"token-de-teste\",\"tipo\":\"Bearer\",\"expiraEm\":\"" + expira + "\","
                    + "\"usuario\":{\"id\":1,\"nome\":\"Pessoa Teste\",\"email\":\"teste@exemplo.com\",\"podeCriarProjetos\":"
                    + "coordenador".equals(papel) + "}}");
        }
        if (tokenExpirado) return json(401, "{\"mensagem\":\"Token inválido ou expirado\"}");
        if (caminho.equals("/projetos") && "GET".equals(metodo)) {
            return json(200, "[" + projeto() + "]");
        }
        if (caminho.equals("/projetos/1")) return json(200, projeto());
        if (caminho.equals("/projetos/1/dashboard")) return json(200, DASHBOARD);
        if (caminho.equals("/projetos/1/rubricas")) return json(200, "[" + RUBRICA + "]");
        if (caminho.startsWith("/projetos/1/lancamentos") && "GET".equals(metodo)) return json(200, LANCAMENTOS);
        if (caminho.equals("/projetos/1/historico")) return json(200, "[]");
        return json(404, "{\"mensagem\":\"Recurso não encontrado\"}");
    }

    private String projeto() {
        return "{\"id\":1,\"codigo\":\"PESQ-TESTE\",\"titulo\":\"Projeto Teste\",\"descricao\":\"\","
                + "\"vigenciaInicio\":\"2026-01-01\",\"vigenciaFim\":\"2026-12-31\",\"fonteRecursos\":\"Edital teste\","
                + "\"responsaveis\":[{\"nome\":\"Pessoa Teste\",\"funcao\":\"Coordenação\"}],"
                + "\"orcamentoAprovado\":30000,\"papel\":\"" + papel + "\"}";
    }

    private static final String RUBRICA = "{\"id\":1,\"nome\":\"Diárias\",\"rubricaPaiId\":null,\"orcamento\":1600,"
            + "\"executado\":1280,\"previsto\":0,\"saldo\":320,\"percentualComprometido\":80,"
            + "\"alerta\":{\"nivel\":\"ATENCAO\",\"mensagem\":\"Rubrica próxima do limite (80,0% comprometido)\"},\"subrubricas\":[]}";

    private static final String DASHBOARD = "{\"projetoId\":1,\"receitaTotal\":20000,\"despesaTotal\":1280,"
            + "\"saldoDisponivel\":18720,\"orcamentoPrevisto\":30000,\"orcamentoExecutado\":1280,\"percentualExecutado\":4.27,"
            + "\"participacaoPorRubrica\":[{\"rubricaId\":1,\"rubrica\":\"Diárias\",\"valor\":1280,\"percentual\":100}],"
            + "\"rubricas\":[" + RUBRICA + "],"
            + "\"alertas\":[{\"rubricaId\":1,\"rubrica\":\"Diárias\",\"nivel\":\"ATENCAO\",\"mensagem\":\"Rubrica próxima do limite (80,0% comprometido)\"}],"
            + "\"regrasAlerta\":{\"atencaoPercentual\":80,\"criticoPercentual\":100},"
            + "\"atualizadoEm\":\"2026-11-05T12:00:00.000Z\"}";

    private static final String LANCAMENTOS = "{\"itens\":[{\"id\":7,\"projetoId\":1,\"tipo\":\"DESPESA\",\"data\":\"2026-04-15\","
            + "\"descricao\":\"Diárias nacionais\",\"rubricaId\":1,\"rubrica\":{\"id\":1,\"nome\":\"Diárias\",\"rubricaPaiId\":null},"
            + "\"quantidade\":4,\"valorUnitario\":320,\"valorTotal\":1280,\"situacao\":\"REALIZADO\","
            + "\"criadoPor\":{\"id\":1,\"nome\":\"Pessoa Teste\"},\"criadoEm\":\"2026-04-15T12:00:00.000Z\","
            + "\"atualizadoPor\":{\"id\":1,\"nome\":\"Pessoa Teste\"},\"atualizadoEm\":\"2026-04-15T12:00:00.000Z\","
            + "\"quantidadeComprovantes\":0}],\"total\":1,\"pagina\":1,\"tamanho\":20}";
}
