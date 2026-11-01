package cat.riffs;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;

/**
 * Regla de compassos i temps a dalt de les pistes. Tocar-la posa el capçal.
 * 2.36: mantenir-la premuda (vibra) i arrossegar marca els compassos del bucle; mantenir premut sense moure
 * dins el bucle l'encén o l'apaga.
 */
public class RegleView extends View {

    public interface OientCapcal {
        void capcal(long ms, boolean acabat);
    }

    public interface OientBucle {
        /** S'han marcat els compassos de {@code de} (des de 0) fins abans de {@code a}. */
        void marca(int de, int a);

        /** Mantenir premut sense moure dins el bucle que ja hi ha: encendre'l o apagar-lo. */
        void alterna();

        /** Mentre s'arrossega (per escriure a dalt quins compassos). */
        default void marcant(int de, int a) {}
    }

    static final long TEMPS_PREMUT = 400;
    static final int COLOR_BUCLE = Ui.GUITARRA;

    private final Projecte projecte;
    private final Vista vista;
    private final Paint linia = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint capcal = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path triangle = new Path();
    private OientCapcal oient;
    private OientBucle oientBucle;
    private final Paint bucle = new Paint(Paint.ANTI_ALIAS_FLAG);
    // gest
    private float x0, y0;
    private boolean marcant = false, mogut = false;
    private long capcalAbans;
    private int c0 = -1, selDe = -1, selA = -1;
    private final Runnable premut = this::comencaBucle;

    public RegleView(Context c, Projecte p, Vista v) {
        super(c);
        projecte = p;
        vista = v;
        text.setColor(Ui.SUAU);
        text.setTextSize(Ui.dp(c, 11));
        text.setTypeface(Ui.MITJA);
        capcal.setColor(Ui.TEXT);
        setBackgroundColor(Ui.SUPERFICIE);
    }

    public void setOientCapcal(OientCapcal o) {
        oient = o;
    }

    public void setOientBucle(OientBucle o) {
        oientBucle = o;
    }

    private int compasA(float x) {
        return (int) Math.max(0, Math.floor(vista.ms(x) / projecte.msPerCompas()));
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        double mpt = projecte.msPerTemps();
        int compas = projecte.compas;
        float pxTemps = (float) (mpt * vista.pxPerMs);
        // amb molt poc zoom, només numera cada 2, 4, 8... compassos
        int cadaCompas = 1;
        while (pxTemps * compas * cadaCompas < Ui.dp(getContext(), 34)) cadaCompas *= 2;

        long primer = (long) Math.floor(vista.ms(0) / mpt);
        long darrer = (long) Math.ceil(vista.ms(w) / mpt);
        for (long t = Math.max(0, primer); t <= darrer; t++) {
            float x = vista.x(t * mpt);
            boolean esCompas = t % compas == 0;
            long numCompas = t / compas + 1;
            if (esCompas) {
                boolean numera = (numCompas - 1) % cadaCompas == 0;
                linia.setColor(numera ? 0x66FFFFFF : 0x33FFFFFF);
                linia.setStrokeWidth(Ui.dp(getContext(), 1));
                c.drawLine(x, numera ? h * 0.35f : h * 0.6f, x, h, linia);
                if (numera) c.drawText(Long.toString(numCompas), x + Ui.dp(getContext(), 4), h * 0.5f, text);
            } else if (pxTemps > Ui.dp(getContext(), 7)) {
                linia.setColor(0x26FFFFFF);
                linia.setStrokeWidth(Ui.dp(getContext(), 1));
                c.drawLine(x, h * 0.72f, x, h, linia);
            }
        }
        // bucle (o el que s'està marcant)
        int de = marcant ? selDe : projecte.bucleDe, a = marcant ? selA : projecte.bucleA;
        if (de >= 0 && a > de) {
            boolean actiu = marcant || projecte.bucleActivat;
            float xa = vista.x(de * projecte.msPerCompas()), xb = vista.x(a * projecte.msPerCompas());
            int col = actiu ? COLOR_BUCLE : Ui.SUAU;
            bucle.setStyle(Paint.Style.FILL);
            bucle.setColor((col & 0x00FFFFFF) | (actiu ? 0x40000000 : 0x26000000));
            c.drawRect(xa, 0, xb, h, bucle);
            bucle.setColor(col);
            float g = Ui.dp(getContext(), 3);
            c.drawRect(xa, 0, xb, g, bucle);                       // franja de dalt
            c.drawRect(xa, 0, xa + Ui.dp(getContext(), 2), h, bucle);   // vores
            c.drawRect(xb - Ui.dp(getContext(), 2), 0, xb, h, bucle);
        }

        // línia de baix
        linia.setColor(Ui.LINIA);
        c.drawLine(0, h - 0.5f, w, h - 0.5f, linia);

        // capçal
        float xc = vista.x(vista.capcalMs);
        if (xc >= -20 && xc <= w + 20) {
            float m = Ui.dp(getContext(), 7);
            triangle.reset();
            triangle.moveTo(xc - m, h - m * 1.4f);
            triangle.lineTo(xc + m, h - m * 1.4f);
            triangle.lineTo(xc, h);
            triangle.close();
            c.drawPath(triangle, capcal);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (oient == null) return super.onTouchEvent(e);
        int a = e.getActionMasked();
        float x = e.getX();
        switch (a) {
            case MotionEvent.ACTION_DOWN:
                x0 = x;
                y0 = e.getY();
                mogut = false;
                marcant = false;
                capcalAbans = vista.capcalMs;
                getParent().requestDisallowInterceptTouchEvent(true);
                if (oientBucle != null) postDelayed(premut, TEMPS_PREMUT);
                oient.capcal(Math.max(0, Math.round(vista.ms(x))), false);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!mogut && (Math.abs(x - x0) > Ui.dp(getContext(), 8) || Math.abs(e.getY() - y0) > Ui.dp(getContext(), 8))) {
                    mogut = true;
                    if (!marcant) removeCallbacks(premut);
                }
                if (marcant) {
                    int c = compasA(x);
                    selDe = Math.min(c0, c);
                    selA = Math.max(c0, c) + 1;
                    if (oientBucle != null) oientBucle.marcant(selDe, selA);
                    invalidate();
                } else {
                    oient.capcal(Math.max(0, Math.round(vista.ms(x))), false);
                }
                return true;
            case MotionEvent.ACTION_UP:
                removeCallbacks(premut);
                if (marcant) {
                    marcant = false;
                    boolean nomesUn = selA - selDe == 1 && selDe == c0;
                    boolean dins = projecte.teBucle() && c0 >= projecte.bucleDe && c0 < projecte.bucleA;
                    if (oientBucle != null) {
                        if (nomesUn && dins && compasA(x) == c0) oientBucle.alterna();
                        else oientBucle.marca(selDe, selA);
                    }
                    invalidate();
                    performClick();
                    return true;
                }
                oient.capcal(Math.max(0, Math.round(vista.ms(x))), true);
                performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(premut);
                if (marcant) {
                    marcant = false;
                    vista.capcalMs = capcalAbans;
                    invalidate();
                }
                return true;
            default:
                return true;
        }
    }

    /** Mantenir premut sense moure: comença a marcar el bucle (el capçal torna on era). */
    private void comencaBucle() {
        if (mogut || oientBucle == null) return;
        marcant = true;
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        vista.capcalMs = capcalAbans;
        oient.capcal(capcalAbans, false);
        c0 = compasA(x0);
        selDe = c0;
        selA = c0 + 1;
        oientBucle.marcant(selDe, selA);
        invalidate();
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
