package br.gestaofinanceira.app.ui.lancamentos;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.util.Pair;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;

import br.gestaofinanceira.app.GestaoFinanceiraApp;
import br.gestaofinanceira.app.R;
import br.gestaofinanceira.app.data.remote.dto.Lancamento;
import br.gestaofinanceira.app.data.remote.dto.PaginaLancamentos;
import br.gestaofinanceira.app.data.remote.dto.Rubrica;
import br.gestaofinanceira.app.ui.common.Chamadas;
import br.gestaofinanceira.app.ui.common.EstadoTela;
import br.gestaofinanceira.app.ui.common.Formatos;
import br.gestaofinanceira.app.ui.common.Formularios;
import br.gestaofinanceira.app.ui.common.OpcoesRubrica;
import br.gestaofinanceira.app.ui.common.ProjetoAtual;
import br.gestaofinanceira.app.ui.relatorios.Relatorios;
import retrofit2.Call;

/**
 * GP-04 e GP-07: lançamentos do projeto com filtros combináveis (período, rubrica, tipo,
 * situação e busca), paginação ao rolar e acesso ao detalhe. "Novo lançamento" só aparece
 * para quem pode lançar (GP-01).
 */
public class LancamentosActivity extends AppCompatActivity {

    /** Opcional: abre já filtrado por uma rubrica (vindo da tela de rubricas). */
    public static final String EXTRA_RUBRICA_ID = "rubrica_id";
    public static final String EXTRA_RUBRICA_NOME = "rubrica_nome";
    private static final String ESTADO_FILTROS = "filtros";
    private static final int TAMANHO_PAGINA = 20;

    private ProjetoAtual projeto;
    private Filtros filtros = new Filtros();
    private LancamentosAdapter adapter;
    private SwipeRefreshLayout swipe;
    private EstadoTela estado;
    private TextView contagem;
    private Chip chipPeriodo, chipRubrica, chipTipo, chipSituacao, chipLimpar;
    private TextInputEditText busca;
    private Call<PaginaLancamentos> chamada;
    private Call<List<Rubrica>> chamadaRubricas;
    private List<Rubrica> rubricas;
    private int paginaAtual;
    private int carregados;
    private int total;
    private boolean carregando;
    private boolean temConteudo;
    /** Indica que o projeto mudou (lançamento criado/editado), para o dashboard recarregar ao voltar. */
    private boolean houveAlteracao;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable buscaAtrasada = this::recarregar;

