package br.gestaofinanceira.app.ui.relatorios;

import android.app.Activity;
import android.view.View;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.LinkedHashMap;
import java.util.Map;

import br.gestaofinanceira.app.GestaoFinanceiraApp;
import br.gestaofinanceira.app.R;
import br.gestaofinanceira.app.ui.common.Arquivos;
import br.gestaofinanceira.app.ui.lancamentos.Filtros;

/**
 * GP-08: relatório resumido em PDF ou planilha (CSV, abre no Excel) gerado pela API,
 * com os filtros da tela. Depois de baixar, o usuário abre ou compartilha o arquivo.
 */
public final class Relatorios {

    private Relatorios() {
    }

    public static void exportar(Activity tela, View ancora, long projetoId, String codigoOuTitulo, Filtros filtros) {
        String[] opcoes = {tela.getString(R.string.relatorio_pdf), tela.getString(R.string.relatorio_planilha)};
        String mensagemFiltros = filtros != null && filtros.algumAtivo()
                ? tela.getString(R.string.relatorio_com_filtros) : null;
        MaterialAlertDialogBuilder dialogo = new MaterialAlertDialogBuilder(tela)
                .setTitle(mensagemFiltros == null ? tela.getString(R.string.relatorio_titulo)
                        : tela.getString(R.string.relatorio_titulo) + " (" + mensagemFiltros + ")")
                .setItems(opcoes, (d, qual) -> baixar(tela, ancora, projetoId, codigoOuTitulo, filtros, qual == 0))
                .setNegativeButton(R.string.cancelar, null);
        dialogo.show();
    }

    private static void baixar(Activity tela, View ancora, long projetoId, String nomeBase, Filtros filtros, boolean pdf) {
        Map<String, String> q = filtros == null ? new LinkedHashMap<>() : filtros.paraConsulta();
        q.put("formato", pdf ? "pdf" : "csv");
        String mime = pdf ? "application/pdf" : "text/csv";
        String nome = (pdf ? "relatorio-" : "lancamentos-") + nomeBase.replaceAll("\\s+", "-") + (pdf ? ".pdf" : ".csv");

        Snackbar aguarde = Snackbar.make(ancora, R.string.gerando_relatorio, Snackbar.LENGTH_INDEFINITE);
        aguarde.show();
        Arquivos.baixar(tela, GestaoFinanceiraApp.de(tela).getApi().relatorio(projetoId, q), nome,
                arquivo -> {
                    aguarde.dismiss();
                    new MaterialAlertDialogBuilder(tela)
                            .setTitle(R.string.relatorio_pronto)
                            .setMessage(arquivo.getName())
                            .setNeutralButton(R.string.fechar, null)
                            .setNegativeButton(R.string.abrir, (d, w) -> {
                                if (!Arquivos.abrir(tela, arquivo, mime)) {
                                    Snackbar.make(ancora, R.string.sem_app_para_abrir, Snackbar.LENGTH_LONG).show();
                                }
                            })
                            .setPositiveButton(R.string.compartilhar, (d, w) -> Arquivos.compartilhar(tela, arquivo, mime))
                            .show();
                },
                mensagem -> {
                    aguarde.dismiss();
                    Snackbar.make(ancora, mensagem, Snackbar.LENGTH_LONG)
                            .setAction(R.string.tentar_novamente, v -> baixar(tela, ancora, projetoId, nomeBase, filtros, pdf))
                            .show();
                });
    }
}
