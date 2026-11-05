package br.gestaofinanceira.app.ui.rubricas;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import br.gestaofinanceira.app.GestaoFinanceiraApp;
import br.gestaofinanceira.app.R;
import br.gestaofinanceira.app.data.remote.dto.Rubrica;
import br.gestaofinanceira.app.data.remote.dto.RubricaRequest;
import br.gestaofinanceira.app.ui.common.Chamadas;
import br.gestaofinanceira.app.ui.common.EstadoTela;
import br.gestaofinanceira.app.ui.common.Formatos;
import br.gestaofinanceira.app.ui.common.Formularios;
import br.gestaofinanceira.app.ui.common.OpcoesRubrica;
import br.gestaofinanceira.app.ui.common.ProjetoAtual;
import br.gestaofinanceira.app.ui.lancamentos.LancamentosActivity;
import retrofit2.Call;

/**
 * GP-03: rubricas e subrubricas do projeto (dois níveis) com orçado, executado, previsto, saldo
 * e alerta. O coordenador cria e edita; as rubricas não são fixas no código, então "outras
 * configuráveis" são só novas rubricas. Tocar em uma rubrica abre os lançamentos dela.
 */
public class RubricasActivity extends AppCompatActivity {

    private ProjetoAtual projeto;
    private RubricasAdapter adapter;
    private SwipeRefreshLayout swipe;
    private EstadoTela estado;
    private List<Rubrica> rubricas = Collections.emptyList();
    private boolean temConteudo;
    private Call<?> chamada;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rubricas);
        projeto = ProjetoAtual.ler(getIntent());

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setSubtitle(projeto.titulo);
        }
        ((TextView) findViewById(R.id.resumo)).setText(projeto.permissoes.podeGerenciar()
                ? R.string.rubricas_dica_coordenador : R.string.rubricas_dica);

        adapter = new RubricasAdapter(this::selecionada);
        RecyclerView lista = findViewById(R.id.lista);
        lista.setAdapter(adapter);
        swipe = findViewById(R.id.swipe);
        swipe.setOnRefreshListener(this::carregar);
        estado = new EstadoTela(findViewById(R.id.estado), this::carregar);

        ExtendedFloatingActionButton nova = findViewById(R.id.nova);
        if (projeto.permissoes.podeGerenciar()) {
            nova.setVisibility(View.VISIBLE);
            nova.setOnClickListener(v -> abrirDialogo(null));
        }
        carregar();
    }

    private void carregar() {
        if (chamada != null) chamada.cancel();
        if (!temConteudo) estado.carregando();
        chamada = Chamadas.enfileirar(this, GestaoFinanceiraApp.de(this).getApi().rubricas(projeto.id),
                lista -> {
                    swipe.setRefreshing(false);
                    rubricas = lista == null ? Collections.emptyList() : lista;
                    adapter.atualizar(rubricas, null);
                    temConteudo = true;
                    if (rubricas.isEmpty()) estado.vazio(getString(R.string.nenhuma_rubrica));
                    else estado.esconder();
                },
                (mensagem, falha) -> {
                    swipe.setRefreshing(false);
                    if (temConteudo) {
                        Snackbar.make(swipe, mensagem, Snackbar.LENGTH_LONG).setAction(R.string.tentar_novamente, v -> carregar()).show();
                    } else {
                        estado.erro(mensagem);
                    }
                });
    }

    private void selecionada(Rubrica r) {
        if (!projeto.permissoes.podeGerenciar()) {
            verLancamentos(r);
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(r.nome)
                .setItems(new String[]{getString(R.string.ver_lancamentos), getString(R.string.editar_rubrica)}, (d, qual) -> {
                    if (qual == 0) verLancamentos(r);
                    else abrirDialogo(r);
                })
                .show();
    }

    private void verLancamentos(Rubrica r) {
        Intent intent = projeto.colocar(new Intent(this, LancamentosActivity.class));
        intent.putExtra(LancamentosActivity.EXTRA_RUBRICA_ID, r.id);
        intent.putExtra(LancamentosActivity.EXTRA_RUBRICA_NOME, r.nome);
        startActivity(intent);
    }

    /** Cria (rubrica == null) ou edita uma rubrica. O diálogo só fecha quando a API aceita. */
    private void abrirDialogo(Rubrica existente) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialogo_rubrica, null);
        TextInputLayout campoNome = view.findViewById(R.id.campo_nome);
        TextInputLayout campoOrcamento = view.findViewById(R.id.campo_orcamento);
        TextInputLayout campoPai = view.findViewById(R.id.campo_pai);
        TextInputEditText nome = view.findViewById(R.id.nome);
        TextInputEditText orcamento = view.findViewById(R.id.orcamento);
        AutoCompleteTextView pai = view.findViewById(R.id.pai);
        TextView erro = view.findViewById(R.id.erro);
        View progresso = view.findViewById(R.id.progresso);

        Long[] paiEscolhido = {null};
        OpcoesRubrica opcoesPai = new OpcoesRubrica(rubricas, getString(R.string.nenhuma_principal), true);
        if (existente == null) {
            opcoesPai.ligar(this, pai);
            pai.setText(opcoesPai.rotulo(null), false);
            pai.setOnItemClickListener((p, v, pos, id) -> {
                paiEscolhido[0] = opcoesPai.idNaPosicao(pos);
                pai.setText(opcoesPai.rotulo(paiEscolhido[0]), false);
            });
        } else {
            // A API só permite mudar nome e orçamento; o nível da rubrica é fixo.
            campoPai.setVisibility(View.GONE);
            nome.setText(existente.nome);
            orcamento.setText(Formatos.numeroEditavel(existente.orcamento, 2));
        }

        Map<String, TextInputLayout> campos = new HashMap<>();
        campos.put("nome", campoNome);
        campos.put("orcamento", campoOrcamento);
        campos.put("rubricaPaiId", campoPai);

        AlertDialog dialogo = new MaterialAlertDialogBuilder(this)
                .setTitle(existente == null ? R.string.nova_rubrica : R.string.editar_rubrica)
                .setView(view)
                .setNegativeButton(R.string.cancelar, null)
                .setPositiveButton(R.string.salvar, null)
                .create();
        dialogo.setOnShowListener(d -> {
            Button salvar = dialogo.getButton(AlertDialog.BUTTON_POSITIVE);
            salvar.setOnClickListener(v -> {
                Formularios.limparErros(campos);
                erro.setVisibility(View.GONE);
                String textoNome = Formularios.texto(nome);
                BigDecimal valor = Formatos.lerNumero(Formularios.texto(orcamento));
                boolean ok = true;
                if (textoNome.isEmpty()) {
                    campoNome.setError(getString(R.string.erro_nome_rubrica));
                    ok = false;
                }
                if (valor == null || valor.signum() < 0) {
                    campoOrcamento.setError(getString(R.string.erro_orcamento_rubrica));
                    ok = false;
                }
                if (!ok) return;

                RubricaRequest r = new RubricaRequest();
                r.nome = textoNome;
                r.orcamento = valor;
                r.rubricaPaiId = existente == null ? paiEscolhido[0] : null;
                String resumo = existente == null
                        ? getString(R.string.confirmar_rubrica_nova_formato, textoNome, Formatos.moeda(valor),
                        paiEscolhido[0] == null ? getString(R.string.rubrica_principal_curta) : opcoesPai.rotulo(paiEscolhido[0]))
                        : getString(R.string.confirmar_rubrica_edicao_formato, existente.nome, textoNome,
                        Formatos.moeda(existente.orcamento), Formatos.moeda(valor));
                Formularios.confirmar(this, R.string.confirmar_alteracao, resumo, R.string.salvar, () -> {
                    progresso.setVisibility(View.VISIBLE);
                    salvar.setEnabled(false);
                    Call<Rubrica> c = existente == null
                            ? GestaoFinanceiraApp.de(this).getApi().criarRubrica(projeto.id, r)
                            : GestaoFinanceiraApp.de(this).getApi().editarRubrica(existente.id, r);
                    Chamadas.enfileirar(this, c,
                            salva -> {
                                dialogo.dismiss();
                                setResult(RESULT_OK);
                                Snackbar.make(swipe, R.string.rubrica_salva, Snackbar.LENGTH_SHORT).show();
                                carregar();
                            },
                            (mensagem, falha) -> {
                                progresso.setVisibility(View.INVISIBLE);
                                salvar.setEnabled(true);
                                List<String> semCampo = falha == null ? Collections.singletonList(mensagem)
                                        : Formularios.aplicarErros(falha.campos, campos);
                                if (falha != null && falha.campos.isEmpty()) semCampo = Collections.singletonList(mensagem);
                                if (!semCampo.isEmpty()) {
                                    erro.setText(String.join("\n", semCampo));
                                    erro.setVisibility(View.VISIBLE);
                                }
                            });
                });
            });
        });
        dialogo.show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onDestroy() {
        if (chamada != null) chamada.cancel();
        super.onDestroy();
    }
}
