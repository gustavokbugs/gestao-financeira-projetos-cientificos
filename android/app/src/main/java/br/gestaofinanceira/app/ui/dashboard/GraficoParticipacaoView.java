package br.gestaofinanceira.app.ui.dashboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * GP-06: gráfico de rosca com a participação de cada rubrica nas despesas. Desenhado com Canvas
 * para não depender de biblioteca externa. Os percentuais vêm prontos da API.
 */
public class GraficoParticipacaoView extends View {

    private static final float VAO_GRAUS = 1.5f;

    private final Paint fatia = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fundo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textoCentral = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textoSecundario = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF area = new RectF();
    private final List<Float> percentuais = new ArrayList<>();
    private final List<Integer> cores = new ArrayList<>();
    private String centro = "";
    private String legendaCentro = "";

    public GraficoParticipacaoView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        float densidade = getResources().getDisplayMetrics().density;
        fatia.setStyle(Paint.Style.STROKE);
        fatia.setStrokeWidth(22 * densidade);
        fundo.setStyle(Paint.Style.STROKE);
        fundo.setStrokeWidth(22 * densidade);
        fundo.setColor(0x1F888888);
        textoCentral.setTextAlign(Paint.Align.CENTER);
        textoCentral.setFakeBoldText(true);
        textoCentral.setTextSize(18 * getResources().getDisplayMetrics().scaledDensity);
        textoSecundario.setTextAlign(Paint.Align.CENTER);
        textoSecundario.setTextSize(12 * getResources().getDisplayMetrics().scaledDensity);
    }

    /** percentuais em 0–100, na mesma ordem das cores. */
    public void definir(List<Float> novosPercentuais, List<Integer> novasCores, String textoCentro,
                        String textoLegenda, int corTexto, int corTextoSecundario) {
        percentuais.clear();
        percentuais.addAll(novosPercentuais);
        cores.clear();
        cores.addAll(novasCores);
        centro = textoCentro;
        legendaCentro = textoLegenda;
        textoCentral.setColor(corTexto);
        textoSecundario.setColor(corTextoSecundario);
        StringBuilder descricao = new StringBuilder(textoLegenda).append(": ").append(textoCentro);
        setContentDescription(descricao);
        invalidate();
    }

    @Override
    protected void onMeasure(int larguraSpec, int alturaSpec) {
        int largura = MeasureSpec.getSize(larguraSpec);
        int lado = Math.min(largura, (int) (200 * getResources().getDisplayMetrics().density));
        setMeasuredDimension(largura, lado);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float meia = fatia.getStrokeWidth() / 2;
        float lado = Math.min(getWidth(), getHeight()) - 2 * meia;
        float esquerda = (getWidth() - lado) / 2f;
        float topo = (getHeight() - lado) / 2f;
        area.set(esquerda, topo, esquerda + lado, topo + lado);
        canvas.drawOval(area, fundo);

        float soma = 0;
        for (Float p : percentuais) soma += Math.max(0, p);
        float inicio = -90;
        if (soma > 0) {
            for (int i = 0; i < percentuais.size(); i++) {
                float graus = 360f * Math.max(0, percentuais.get(i)) / Math.max(100f, soma);
                if (graus <= 0) continue;
                fatia.setColor(cores.get(i % cores.size()));
                float desenho = graus > VAO_GRAUS * 2 && percentuais.size() > 1 ? graus - VAO_GRAUS : graus;
                canvas.drawArc(area, inicio, desenho, false, fatia);
                inicio += graus;
            }
        }
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        canvas.drawText(centro, cx, cy, textoCentral);
        canvas.drawText(legendaCentro, cx, cy + textoSecundario.getTextSize() * 1.4f, textoSecundario);
    }
}
