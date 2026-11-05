package br.gestaofinanceira.app.ui.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.math.BigDecimal;

public class FormatosTest {

    private static String semEspacoFixo(String s) {
        return s.replace(' ', ' ').replace(' ', ' ');
    }

    @Test
    public void moedaNoPadraoBrasileiro() {
        assertEquals("R$ 1.600,50", semEspacoFixo(Formatos.moeda(new BigDecimal("1600.5"))));
        assertEquals("R$ 0,00", semEspacoFixo(Formatos.moeda(null)));
        assertEquals("-R$ 350,00", semEspacoFixo(Formatos.moeda(new BigDecimal("-350"))));
    }

    @Test
    public void moedaSempreTemEspacoDepoisDoSimbolo() {
        // Em qualquer versão do Android/ICU: nunca "R$1.600,50".
        assertEquals(false, Formatos.moeda(new BigDecimal("1600.5")).matches(".*R\\$\\d.*"));
    }

    @Test
    public void lerNumeroAceitaFormatosComuns() {
        assertEquals(0, new BigDecimal("1234.56").compareTo(Formatos.lerNumero("1.234,56")));
        assertEquals(0, new BigDecimal("1234.56").compareTo(Formatos.lerNumero("1234,56")));
        assertEquals(0, new BigDecimal("1234.56").compareTo(Formatos.lerNumero("R$ 1.234,56")));
        assertEquals(0, new BigDecimal("1234.56").compareTo(Formatos.lerNumero("1234.56")));
        assertEquals(0, new BigDecimal("1234567").compareTo(Formatos.lerNumero("1.234.567")));
        assertEquals(0, new BigDecimal("4").compareTo(Formatos.lerNumero("4")));
    }

    @Test
    public void lerNumeroRejeitaTextoInvalido() {
        assertNull(Formatos.lerNumero(""));
        assertNull(Formatos.lerNumero(null));
        assertNull(Formatos.lerNumero("abc"));
        assertNull(Formatos.lerNumero("1,2,3"));
    }

    @Test
    public void numeroEditavelVoltaIgualAoLer() {
        BigDecimal valor = new BigDecimal("1600.50");
        assertEquals("1.600,50", Formatos.numeroEditavel(valor, 2));
        assertEquals(0, valor.compareTo(Formatos.lerNumero(Formatos.numeroEditavel(valor, 2))));
        // Quantidade sem separador de milhar: "1000" não pode virar 1,000.
        BigDecimal qtd = new BigDecimal("1000");
        assertEquals("1000", Formatos.numeroEditavel(qtd, 3));
        assertEquals(0, qtd.compareTo(Formatos.lerNumero(Formatos.numeroEditavel(qtd, 3))));
        assertEquals("2,5", Formatos.numeroEditavel(new BigDecimal("2.5"), 3));
    }

    @Test
    public void totalEstimadoArredondaComoOServidor() {
        assertEquals(new BigDecimal("1280.00"), Formatos.totalEstimado(new BigDecimal("4"), new BigDecimal("320")));
        assertEquals(new BigDecimal("3.33"), Formatos.totalEstimado(new BigDecimal("0.333"), new BigDecimal("10")));
        assertNull(Formatos.totalEstimado(null, BigDecimal.ONE));
    }

    @Test
    public void datas() {
        assertEquals("01/03/2026", Formatos.data("2026-03-01"));
        assertEquals("", Formatos.data(null));
        assertEquals("texto", Formatos.data("texto"));
    }

    @Test
    public void rotulos() {
        assertEquals("Realizado", Formatos.situacao("REALIZADO"));
        assertEquals("Despesa", Formatos.tipo("DESPESA"));
        assertEquals("Coordenador", Formatos.papel("coordenador"));
        assertEquals("Cancelamento", Formatos.acao("CANCELAMENTO"));
        assertEquals("Valor unitário", Formatos.campo("valorUnitario"));
        assertEquals("1,5 MB", Formatos.tamanhoArquivo(1572864));
    }
}
