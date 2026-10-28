package cat.riffs;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * Botó de gravar, com el d'una gravadora professional: un anell clar i, a dins,
 * un cercle vermell que es transforma en un quadrat arrodonit mentre grava.
 */
public class BotoGravar extends View {

    private final Paint anell = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ple = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private float forma = 0;        // 0 = cercle, 1 = quadrat
    private boolean gravant = false;
    private ValueAnimator anim;

    public BotoGravar(Context c) {
        super(c);
        anell.setStyle(Paint.Style.STROKE);
        anell.setStrokeWidth(Ui.dp(c, 4));
        anell.setColor(0xFFE9E7EE);
        ple.setColor(Ui.ACCENT);
        setClickable(true);
        setFocusable(true);
        setContentDescription("Grava");
    }

    public boolean gravant() {
        return gravant;
    }

    public void setGravant(boolean g) {
        if (g == gravant) return;
        gravant = g;
        setContentDescription(g ? "Atura" : "Grava");
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(forma, g ? 1f : 0f);
        anim.setDuration(220);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(a -> {
            forma = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        float ext = Math.min(w, h) / 2f - anell.getStrokeWidth() / 2f - 1;
        c.drawCircle(cx, cy, ext, anell);

        float radi = ext - anell.getStrokeWidth() - Ui.dp(getContext(), 3);
        float costat = radi + (radi * 0.48f - radi) * forma;      // mig costat
        float cantonada = costat + (radi * 0.16f - costat) * forma;
        ple.setColor(isPressed() ? 0xFFC94030 : Ui.ACCENT);
        r.set(cx - costat, cy - costat, cx + costat, cy + costat);
        c.drawRoundRect(r, cantonada, cantonada, ple);
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        invalidate();
    }
}
