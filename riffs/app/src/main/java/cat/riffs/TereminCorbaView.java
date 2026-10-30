package cat.riffs;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import java.util.List;

/**
 * Dibuix d'un gest de teremin: el temps de costat (amb els compassos i els temps marcats) i el to amunt i avall
 * (una línia per semitò, el nom a cada Do). El camí del dit és una línia més gruixuda i viva com més fort sonava.
 */
public class TereminCorbaView extends View {

    private Gest gest;
    private double posSonant = -1;   // on sona (en compassos), o -1
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;
    private final float ampleNoms;

    public TereminCorbaView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        ampleNoms = 52 * d;
        p.setTypeface(Ui.MITJA);
        p.setStrokeCap(Paint.Cap.ROUND);
    }

    public void posa(Gest g) {
        gest = g;
        invalidate();
    }

    /** On sona ara, en compassos des de l'inici (-1 = no sona). */
    public void posaSonant(double compassos) {
        posSonant = compassos;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (gest == null) return;
        float w = getWidth(), h = getHeight();
        float x0 = ampleNoms, ample = w - x0 - 8 * d;
        double[] m = gest.marges();
        double baix = Math.floor(m[0]) - 2, dalt = Math.ceil(m[1]) + 2;
        if (dalt - baix < 12) {
            double mig = (baix + dalt) / 2;
            baix = mig - 6;
            dalt = mig + 6;
        }
        double fBaix = baix, fDalt = dalt;
        java.util.function.DoubleUnaryOperator y = n -> (float) (h - 6 * d - (n - fBaix) / (fDalt - fBaix) * (h - 12 * d));
        p.setStyle(Paint.Style.FILL);
        p.setColor(Ui.SUPERFICIE);
        c.drawRect(x0, 0, x0 + ample, h, p);
        // semitons
        p.setTextSize(11 * d);
        for (int n = (int) Math.ceil(baix); n <= dalt; n++) {
            float yy = (float) y.applyAsDouble(n);
            boolean esDo = Math.floorMod(n, 12) == 0;
            p.setColor(esDo ? Ui.LINIA_FORTA : Melodia.esNegra(n) ? 0xFF1B1B20 : Ui.LINIA);
            c.drawRect(x0, yy - (esDo ? 0.8f : 0.4f) * d, x0 + ample, yy + (esDo ? 0.8f : 0.4f) * d, p);
            if (esDo || (dalt - baix <= 18 && !Melodia.esNegra(n))) {
                p.setColor(esDo ? Ui.TEXT : Ui.APAGAT);
                c.drawText(Melodia.nomNota(n), 6 * d, yy + 4 * d, p);
            }
        }
        // compassos i temps
        int temps = gest.compas == 6 ? 2 : Math.max(1, gest.compas);
        for (int k = 0; k <= gest.compassos * temps; k++) {
            float x = x0 + ample * k / (float) (gest.compassos * temps);
            boolean comp = k % temps == 0;
            p.setColor(comp ? Ui.alfa(Ui.TEXT, 100) : Ui.alfa(Ui.TEXT, 30));
            float g = (comp ? 1.6f : 0.8f) * d;
            c.drawRect(x - g / 2, 0, x + g / 2, h, p);
        }
        // el camí del dit
        p.setStyle(Paint.Style.STROKE);
        for (List<Gest.Punt> tr : gest.trossos) {
            for (int i = 1; i < tr.size(); i++) {
                Gest.Punt a = tr.get(i - 1), b = tr.get(i);
                double vol = (a.vol + b.vol) / 2;
                p.setStrokeWidth((float) (2 + 9 * vol) * d);
                p.setColor(Ui.alfa(Ui.TEREMIN, (int) (90 + 165 * vol)));
                c.drawLine(x0 + (float) (a.t / gest.compassos) * ample, (float) y.applyAsDouble(a.nota),
                        x0 + (float) (b.t / gest.compassos) * ample, (float) y.applyAsDouble(b.nota), p);
            }
        }
        p.setStyle(Paint.Style.FILL);
        if (gest.buit()) {
            p.setColor(Ui.SUAU);
            p.setTextSize(14 * d);
            String t = "Encara no hi ha res gravat: encén el botó de gravar de la pista i mantén premut el play";
            c.drawText(t, x0 + Math.max(8 * d, (ample - p.measureText(t)) / 2), h / 2, p);
        }
        // on sona
        if (posSonant >= 0) {
            float x = x0 + (float) (posSonant / gest.compassos) * ample;
            p.setColor(Ui.TEXT);
            c.drawRect(x - 1 * d, 0, x + 1 * d, h, p);
        }
    }
}
