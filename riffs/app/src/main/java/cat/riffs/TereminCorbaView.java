package cat.riffs;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

import java.util.List;

/**
 * Dibuix d'un gest de teremin: el temps de costat (amb els compassos i els temps marcats) i el to amunt i avall
 * (una línia per semitò, el nom a cada Do). El camí del dit és una línia més gruixuda i viva com més fort sonava.
 * El gest no s'edita: un dit no fa res. Dos dits (2.29, com a tots els editors): arrossegar = desplaçar-se,
 * obrir/tancar de costat = zoom del temps, amunt i avall = zoom del to.
 */
public class TereminCorbaView extends View {

    private Gest gest;
    private double posSonant = -1;   // on sona (en compassos), o -1
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;
    private final float ampleNoms;
    /** Vista: temps (en compassos) i to (notes de baix i de dalt). Sense posar = tot el gest. */
    private double iniciC = 0, visiblesC = -1, baixV = 0, daltV = -1;
    // dos dits
    private static final int DD_NOSE = 0, DD_MOU = 1, DD_ZOOM_H = 2, DD_ZOOM_V = 3;
    private boolean dosDits;
    private int modeDD;
    private float sepX0, sepY0, cx0, cy0;
    private double iniciC0, visiblesC0, baix0, dalt0;

    public TereminCorbaView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        ampleNoms = 52 * d;
        p.setTypeface(Ui.MITJA);
        p.setStrokeCap(Paint.Cap.ROUND);
    }

    public void posa(Gest g) {
        boolean primer = gest == null;
        gest = g;
        if (primer) {
            visiblesC = -1;
            daltV = -1;
        }
        limita();
        invalidate();
    }

    /** Marges del to per defecte (tot el gest, mínim una octava). */
    private double[] margesGest() {
        double[] m = gest.marges();
        double baix = Math.floor(m[0]) - 2, dalt = Math.ceil(m[1]) + 2;
        if (dalt - baix < 12) {
            double mig = (baix + dalt) / 2;
            baix = mig - 6;
            dalt = mig + 6;
        }
        return new double[]{baix, dalt};
    }

    private void limita() {
        if (gest == null) return;
        if (visiblesC <= 0) {
            iniciC = 0;
            visiblesC = gest.compassos;
        }
        double min = 1.0 / Math.max(1, gest.compas == 6 ? 2 : gest.compas);
        visiblesC = Math.max(min, Math.min(gest.compassos, visiblesC));
        iniciC = Math.max(0, Math.min(gest.compassos - visiblesC, iniciC));
        if (daltV <= baixV) {
            double[] m = margesGest();
            baixV = m[0];
            daltV = m[1];
        }
        double span = Math.max(6, Math.min(72, daltV - baixV));
        double mig = Math.max(Melodia.NOTA_MIN, Math.min(Melodia.NOTA_MAX, (baixV + daltV) / 2));
        baixV = mig - span / 2;
        daltV = mig + span / 2;
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
        limita();
        double baix = baixV, dalt = daltV;
        double fBaix = baix, fDalt = dalt;
        double fIni = iniciC, fVis = visiblesC;
        java.util.function.DoubleUnaryOperator xT = t -> x0 + (t - fIni) / fVis * ample;
        c.save();
        c.clipRect(0, 0, w, h);
        java.util.function.DoubleUnaryOperator y = n -> (float) (h - 6 * d - (n - fBaix) / (fDalt - fBaix) * (h - 12 * d));
        p.setStyle(Paint.Style.FILL);
        p.setColor(Ui.SUPERFICIE);
        c.drawRect(x0, 0, x0 + ample, h, p);
        // semitons (els noms van a la columna de l'esquerra, que no es tapa)
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
        c.save();
        c.clipRect(x0, 0, x0 + ample, h);
        for (int k = 0; k <= gest.compassos * temps; k++) {
            float x = (float) xT.applyAsDouble(k / (double) temps);
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
                c.drawLine((float) xT.applyAsDouble(a.t), (float) y.applyAsDouble(a.nota),
                        (float) xT.applyAsDouble(b.t), (float) y.applyAsDouble(b.nota), p);
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
            float x = (float) xT.applyAsDouble(posSonant);
            p.setColor(Ui.TEXT);
            c.drawRect(x - 1 * d, 0, x + 1 * d, h, p);
        }
        c.restore();
        c.restore();
    }

    // ---------- Dos dits: desplaçar i zoom ----------

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (gest == null) return false;
        int acc = ev.getActionMasked();
        if (acc == MotionEvent.ACTION_DOWN) {
            getParent().requestDisallowInterceptTouchEvent(true);
            return true;   // un dit no fa res, però ens quedem el gest per si arriba el segon
        }
        if ((acc == MotionEvent.ACTION_POINTER_DOWN && ev.getPointerCount() == 2)
                || (acc == MotionEvent.ACTION_POINTER_UP && ev.getPointerCount() == 3)) {
            dosDits = true;
            modeDD = DD_NOSE;
            sepX0 = Math.abs(ev.getX(0) - ev.getX(1));
            sepY0 = Math.abs(ev.getY(0) - ev.getY(1));
            cx0 = (ev.getX(0) + ev.getX(1)) / 2f;
            cy0 = (ev.getY(0) + ev.getY(1)) / 2f;
            iniciC0 = iniciC;
            visiblesC0 = visiblesC;
            baix0 = baixV;
            dalt0 = daltV;
            return true;
        }
        if (acc == MotionEvent.ACTION_MOVE && dosDits && ev.getPointerCount() >= 2) {
            float sepX = Math.abs(ev.getX(0) - ev.getX(1));
            float sepY = Math.abs(ev.getY(0) - ev.getY(1));
            float cx = (ev.getX(0) + ev.getX(1)) / 2f;
            float cy = (ev.getY(0) + ev.getY(1)) / 2f;
            if (modeDD == DD_NOSE) {
                if (Math.abs(sepX - sepX0) > 36 * d) modeDD = DD_ZOOM_H;
                else if (Math.abs(sepY - sepY0) > 36 * d) modeDD = DD_ZOOM_V;
                else if (Math.hypot(cx - cx0, cy - cy0) > 10 * d) modeDD = DD_MOU;
                else return true;
                sepX0 = sepX;
                sepY0 = sepY;
            }
            float ample = Math.max(1, getWidth() - ampleNoms - 8 * d), alt = Math.max(1, getHeight() - 12 * d);
            if (modeDD == DD_MOU) {
                iniciC = iniciC0 - (cx - cx0) / ample * visiblesC0;
                double dn = (cy - cy0) / alt * (dalt0 - baix0);
                baixV = baix0 + dn;
                daltV = dalt0 + dn;
            } else if (modeDD == DD_ZOOM_H && sepX > 10 * d) {
                double tCentre = iniciC0 + (cx0 - ampleNoms) / ample * visiblesC0;
                visiblesC = visiblesC0 * sepX0 / sepX;
                limita();
                iniciC = tCentre - (cx - ampleNoms) / ample * visiblesC;
            } else if (modeDD == DD_ZOOM_V && sepY > 10 * d) {
                double mig = (baix0 + dalt0) / 2;
                double span = Math.max(6, Math.min(72, (dalt0 - baix0) * sepY0 / sepY));
                baixV = mig - span / 2;
                daltV = mig + span / 2;
            }
            limita();
            invalidate();
            return true;
        }
        if (acc == MotionEvent.ACTION_UP || acc == MotionEvent.ACTION_CANCEL) {
            dosDits = false;
            getParent().requestDisallowInterceptTouchEvent(false);
        }
        return true;
    }
}
