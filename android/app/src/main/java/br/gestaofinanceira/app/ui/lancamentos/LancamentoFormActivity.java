package br.gestaofinanceira.app.ui.lancamentos;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import br.gestaofinanceira.app.GestaoFinanceiraApp;
import br.gestaofinanceira.app.R;
import br.gestaofinanceira.app.data.remote.ApiErros;
import br.gestaofinanceira.app.data.remote.dto.Alerta;
import br.gestaofinanceira.app.data.remote.dto.Lancamento;
import br.gestaofinanceira.app.data.remote.dto.LancamentoRequest;
import br.gestaofinanceira.app.data.remote.dto.Rubrica;
import br.gestaofinanceira.app.ui.common.Chamadas;
import br.gestaofinanceira.app.ui.common.EstadoTela;
import br.gestaofinanceira.app.ui.common.Formatos;
import br.gestaofinanceira.app.ui.common.Formularios;
import br.gestaofinanceira.app.ui.common.OpcoesRubrica;
import br.gestaofinanceira.app.ui.common.ProjetoAtual;
import retrofit2.Call;

/**
 * GP-04 e GP-09: cria ou edita um lançamento. Valida localmente, pede confirmação com o resumo,
 * liga os erros 422 da API aos campos, trata conflito de edição (409) e mostra o alerta da
 * rubrica devolvido pela API depois de salvar (GP-10).
 */
public class LancamentoFormActivity extends AppCompatActivity {

    /** Se presente, edita este lançamento; senão, cria um novo. */
    public static final String EXTRA_LANCAMENTO_ID = "lancamento_id";
    private static final String ESTADO_DATA = "data";
    private static final String ESTADO_RUBRICA = "rubrica";

    private ProjetoAtual projeto;
    private long lancamentoId = -1;
    private String atualizadoEm;
    private String data;
    private Long rubricaId;
    private OpcoesRubrica opcoes;
    private String estadoInicial;

