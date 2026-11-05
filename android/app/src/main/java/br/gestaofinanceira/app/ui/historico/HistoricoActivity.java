package br.gestaofinanceira.app.ui.historico;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

import br.gestaofinanceira.app.GestaoFinanceiraApp;
import br.gestaofinanceira.app.R;
import br.gestaofinanceira.app.data.remote.dto.Movimentacao;
import br.gestaofinanceira.app.ui.common.Chamadas;
import br.gestaofinanceira.app.ui.common.Detalhes;
import br.gestaofinanceira.app.ui.common.EstadoTela;
import br.gestaofinanceira.app.ui.common.ProjetoAtual;
import br.gestaofinanceira.app.ui.lancamentos.LancamentoDetalheActivity;
import retrofit2.Call;

/** GP-07 e GP-09: movimentações do projeto com ação, autor, data e campos alterados (mais recentes primeiro). */
public class HistoricoActivity extends AppCompatActivity {

    private ProjetoAtual projeto;
    private final List<Movimentacao> itens = new ArrayList<>();
    private RecyclerView.Adapter<RecyclerView.ViewHolder> adapter;
    private SwipeRefreshLayout swipe;
    private EstadoTela estado;
    private boolean temConteudo;
    private Call<List<Movimentacao>> chamada;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_historico);
        projeto = ProjetoAtual.ler(getIntent());
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setSubtitle(projeto.titulo);
        }

        adapter = new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = getLayoutInflater().inflate(R.layout.item_movimentacao, parent, false);
                return new RecyclerView.ViewHolder(v) {
                };
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
                Movimentacao m = itens.get(position);
                Detalhes.preencherMovimentacao(h.itemView, m, true);
                if ("lancamento".equals(m.entidade)) {
                    h.itemView.setOnClickListener(v -> abrirLancamento(m.entidadeId));
                } else {
                    h.itemView.setOnClickListener(null);
                    h.itemView.setClickable(false);
                }
            }

            @Override
            public int getItemCount() {
                return itens.size();
            }
        };
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
        chamada = Chamadas.enfileirar(this, GestaoFinanceiraApp.de(this).getApi().historico(projeto.id),
                lista -> {
                    swipe.setRefreshing(false);
                    itens.clear();
                    if (lista != null) itens.addAll(lista);
                    adapter.notifyDataSetChanged();
                    temConteudo = true;
                    if (itens.isEmpty()) estado.vazio(getString(R.string.historico_vazio));
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

    private void abrirLancamento(long id) {
        Intent intent = projeto.colocar(new Intent(this, LancamentoDetalheActivity.class));
        intent.putExtra(LancamentoDetalheActivity.EXTRA_LANCAMENTO_ID, id);
        startActivity(intent);
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
