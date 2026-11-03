package br.gestaofinanceira.app.ui.projetos;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;

import br.gestaofinanceira.app.GestaoFinanceiraApp;
import br.gestaofinanceira.app.R;
import br.gestaofinanceira.app.data.remote.ApiErros;
import br.gestaofinanceira.app.data.remote.dto.Projeto;
import br.gestaofinanceira.app.ui.common.EstadoTela;
import br.gestaofinanceira.app.ui.dashboard.DashboardActivity;
import br.gestaofinanceira.app.ui.login.LoginActivity;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** GP-01: seleção do projeto entre os que o usuário tem acesso, com o papel em cada um. */
public class ProjetosActivity extends AppCompatActivity {

    private ProjetosAdapter adapter;
    private SwipeRefreshLayout swipe;
    private EstadoTela estado;
    private boolean temConteudo;
    private Call<List<Projeto>> chamada;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_projetos);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setSubtitle(GestaoFinanceiraApp.de(this).getSessao().getNome());
        }

        adapter = new ProjetosAdapter(this::abrirDashboard);
        RecyclerView lista = findViewById(R.id.lista);
        lista.setAdapter(adapter);

        swipe = findViewById(R.id.swipe);
        swipe.setOnRefreshListener(this::carregar);
        estado = new EstadoTela(findViewById(R.id.estado), this::carregar);

        carregar();
    }

    private void carregar() {
        if (chamada != null) chamada.cancel();
        if (!temConteudo) estado.carregando();
        chamada = GestaoFinanceiraApp.de(this).getApi().listarProjetos();
        chamada.enqueue(new Callback<List<Projeto>>() {
            @Override
            public void onResponse(Call<List<Projeto>> call, Response<List<Projeto>> resposta) {
                if (isFinishing() || isDestroyed()) return;
                swipe.setRefreshing(false);
                List<Projeto> projetos = resposta.body();
                if (!resposta.isSuccessful() || projetos == null) {
                    falhou(ApiErros.mensagem(resposta));
                    return;
                }
                adapter.atualizar(projetos);
                temConteudo = !projetos.isEmpty();
                if (temConteudo) {
                    estado.esconder();
                } else {
                    estado.vazio(getString(R.string.nenhum_projeto));
                }
            }

            @Override
            public void onFailure(Call<List<Projeto>> call, Throwable falha) {
                if (call.isCanceled() || isFinishing() || isDestroyed()) return;
                swipe.setRefreshing(false);
                falhou(ApiErros.mensagem(falha));
            }
        });
    }

    private void falhou(String mensagem) {
        if (temConteudo) {
            Snackbar.make(swipe, mensagem, Snackbar.LENGTH_LONG).setAction(R.string.tentar_novamente, v -> carregar()).show();
        } else {
            estado.erro(mensagem);
        }
    }

    private void abrirDashboard(Projeto projeto) {
        Intent intent = new Intent(this, DashboardActivity.class);
        intent.putExtra(DashboardActivity.EXTRA_PROJETO_ID, projeto.id);
        intent.putExtra(DashboardActivity.EXTRA_PROJETO_TITULO, projeto.titulo);
        intent.putExtra(DashboardActivity.EXTRA_PAPEL, projeto.papel);
        startActivity(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_projetos, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.acao_sair) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.sair)
                    .setMessage(R.string.confirmar_sair)
                    .setNegativeButton(R.string.cancelar, null)
                    .setPositiveButton(R.string.sair, (d, w) -> sair())
                    .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void sair() {
        GestaoFinanceiraApp.de(this).getSessao().limpar();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        if (chamada != null) chamada.cancel();
        super.onDestroy();
    }
}
