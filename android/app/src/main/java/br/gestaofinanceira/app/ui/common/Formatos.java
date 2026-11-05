package br.gestaofinanceira.app.ui.common;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Formatação de valores no padrão brasileiro. */
public final class Formatos {

    public static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy", PT_BR);
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", PT_BR);

    private Formatos() {
    }

    public static String moeda(BigDecimal valor) {
        return NumberFormat.getCurrencyInstance(PT_BR).format(valor == null ? BigDecimal.ZERO : valor);
    }

    public static String percentual(Double valor) {
        return valor == null ? "-" : String.format(PT_BR, "%.1f%%", valor);
    }

    /** "2026-03-01" → "01/03/2026". */
    public static String data(String iso) {
        try {
            return LocalDate.parse(iso).format(DATA);
        } catch (Exception e) {
            return iso == null ? "" : iso;
        }
    }

    /** Instante ISO-8601 (UTC) → data e hora no fuso do aparelho. */
    public static String dataHora(String iso) {
        try {
            return Instant.parse(iso).atZone(ZoneId.systemDefault()).format(DATA_HORA);
        } catch (Exception e) {
            return iso == null ? "" : iso;
        }
    }

    public static String papel(String papel) {
        if (papel == null) return "";
        switch (papel) {
            case "coordenador":
                return "Coordenador";
            case "gestor":
                return "Gestor";
            case "consulta":
                return "Consulta";
            default:
                return papel;
        }
    }

    /** Número no formato brasileiro para campos de edição: 1600.5 → "1.600,50" (sem "R$"). */
    public static String numeroEditavel(BigDecimal valor, int casas) {
        if (valor == null) return "";
        NumberFormat f = NumberFormat.getNumberInstance(PT_BR);
        f.setMinimumFractionDigits(casas == 3 ? 0 : casas);
        f.setMaximumFractionDigits(casas);
        // Sem separador de milhar na quantidade: "1.000" seria lido de volta como 1,000.
        f.setGroupingUsed(casas != 3);
        return f.format(valor);
    }

    /**
     * Lê um valor digitado em pt-BR: "1.234,56", "1234,56", "R$ 1.234,56" ou "1234.56".
     * Retorna null se não for um número válido.
     */
    public static BigDecimal lerNumero(String texto) {
        if (texto == null) return null;
        String s = texto.replace("R$", "").replace("\u00a0", "").replace(" ", "").trim();
        if (s.isEmpty()) return null;
        if (s.contains(",")) {
            s = s.replace(".", "").replace(",", ".");
        } else if (s.indexOf('.') != s.lastIndexOf('.')) {
            s = s.replace(".", ""); // "1.234.567" sem vírgula: pontos são milhares
        }
        if (!s.matches("-?\\d+(\\.\\d+)?")) return null;
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
