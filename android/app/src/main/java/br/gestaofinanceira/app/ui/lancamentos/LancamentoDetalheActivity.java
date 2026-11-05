package br.gestaofinanceira.app.ui.lancamentos;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import br.gestaofinanceira.app.GestaoFinanceiraApp;
import br.gestaofinanceira.app.R;
import br.gestaofinanceira.app.data.remote.dto.Comprovante;
import br.gestaofinanceira.app.data.remote.dto.Lancamento;
import br.gestaofinanceira.app.data.remote.dto.LancamentoRequest;
import br.gestaofinanceira.app.data.remote.dto.Movimentacao;
import br.gestaofinanceira.app.ui.common.Arquivos;
import br.gestaofinanceira.app.ui.common.Chamadas;
import br.gestaofinanceira.app.ui.common.Comprovantes;
import br.gestaofinanceira.app.ui.common.Cores;
import br.gestaofinanceira.app.ui.common.Detalhes;
import br.gestaofinanceira.app.ui.common.EstadoTela;
import br.gestaofinanceira.app.ui.common.Formatos;
import br.gestaofinanceira.app.ui.common.ProjetoAtual;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import retrofit2.Call;

/**
 * GP-07 e GP-09: detalhe do lançamento com autoria, datas e histórico de alterações.
 * GP-05: lista, abre e anexa comprovantes (foto pela câmera do sistema, imagem da galeria ou PDF).
 * Editar, cancelar e anexar só aparecem para quem pode lançar (GP-01).
 */
public class LancamentoDetalheActivity extends AppCompatActivity {

    public static final String EXTRA_LANCAMENTO_ID = "lancamento_id";
    /** Abre já perguntando como anexar o comprovante (depois de criar o lançamento). */
    public static final String EXTRA_ANEXAR = "anexar";
    private static final String ESTADO_FOTO = "foto";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler principal = new Handler(Looper.getMainLooper());

    private ProjetoAtual projeto;
    private long lancamentoId;
    private Lancamento lancamento;
    private SwipeRefreshLayout swipe;
    private EstadoTela estado;
    private View progressoEnvio;
    private Call<?> chamada;
    private Call<?> chamadaEnvio;
    private boolean enviando;
    private File fotoPendente;
    private boolean perguntarAnexo;

