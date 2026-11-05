package br.gestaofinanceira.app.ui.dashboard;

import android.content.Intent;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import br.gestaofinanceira.app.GestaoFinanceiraApp;
import br.gestaofinanceira.app.R;
import br.gestaofinanceira.app.data.remote.dto.Alerta;
import br.gestaofinanceira.app.data.remote.dto.Dashboard;
import br.gestaofinanceira.app.data.remote.dto.Rubrica;
import br.gestaofinanceira.app.ui.common.Chamadas;
import br.gestaofinanceira.app.ui.common.EstadoTela;
import br.gestaofinanceira.app.ui.common.Formatos;
import br.gestaofinanceira.app.ui.common.ProjetoAtual;
import br.gestaofinanceira.app.ui.historico.HistoricoActivity;
import br.gestaofinanceira.app.ui.lancamentos.LancamentosActivity;
import br.gestaofinanceira.app.ui.projeto.ProjetoDetalheActivity;
import br.gestaofinanceira.app.ui.relatorios.Relatorios;
import br.gestaofinanceira.app.ui.rubricas.RubricasActivity;
import br.gestaofinanceira.app.ui.rubricas.RubricasAdapter;
import retrofit2.Call;

/**
 * GP-06 e GP-10: resumo financeiro do projeto. Todos os valores vêm prontos da API; o app não
 * recalcula nada. Daqui o usuário vai para lançamentos, rubricas, projeto, histórico e relatório.
 */
public class DashboardActivity extends AppCompatActivity {

    private ProjetoAtual projeto;
    private SwipeRefreshLayout swipe;
    private EstadoTela estado;
    private RubricasAdapter rubricasAdapter;
    private boolean temConteudo;
    private Call<Dashboard> chamada;

