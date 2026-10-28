package cat.riffs;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Un punt per cada temps del compàs; s'encén el que sona. El primer és una mica més gran. */
public class PuntsTemps extends View {

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int compas = 4;
    private int actiu = -1;
    private int color = Ui.ACCENT;

    public PuntsTemps(Context c) {
        super(c);
    }

    public void setCompas(int n) {
        compas = Math.max(1, n);
        invalidate();
    }

    public void setActiu(int index, int color) {
        if (index == actiu && color == this.color) return;
        actiu = index;
        this.color = color;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float h = getHeight();
        float r = h / 2f * 0.62f;
        float pas = h * 1.6f;
        float total = (compas - 1) * pas;
        float x0 = getWidth() / 2f - total / 2f;
        for (int i = 0; i < compas; i++) {
            boolean a = i == actiu;
            p.setColor(a ? color : Ui.LINIA_FORTA);
            float radi = (i == 0 ? r * 1.25f : r) * (a ? 1.15f : 1f);
            c.drawCircle(x0 + i * pas, h / 2f, radi, p);
        }
    }
}
