package br.gestaofinanceira.app.ui.common;

import android.app.Activity;
import android.content.Context;
import android.widget.EditText;

import androidx.annotation.StringRes;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.CompositeDateValidator;
import com.google.android.material.datepicker.DateValidatorPointBackward;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import br.gestaofinanceira.app.R;

/** Utilidades dos formulários: datas, confirmação (GP-09) e erros de validação vindos da API. */
public final class Formularios {

    public interface AoEscolherData {
        void escolhida(String isoData);
    }

    private Formularios() {
    }

    public static String texto(EditText campo) {
        return campo.getText() == null ? "" : campo.getText().toString().trim();
    }

    /** Calendário limitado ao intervalo [inicio, fim] (datas ISO; qualquer um pode ser null). */
    public static void escolherData(FragmentActivity tela, @StringRes int titulo, String atual,
                                    String inicio, String fim, AoEscolherData aoEscolher) {
        CalendarConstraints.Builder limites = new CalendarConstraints.Builder();
        List<CalendarConstraints.DateValidator> validadores = new ArrayList<>();
        Long min = milis(inicio);
        Long max = milis(fim);
        if (min != null) {
            limites.setStart(min);
            validadores.add(DateValidatorPointForward.from(min));
        }
        if (max != null) {
            limites.setEnd(max);
            validadores.add(DateValidatorPointBackward.before(max));
        }
        if (!validadores.isEmpty()) limites.setValidator(CompositeDateValidator.allOf(validadores));

        Long selecao = milis(atual);
        if (selecao == null) {
            long hoje = milis(LocalDate.now().toString());
            selecao = (min != null && hoje < min) ? min : (max != null && hoje > max) ? max : hoje;
        }
        limites.setOpenAt(selecao);

        MaterialDatePicker<Long> seletor = MaterialDatePicker.Builder.datePicker()
                .setTitleText(titulo)
                .setSelection(selecao)
                .setCalendarConstraints(limites.build())
                .build();
        seletor.addOnPositiveButtonClickListener(ms -> aoEscolher.escolhida(iso(ms)));
        seletor.show(tela.getSupportFragmentManager(), "data");
    }

    /** O MaterialDatePicker trabalha com meia-noite em UTC. */
    public static Long milis(String iso) {
        if (iso == null || iso.isEmpty()) return null;
        try {
            return LocalDate.parse(iso).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        } catch (Exception e) {
            return null;
        }
    }

    public static String iso(long milisUtc) {
        return Instant.ofEpochMilli(milisUtc).atZone(ZoneOffset.UTC).toLocalDate().toString();
    }

    public static void confirmar(Context ctx, @StringRes int titulo, CharSequence mensagem,
                                 @StringRes int botao, Runnable aoConfirmar) {
        new MaterialAlertDialogBuilder(ctx)
                .setTitle(titulo)
                .setMessage(mensagem)
                .setNegativeButton(R.string.cancelar, null)
                .setPositiveButton(botao, (d, w) -> aoConfirmar.run())
                .show();
    }

    /** Pergunta antes de descartar o que foi digitado. */
    public static void confirmarDescarte(Activity tela) {
        new MaterialAlertDialogBuilder(tela)
                .setTitle(R.string.descartar_titulo)
                .setMessage(R.string.descartar_mensagem)
                .setNegativeButton(R.string.continuar_editando, null)
                .setPositiveButton(R.string.descartar, (d, w) -> tela.finish())
                .show();
    }

    /**
     * Liga os erros 422 da API aos campos. Retorna as mensagens que não têm campo na tela,
     * para exibir em outro lugar (Snackbar ou diálogo).
     */
    public static List<String> aplicarErros(Map<String, String> erros, Map<String, TextInputLayout> campos) {
        List<String> semCampo = new ArrayList<>();
        boolean focou = false;
        for (Map.Entry<String, String> e : erros.entrySet()) {
            TextInputLayout campo = campos.get(e.getKey());
            if (campo != null) {
                campo.setError(e.getValue());
                if (!focou) {
                    campo.requestFocus();
                    focou = true;
                }
            } else {
                semCampo.add(e.getValue());
            }
        }
        return semCampo;
    }

    /** Apaga o erro de cada campo assim que o usuário volta a digitar nele. */
    public static void limparAoDigitar(Map<String, TextInputLayout> campos) {
        for (TextInputLayout campo : campos.values()) {
            EditText texto = campo.getEditText();
            if (texto == null) continue;
            texto.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int a, int b, int c) {
                }

                @Override
                public void onTextChanged(CharSequence s, int a, int b, int c) {
                    if (campo.getError() != null) campo.setError(null);
                }

                @Override
                public void afterTextChanged(android.text.Editable s) {
                }
            });
        }
    }

    public static void limparErros(Map<String, TextInputLayout> campos) {
        for (TextInputLayout campo : campos.values()) campo.setError(null);
    }
}
