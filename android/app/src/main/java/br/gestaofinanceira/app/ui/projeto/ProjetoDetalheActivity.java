package br.gestaofinanceira.app.ui.projeto;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.LinearLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

import br.gestaofinanceira.app.GestaoFinanceiraApp;
import br.gestaofinanceira.app.R;
import br.gestaofinanceira.app.data.remote.dto.Projeto;
import br.gestaofinanceira.app.ui.common.Chamadas;
import br.gestaofinanceira.app.ui.common.Detalhes;
import br.gestaofinanceira.app.ui.common.EstadoTela;
import br.gestaofinanceira.app.ui.common.Formatos;
import br.gestaofinanceira.app.ui.common.ProjetoAtual;
import retrofit2.Call;

/** GP-02: dados do projeto (vigência, fonte de recursos, responsáveis, orçamento). O coordenador edita. */
public class ProjetoDetalheActivity extends AppCompatActivity {

    private ProjetoAtual projeto;
    private Projeto dados;
    private SwipeRefreshLayout swipe;
    private EstadoTela estado;
    private Call<Projeto> chamada;
    private boolean editado;

    private final ActivityResultLauncher<Intent> editar = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), r -> {
                if (r.getResultCode() == RESULT_OK) {
                    editado = true;
                    carregar();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_projeto_detalhe);
        projeto = ProjetoAtual.ler(getIntent());
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        swipe = findViewById(R.id.swipe);
        swipe.setOnRefreshListener(this::carregar);
        estado = new EstadoTela(findViewById(R.id.estado), this::carregar);
        carregar();
    }

    private void carregar() {
        if (chamada != null) chamada.cancel();
        if (dados == null) estado.carregando();
        chamada = Chamadas.enfileirar(this, GestaoFinanceiraApp.de(this).getApi().projeto(projeto.id),
                p -> {
                    swipe.setRefreshing(false);
                    if (p == null) {
                        estado.erro(getString(R.string.resposta_invalida));
                        return;
                    }
                    dados = p;
                    exibir(p);
                    estado.esconder();
                    invalidateOptionsMenu();
                    // Devolve título e vigência atualizados para as telas anteriores (ProjetoAtual).
                    if (editado) setResult(RESULT_OK, ProjetoAtual.de(p).colocar(new Intent()));
                },
                (mensagem, falha) -> {
                    swipe.setRefreshing(false);
                    if (dados != null) Snackbar.make(swipe, mensagem, Snackbar.LENGTH_LONG).show();
                    else estado.erro(mensagem);
                });
    }

    private void exibir(Projeto p) {
        LinearLayout campos = findViewById(R.id.campos);
        campos.removeAllViews();
        Detalhes.campo(campos, getString(R.string.codigo), p.codigo);
        Detalhes.campo(campos, getString(R.string.titulo), p.titulo);
        Detalhes.campo(campos, getString(R.string.descricao), p.descricao);
        Detalhes.campo(campos, getString(R.string.vigencia), getString(R.string.periodo_formato,
                Formatos.data(p.vigenciaInicio), Formatos.data(p.vigenciaFim)));
        Detalhes.campo(campos, getString(R.string.fonte_recursos), p.fonteRecursos);
        Detalhes.campo(campos, getString(R.string.orcamento_aprovado), Formatos.moeda(p.orcamentoAprovado));
        List<String> responsaveis = new ArrayList<>();
        if (p.responsaveis != null) {
            for (Projeto.Responsavel r : p.responsaveis) {
                responsaveis.add(r.funcao == null || r.funcao.isEmpty() ? r.nome : r.nome + " (" + r.funcao + ")");
            }
        }
        Detalhes.campo(campos, getString(R.string.responsaveis), String.join("\n", responsaveis));
        Detalhes.campo(campos, getString(R.string.seu_perfil), Formatos.papel(p.papel));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_editar, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.acao_editar).setVisible(dados != null && projeto.permissoes.podeGerenciar());
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.acao_editar) {
            editar.launch(new Intent(this, ProjetoFormActivity.class).putExtra(ProjetoFormActivity.EXTRA_PROJETO_ID, projeto.id));
            return true;
        }
        return super.onOptionsItemSelected(item);
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
