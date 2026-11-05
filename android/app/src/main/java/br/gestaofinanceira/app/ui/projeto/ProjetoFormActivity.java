package br.gestaofinanceira.app.ui.projeto;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import br.gestaofinanceira.app.GestaoFinanceiraApp;
import br.gestaofinanceira.app.R;
import br.gestaofinanceira.app.data.local.SessionManager;
import br.gestaofinanceira.app.data.remote.dto.Projeto;
import br.gestaofinanceira.app.data.remote.dto.ProjetoRequest;
import br.gestaofinanceira.app.ui.common.Chamadas;
import br.gestaofinanceira.app.ui.common.EstadoTela;
import br.gestaofinanceira.app.ui.common.Formatos;
import br.gestaofinanceira.app.ui.common.Formularios;
import br.gestaofinanceira.app.ui.common.ProjetoAtual;
import br.gestaofinanceira.app.ui.dashboard.DashboardActivity;
import retrofit2.Call;

/**
 * GP-02: cadastro (usuários com podeCriarProjetos) e edição (coordenador) do projeto: vigência,
 * fonte de recursos, responsáveis e orçamento aprovado. Pede confirmação antes de salvar (GP-09).
 */
public class ProjetoFormActivity extends AppCompatActivity {

    /** Se presente, edita; senão, cria. */
    public static final String EXTRA_PROJETO_ID = "projeto_id";

