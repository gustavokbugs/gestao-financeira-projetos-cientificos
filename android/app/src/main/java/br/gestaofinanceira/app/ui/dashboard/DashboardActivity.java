package br.gestaofinanceira.app.ui.dashboard;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import br.gestaofinanceira.app.GestaoFinanceiraApp;
import br.gestaofinanceira.app.R;
import br.gestaofinanceira.app.data.remote.ApiErros;
import br.gestaofinanceira.app.data.remote.dto.Alerta;
import br.gestaofinanceira.app.data.remote.dto.Dashboard;
import br.gestaofinanceira.app.data.remote.dto.Rubrica;
import br.gestaofinanceira.app.ui.common.EstadoTela;
import br.gestaofinanceira.app.ui.common.Formatos;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** GP-06 e GP-10: resumo financeiro do projeto. Todos os valores vêm prontos da API. */
public class DashboardActivity extends AppCompatActivity {

    public static final String EXTRA_PROJETO_ID = "projeto_id";
    public static final String EXTRA_PROJETO_TITULO = "projeto_titulo";
    public static final String EXTRA_PAPEL = "papel";

    private long projetoId;
    private SwipeRefreshLayout swipe;
    private EstadoTela estado;
    private RubricasAdapter rubricasAdapter;
    private boolean temConteudo;
    private Call<Dashboard> chamada;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        projetoId = getIntent().getLongExtra(EXTRA_PROJETO_ID, -1);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getIntent().getStringExtra(EXTRA_PROJETO_TITULO));
            getSupportActionBar().setSubtitle(getString(R.string.perfil_formato,
                    Formatos.papel(getIntent().getStringExtra(EXTRA_PAPEL))));
        }

        rubricasAdapter = new RubricasAdapter();
        RecyclerView lista = findViewById(R.id.lista_rubricas);
        lista.setNestedScrollingEnabled(false);
        lista.setAdapter(rubricasAdapter);

        swipe = findViewById(R.id.swipe);
        swipe.setOnRefreshListener(this::carregar);
        estado = new EstadoTela(findViewById(R.id.estado), this::carregar);

        carregar();
    }

    private void carregar() {
        if (chamada != null) chamada.cancel();
        if (!temConteudo) estado.carregando();
        chamada = GestaoFinanceiraApp.de(this).getApi().dashboard(projetoId);
        chamada.enqueue(new Callback<Dashboard>() {
            @Override
            public void onResponse(Call<Dashboard> call, Response<Dashboard> resposta) {
                if (isFinishing() || isDestroyed()) return;
                swipe.setRefreshing(false);
                Dashboard d = resposta.body();
                if (!resposta.isSuccessful() || d == null) {
                    falhou(ApiErros.mensagem(resposta));
                    return;
                }
                exibir(d);
                temConteudo = true;
                estado.esconder();
            }

            @Override
            public void onFailure(Call<Dashboard> call, Throwable falha) {
                if (call.isCanceled() || isFinishing() || isDestroyed()) return;
                swipe.setRefreshing(false);
                falhou(ApiErros.mensagem(falha));
            }
        });
    }

    private void exibir(Dashboard d) {
        this.<TextView>findViewById(R.id.valor_receita).setText(Formatos.moeda(d.receitaTotal));
        this.<TextView>findViewById(R.id.valor_despesa).setText(Formatos.moeda(d.despesaTotal));
        TextView saldo = findViewById(R.id.valor_saldo);
        saldo.setText(Formatos.moeda(d.saldoDisponivel));
        boolean negativo = d.saldoDisponivel != null && d.saldoDisponivel.signum() < 0;
        saldo.setTextColor(ContextCompat.getColor(this, negativo ? R.color.alerta_critico : R.color.saldo));

        double percentual = d.percentualExecutado == null ? 0 : d.percentualExecutado;
        LinearProgressIndicator progresso = findViewById(R.id.progresso_orcamento);
        progresso.setProgressCompat((int) Math.min(100, Math.round(percentual)), true);
        this.<TextView>findViewById(R.id.texto_orcamento).setText(getString(R.string.orcamento_executado_formato,
                Formatos.moeda(d.orcamentoExecutado), Formatos.moeda(d.orcamentoPrevisto), Formatos.percentual(d.percentualExecutado)));

        LinearLayout listaAlertas = findViewById(R.id.lista_alertas);
        listaAlertas.removeAllViews();
        boolean temAlertas = d.alertas != null && !d.alertas.isEmpty();
        findViewById(R.id.titulo_alertas).setVisibility(temAlertas ? View.VISIBLE : View.GONE);
        if (temAlertas) {
            LayoutInflater inflater = LayoutInflater.from(this);
            for (Alerta alerta : d.alertas) {
                TextView item = (TextView) inflater.inflate(R.layout.item_alerta, listaAlertas, false);
                boolean critico = Alerta.CRITICO.equals(alerta.nivel);
                item.setText(getString(R.string.alerta_formato, alerta.rubrica, alerta.mensagem));
                item.setBackgroundResource(critico ? R.drawable.fundo_alerta_critico : R.drawable.fundo_alerta_atencao);
                item.setTextColor(ContextCompat.getColor(this, critico ? R.color.texto_alerta_critico : R.color.texto_alerta_atencao));
                listaAlertas.addView(item);
            }
        }

        Map<Long, Double> participacao = new HashMap<>();
        if (d.participacaoPorRubrica != null) {
            for (Dashboard.Participacao p : d.participacaoPorRubrica) participacao.put(p.rubricaId, p.percentual);
        }
        rubricasAdapter.atualizar(d.rubricas == null ? Collections.<Rubrica>emptyList() : d.rubricas, participacao);

        this.<TextView>findViewById(R.id.atualizado_em).setText(getString(R.string.atualizado_em_formato, Formatos.dataHora(d.atualizadoEm)));
    }

    private void falhou(String mensagem) {
        if (temConteudo) {
            Snackbar.make(swipe, mensagem, Snackbar.LENGTH_LONG).setAction(R.string.tentar_novamente, v -> carregar()).show();
        } else {
            estado.erro(mensagem);
        }
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
