package br.gestaofinanceira.app;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import br.gestaofinanceira.app.ui.login.LoginActivity;

/** Testes instrumentados dos fluxos principais (E-10/E-12), contra a API falsa. */
@RunWith(AndroidJUnit4.class)
public class FluxosTest {

    private final ApiFalsa api = new ApiFalsa();
    private ActivityScenario<LoginActivity> cena;

    @Before
    public void preparar() throws Exception {
        String url = api.iniciar();
        GestaoFinanceiraApp app = (GestaoFinanceiraApp) InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getApplicationContext();
        app.getSessao().limpar();
        app.usarApi(url);
    }

    @After
    public void encerrar() throws Exception {
        if (cena != null) cena.close();
        api.parar();
    }

    private void entrar(String senha) {
        cena = ActivityScenario.launch(LoginActivity.class);
        onView(withId(R.id.email)).perform(replaceText("teste@exemplo.com"));
        onView(withId(R.id.senha)).perform(replaceText(senha), closeSoftKeyboard());
        onView(withId(R.id.entrar)).perform(click());
    }

    private void abrirProjeto() {
        Aguardar.ver(withText("Projeto Teste"));
        onView(withText("Projeto Teste")).perform(click());
        Aguardar.ver(withId(R.id.valor_receita));
    }

    @Test
    public void emailInvalidoNaoChamaApi() {
        cena = ActivityScenario.launch(LoginActivity.class);
        onView(withId(R.id.email)).perform(replaceText("nao-e-email"));
        onView(withId(R.id.senha)).perform(replaceText("x"), closeSoftKeyboard());
        onView(withId(R.id.entrar)).perform(click());
        onView(withText("Informe um e-mail válido")).check(matches(isDisplayed()));
        assertEquals(0, api.servidor.getRequestCount());
    }

    @Test
    public void senhaErradaMostraMensagemDaApi() {
        entrar("errada");
        Aguardar.ver(withText("E-mail ou senha incorretos"));
    }

    @Test
    public void loginProjetoDashboardComValoresDaApi() {
        entrar("senha123");
        abrirProjeto();
        onView(withId(R.id.valor_receita)).check(matches(withText(containsString("20.000,00"))));
        onView(withId(R.id.valor_despesa)).check(matches(withText(containsString("1.280,00"))));
        onView(withId(R.id.valor_saldo)).check(matches(withText(containsString("18.720,00"))));
        onView(withId(R.id.regras_alerta)).perform(scrollTo()).check(matches(withText(containsString("80,0%"))));
    }

    @Test
    public void coordenadorVeNovoLancamento() {
        entrar("senha123");
        abrirProjeto();
        onView(withId(R.id.botao_lancamentos)).perform(click());
        Aguardar.ver(withText("Diárias nacionais"));
        onView(withId(R.id.novo)).check(matches(isDisplayed()));
    }

    @Test
    public void consultaNaoVeAcoesDeEscrita() {
        api.papel = "consulta";
        entrar("senha123");
        Aguardar.ver(withText("Projeto Teste"));
        onView(withId(R.id.novo)).check(matches(not(isDisplayed()))); // "Novo projeto"
        abrirProjeto();
        onView(withId(R.id.botao_lancamentos)).perform(click());
        Aguardar.ver(withText("Diárias nacionais"));
        onView(withId(R.id.novo)).check(matches(not(isDisplayed()))); // "Novo lançamento"
    }

    @Test
    public void formularioValidaCamposObrigatorios() {
        entrar("senha123");
        abrirProjeto();
        onView(withId(R.id.botao_lancamentos)).perform(click());
        Aguardar.ver(withId(R.id.novo));
        onView(withId(R.id.novo)).perform(click());
        Aguardar.ver(withId(R.id.tipo)); // o "Salvar" pode estar abaixo da dobra em telas menores
        onView(withId(R.id.salvar)).perform(scrollTo(), click());
        onView(withText("Informe a data")).check(matches(isDisplayed()));
        onView(withText("Informe a descrição")).check(matches(isDisplayed()));
        onView(withText("Informe a rubrica da despesa")).check(matches(isDisplayed()));
    }

    @Test
    public void semRedeMostraErroETentaDeNovo() {
        entrar("senha123");
        Aguardar.ver(withText("Projeto Teste"));
        api.semRede = true;
        abrirProjetoSemEsperarDashboard();
        Aguardar.ver(allOf(withId(R.id.estado_mensagem), withText(containsString("Sem conexão"))));
        api.semRede = false;
        onView(allOf(withId(R.id.estado_tentar), isDisplayed())).perform(click());
        Aguardar.ver(withId(R.id.valor_receita));
    }

    @Test
    public void sessaoExpiradaVoltaAoLogin() {
        entrar("senha123");
        Aguardar.ver(withText("Projeto Teste"));
        api.tokenExpirado = true;
        abrirProjetoSemEsperarDashboard();
        Aguardar.ver(withText("Sua sessão expirou. Entre novamente."));
        onView(withId(R.id.entrar)).check(matches(isDisplayed()));
    }

    private void abrirProjetoSemEsperarDashboard() {
        onView(withText("Projeto Teste")).perform(click());
    }
}
