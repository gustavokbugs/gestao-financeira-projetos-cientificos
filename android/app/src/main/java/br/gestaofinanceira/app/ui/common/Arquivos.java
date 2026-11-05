package br.gestaofinanceira.app.ui.common;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import br.gestaofinanceira.app.R;
import br.gestaofinanceira.app.data.remote.ApiErros;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;

/**
 * Baixa arquivos da API (relatórios, comprovantes) para o cache do app e os entrega a outro app
 * com FileProvider, sem pedir permissão de armazenamento.
 */
public final class Arquivos {

    public interface AoBaixar {
        void baixado(File arquivo);
    }

    public interface AoFalhar {
        void falhou(String mensagem);
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler PRINCIPAL = new Handler(Looper.getMainLooper());

    private Arquivos() {
    }

    public static File pasta(Activity tela) {
        File pasta = new File(tela.getCacheDir(), "compartilhados");
        //noinspection ResultOfMethodCallIgnored
        pasta.mkdirs();
        return pasta;
    }

    /** Executa a chamada fora da thread principal e grava o corpo em cache/compartilhados/nome. */
    public static void baixar(Activity tela, Call<ResponseBody> chamada, String nome, AoBaixar ok, AoFalhar erro) {
        File destino = new File(pasta(tela), nomeSeguro(nome));
        EXECUTOR.execute(() -> {
            String mensagemErro = null;
            try {
                Response<ResponseBody> resposta = chamada.execute();
                if (!resposta.isSuccessful() || resposta.body() == null) {
                    mensagemErro = resposta.code() == 404 || resposta.code() == 501
                            ? tela.getString(R.string.arquivo_indisponivel)
                            : ApiErros.mensagem(resposta);
                } else {
                    try (InputStream in = resposta.body().byteStream(); OutputStream out = new FileOutputStream(destino)) {
                        byte[] buffer = new byte[16 * 1024];
                        int lidos;
                        while ((lidos = in.read(buffer)) != -1) out.write(buffer, 0, lidos);
                    }
                }
            } catch (Exception e) {
                mensagemErro = ApiErros.mensagem(e);
                //noinspection ResultOfMethodCallIgnored
                destino.delete();
            }
            String finalErro = mensagemErro;
            PRINCIPAL.post(() -> {
                if (chamada.isCanceled() || tela.isFinishing() || tela.isDestroyed()) return;
                if (finalErro == null) ok.baixado(destino);
                else erro.falhou(finalErro);
            });
        });
    }

    public static Uri uri(Activity tela, File arquivo) {
        return FileProvider.getUriForFile(tela, tela.getPackageName() + ".arquivos", arquivo);
    }

    public static void compartilhar(Activity tela, File arquivo, String mime) {
        Intent envio = new Intent(Intent.ACTION_SEND)
                .setType(mime)
                .putExtra(Intent.EXTRA_STREAM, uri(tela, arquivo))
                .putExtra(Intent.EXTRA_SUBJECT, arquivo.getName())
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        tela.startActivity(Intent.createChooser(envio, tela.getString(R.string.compartilhar)));
    }

    /** Abre com o app padrão. Retorna false se não houver app para o tipo. */
    public static boolean abrir(Activity tela, File arquivo, String mime) {
        Intent ver = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri(tela, arquivo), mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            tela.startActivity(ver);
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        }
    }

    static String nomeSeguro(String nome) {
        String limpo = nome == null ? "" : nome.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        return limpo.isEmpty() ? "arquivo" : limpo;
    }
}
