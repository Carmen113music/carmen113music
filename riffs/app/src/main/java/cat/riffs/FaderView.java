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

    // ---------- Dibuix (2.37: estil Cubase) ----------

    private float dp(float v) {
        return Ui.dp(getContext(), v);
    }

    private float yDe(double pos, float dalt, float baix) {
        return (float) (baix - pos * (baix - dalt));
    }

    /** Escala del fader, com al Cubase: els números són dB per sota de 0 (sense el signe). */
    private static final double[] ESCALA = {6, 0, -5, -10, -15, -20, -30, -40};
    /** Escala del vúmetre. */
    private static final double[] ESCALA_VU = {0, -6, -12, -18, -24, -30, -40, -50};
    private final android.graphics.Path cami = new android.graphics.Path();

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float dalt = dp(14), baix = h - dp(14);
        // zones: [números del fader][guia + tapa][números del vúmetre][vúmetre]
        float xGuia = w * 0.40f;
        float tw = dp(13), th = dp(17);          // mitja amplada i mitja alçada de la tapa
        float xNum = xGuia - tw - dp(3);          // els números, a l'esquerra de la tapa
        float am = dp(7), xm = w - am - dp(4);
        float xNumVu = xm - dp(3);

        // fons de la zona del fader i de la del vúmetre (com dues ranures)
        ple.setColor(0xFF121216);
        r.set(dp(2), dalt - dp(8), xGuia + w * 0.2f, baix + dp(8));
        c.drawRoundRect(r, dp(4), dp(4), ple);
        r.set(xNumVu - dp(16), dalt - dp(8), w - dp(2), baix + dp(8));
        c.drawRoundRect(r, dp(4), dp(4), ple);

        // escala del fader (a l'esquerra)
        text.setTextAlign(Paint.Align.RIGHT);
        text.setTextSize(dp(8));
        for (double m : ESCALA) {
            float y = yDe(posicio(m), dalt, baix);
            ple.setColor(m == 0 ? 0xFF9A9AA6 : 0xFF4A4A55);
            c.drawRect(xGuia - dp(8), y - 0.5f, xGuia - dp(4), y + 0.5f, ple);
            text.setColor(m == 0 ? Ui.TEXT : Ui.SUAU);
            String t = m > 0 ? String.valueOf((int) m) : m == 0 ? "0" : String.valueOf((int) -m);
            c.drawText(t, xNum, y + dp(3), text);
        }
        text.setColor(Ui.SUAU);
        c.drawText("∞", xNum, baix + dp(3), text);

        // escala del vúmetre
        for (double m : ESCALA_VU) {
            float y = yDe(posicio(m), dalt, baix);
            text.setColor(Ui.APAGAT);
            c.drawText(m == 0 ? "0" : String.valueOf((int) -m), xNumVu, y + dp(3), text);
        }

        // vúmetre
        r.set(xm, dalt, xm + am, baix);
        ple.setColor(0xFF07070A);
        c.drawRect(r, ple);
        if (nivellDb > MIN_DB) {
            float y = yDe(posicio(nivellDb), dalt, baix);
            float y0db = yDe(posicio(0), dalt, baix);
            float yM6 = yDe(posicio(-6), dalt, baix);
            ple.setColor(0xFF2FC2E8);   // blau cel, com el Cubase
            c.drawRect(xm, Math.max(y, yM6), xm + am, baix, ple);
            if (y < yM6) {
                ple.setColor(Ui.GUITARRA);
                c.drawRect(xm, Math.max(y, y0db), xm + am, yM6, ple);
            }
            if (y < y0db) {
                ple.setColor(Ui.ACCENT);
                c.drawRect(xm, y, xm + am, y0db, ple);
            }
        }
        if (picDb > MIN_DB) {
            float yp = yDe(posicio(picDb), dalt, baix);
            ple.setColor(picDb > 0 ? Ui.ACCENT : Ui.TEXT);
            c.drawRect(xm, yp - dp(0.8f), xm + am, yp + dp(0.8f), ple);
        }

        // guia del fader: una ranura negra
        ple.setColor(0xFF050507);
        r.set(xGuia - dp(2), dalt, xGuia + dp(2), baix);
        c.drawRoundRect(r, dp(2), dp(2), ple);
        ple.setColor(0xFF2A2A31);
        c.drawRect(xGuia + dp(2), dalt, xGuia + dp(2.6f), baix, ple);

        // tapa del fader (blava amb ratlles, com al Cubase)
        float yf = yDe(posicio(db), dalt, baix);
        r.set(xGuia - tw, yf - th, xGuia + tw, yf + th);
        ple.setShader(new android.graphics.LinearGradient(0, yf - th, 0, yf + th,
                new int[]{0xFFE6F6FF, 0xFF9BD8F7, 0xFF5BB8EC, 0xFF3C9AD6},
                new float[]{0f, 0.35f, 0.7f, 1f}, android.graphics.Shader.TileMode.CLAMP));
        c.drawRoundRect(r, dp(3), dp(3), ple);
        ple.setShader(null);
        ple.setStyle(Paint.Style.STROKE);
        ple.setStrokeWidth(dp(1));
        ple.setColor(0xFF1E5E8A);
        c.drawRoundRect(r, dp(3), dp(3), ple);
        ple.setStyle(Paint.Style.FILL);
        // ratlles d'agafar
        ple.setColor(0x6614405F);
        for (int k = -3; k <= 3; k++) {
            if (k == 0) continue;
            float y = yf + k * dp(4);
            c.drawRect(xGuia - tw + dp(3), y - 0.5f, xGuia + tw - dp(3), y + 0.5f, ple);
        }
        // línia del mig (on és el valor)
        ple.setColor(0xFF0B2233);
        c.drawRect(xGuia - tw + dp(1), yf - dp(1), xGuia + tw - dp(1), yf + dp(1), ple);
        // un toc del color de la pista a la tapa
        ple.setColor(color);
        r.set(xGuia - tw + dp(1), yf - th + dp(1), xGuia - tw + dp(4), yf + th - dp(1));
        c.drawRect(r, ple);
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