    private final ActivityResultLauncher<Intent> abrirTela = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), resultado -> {
                if (resultado.getResultCode() == Activity.RESULT_OK) {
                    houveAlteracao = true;
                    setResult(RESULT_OK);
                    recarregar();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lancamentos);
        projeto = ProjetoAtual.ler(getIntent());

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setSubtitle(projeto.titulo);
        }

        if (savedInstanceState != null && savedInstanceState.getSerializable(ESTADO_FILTROS) != null) {
            filtros = (Filtros) savedInstanceState.getSerializable(ESTADO_FILTROS);
        } else if (getIntent().hasExtra(EXTRA_RUBRICA_ID)) {
            filtros.rubricaId = getIntent().getLongExtra(EXTRA_RUBRICA_ID, -1);
            filtros.rubricaNome = getIntent().getStringExtra(EXTRA_RUBRICA_NOME);
        }

        adapter = new LancamentosAdapter(this::abrirDetalhe);
        RecyclerView lista = findViewById(R.id.lista);
        lista.setAdapter(adapter);
        lista.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
                if (dy > 0 && lm != null && !carregando && carregados < total
                        && lm.findLastVisibleItemPosition() >= adapter.getItemCount() - 5) {
                    carregarPagina(paginaAtual + 1);
                }
            }
        });

        swipe = findViewById(R.id.swipe);
        swipe.setOnRefreshListener(this::recarregar);
        estado = new EstadoTela(findViewById(R.id.estado), this::recarregar);
        contagem = findViewById(R.id.contagem);

        configurarBusca();
        configurarFiltros();

        ExtendedFloatingActionButton novo = findViewById(R.id.novo);
        if (projeto.permissoes.podeLancar()) {
            novo.setVisibility(View.VISIBLE);
            novo.setOnClickListener(v -> abrirTela.launch(projeto.colocar(new Intent(this, LancamentoFormActivity.class))));
            lista.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                    if (dy > 8) novo.shrink();
                    else if (dy < -8) novo.extend();
                }
            });
        }

        recarregar();
    }

    private void configurarBusca() {
        busca = findViewById(R.id.busca);
        if (filtros.busca != null) busca.setText(filtros.busca);
        busca.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String novo = s.toString().trim();
                if (novo.equals(filtros.busca == null ? "" : filtros.busca)) return;
                filtros.busca = novo.isEmpty() ? null : novo;
                handler.removeCallbacks(buscaAtrasada);
                handler.postDelayed(buscaAtrasada, 500);
            }
        });
        busca.setOnEditorActionListener((v, acao, e) -> {
            if (acao == EditorInfo.IME_ACTION_SEARCH) {
                handler.removeCallbacks(buscaAtrasada);
                recarregar();
                return true;
            }
            return false;
        });
    }

    private void configurarFiltros() {
        chipPeriodo = findViewById(R.id.filtro_periodo);
        chipRubrica = findViewById(R.id.filtro_rubrica);
        chipTipo = findViewById(R.id.filtro_tipo);
        chipSituacao = findViewById(R.id.filtro_situacao);
        chipLimpar = findViewById(R.id.filtro_limpar);

        chipPeriodo.setOnClickListener(v -> escolherPeriodo());
        chipRubrica.setOnClickListener(v -> escolherRubrica());
        chipTipo.setOnClickListener(v -> escolherOpcao(R.string.filtro_tipo,
                new String[]{null, Lancamento.RECEITA, Lancamento.DESPESA}, filtros.tipo, valor -> filtros.tipo = valor));
        chipSituacao.setOnClickListener(v -> escolherOpcao(R.string.filtro_situacao,
                new String[]{null, Lancamento.PREVISTO, Lancamento.REALIZADO, Lancamento.CANCELADO},
                filtros.situacao, valor -> filtros.situacao = valor));

        chipPeriodo.setOnCloseIconClickListener(v -> aplicar(() -> filtros.de = filtros.ate = null));
        chipRubrica.setOnCloseIconClickListener(v -> aplicar(() -> {
            filtros.rubricaId = null;
            filtros.rubricaNome = null;
        }));
        chipTipo.setOnCloseIconClickListener(v -> aplicar(() -> filtros.tipo = null));
        chipSituacao.setOnCloseIconClickListener(v -> aplicar(() -> filtros.situacao = null));
        chipLimpar.setOnClickListener(v -> {
            filtros.limpar();
            busca.setText("");
            handler.removeCallbacks(buscaAtrasada);
            recarregar();
        });
        atualizarChips();
    }

    private interface Definir {
        void definir(String valor);
    }

    private void aplicar(Runnable mudanca) {
        mudanca.run();
        recarregar();
    }

    private void escolherOpcao(int titulo, String[] valores, String atual, Definir definir) {
        String[] rotulos = new String[valores.length];
        int marcado = 0;
        for (int i = 0; i < valores.length; i++) {
            rotulos[i] = valores[i] == null ? getString(R.string.todos)
                    : titulo == R.string.filtro_tipo ? Formatos.tipo(valores[i]) : Formatos.situacao(valores[i]);
            if (valores[i] != null && valores[i].equals(atual)) marcado = i;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(titulo)
                .setSingleChoiceItems(rotulos, marcado, (d, qual) -> {
                    d.dismiss();
                    aplicar(() -> definir.definir(valores[qual]));
                })
                .setNegativeButton(R.string.cancelar, null)
                .show();
    }

    private void escolherPeriodo() {
        CalendarConstraints.Builder limites = new CalendarConstraints.Builder();
        Long inicio = Formularios.milis(projeto.vigenciaInicio);
        Long fim = Formularios.milis(projeto.vigenciaFim);
        if (inicio != null) limites.setStart(inicio);
        if (fim != null) limites.setEnd(fim);
        MaterialDatePicker.Builder<Pair<Long, Long>> builder = MaterialDatePicker.Builder.dateRangePicker()
                .setTitleText(R.string.filtro_periodo)
                .setCalendarConstraints(limites.build());
        Long de = Formularios.milis(filtros.de);
        Long ate = Formularios.milis(filtros.ate);
        if (de != null && ate != null) builder.setSelection(new Pair<>(de, ate));
        MaterialDatePicker<Pair<Long, Long>> seletor = builder.build();
        seletor.addOnPositiveButtonClickListener(sel -> aplicar(() -> {
            filtros.de = sel.first == null ? null : Formularios.iso(sel.first);
            filtros.ate = sel.second == null ? null : Formularios.iso(sel.second);
        }));
        seletor.show(getSupportFragmentManager(), "periodo");
    }

    private void escolherRubrica() {
        if (rubricas != null) {
            mostrarRubricas();
            return;
        }
        Snackbar aguarde = Snackbar.make(swipe, R.string.carregando_rubricas, Snackbar.LENGTH_INDEFINITE);
        aguarde.show();
        chamadaRubricas = Chamadas.enfileirar(this, GestaoFinanceiraApp.de(this).getApi().rubricas(projeto.id),
                lista -> {
                    aguarde.dismiss();
                    rubricas = lista;
                    mostrarRubricas();
                },
                (mensagem, falha) -> {
                    aguarde.dismiss();
                    Snackbar.make(swipe, mensagem, Snackbar.LENGTH_LONG)
                            .setAction(R.string.tentar_novamente, v -> escolherRubrica()).show();
                });
    }

    private void mostrarRubricas() {
        OpcoesRubrica opcoes = new OpcoesRubrica(rubricas, getString(R.string.todas_rubricas), false);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.filtro_rubrica)
                .setItems(opcoes.rotulos(), (d, qual) -> aplicar(() -> {
                    filtros.rubricaId = opcoes.idNaPosicao(qual);
                    filtros.rubricaNome = filtros.rubricaId == null ? null : opcoes.rotulo(filtros.rubricaId);
                }))
                .setNegativeButton(R.string.cancelar, null)
                .show();
    }

    private void atualizarChips() {
        marcar(chipPeriodo, filtros.de != null || filtros.ate != null,
                getString(R.string.periodo_formato, Formatos.data(filtros.de), Formatos.data(filtros.ate)),
                getString(R.string.filtro_periodo));
        marcar(chipRubrica, filtros.rubricaId != null, filtros.rubricaNome, getString(R.string.filtro_rubrica));
        marcar(chipTipo, filtros.tipo != null, Formatos.tipo(filtros.tipo), getString(R.string.filtro_tipo));
        marcar(chipSituacao, filtros.situacao != null, Formatos.situacao(filtros.situacao), getString(R.string.filtro_situacao));
        chipLimpar.setVisibility(filtros.algumAtivo() ? View.VISIBLE : View.GONE);
    }

    private static void marcar(Chip chip, boolean ativo, String textoAtivo, String textoPadrao) {
        chip.setChecked(ativo);
        chip.setText(ativo && textoAtivo != null ? textoAtivo : textoPadrao);
        chip.setCloseIconVisible(ativo);
    }

    private void recarregar() {
        atualizarChips();
        carregarPagina(1);
    }

    private void carregarPagina(int pagina) {
        if (chamada != null) chamada.cancel();
        carregando = true;
        if (pagina == 1 && !temConteudo) estado.carregando();
        chamada = Chamadas.enfileirar(this,
                GestaoFinanceiraApp.de(this).getApi().lancamentos(projeto.id, filtros.paraPagina(pagina, TAMANHO_PAGINA)),
                p -> {
                    carregando = false;
                    swipe.setRefreshing(false);
                    if (p == null || p.itens == null) {
                        falhou(getString(R.string.resposta_invalida), pagina);
                        return;
                    }
                    paginaAtual = pagina;
                    total = p.total;
                    if (pagina == 1) {
                        adapter.substituir(p.itens);
                        carregados = p.itens.size();
                    } else {
                        adapter.acrescentar(p.itens);
                        carregados += p.itens.size();
                    }
                    contagem.setText(getResources().getQuantityString(R.plurals.lancamentos_encontrados, total, total));
                    temConteudo = true;
                    if (total == 0) {
                        estado.vazio(getString(filtros.algumAtivo() ? R.string.nenhum_lancamento_filtro : R.string.nenhum_lancamento));
                    } else {
                        estado.esconder();
                    }
                },
                (mensagem, falha) -> {
                    carregando = false;
                    swipe.setRefreshing(false);
                    falhou(mensagem, pagina);
                });
    }

    private void falhou(String mensagem, int pagina) {
        if (temConteudo) {
            Snackbar.make(swipe, mensagem, Snackbar.LENGTH_LONG)
                    .setAction(R.string.tentar_novamente, v -> carregarPagina(pagina)).show();
        } else {
            estado.erro(mensagem);
        }
    }

    private void abrirDetalhe(Lancamento l) {
        Intent intent = projeto.colocar(new Intent(this, LancamentoDetalheActivity.class));
        intent.putExtra(LancamentoDetalheActivity.EXTRA_LANCAMENTO_ID, l.id);
        abrirTela.launch(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_lancamentos, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.acao_relatorio) {
            Relatorios.exportar(this, swipe, projeto.id, projeto.titulo, filtros);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(ESTADO_FILTROS, filtros);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(buscaAtrasada);
        if (chamada != null) chamada.cancel();
        if (chamadaRubricas != null) chamadaRubricas.cancel();
        super.onDestroy();
    }
}
