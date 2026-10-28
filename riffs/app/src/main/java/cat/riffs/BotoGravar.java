package cat.riffs;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/**
 * El botó gran de gravar: un cercle vermell amb un punt (aturat) o un quadrat (gravant).
 * Mentre grava, un anell al voltant creix amb el volum.
 */
public class BotoGravar extends View {

    private final Paint ple = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint anell = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF quadrat = new RectF();
    private boolean gravant = false;
    private float nivell = 0;    // 0..1, ja suavitzat

    public BotoGravar(Context c) {
        super(c);
        anell.setStyle(Paint.Style.FILL);
        setClickable(true);
        setFocusable(true);
        setContentDescription("Grava");
    }

    public void setGravant(boolean g) {
        gravant = g;
        nivell = 0;
        setContentDescription(g ? "Atura" : "Grava");
        invalidate();
    }

    /** Nivell nou (0..1). Puja de seguida i baixa a poc a poc, perquè no faci pampallugues. */
    public void setNivell(float pic) {
        // escala aproximada a decibels perquè els sons fluixos també es vegin
        float db = pic <= 0.0001f ? -60f : (float) (20 * Math.log10(pic));
        float v = Math.max(0f, Math.min(1f, (db + 50f) / 50f));
        nivell = v > nivell ? v : nivell * 0.85f + v * 0.15f;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        float maxim = Math.min(w, h) / 2f;
        float radi = maxim * 0.72f;

        if (gravant) {
            anell.setColor(Ui.ACCENT);
            anell.setAlpha(70);
            c.drawCircle(cx, cy, radi + (maxim - radi) * nivell, anell);
        }
        ple.setColor(isPressed() ? 0xFFB8412A : Ui.ACCENT);
        c.drawCircle(cx, cy, radi, ple);

        ple.setColor(0xFFFFFFFF);
        if (gravant) {
            float m = radi * 0.38f;
            quadrat.set(cx - m, cy - m, cx + m, cy + m);
            c.drawRoundRect(quadrat, m * 0.25f, m * 0.25f, ple);
        } else {
            c.drawCircle(cx, cy, radi * 0.30f, ple);
        }
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        invalidate();
    }
}
