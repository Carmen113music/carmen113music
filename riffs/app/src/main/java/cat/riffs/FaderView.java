package cat.riffs;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/**
 * Fader vertical gros de la taula de so, amb el vúmetre al costat.
 * Va de -∞ (-60 dB) a +6 dB; el 0 dB queda a tres quarts d'alçada i hi ha més precisió a prop del 0.
 * Arrossegar mou el fader (sense saltar on toques); doble toc el torna a 0 dB.
 */
public class FaderView extends View {

    public interface Oient {
        void canvi(double db, boolean acabat);
    }

    private static final double MAX_DB = 6, MIN_DB = Projecte.SILENCI_DB;
    private static final double[] MARQUES = {6, 0, -6, -12, -24, -48};

    private double db;
    private int color;
    private Oient oient;

    // vúmetre (en dB): el que es veu cau a poc a poc; el pic es queda una estona
    private float nivellDb = -100, picDb = -100;
    private long picQuan;

    private final Paint ple = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    private float y0;
    private double db0;
    private long darrerToc;
    private boolean cedit;

    public FaderView(Context c, double db, int color) {
        super(c);
        this.db = db;
        this.color = color;
        text.setTextSize(Ui.dp(c, 8));
        text.setColor(Ui.APAGAT);
        text.setTextAlign(Paint.Align.RIGHT);
    }

    public void setOient(Oient o) {
        oient = o;
    }

    public double db() {
        return db;
    }

    // ---------- Escala ----------

    /** Posició (0 = baix, 1 = dalt) d'un valor en dB. */
    public static double posicio(double db) {
        if (db >= 0) return 0.75 + Math.min(MAX_DB, db) / MAX_DB * 0.25;
        if (db <= MIN_DB) return 0;
        return 0.75 * (1 - Math.sqrt(-db / -MIN_DB));
    }

    /** Valor en dB d'una posició (0 = baix, 1 = dalt). */
    public static double dbDe(double pos) {
        pos = Math.max(0, Math.min(1, pos));
        if (pos >= 0.75) return (pos - 0.75) / 0.25 * MAX_DB;
        double k = 1 - pos / 0.75;
        return pos <= 0.001 ? MIN_DB : -(-MIN_DB) * k * k;
    }

    /** Pic (0..1) que ha sonat ara a la pista. */
    public void setNivell(float pic) {
        float d = pic <= 0.00001f ? -100 : (float) (20 * Math.log10(pic));
        if (d >= nivellDb) nivellDb = d;
        else nivellDb = Math.max(d, nivellDb - 1.2f);     // cau suau
        long ara = System.currentTimeMillis();
        if (d >= picDb || ara - picQuan > 1500) {
            picDb = d;
            picQuan = ara;
        }
        invalidate();
    }

    /** Pic més alt dels darrers segons (dB), per mostrar-lo en número. */
    public float picDb() {
        return picDb;
    }

    // ---------- Dibuix ----------

    private float dp(float v) {
        return Ui.dp(getContext(), v);
    }

    private float yDe(double pos, float dalt, float baix) {
        return (float) (baix - pos * (baix - dalt));
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float dalt = dp(12), baix = h - dp(12);
        float xMig = w * 0.48f;

        // marques de l'escala
        ple.setColor(Ui.LINIA_FORTA);
        for (double m : MARQUES) {
            float y = yDe(posicio(m), dalt, baix);
            c.drawRect(xMig - dp(10), y - 0.5f, xMig + dp(10), y + 0.5f, ple);
            c.drawText(m > 0 ? "+" + (int) m : String.valueOf((int) m), xMig - dp(14), y + dp(3), text);
        }

        // vúmetre (a la dreta del fader)
        float am = dp(6), xm = w - am - dp(3);
        r.set(xm, dalt, xm + am, baix);
        ple.setColor(Ui.SUPERFICIE2);
        c.drawRoundRect(r, dp(2), dp(2), ple);
        if (nivellDb > MIN_DB) {
            float y = yDe(posicio(nivellDb), dalt, baix);
            float y0db = yDe(posicio(0), dalt, baix);
            float yM6 = yDe(posicio(-6), dalt, baix);
            ple.setColor(0xFF4FC97A);   // verd
            c.drawRect(xm, Math.max(y, yM6), xm + am, baix, ple);
            if (y < yM6) {
                ple.setColor(Ui.GUITARRA);   // ambre de -6 a 0
                c.drawRect(xm, Math.max(y, y0db), xm + am, yM6, ple);
            }
            if (y < y0db) {
                ple.setColor(Ui.ACCENT);     // vermell per sobre de 0
                c.drawRect(xm, y, xm + am, y0db, ple);
            }
        }
        if (picDb > MIN_DB) {
            float yp = yDe(posicio(picDb), dalt, baix);
            ple.setColor(picDb > 0 ? Ui.ACCENT : Ui.TEXT);
            c.drawRect(xm, yp - dp(1), xm + am, yp + dp(1), ple);
        }

        // guia del fader
        ple.setColor(Ui.SUPERFICIE2);
        r.set(xMig - dp(2.5f), dalt, xMig + dp(2.5f), baix);
        c.drawRoundRect(r, dp(2.5f), dp(2.5f), ple);
        float yf = yDe(posicio(db), dalt, baix);
        ple.setColor(Ui.alfa(color, 150));
        r.set(xMig - dp(2.5f), yf, xMig + dp(2.5f), baix);
        c.drawRoundRect(r, dp(2.5f), dp(2.5f), ple);

        // capçal del fader
        r.set(xMig - dp(13), yf - dp(10), xMig + dp(13), yf + dp(10));
        ple.setColor(0xFF3A3A43);
        c.drawRoundRect(r, dp(4), dp(4), ple);
        ple.setColor(Ui.TEXT);
        c.drawRect(xMig - dp(10), yf - dp(1), xMig + dp(10), yf + dp(1), ple);
    }

    // ---------- Tocs ----------

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float alt = getHeight() - dp(24);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                cedit = false;
                y0 = e.getY();
                db0 = db;
                return true;
            case MotionEvent.ACTION_MOVE: {
                if (alt <= 0 || cedit) return true;
                double pos = posicio(db0) - (e.getY() - y0) / alt;
                double nou = dbDe(pos);
                if (Math.abs(nou) < 0.5) nou = 0;   // s'enganxa a 0 dB
                if (nou != db) {
                    db = nou;
                    invalidate();
                    if (oient != null) oient.canvi(db, false);
                }
                return true;
            }
            case MotionEvent.ACTION_UP: {
                if (cedit) {
                    cedit = false;
                    return true;
                }
                long ara = System.currentTimeMillis();
                boolean quiet = Math.abs(e.getY() - y0) < dp(6);
                if (quiet && ara - darrerToc < 320) {
                    db = 0;                       // doble toc: 0 dB
                    invalidate();
                    darrerToc = 0;
                } else {
                    darrerToc = quiet ? ara : 0;
                }
                if (oient != null) oient.canvi(db, true);
                return true;
            }
            case MotionEvent.ACTION_POINTER_DOWN:
                // un segon dit: és per desplaçar la taula, no per moure el fader. Torna on era i cedeix el gest.
                if (db != db0) {
                    db = db0;
                    invalidate();
                    if (oient != null) oient.canvi(db, false);
                }
                cedit = true;
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            case MotionEvent.ACTION_CANCEL:
                cedit = false;
                if (oient != null) oient.canvi(db, true);
                return true;
            default:
                return true;
        }
    }
}
