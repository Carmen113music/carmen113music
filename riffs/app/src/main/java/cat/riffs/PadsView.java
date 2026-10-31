package cat.riffs;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.SparseIntArray;
import android.view.MotionEvent;
import android.view.View;

/**
 * Pads d'acords (com els del Cubase): 12 rajoles, una octava. A baix, 7 pads (posicions de Do, Re, Mi, Fa, Sol, La, Si);
 * a dalt, 5 pads desplaçats com les tecles negres (Do#, Re#, Fa#, Sol#, La#). Els pads es compten d'esquerra a dreta
 * com en un piano: 1 = Do, 2 = Do#, 3 = Re… Cada pad fa sonar el seu acord sencer; es poden tocar diversos pads alhora.
 * En mode «edita», tocar un pad avisa per canviar-ne l'acord en lloc de fer-lo sonar.
 */
public class PadsView extends View {

    public interface Oient {
        /** Toca (o edita, si {@code edita}) el pad {@code pad} (0–11). */
        void edita(int pad);
    }

    private static final int[] BAIX = {0, 2, 4, 5, 7, 9, 11};
    private static final int[] DALT = {1, 3, 6, 8, 10};
    /** Posició a dalt (en amples de pad de baix, des de l'esquerra) del centre de cada pad negre. */
    private static final float[] CENTRES_DALT = {1, 2, 4, 5, 6};

    private Acords acords = Acords.perDefecte();
    private int doBase = 48;
    private TeclatView.OientPoli oientPoli;
    private Oient oient;
    private boolean edita = false;
    private int color = Ui.MIDI;

    private final SparseIntArray padDeDit = new SparseIntArray();
    private final java.util.Set<Integer> premuts = new java.util.HashSet<>();
    private final java.util.Map<Integer, Integer> comptes = new java.util.HashMap<>();

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float d;

