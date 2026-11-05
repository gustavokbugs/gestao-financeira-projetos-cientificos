package br.gestaofinanceira.app;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;

import android.view.View;

import org.hamcrest.Matcher;

/**
 * As respostas do Retrofit chegam em outra thread, que o Espresso não acompanha.
 * Este helper repete a verificação por alguns segundos até a tela mudar.
 */
public final class Aguardar {

    private Aguardar() {
    }

    public static void ver(Matcher<View> alvo) {
        long limite = System.currentTimeMillis() + 10_000;
        while (true) {
            try {
                onView(alvo).check(matches(isDisplayed()));
                return;
            } catch (Throwable e) {
                if (System.currentTimeMillis() > limite) throw e;
                try {
                    Thread.sleep(150);
                } catch (InterruptedException ignorado) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