    private MaterialButtonToggleGroup tipo;
    private MaterialButtonToggleGroup situacao;
    private TextInputEditText campoTextoData;
    private TextInputEditText descricao;
    private TextInputEditText quantidade;
    private TextInputEditText valorUnitario;
    private AutoCompleteTextView rubrica;
    private TextInputLayout campoData, campoDescricao, campoRubrica, campoQuantidade, campoValorUnitario;
    private TextView total, erroTipo, erroGeral;
    private MaterialButton salvar;
    private LinearProgressIndicator progresso;
    private EstadoTela estado;
    private Map<String, TextInputLayout> campos;
    private Call<?> chamada;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lancamento_form);
        projeto = ProjetoAtual.ler(getIntent());
        lancamentoId = getIntent().getLongExtra(EXTRA_LANCAMENTO_ID, -1);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(editando() ? R.string.editar_lancamento : R.string.novo_lancamento);
            getSupportActionBar().setSubtitle(projeto.titulo);
        }

        tipo = findViewById(R.id.tipo);
        situacao = findViewById(R.id.situacao);
        campoTextoData = findViewById(R.id.data);
        descricao = findViewById(R.id.descricao);
        quantidade = findViewById(R.id.quantidade);
        valorUnitario = findViewById(R.id.valor_unitario);
        rubrica = findViewById(R.id.rubrica);
        campoData = findViewById(R.id.campo_data);
        campoDescricao = findViewById(R.id.campo_descricao);
        campoRubrica = findViewById(R.id.campo_rubrica);
        campoQuantidade = findViewById(R.id.campo_quantidade);
        campoValorUnitario = findViewById(R.id.campo_valor_unitario);
        total = findViewById(R.id.total);
        erroTipo = findViewById(R.id.erro_tipo);
        erroGeral = findViewById(R.id.erro_geral);
        salvar = findViewById(R.id.salvar);
        progresso = findViewById(R.id.progresso);
        estado = new EstadoTela(findViewById(R.id.estado), this::carregar);

        campos = new HashMap<>();
        campos.put("data", campoData);
        campos.put("descricao", campoDescricao);
        campos.put("rubricaId", campoRubrica);
        campos.put("quantidade", campoQuantidade);
        campos.put("valorUnitario", campoValorUnitario);
        Formularios.limparAoDigitar(campos);

        campoTextoData.setOnClickListener(v -> escolherData());
        campoData.setEndIconOnClickListener(v -> escolherData());
        rubrica.setOnItemClickListener((p, v, posicao, id) -> {
            rubricaId = opcoes.idNaPosicao(posicao);
            rubrica.setText(opcoes.rotulo(rubricaId), false);
            campoRubrica.setError(null);
        });
        tipo.addOnButtonCheckedListener((g, id, marcado) -> {
            if (!marcado) return;
            erroTipo.setVisibility(View.GONE);
            campoRubrica.setHint(id == R.id.tipo_despesa ? R.string.rubrica : R.string.rubrica_opcional);
        });
        TextWatcher recalcular = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                atualizarTotal();
            }
        };
        quantidade.addTextChangedListener(recalcular);
        valorUnitario.addTextChangedListener(recalcular);
        salvar.setOnClickListener(v -> tentarSalvar());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                sairComConfirmacao();
            }
        });

        if (savedInstanceState != null) {
            data = savedInstanceState.getString(ESTADO_DATA);
            rubricaId = savedInstanceState.containsKey(ESTADO_RUBRICA) ? savedInstanceState.getLong(ESTADO_RUBRICA) : null;
        }
        if (!editando()) {
            tipo.check(R.id.tipo_despesa);
            situacao.check(R.id.situacao_previsto);
            quantidade.setText("1");
        }
        carregar();
    }

    private boolean editando() {
        return lancamentoId > 0;
    }

    /** Carrega as rubricas e, na edição, o lançamento. */
    private void carregar() {
        estado.carregando();
        chamada = Chamadas.enfileirar(this, GestaoFinanceiraApp.de(this).getApi().rubricas(projeto.id),
                lista -> {
                    opcoes = new OpcoesRubrica(lista, getString(R.string.sem_rubrica), false);
                    opcoes.ligar(this, rubrica);
                    if (editando()) {
                        carregarLancamento();
                    } else {
                        exibirData();
                        rubrica.setText(rubricaId == null ? "" : opcoes.rotulo(rubricaId), false);
                        estado.esconder();
                        estadoInicial = retrato();
                    }
                },
                (mensagem, falha) -> estado.erro(mensagem));
    }

    private void carregarLancamento() {
        chamada = Chamadas.enfileirar(this, GestaoFinanceiraApp.de(this).getApi().lancamento(lancamentoId),
                l -> {
                    if (l == null) {
                        estado.erro(getString(R.string.resposta_invalida));
                        return;
                    }
                    preencher(l);
                    estado.esconder();
                    estadoInicial = retrato();
                },
                (mensagem, falha) -> estado.erro(mensagem));
    }

    private void preencher(Lancamento l) {
        atualizadoEm = l.atualizadoEm;
        tipo.check(Lancamento.RECEITA.equals(l.tipo) ? R.id.tipo_receita : R.id.tipo_despesa);
        situacao.check(Lancamento.REALIZADO.equals(l.situacao) ? R.id.situacao_realizado : R.id.situacao_previsto);
        data = l.data;
        exibirData();
        descricao.setText(l.descricao);
        rubricaId = l.rubricaId;
        rubrica.setText(rubricaId == null ? "" : opcoes.rotulo(rubricaId), false);
        quantidade.setText(Formatos.numeroEditavel(l.quantidade, 3));
        valorUnitario.setText(Formatos.numeroEditavel(l.valorUnitario, 2));
    }

    private void escolherData() {
        Formularios.escolherData(this, R.string.data, data, projeto.vigenciaInicio, projeto.vigenciaFim, iso -> {
            data = iso;
            exibirData();
            campoData.setError(null);
        });
    }

    private void exibirData() {
        campoTextoData.setText(data == null ? "" : Formatos.data(data));
        campoData.setHelperText(getString(R.string.vigencia_formato,
                Formatos.data(projeto.vigenciaInicio), Formatos.data(projeto.vigenciaFim)));
    }

    private void atualizarTotal() {
        BigDecimal t = Formatos.totalEstimado(Formatos.lerNumero(Formularios.texto(quantidade)),
                Formatos.lerNumero(Formularios.texto(valorUnitario)));
        total.setText(getString(R.string.total_formato, t == null ? "-" : Formatos.moeda(t)));
    }

    private String tipoEscolhido() {
        int id = tipo.getCheckedButtonId();
        return id == R.id.tipo_receita ? Lancamento.RECEITA : id == R.id.tipo_despesa ? Lancamento.DESPESA : null;
    }

    private String situacaoEscolhida() {
        return situacao.getCheckedButtonId() == R.id.situacao_realizado ? Lancamento.REALIZADO : Lancamento.PREVISTO;
    }

    private LancamentoRequest montar() {
        LancamentoRequest r = new LancamentoRequest();
        r.tipo = tipoEscolhido();
        r.data = data;
        r.descricao = Formularios.texto(descricao);
        r.rubricaId = rubricaId;
        r.quantidade = Formatos.lerNumero(Formularios.texto(quantidade));
        r.valorUnitario = Formatos.lerNumero(Formularios.texto(valorUnitario));
        r.situacao = situacaoEscolhida();
        r.atualizadoEm = atualizadoEm;
        return r;
    }

    private void tentarSalvar() {
        Formularios.limparErros(campos);
        erroTipo.setVisibility(View.GONE);
        erroGeral.setVisibility(View.GONE);
        LancamentoRequest r = montar();
        Map<String, String> erros = ValidadorLancamento.validar(r.tipo, r.data, r.descricao, r.rubricaId,
                r.quantidade, r.valorUnitario, projeto.vigenciaInicio, projeto.vigenciaFim);
        if (!erros.isEmpty()) {
            mostrarErros(erros);
            return;
        }
        String resumo = getString(R.string.confirmar_lancamento_formato,
                Formatos.tipo(r.tipo), Formatos.data(r.data), r.descricao,
                r.rubricaId == null ? getString(R.string.sem_rubrica) : opcoes.rotulo(r.rubricaId),
                Formatos.numeroEditavel(r.quantidade, 3), Formatos.moeda(r.valorUnitario),
                Formatos.moeda(Formatos.totalEstimado(r.quantidade, r.valorUnitario)), Formatos.situacao(r.situacao));
        Formularios.confirmar(this, editando() ? R.string.confirmar_alteracao : R.string.confirmar_lancamento,
                resumo, R.string.salvar, () -> enviar(r));
    }

    private void mostrarErros(Map<String, String> erros) {
        if (erros.containsKey("tipo")) {
            erroTipo.setText(erros.get("tipo"));
            erroTipo.setVisibility(View.VISIBLE);
        }
        Map<String, String> outros = new HashMap<>(erros);
        outros.remove("tipo");
        List<String> semCampo = Formularios.aplicarErros(outros, campos);
        if (!semCampo.isEmpty()) {
            erroGeral.setText(String.join("\n", semCampo));
            erroGeral.setVisibility(View.VISIBLE);
        }
    }

    private void enviar(LancamentoRequest r) {
        ocupado(true);
        Call<Lancamento> c = editando()
                ? GestaoFinanceiraApp.de(this).getApi().editarLancamento(lancamentoId, r)
                : GestaoFinanceiraApp.de(this).getApi().criarLancamento(projeto.id, r);
        chamada = Chamadas.enfileirar(this, c, this::salvo, (mensagem, falha) -> {
            ocupado(false);
            falhou(mensagem, falha);
        });
    }

    private void falhou(String mensagem, ApiErros.Falha falha) {
        if (falha != null && falha.conflito()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.conflito_titulo)
                    .setMessage(mensagem)
                    .setNegativeButton(R.string.cancelar, null)
                    .setPositiveButton(R.string.recarregar, (d, w) -> {
                        estado.carregando();
                        carregarLancamento();
                    })
                    .show();
            return;
        }
        if (falha != null && !falha.campos.isEmpty()) {
            mostrarErros(falha.campos);
            if (erroGeral.getVisibility() != View.VISIBLE) {
                erroGeral.setText(mensagem);
                erroGeral.setVisibility(View.VISIBLE);
            }
            return;
        }
        erroGeral.setText(mensagem);
        erroGeral.setVisibility(View.VISIBLE);
    }

    private void salvo(Lancamento l) {
        ocupado(false);
        setResult(RESULT_OK);
        Alerta alerta = l == null ? null : l.alertaRubrica;
        boolean novo = !editando() && l != null;
        StringBuilder mensagem = new StringBuilder(getString(R.string.lancamento_salvo_formato,
                l == null ? "" : Formatos.moeda(l.valorTotal)));
        if (alerta != null) {
            mensagem.append("\n\n").append(getString(R.string.alerta_rubrica_formato,
                    l.rubrica == null ? "" : l.rubrica.nome, alerta.mensagem));
        }
        MaterialAlertDialogBuilder dialogo = new MaterialAlertDialogBuilder(this)
                .setTitle(alerta == null ? R.string.lancamento_salvo
                        : Alerta.CRITICO.equals(alerta.nivel) ? R.string.atencao_saldo_insuficiente : R.string.atencao_limite)
                .setMessage(mensagem)
                .setCancelable(false)
                .setPositiveButton(R.string.ok, (d, w) -> finish());
        if (novo) {
            dialogo.setNeutralButton(R.string.anexar_comprovante, (d, w) -> {
                Intent detalhe = projeto.colocar(new Intent(this, LancamentoDetalheActivity.class));
                detalhe.putExtra(LancamentoDetalheActivity.EXTRA_LANCAMENTO_ID, l.id);
                detalhe.putExtra(LancamentoDetalheActivity.EXTRA_ANEXAR, true);
                startActivity(detalhe);
                finish();
            });
        }
        dialogo.show();
    }

    private void ocupado(boolean ativo) {
        progresso.setVisibility(ativo ? View.VISIBLE : View.GONE);
        salvar.setEnabled(!ativo);
    }

    /** Representação do formulário para saber se algo foi digitado. */
    private String retrato() {
        return tipoEscolhido() + "|" + situacaoEscolhida() + "|" + data + "|" + rubricaId + "|"
                + Formularios.texto(descricao) + "|" + Formularios.texto(quantidade) + "|" + Formularios.texto(valorUnitario);
    }

    private void sairComConfirmacao() {
        if (estadoInicial != null && !estadoInicial.equals(retrato())) {
            Formularios.confirmarDescarte(this);
        } else {
            finish();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        sairComConfirmacao();
        return true;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(ESTADO_DATA, data);
        if (rubricaId != null) outState.putLong(ESTADO_RUBRICA, rubricaId);
    }

    @Override
    protected void onDestroy() {
        if (chamada != null) chamada.cancel();
        super.onDestroy();
    }
}