    public PadsView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        p.setTypeface(Ui.MITJA);
        setHapticFeedbackEnabled(true);
    }

    public void posaOientPoli(TeclatView.OientPoli o) {
        oientPoli = o;
    }

    public void posaOient(Oient o) {
        oient = o;
    }

    public void posaAcords(Acords a) {
        deixaTot(android.os.SystemClock.uptimeMillis() * 1_000_000L);
        acords = a;
        invalidate();
    }

    public Acords acords() {
        return acords;
    }

    /** El Do de l'octava on sonen les arrels. */
    public void posaBase(int doEsquerra) {
        deixaTot(android.os.SystemClock.uptimeMillis() * 1_000_000L);
        doBase = Math.max(Melodia.NOTA_MIN + 12, Math.min(Melodia.NOTA_MAX - 24, doEsquerra));
        invalidate();
    }

    public int base() {
        return doBase;
    }

    public void posaEdita(boolean e) {
        deixaTot(android.os.SystemClock.uptimeMillis() * 1_000_000L);
        edita = e;
        invalidate();
    }

    public boolean edita() {
        return edita;
    }

    public void posaColor(int c) {
        color = c;
        invalidate();
    }

    /** Deixa de sonar tot. */
    public void deixaTot(long nanos) {
        for (int pad : new java.util.ArrayList<>(premuts)) deixa(pad, nanos);
        premuts.clear();
        padDeDit.clear();
        invalidate();
    }

    private void prem(int pad, long nanos) {
        for (int n : acords.pads[pad].notes(doBase)) {
            int c = comptes.getOrDefault(n, 0);
            comptes.put(n, c + 1);
            if (c == 0 && oientPoli != null) oientPoli.encen(n, nanos);
        }
    }

    private void deixa(int pad, long nanos) {
        for (int n : acords.pads[pad].notes(doBase)) {
            int c = comptes.getOrDefault(n, 0);
            if (c <= 1) {
                comptes.remove(n);
                if (c == 1 && oientPoli != null) oientPoli.apaga(n, nanos);
            } else {
                comptes.put(n, c - 1);
            }
        }
    }

    // ---------- Geometria ----------

    private float amplePad() {
        return getWidth() / 7f;
    }

    private float altFila() {
        return getHeight() / 2f;
    }

    /** El rectangle d'un pad (0–11). */
    private void rect(int pad, RectF out) {
        float w = amplePad(), h = altFila(), m = 4 * d;
        int ib = indexDe(BAIX, pad);
        if (ib >= 0) {
            out.set(ib * w + m, h + m, (ib + 1) * w - m, 2 * h - m);
            return;
        }
        int id = indexDe(DALT, pad);
        float cx = CENTRES_DALT[id] * w;
        out.set(cx - w / 2 + m, m, cx + w / 2 - m, h - m);
    }

    private static int indexDe(int[] v, int x) {
        for (int i = 0; i < v.length; i++) if (v[i] == x) return i;
        return -1;
    }

    /** Quin pad hi ha en aquest punt (-1 = cap). */
    private int padA(float x, float y) {
        for (int pad = 0; pad < Acords.PADS; pad++) {
            rect(pad, r);
            r.inset(-3 * d, -3 * d);
            if (r.contains(x, y)) return pad;
        }
        return -1;
    }

    // ---------- Dibuix ----------

    @Override
    protected void onDraw(Canvas c) {
        p.setStyle(Paint.Style.FILL);
        for (int pad = 0; pad < Acords.PADS; pad++) {
            rect(pad, r);
            Acords.Acord a = acords.pads[pad];
            boolean premut = premuts.contains(pad);
            p.setColor(premut ? color : a.buit() ? Ui.SUPERFICIE : Ui.SUPERFICIE2);
            c.drawRoundRect(r, 10 * d, 10 * d, p);
            // vora (més marcada en mode edita)
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth((edita ? 1.6f : 1f) * d);
            p.setColor(edita ? Ui.alfa(color, 200) : Ui.LINIA_FORTA);
            c.drawRoundRect(r, 10 * d, 10 * d, p);
            p.setStyle(Paint.Style.FILL);
            // número del pad, petit, a dalt a l'esquerra
            p.setTextSize(10 * d);
            p.setColor(premut ? Ui.alfa(Ui.SOBRE_COLOR, 160) : Ui.APAGAT);
            c.drawText(String.valueOf(pad + 1), r.left + 8 * d, r.top + 15 * d, p);
            // nom de l'acord, gran, al mig
            String nom = a.buit() ? (edita ? "+" : "—") : a.nom();
            float mida = Math.min(22 * d, r.height() * 0.32f);
            p.setTextSize(mida);
            while (mida > 9 * d && p.measureText(nom) > r.width() - 12 * d) {
                mida -= 1 * d;
                p.setTextSize(mida);
            }
            p.setColor(premut ? Ui.SOBRE_COLOR : a.buit() ? Ui.APAGAT : Ui.TEXT);
            c.drawText(nom, r.centerX() - p.measureText(nom) / 2, r.centerY() + mida * 0.36f, p);
        }
    }

    // ---------- Toc ----------

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        int acc = ev.getActionMasked();
        long nanos = ev.getEventTime() * 1_000_000L;
        if (edita) {
            if (acc == MotionEvent.ACTION_DOWN) {
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }
            if (acc == MotionEvent.ACTION_UP) {
                int pad = padA(ev.getX(), ev.getY());
                if (pad >= 0 && oient != null) oient.edita(pad);
            }
            return true;
        }
        switch (acc) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int i = ev.getActionIndex();
                padDeDit.put(ev.getPointerId(i), padA(ev.getX(i), ev.getY(i)));
                getParent().requestDisallowInterceptTouchEvent(true);
                break;
            }
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < ev.getPointerCount(); i++) {
                    int id = ev.getPointerId(i);
                    if (padDeDit.indexOfKey(id) >= 0) padDeDit.put(id, padA(ev.getX(i), ev.getY(i)));
                }
                break;
            case MotionEvent.ACTION_POINTER_UP:
                padDeDit.delete(ev.getPointerId(ev.getActionIndex()));
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                padDeDit.clear();
                break;
            default:
                return true;
        }
        java.util.Set<Integer> ara = new java.util.HashSet<>();
        for (int k = 0; k < padDeDit.size(); k++) {
            int pad = padDeDit.valueAt(k);
            if (pad >= 0 && !acords.pads[pad].buit()) ara.add(pad);
        }
        for (int pad : new java.util.ArrayList<>(premuts)) if (!ara.contains(pad)) deixa(pad, nanos);
        boolean nou = false;
        for (int pad : ara) {
            if (!premuts.contains(pad)) {
                prem(pad, nanos);
                nou = true;
            }
        }
        if (!ara.equals(premuts)) {
            premuts.clear();
            premuts.addAll(ara);
            if (nou) performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            invalidate();
        }
        return true;
    }
}