    private long projetoId;
    private String inicio;
    private String fim;
    private String estadoInicial;
    private TextInputLayout campoCodigo, campoTitulo, campoInicio, campoFim, campoFonte, campoOrcamento;
    private TextInputEditText codigo, titulo, descricao, textoInicio, textoFim, fonte, orcamento;
    private LinearLayout listaResponsaveis;
    private TextView erroResponsaveis, erroGeral;
    private MaterialButton salvar;
    private View progresso;
    private EstadoTela estado;
    private Map<String, TextInputLayout> campos;
    private Call<Projeto> chamada;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_projeto_form);
        projetoId = getIntent().getLongExtra(EXTRA_PROJETO_ID, -1);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(editando() ? R.string.editar_projeto : R.string.novo_projeto);
        }

        campoCodigo = findViewById(R.id.campo_codigo);
        campoTitulo = findViewById(R.id.campo_titulo);
        campoInicio = findViewById(R.id.campo_inicio);
        campoFim = findViewById(R.id.campo_fim);
        campoFonte = findViewById(R.id.campo_fonte);
        campoOrcamento = findViewById(R.id.campo_orcamento);
        codigo = findViewById(R.id.codigo);
        titulo = findViewById(R.id.titulo);
        descricao = findViewById(R.id.descricao);
        textoInicio = findViewById(R.id.inicio);
        textoFim = findViewById(R.id.fim);
        fonte = findViewById(R.id.fonte);
        orcamento = findViewById(R.id.orcamento);
        listaResponsaveis = findViewById(R.id.lista_responsaveis);
        erroResponsaveis = findViewById(R.id.erro_responsaveis);
        erroGeral = findViewById(R.id.erro_geral);
        salvar = findViewById(R.id.salvar);
        progresso = findViewById(R.id.progresso);
        estado = new EstadoTela(findViewById(R.id.estado), this::carregar);

        campos = new HashMap<>();
        campos.put("codigo", campoCodigo);
        campos.put("titulo", campoTitulo);
        campos.put("vigenciaInicio", campoInicio);
        campos.put("vigenciaFim", campoFim);
        campos.put("fonteRecursos", campoFonte);
        campos.put("orcamentoAprovado", campoOrcamento);
        Formularios.limparAoDigitar(campos);

        textoInicio.setOnClickListener(v -> Formularios.escolherData(this, R.string.inicio_vigencia, inicio, null, fim, iso -> {
            inicio = iso;
            textoInicio.setText(Formatos.data(iso));
            campoInicio.setError(null);
        }));
        textoFim.setOnClickListener(v -> Formularios.escolherData(this, R.string.fim_vigencia, fim, inicio, null, iso -> {
            fim = iso;
            textoFim.setText(Formatos.data(iso));
            campoFim.setError(null);
        }));
        findViewById(R.id.adicionar_responsavel).setOnClickListener(v -> adicionarResponsavel(null));
        salvar.setOnClickListener(v -> tentarSalvar());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                sairComConfirmacao();
            }
        });

        if (editando()) {
            // O código não é editável pela API.
            campoCodigo.setVisibility(View.GONE);
            carregar();
        } else {
            SessionManager sessao = GestaoFinanceiraApp.de(this).getSessao();
            adicionarResponsavel(new Projeto.Responsavel(sessao.getNome(), getString(R.string.funcao_coordenador)));
            estadoInicial = retrato();
        }
    }

    private boolean editando() {
        return projetoId > 0;
    }

    private void carregar() {
        estado.carregando();
        chamada = Chamadas.enfileirar(this, GestaoFinanceiraApp.de(this).getApi().projeto(projetoId),
                p -> {
                    if (p == null) {
                        estado.erro(getString(R.string.resposta_invalida));
                        return;
                    }
                    titulo.setText(p.titulo);
                    descricao.setText(p.descricao);
                    inicio = p.vigenciaInicio;
                    fim = p.vigenciaFim;
                    textoInicio.setText(Formatos.data(inicio));
                    textoFim.setText(Formatos.data(fim));
                    fonte.setText(p.fonteRecursos);
                    orcamento.setText(Formatos.numeroEditavel(p.orcamentoAprovado, 2));
                    listaResponsaveis.removeAllViews();
                    if (p.responsaveis != null) for (Projeto.Responsavel r : p.responsaveis) adicionarResponsavel(r);
                    estado.esconder();
                    estadoInicial = retrato();
                },
                (mensagem, falha) -> estado.erro(mensagem));
    }

    private void adicionarResponsavel(Projeto.Responsavel r) {
        View linha = LayoutInflater.from(this).inflate(R.layout.item_responsavel, listaResponsaveis, false);
        if (r != null) {
            ((TextInputEditText) linha.findViewById(R.id.nome)).setText(r.nome);
            ((TextInputEditText) linha.findViewById(R.id.funcao)).setText(r.funcao);
        }
        linha.findViewById(R.id.remover).setOnClickListener(v -> listaResponsaveis.removeView(linha));
        listaResponsaveis.addView(linha);
    }

    private List<Projeto.Responsavel> responsaveis() {
        List<Projeto.Responsavel> lista = new ArrayList<>();
        for (int i = 0; i < listaResponsaveis.getChildCount(); i++) {
            View linha = listaResponsaveis.getChildAt(i);
            String nome = Formularios.texto(linha.findViewById(R.id.nome));
            String funcao = Formularios.texto(linha.findViewById(R.id.funcao));
            if (nome.isEmpty() && funcao.isEmpty()) continue; // linha em branco é ignorada
            lista.add(new Projeto.Responsavel(nome, funcao));
        }
        return lista;
    }

    private ProjetoRequest montar() {
        ProjetoRequest r = new ProjetoRequest();
        String cod = Formularios.texto(codigo);
        r.codigo = editando() || cod.isEmpty() ? null : cod;
        r.titulo = Formularios.texto(titulo);
        r.descricao = Formularios.texto(descricao);
        r.vigenciaInicio = inicio;
        r.vigenciaFim = fim;
        r.fonteRecursos = Formularios.texto(fonte);
        r.orcamentoAprovado = Formatos.lerNumero(Formularios.texto(orcamento));
        r.responsaveis = responsaveis();
        return r;
    }

    private void tentarSalvar() {
        Formularios.limparErros(campos);
        erroResponsaveis.setVisibility(View.GONE);
        erroGeral.setVisibility(View.GONE);
        ProjetoRequest r = montar();

        Map<String, String> erros = new HashMap<>();
        if (r.titulo.isEmpty()) erros.put("titulo", getString(R.string.erro_titulo));
        if (r.fonteRecursos.isEmpty()) erros.put("fonteRecursos", getString(R.string.erro_fonte));
        if (r.vigenciaInicio == null) erros.put("vigenciaInicio", getString(R.string.erro_data));
        if (r.vigenciaFim == null) erros.put("vigenciaFim", getString(R.string.erro_data));
        else if (r.vigenciaInicio != null && r.vigenciaFim.compareTo(r.vigenciaInicio) < 0) {
            erros.put("vigenciaFim", getString(R.string.erro_fim_antes_inicio));
        }
        if (r.orcamentoAprovado == null || r.orcamentoAprovado.signum() <= 0) {
            erros.put("orcamentoAprovado", getString(R.string.erro_orcamento_projeto));
        }
        for (Projeto.Responsavel resp : r.responsaveis) {
            if (resp.nome.isEmpty()) erros.put("responsaveis", getString(R.string.erro_responsavel_nome));
        }
        if (r.responsaveis.isEmpty()) erros.put("responsaveis", getString(R.string.erro_responsavel_vazio));
        if (!erros.isEmpty()) {
            mostrarErros(erros);
            return;
        }

        String resumo = getString(R.string.confirmar_projeto_formato, r.titulo,
                Formatos.data(r.vigenciaInicio), Formatos.data(r.vigenciaFim), r.fonteRecursos,
                Formatos.moeda(r.orcamentoAprovado), r.responsaveis.size());
        Formularios.confirmar(this, editando() ? R.string.confirmar_alteracao : R.string.confirmar_projeto,
                resumo, R.string.salvar, () -> enviar(r));
    }

    private void mostrarErros(Map<String, String> erros) {
        Map<String, String> outros = new HashMap<>(erros);
        String resp = outros.remove("responsaveis");
        if (resp != null) {
            erroResponsaveis.setText(resp);
            erroResponsaveis.setVisibility(View.VISIBLE);
        }
        List<String> semCampo = Formularios.aplicarErros(outros, campos);
        if (!semCampo.isEmpty()) {
            erroGeral.setText(String.join("\n", semCampo));
            erroGeral.setVisibility(View.VISIBLE);
        }
    }

    private void enviar(ProjetoRequest r) {
        progresso.setVisibility(View.VISIBLE);
        salvar.setEnabled(false);
        Call<Projeto> c = editando()
                ? GestaoFinanceiraApp.de(this).getApi().editarProjeto(projetoId, r)
                : GestaoFinanceiraApp.de(this).getApi().criarProjeto(r);
        chamada = Chamadas.enfileirar(this, c,
                p -> {
                    setResult(RESULT_OK);
                    if (!editando() && p != null) {
                        // Projeto novo: abre direto o dashboard dele para cadastrar as rubricas.
                        startActivity(ProjetoAtual.de(p).colocar(new Intent(this, DashboardActivity.class)));
                    }
                    finish();
                },
                (mensagem, falha) -> {
                    progresso.setVisibility(View.GONE);
                    salvar.setEnabled(true);
                    if (falha != null && !falha.campos.isEmpty()) {
                        mostrarErros(falha.campos);
                    } else {
                        erroGeral.setText(mensagem);
                        erroGeral.setVisibility(View.VISIBLE);
                    }
                });
    }

    private String retrato() {
        StringBuilder s = new StringBuilder();
        s.append(Formularios.texto(codigo)).append('|').append(Formularios.texto(titulo)).append('|')
                .append(Formularios.texto(descricao)).append('|').append(inicio).append('|').append(fim).append('|')
                .append(Formularios.texto(fonte)).append('|').append(Formularios.texto(orcamento));
        for (Projeto.Responsavel r : responsaveis()) s.append('|').append(r.nome).append('/').append(r.funcao);
        return s.toString();
    }

    private void sairComConfirmacao() {
        if (estadoInicial != null && !estadoInicial.equals(retrato())) Formularios.confirmarDescarte(this);
        else finish();
    }

    @Override
    public boolean onSupportNavigateUp() {
        sairComConfirmacao();
        return true;
    }

    @Override
    protected void onDestroy() {
        if (chamada != null) chamada.cancel();
        super.onDestroy();
    }
}
