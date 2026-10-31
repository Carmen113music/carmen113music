package cat.riffs;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

/**
 * Superfície per tocar el teremin: una quadrícula amb un punt al mig. Amb el dit a la dreta del punt sona més agut,
 * a l'esquerra més greu (una línia per semitò, més marcada a cada Do, i el nom de cada nota blanca; les franges
 * fosques són les notes negres); amunt més fort, avall més fluix.
 * Només compta un dit. Si «notes justes» està encès, el to salta a la nota més propera (amb el glide del so).
 */
public class TereminView extends View {

    public interface Oient {
        /** {@code posat}: el dit es posa ara; després, a cada moviment, {@code posat} = false. */
        void toca(double nota, double vol, boolean posat, long nanos);

        void aixeca(long nanos);
    }

    /** Semitons a cada costat del punt del mig. */
    public static final int RANG = 12;
    private static final String[] NOMS = {"Do", "", "Re", "", "Mi", "Fa", "", "Sol", "", "La", "", "Si"};

    private int centre = 72;
    private boolean justes = false;
    private Oient oient;
    private int dit = -1;
    private float xd = -1, yd = -1;
    private double notaAra, volAra;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;

    public TereminView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        p.setTypeface(Ui.MITJA);
    }

    public void posaOient(Oient o) {
        oient = o;
    }

    public void posaCentre(int nota) {
        centre = Math.max(Melodia.NOTA_MIN + RANG, Math.min(Melodia.NOTA_MAX - RANG, nota));
        invalidate();
    }

    public int centre() {
        return centre;
    }

    public void posaJustes(boolean j) {
        justes = j;
    }

    public boolean justes() {
        return justes;
    }

    private double notaDeX(float x) {
        double n = centre + (x / Math.max(1, getWidth()) - 0.5) * 2 * RANG;
        return justes ? Math.round(n) : n;
    }

    private double volDeY(float y) {
        return Math.max(0, Math.min(1, 1 - y / Math.max(1, getHeight())));
    }

    private float xDeNota(double n) {
        return (float) ((0.5 + (n - centre) / (2.0 * RANG)) * getWidth());
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        p.setStyle(Paint.Style.FILL);
        p.setColor(Ui.SUPERFICIE);
        c.drawRoundRect(0, 0, w, h, 14 * d, 14 * d, p);
        // línies de volum (cada quart)
        p.setColor(Ui.LINIA);
        for (int k = 1; k < 4; k++) c.drawRect(0, h * k / 4f - 0.5f * d, w, h * k / 4f + 0.5f * d, p);
        // franges de les notes negres (com les tecles d'un piano): ocupen la zona on sona aquella nota
        float ampleSemi = w / (2f * RANG);
        p.setColor(0xFF111114);
        for (int n = centre - RANG; n <= centre + RANG; n++) {
            if (!Melodia.esNegra(n)) continue;
            float x = xDeNota(n);
            c.drawRect(Math.max(0, x - ampleSemi / 2), 0, Math.min(w, x + ampleSemi / 2), h, p);
        }
        // una línia per semitò (més forta a cada Do)
        for (int n = centre - RANG; n <= centre + RANG; n++) {
            float x = xDeNota(n);
            boolean esDo = Math.floorMod(n, 12) == 0;
            p.setColor(esDo ? Ui.alfa(Ui.TEXT, 110) : Melodia.esNegra(n) ? Ui.alfa(Ui.TEXT, 18) : Ui.alfa(Ui.TEXT, 40));
            float g = (esDo ? 1.5f : 0.8f) * d;
            c.drawRect(x - g / 2, 0, x + g / 2, h, p);
        }
        // el nom de cada nota blanca, a baix i a dalt (2.29); primer els Do, i els altres només si no els trepitgen
        java.util.List<float[]> ocupat = new java.util.ArrayList<>();
        for (int passada = 0; passada < 2; passada++) {
            for (int n = centre - RANG; n <= centre + RANG; n++) {
                if (Melodia.esNegra(n)) continue;
                boolean esDo = Math.floorMod(n, 12) == 0;
                if (esDo != (passada == 0)) continue;
                String t = esDo ? Melodia.nomNota(n) : NOMS[Math.floorMod(n, 12)];
                p.setTextSize((esDo ? 12 : 11) * d);
                float tw = p.measureText(t);
                float tx = Math.max(4 * d, Math.min(w - tw - 4 * d, xDeNota(n) - tw / 2));
                boolean xoca = false;
                for (float[] o : ocupat) if (tx < o[1] + 3 * d && tx + tw > o[0] - 3 * d) xoca = true;
                if (xoca) continue;
                ocupat.add(new float[]{tx, tx + tw});
                p.setColor(esDo ? Ui.TEXT : n == centre ? Ui.TEREMIN : Ui.SUAU);
                c.drawText(t, tx, h - 8 * d, p);
                c.drawText(t, tx, 30 * d, p);   // i també a dalt, perquè el dit no les tapi
            }
        }
        // rètols de volum
        p.setColor(Ui.APAGAT);
        p.setTextSize(11 * d);
        c.drawText("fort", 8 * d, 14 * d, p);
        c.drawText("fluix", 8 * d, h - 26 * d, p);
        // el punt del mig
        p.setColor(Ui.alfa(Ui.TEREMIN, 110));
        c.drawCircle(w / 2f, h / 2f, 7 * d, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.5f * d);
        c.drawCircle(w / 2f, h / 2f, 14 * d, p);
        p.setStyle(Paint.Style.FILL);
        // el dit
        if (dit >= 0) {
            float x = justes ? xDeNota(notaAra) : xd;
            p.setColor(Ui.alfa(Ui.TEREMIN, 60));
            c.drawRect(x - 1 * d, 0, x + 1 * d, h, p);
            c.drawRect(0, yd - 1 * d, w, yd + 1 * d, p);
            float radi = (float) (14 + 22 * volAra) * d;
            p.setColor(Ui.alfa(Ui.TEREMIN, 90));
            c.drawCircle(x, yd, radi, p);
            p.setColor(Ui.TEREMIN);
            c.drawCircle(x, yd, 8 * d, p);
            // nota i desafinació
            long cents = Math.round((notaAra - Math.round(notaAra)) * 100);
            String t = Melodia.nomNota((int) Math.round(notaAra)) + (cents == 0 ? "" : String.format(" %+d", cents));
            p.setTextSize(15 * d);
            p.setColor(Ui.TEXT);
            float tx = Math.min(w - p.measureText(t) - 8 * d, x + radi + 6 * d);
            c.drawText(t, Math.max(8 * d, tx), Math.max(20 * d, yd - radi - 6 * d), p);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        int acc = ev.getActionMasked();
        long nanos = ev.getEventTime() * 1_000_000L;
        switch (acc) {
            case MotionEvent.ACTION_DOWN: {
                dit = ev.getPointerId(0);
                getParent().requestDisallowInterceptTouchEvent(true);
                mou(ev.getX(0), ev.getY(0), true, nanos);
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                int i = ev.findPointerIndex(dit);
                if (dit < 0 || i < 0) return true;
                // tots els punts que hi ha hagut entre dos esdeveniments (el dit es mou de pressa)
                for (int h = 0; h < ev.getHistorySize(); h++) {
                    mou(ev.getHistoricalX(i, h), ev.getHistoricalY(i, h), false, ev.getHistoricalEventTime(h) * 1_000_000L);
                }
                mou(ev.getX(i), ev.getY(i), false, nanos);
                return true;
            }
            case MotionEvent.ACTION_POINTER_UP:
                if (ev.getPointerId(ev.getActionIndex()) != dit) return true;
                // el dit que tocava s'aixeca: s'acaba (els altres dits no compten)
                aixeca(nanos);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dit >= 0) aixeca(nanos);
                return true;
            default:
                return true;
        }
    }

    private void mou(float x, float y, boolean posat, long nanos) {
        xd = Math.max(0, Math.min(getWidth(), x));
        yd = Math.max(0, Math.min(getHeight(), y));
        notaAra = notaDeX(xd);
        volAra = volDeY(yd);
        invalidate();
        if (oient != null) oient.toca(notaAra, volAra, posat, nanos);
    }

    private void aixeca(long nanos) {
        dit = -1;
        invalidate();
        if (oient != null) oient.aixeca(nanos);
    }
}