    private final ActivityResultLauncher<Intent> editar = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), r -> {
                if (r.getResultCode() == RESULT_OK) setResult(RESULT_OK);
                // Recarrega mesmo sem salvar: o formulário pode ter mostrado um conflito (409),
                // e o atualizadoEm daqui precisa estar em dia para o cancelamento.
                carregar();
            });

    private final ActivityResultLauncher<Uri> tirarFoto = registerForActivityResult(
            new ActivityResultContracts.TakePicture(), ok -> {
                if (Boolean.TRUE.equals(ok) && fotoPendente != null) {
                    enviar(Arquivos.uri(this, fotoPendente), "foto-comprovante.jpg");
                }
            });

    private final ActivityResultLauncher<PickVisualMediaRequest> escolherImagem = registerForActivityResult(
            new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) enviar(uri, "imagem-comprovante.jpg");
            });

    private final ActivityResultLauncher<String[]> escolherPdf = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) enviar(uri, "comprovante.pdf");
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lancamento_detalhe);
        projeto = ProjetoAtual.ler(getIntent());
        lancamentoId = getIntent().getLongExtra(EXTRA_LANCAMENTO_ID, -1);
        perguntarAnexo = savedInstanceState == null && getIntent().getBooleanExtra(EXTRA_ANEXAR, false);
        if (savedInstanceState != null && savedInstanceState.getString(ESTADO_FOTO) != null) {
            fotoPendente = new File(savedInstanceState.getString(ESTADO_FOTO));
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setSubtitle(projeto.titulo);
        }
        swipe = findViewById(R.id.swipe);
        swipe.setOnRefreshListener(this::carregar);
        estado = new EstadoTela(findViewById(R.id.estado), this::carregar);
        progressoEnvio = findViewById(R.id.progresso_envio);
        findViewById(R.id.tirar_foto).setOnClickListener(v -> abrirCamera());
        findViewById(R.id.escolher_arquivo).setOnClickListener(v -> escolherArquivo());

        carregar();
    }

    private void carregar() {
        if (chamada != null) chamada.cancel();
        if (lancamento == null) estado.carregando();
        chamada = Chamadas.enfileirar(this, GestaoFinanceiraApp.de(this).getApi().lancamento(lancamentoId),
                l -> {
                    swipe.setRefreshing(false);
                    if (l == null) {
                        falhou(getString(R.string.resposta_invalida));
                        return;
                    }
                    lancamento = l;
                    exibir(l);
                    estado.esconder();
                    invalidateOptionsMenu();
                    if (perguntarAnexo && podeAlterar()) {
                        perguntarAnexo = false;
                        perguntarComoAnexar();
                    }
                },
                (mensagem, falha) -> {
                    swipe.setRefreshing(false);
                    falhou(mensagem);
                });
    }

    private void falhou(String mensagem) {
        if (lancamento != null) {
            Snackbar.make(swipe, mensagem, Snackbar.LENGTH_LONG).setAction(R.string.tentar_novamente, v -> carregar()).show();
        } else {
            estado.erro(mensagem);
        }
    }

    private boolean podeAlterar() {
        return lancamento != null && projeto.permissoes.podeLancar() && !Lancamento.CANCELADO.equals(lancamento.situacao);
    }

    private void exibir(Lancamento l) {
        boolean cancelado = Lancamento.CANCELADO.equals(l.situacao);
        boolean receita = Lancamento.RECEITA.equals(l.tipo);
        ((TextView) findViewById(R.id.descricao)).setText(l.descricao);
        TextView valor = findViewById(R.id.valor);
        valor.setText((receita ? "+ " : "− ") + Formatos.moeda(l.valorTotal));
        valor.setTextColor(cancelado ? ContextCompat.getColor(this, R.color.cancelado) : Cores.tipo(this, l.tipo));
        Cores.riscar(valor, cancelado);
        Cores.etiquetaSituacao(findViewById(R.id.situacao), l.situacao);
        findViewById(R.id.aviso_cancelado).setVisibility(cancelado ? View.VISIBLE : View.GONE);

        LinearLayout campos = findViewById(R.id.campos);
        campos.removeAllViews();
        Detalhes.campo(campos, getString(R.string.tipo), Formatos.tipo(l.tipo));
        Detalhes.campo(campos, getString(R.string.data), Formatos.data(l.data));
        Detalhes.campo(campos, getString(R.string.rubrica), l.rubrica == null ? getString(R.string.sem_rubrica) : l.rubrica.nome);
        Detalhes.campo(campos, getString(R.string.quantidade), Formatos.numeroEditavel(l.quantidade, 3));
        Detalhes.campo(campos, getString(R.string.valor_unitario), Formatos.moeda(l.valorUnitario));
        Detalhes.campo(campos, getString(R.string.valor_total), Formatos.moeda(l.valorTotal));
        Detalhes.campo(campos, getString(R.string.criado_por), getString(R.string.autor_data_formato,
                l.criadoPor == null ? "" : l.criadoPor.nome, Formatos.dataHora(l.criadoEm)));
        Detalhes.campo(campos, getString(R.string.atualizado_por), getString(R.string.autor_data_formato,
                l.atualizadoPor == null ? "" : l.atualizadoPor.nome, Formatos.dataHora(l.atualizadoEm)));

        LinearLayout comprovantes = findViewById(R.id.lista_comprovantes);
        comprovantes.removeAllViews();
        boolean semComprovantes = l.comprovantes == null || l.comprovantes.isEmpty();
        findViewById(R.id.sem_comprovantes).setVisibility(semComprovantes ? View.VISIBLE : View.GONE);
        if (!semComprovantes) {
            LayoutInflater inflater = LayoutInflater.from(this);
            for (Comprovante c : l.comprovantes) {
                View item = inflater.inflate(R.layout.item_comprovante, comprovantes, false);
                ((TextView) item.findViewById(R.id.nome)).setText(c.nomeArquivo);
                ((TextView) item.findViewById(R.id.info)).setText(getString(R.string.comprovante_info_formato,
                        Formatos.tamanhoArquivo(c.tamanho), c.enviadoPor == null ? "" : c.enviadoPor.nome,
                        Formatos.dataHora(c.enviadoEm)));
                item.setOnClickListener(v -> abrirComprovante(c));
                comprovantes.addView(item);
            }
        }
        findViewById(R.id.acoes_comprovante).setVisibility(podeAlterar() ? View.VISIBLE : View.GONE);

        LinearLayout historico = findViewById(R.id.lista_historico);
        historico.removeAllViews();
        if (l.historico != null) {
            // Mais recente primeiro, como na tela de histórico do projeto.
            for (int i = l.historico.size() - 1; i >= 0; i--) {
                Movimentacao m = l.historico.get(i);
                historico.addView(Detalhes.movimentacao(historico, m, false));
            }
        }
    }

    // ---------------------------------------------------------------- comprovantes

    private void perguntarComoAnexar() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.anexar_comprovante)
                .setItems(new String[]{getString(R.string.tirar_foto), getString(R.string.escolher_imagem),
                        getString(R.string.escolher_pdf)}, (d, qual) -> {
                    if (qual == 0) abrirCamera();
                    else if (qual == 1) abrirGaleria();
                    else escolherPdf.launch(new String[]{"application/pdf"});
                })
                .setNegativeButton(R.string.agora_nao, null)
                .show();
    }

    private void escolherArquivo() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.escolher_arquivo)
                .setItems(new String[]{getString(R.string.escolher_imagem), getString(R.string.escolher_pdf)}, (d, qual) -> {
                    if (qual == 0) abrirGaleria();
                    else escolherPdf.launch(new String[]{"application/pdf"});
                })
                .show();
    }

    private void abrirGaleria() {
        escolherImagem.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    /** O app de câmera do sistema tira a foto: o app não precisa da permissão CAMERA. */
    private void abrirCamera() {
        if (enviando) return;
        File pasta = new File(Arquivos.pasta(this), "fotos");
        //noinspection ResultOfMethodCallIgnored
        pasta.mkdirs();
        fotoPendente = new File(pasta, "foto-" + System.currentTimeMillis() + ".jpg");
        try {
            tirarFoto.launch(Arquivos.uri(this, fotoPendente));
        } catch (android.content.ActivityNotFoundException e) {
            Snackbar.make(swipe, R.string.sem_camera, Snackbar.LENGTH_LONG).show();
        }
    }

    private void enviar(Uri uri, String nomePadrao) {
        if (enviando) return;
        enviandoComprovante(true);
        executor.execute(() -> {
            Comprovantes.Preparado arquivo = null;
            String erro = null;
            try {
                arquivo = Comprovantes.preparar(getContentResolver(), uri, nomePadrao);
            } catch (Comprovantes.ArquivoInvalido e) {
                erro = e.getMessage();
            } catch (Exception e) {
                erro = getString(R.string.erro_ler_arquivo);
            }
            Comprovantes.Preparado pronto = arquivo;
            String mensagemErro = erro;
            principal.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (pronto == null) {
                    enviandoComprovante(false);
                    Snackbar.make(swipe, mensagemErro, Snackbar.LENGTH_LONG).show();
                } else {
                    subir(pronto);
                }
            });
        });
    }

    private void subir(Comprovantes.Preparado arquivo) {
        RequestBody corpo = RequestBody.create(arquivo.conteudo, MediaType.get(arquivo.mime));
        String nome = URLEncoder.encode(arquivo.nome, StandardCharsets.UTF_8).replace("+", "%20");
        chamadaEnvio = Chamadas.enfileirar(this,
                GestaoFinanceiraApp.de(this).getApi().enviarComprovante(lancamentoId, nome, corpo),
                c -> {
                    enviandoComprovante(false);
                    apagarFotoTemporaria();
                    setResult(RESULT_OK);
                    Snackbar.make(swipe, R.string.comprovante_enviado, Snackbar.LENGTH_SHORT).show();
                    carregar();
                },
                (mensagem, falha) -> {
                    enviandoComprovante(false);
                    // O lançamento já está salvo; só o anexo falhou, então dá para tentar de novo.
                    Snackbar.make(swipe, getString(R.string.erro_envio_formato, mensagem), Snackbar.LENGTH_INDEFINITE)
                            .setAction(R.string.tentar_novamente, v -> {
                                enviandoComprovante(true);
                                subir(arquivo);
                            })
                            .show();
                });
    }

    private void apagarFotoTemporaria() {
        if (fotoPendente != null) {
            //noinspection ResultOfMethodCallIgnored
            fotoPendente.delete();
            fotoPendente = null;
        }
    }

    private void enviandoComprovante(boolean ativo) {
        enviando = ativo;
        progressoEnvio.setVisibility(ativo ? View.VISIBLE : View.GONE);
        findViewById(R.id.tirar_foto).setEnabled(!ativo);
        findViewById(R.id.escolher_arquivo).setEnabled(!ativo);
    }

    private void abrirComprovante(Comprovante c) {
        Snackbar aguarde = Snackbar.make(swipe, R.string.baixando_comprovante, Snackbar.LENGTH_INDEFINITE);
        aguarde.show();
        Arquivos.baixar(this, GestaoFinanceiraApp.de(this).getApi().baixarComprovante(c.id), c.id + "-" + c.nomeArquivo,
                arquivo -> {
                    aguarde.dismiss();
                    if (!Arquivos.abrir(this, arquivo, c.tipo)) Arquivos.compartilhar(this, arquivo, c.tipo);
                },
                mensagem -> {
                    aguarde.dismiss();
                    Snackbar.make(swipe, mensagem, Snackbar.LENGTH_LONG)
                            .setAction(R.string.tentar_novamente, v -> abrirComprovante(c)).show();
                });
    }

    // ---------------------------------------------------------------- editar e cancelar

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_lancamento_detalhe, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean pode = podeAlterar();
        menu.findItem(R.id.acao_editar).setVisible(pode);
        menu.findItem(R.id.acao_cancelar_lancamento).setVisible(pode);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.acao_editar) {
            Intent intent = projeto.colocar(new Intent(this, LancamentoFormActivity.class));
            intent.putExtra(LancamentoFormActivity.EXTRA_LANCAMENTO_ID, lancamentoId);
            editar.launch(intent);
            return true;
        }
        if (item.getItemId() == R.id.acao_cancelar_lancamento) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.cancelar_lancamento)
                    .setMessage(getString(R.string.confirmar_cancelamento_formato, lancamento.descricao,
                            Formatos.moeda(lancamento.valorTotal)))
                    .setNegativeButton(R.string.voltar, null)
                    .setPositiveButton(R.string.cancelar_lancamento, (d, w) -> cancelarLancamento())
                    .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void cancelarLancamento() {
        LancamentoRequest r = new LancamentoRequest();
        r.situacao = Lancamento.CANCELADO;
        r.atualizadoEm = lancamento.atualizadoEm;
        estado.carregando();
        chamada = Chamadas.enfileirar(this, GestaoFinanceiraApp.de(this).getApi().editarLancamento(lancamentoId, r),
                l -> {
                    setResult(RESULT_OK);
                    Snackbar.make(swipe, R.string.lancamento_cancelado, Snackbar.LENGTH_SHORT).show();
                    carregar();
                },
                (mensagem, falha) -> {
                    estado.esconder();
                    if (falha != null && falha.conflito()) carregar();
                    Snackbar.make(swipe, mensagem, Snackbar.LENGTH_LONG).show();
                });
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (fotoPendente != null) outState.putString(ESTADO_FOTO, fotoPendente.getAbsolutePath());
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onDestroy() {
        if (chamada != null) chamada.cancel();
        if (chamadaEnvio != null) chamadaEnvio.cancel();
        executor.shutdown();
        super.onDestroy();
    }
}