    /** Qualquer tela que altere dados devolve RESULT_OK; aí o dashboard recarrega. */
    private final ActivityResultLauncher<Intent> abrirTela = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), r -> {
                if (r.getResultCode() != RESULT_OK) return;
                if (ProjetoAtual.presente(r.getData())) {
                    projeto = ProjetoAtual.ler(r.getData());
                    atualizarTitulo();
                }
                carregar();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);
        projeto = ProjetoAtual.ler(getIntent());

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        atualizarTitulo();

        rubricasAdapter = new RubricasAdapter();
        RecyclerView lista = findViewById(R.id.lista_rubricas);
        lista.setNestedScrollingEnabled(false);
        lista.setAdapter(rubricasAdapter);

        swipe = findViewById(R.id.swipe);
        swipe.setOnRefreshListener(this::carregar);
        estado = new EstadoTela(findViewById(R.id.estado), this::carregar);
        findViewById(R.id.botao_lancamentos).setOnClickListener(v -> abrir(LancamentosActivity.class));
        findViewById(R.id.botao_relatorio).setOnClickListener(v -> exportarRelatorio());

        carregar();
    }

    private void atualizarTitulo() {
        if (getSupportActionBar() == null) return;
        getSupportActionBar().setTitle(projeto.titulo);
        getSupportActionBar().setSubtitle(getString(R.string.perfil_formato, Formatos.papel(projeto.papel)));
    }

    private void carregar() {
        if (chamada != null) chamada.cancel();
        if (!temConteudo) estado.carregando();
        chamada = Chamadas.enfileirar(this, GestaoFinanceiraApp.de(this).getApi().dashboard(projeto.id),
                d -> {
                    swipe.setRefreshing(false);
                    if (d == null) {
                        falhou(getString(R.string.resposta_invalida));
                        return;
                    }
                    exibir(d);
                    temConteudo = true;
                    estado.esconder();
                },
                (mensagem, falha) -> {
                    swipe.setRefreshing(false);
                    falhou(mensagem);
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

        exibirGrafico(d);
        exibirResumo(d);
        exibirAlertas(d);

        Map<Long, Double> participacao = new HashMap<>();
        if (d.participacaoPorRubrica != null) {
            for (Dashboard.Participacao p : d.participacaoPorRubrica) participacao.put(p.rubricaId, p.percentual);
        }
        rubricasAdapter.atualizar(d.rubricas == null ? Collections.<Rubrica>emptyList() : d.rubricas, participacao);

        this.<TextView>findViewById(R.id.atualizado_em).setText(getString(R.string.atualizado_em_formato, Formatos.dataHora(d.atualizadoEm)));
    }

    /** GP-06: participação das despesas por rubrica (gráfico + legenda com valor e percentual). */
    private void exibirGrafico(Dashboard d) {
        GraficoParticipacaoView grafico = findViewById(R.id.grafico);
        LinearLayout legenda = findViewById(R.id.legenda);
        legenda.removeAllViews();
        List<Dashboard.Participacao> fatias = new ArrayList<>();
        if (d.participacaoPorRubrica != null) {
            for (Dashboard.Participacao p : d.participacaoPorRubrica) if (p.percentual > 0) fatias.add(p);
        }
        boolean vazio = fatias.isEmpty();
        grafico.setVisibility(vazio ? View.GONE : View.VISIBLE);
        findViewById(R.id.sem_despesas).setVisibility(vazio ? View.VISIBLE : View.GONE);
        if (vazio) return;

        int[] paleta = paleta();
        List<Float> percentuais = new ArrayList<>();
        List<Integer> cores = new ArrayList<>();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < fatias.size(); i++) {
            Dashboard.Participacao p = fatias.get(i);
            int cor = paleta[i % paleta.length];
            percentuais.add((float) p.percentual);
            cores.add(cor);
            View item = inflater.inflate(R.layout.item_legenda, legenda, false);
            item.findViewById(R.id.cor).getBackground().mutate().setTint(cor);
            ((TextView) item.findViewById(R.id.nome)).setText(p.rubrica);
            ((TextView) item.findViewById(R.id.valor)).setText(Formatos.moeda(p.valor));
            ((TextView) item.findViewById(R.id.percentual)).setText(Formatos.percentual(p.percentual));
            legenda.addView(item);
        }
        grafico.definir(percentuais, cores, Formatos.moeda(d.despesaTotal), getString(R.string.despesa_total),
                MaterialColors.getColor(grafico, com.google.android.material.R.attr.colorOnSurface),
                MaterialColors.getColor(grafico, com.google.android.material.R.attr.colorOnSurfaceVariant));
    }

    private int[] paleta() {
        TypedArray arr = getResources().obtainTypedArray(R.array.paleta_grafico);
        int[] cores = new int[arr.length()];
        for (int i = 0; i < cores.length; i++) cores[i] = arr.getColor(i, 0);
        arr.recycle();
        return cores;
    }

    /** Tabela "Resumo Financeiro" como no painel de referência: tipo | tipo de despesa | valor. */
    private void exibirResumo(Dashboard d) {
        TableLayout tabela = findViewById(R.id.tabela_resumo);
        tabela.removeAllViews();
        linhaResumo(tabela, getString(R.string.tipo), getString(R.string.tipo_despesa), getString(R.string.valor), true);
        linhaResumo(tabela, getString(R.string.receita), getString(R.string.receita_total), Formatos.moeda(d.receitaTotal), false);
        if (d.rubricas != null) {
            for (Rubrica r : d.rubricas) {
                linhaResumo(tabela, getString(R.string.despesa), r.nome, Formatos.moeda(r.executado), false);
            }
        }
        linhaResumo(tabela, getString(R.string.despesa), getString(R.string.despesa_total), Formatos.moeda(d.despesaTotal), true);
        linhaResumo(tabela, getString(R.string.saldo), getString(R.string.saldo_disponivel), Formatos.moeda(d.saldoDisponivel), true);
    }

    private void linhaResumo(TableLayout tabela, String tipo, String tipoDespesa, String valor, boolean destaque) {
        View linha = LayoutInflater.from(this).inflate(R.layout.item_resumo, tabela, false);
        TextView[] celulas = {linha.findViewById(R.id.tipo), linha.findViewById(R.id.tipo_despesa), linha.findViewById(R.id.valor)};
        celulas[0].setText(tipo);
        celulas[1].setText(tipoDespesa);
        celulas[2].setText(valor);
        if (destaque) for (TextView c : celulas) c.setTypeface(c.getTypeface(), android.graphics.Typeface.BOLD);
        tabela.addView(linha);
    }

    private void exibirAlertas(Dashboard d) {
        LinearLayout listaAlertas = findViewById(R.id.lista_alertas);
        listaAlertas.removeAllViews();
        boolean temAlertas = d.alertas != null && !d.alertas.isEmpty();
        findViewById(R.id.titulo_alertas).setVisibility(temAlertas ? View.VISIBLE : View.GONE);
        if (!temAlertas) return;
        LayoutInflater inflater = LayoutInflater.from(this);
        for (Alerta alerta : d.alertas) {
            TextView item = (TextView) inflater.inflate(R.layout.item_alerta, listaAlertas, false);
            boolean critico = Alerta.CRITICO.equals(alerta.nivel);
            item.setText(getString(R.string.alerta_formato, alerta.rubrica, alerta.mensagem));
            item.setBackgroundResource(critico ? R.drawable.fundo_alerta_critico : R.drawable.fundo_alerta_atencao);
            item.setTextColor(ContextCompat.getColor(this, critico ? R.color.texto_alerta_critico : R.color.texto_alerta_atencao));
            if (alerta.rubricaId != null) {
                item.setOnClickListener(v -> {
                    Intent intent = projeto.colocar(new Intent(this, LancamentosActivity.class));
                    intent.putExtra(LancamentosActivity.EXTRA_RUBRICA_ID, alerta.rubricaId.longValue());
                    intent.putExtra(LancamentosActivity.EXTRA_RUBRICA_NOME, alerta.rubrica);
                    abrirTela.launch(intent);
                });
            }
            listaAlertas.addView(item);
        }
    }

    private void falhou(String mensagem) {
        if (temConteudo) {
            Snackbar.make(swipe, mensagem, Snackbar.LENGTH_LONG).setAction(R.string.tentar_novamente, v -> carregar()).show();
        } else {
            estado.erro(mensagem);
        }
    }

    private void abrir(Class<?> tela) {
        abrirTela.launch(projeto.colocar(new Intent(this, tela)));
    }

    private void exportarRelatorio() {
        Relatorios.exportar(this, swipe, projeto.id, projeto.titulo, null);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_dashboard, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.acao_lancamentos) abrir(LancamentosActivity.class);
        else if (id == R.id.acao_rubricas) abrir(RubricasActivity.class);
        else if (id == R.id.acao_projeto) abrir(ProjetoDetalheActivity.class);
        else if (id == R.id.acao_historico) abrir(HistoricoActivity.class);
        else if (id == R.id.acao_relatorio) exportarRelatorio();
        else return super.onOptionsItemSelected(item);
        return true;
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
