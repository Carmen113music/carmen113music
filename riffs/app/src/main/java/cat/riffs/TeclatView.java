package cat.riffs;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.SparseIntArray;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Teclat virtual de dues octaves (de Do a Do, 15 tecles blanques) per tocar en directe.
 * Monofònic (sinte): sona l'última tecla premuda; si la deixes i encara en tens una altra de premuda, torna a sonar
 * aquella. Polifònic (MIDI): sona cada tecla premuda; amb els acords encesos, cada tecla fa sonar el seu acord sencer.
 * Arrossegar el dit per les tecles en canvia la nota.
 */
public class TeclatView extends View {

    public interface Oient {
        /**
         * La nota que sona ha canviat. {@code nota} &lt; 0 = ja no en sona cap. {@code lligada} = venia d'una altra
         * nota sense aixecar el dit (legato). {@code nanos}: moment del toc (com System.nanoTime).
         */
        void nota(int nota, boolean lligada, long nanos);
    }

    /** Polifònic: una nota comença o s'acaba de sonar (amb els acords, n'hi ha diverses per tecla). */
    public interface OientPoli {
        void encen(int nota, long nanos);

        void apaga(int nota, long nanos);
    }

    private static final int[] BLANQUES = {0, 2, 4, 5, 7, 9, 11};
    private static final int N_BLANQUES = 15;

    private int base = 48;   // el Do de l'esquerra
    private Oient oient;
    private final SparseIntArray notaDeDit = new SparseIntArray();
    private final List<Integer> ordre = new ArrayList<>();   // dits, de més antic a més recent
    private int sona = -1;
    // polifònic
    private OientPoli oientPoli;
    private Acords acords;
    private final java.util.Set<Integer> tecles = new java.util.HashSet<>();      // tecles premudes ara
    private final java.util.Map<Integer, Integer> comptes = new java.util.HashMap<>();   // quantes vegades sona cada nota

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float d;

    public TeclatView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        p.setTypeface(Ui.MITJA);
        setHapticFeedbackEnabled(false);
    }

    public void posaOient(Oient o) {
        oient = o;
    }

    /** Mode polifònic (instruments MIDI). */
    public void posaOientPoli(OientPoli o) {
        oientPoli = o;
    }

    /** Amb acords (cada tecla fa sonar el seu acord) o sense (null). */
    public void posaAcords(Acords a) {
        deixaTot(android.os.SystemClock.uptimeMillis() * 1_000_000L);
        acords = a;
        invalidate();
    }

    public Acords acords() {
        return acords;
    }

    /** Deixa de sonar tot (per exemple, en canviar d'octava o d'acords amb dits posats). */
    public void deixaTot(long nanos) {
        for (int t : new java.util.ArrayList<>(tecles)) deixaTecla(t, nanos);
        tecles.clear();
        notaDeDit.clear();
        ordre.clear();
        invalidate();
    }

    private int[] notesDe(int tecla) {
        return acords != null ? acords.notes(tecla) : new int[]{tecla};
    }

    private void premTecla(int tecla, long nanos) {
        for (int n : notesDe(tecla)) {
            int c = comptes.getOrDefault(n, 0);
            comptes.put(n, c + 1);
            if (c == 0 && oientPoli != null) oientPoli.encen(n, nanos);
        }
    }

    private void deixaTecla(int tecla, long nanos) {
        for (int n : notesDe(tecla)) {
            int c = comptes.getOrDefault(n, 0);
            if (c <= 1) {
                comptes.remove(n);
                if (c == 1 && oientPoli != null) oientPoli.apaga(n, nanos);
            } else {
                comptes.put(n, c - 1);
            }
        }
    }

    /** Canvia les octaves (el Do de l'esquerra). */
    public void posaBase(int doEsquerra) {
        if (oientPoli != null) deixaTot(android.os.SystemClock.uptimeMillis() * 1_000_000L);
        base = Math.max(Melodia.NOTA_MIN, Math.min(Melodia.NOTA_MAX - 24, doEsquerra));
        invalidate();
    }

    public int base() {
        return base;
    }

    public int sona() {
        return sona;
    }

    private float ampleBlanca() {
        return getWidth() / (float) N_BLANQUES;
    }

    private static boolean teNegraADreta(int k) {
        return k == 0 || k == 1 || k == 3 || k == 4 || k == 5;
    }

    private int notaBlanca(int i) {
        return base + 12 * (i / 7) + BLANQUES[i % 7];
    }

    /** Quina nota hi ha en aquest punt (primer les negres, que estan per sobre). */
    private int notaA(float x, float y) {
        float wb = ampleBlanca();
        if (x < 0 || x >= getWidth() || wb <= 0) return -1;
        float altNegra = getHeight() * 0.6f, ampleNegra = wb * 0.62f;
        if (y < altNegra) {
            int lim = Math.round(x / wb);   // la vora entre blanques més propera
            if (lim >= 1 && lim < N_BLANQUES && Math.abs(x - lim * wb) < ampleNegra / 2 && teNegraADreta((lim - 1) % 7)) {
                return notaBlanca(lim - 1) + 1;
            }
        }
        int i = Math.max(0, Math.min(N_BLANQUES - 1, (int) (x / wb)));
        return notaBlanca(i);
    }

    @Override
    protected void onDraw(Canvas c) {
        float wb = ampleBlanca(), h = getHeight();
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < N_BLANQUES; i++) {
            int nt = notaBlanca(i);
            r.set(i * wb + 1.5f * d, 0, (i + 1) * wb - 1.5f * d, h - 2 * d);
            boolean premuda = oientPoli != null ? tecles.contains(nt) : nt == sona;
            p.setColor(premuda ? colorPremuda() : 0xFFCFCED6);
            c.drawRoundRect(r, 6 * d, 6 * d, p);
            if (acords != null) {
                p.setColor(0xFF2A2A31);
                p.setTextSize(Math.min(12 * d, wb * 0.3f));
                String t = acords.nom(nt);
                c.drawText(t, r.centerX() - p.measureText(t) / 2, h * 0.72f, p);
            }
            if (Math.floorMod(nt, 12) == 0) {
                p.setColor(Ui.SOBRE_COLOR);
                p.setTextSize(Math.min(13 * d, wb * 0.32f));
                String t = Melodia.nomNota(nt);
                c.drawText(t, r.centerX() - p.measureText(t) / 2, h - 14 * d, p);
            }
        }
        float altNegra = h * 0.6f, ampleNegra = wb * 0.62f;
        for (int i = 0; i < N_BLANQUES - 1; i++) {
            if (!teNegraADreta(i % 7)) continue;
            int nt = notaBlanca(i) + 1;
            float x = (i + 1) * wb;
            r.set(x - ampleNegra / 2, 0, x + ampleNegra / 2, altNegra);
            boolean premuda = oientPoli != null ? tecles.contains(nt) : nt == sona;
            p.setColor(premuda ? Ui.alfa(colorPremuda(), 200) : 0xFF1E1E23);
            c.drawRoundRect(r, 4 * d, 4 * d, p);
            if (acords != null) {
                p.setColor(0xFFCFCED6);
                p.setTextSize(Math.min(10 * d, ampleNegra * 0.32f));
                String t = acords.nom(nt);
                c.drawText(t, r.centerX() - p.measureText(t) / 2, altNegra - 8 * d, p);
            }
        }
    }

    private int color = 0;

    /** El color de les tecles premudes (el de la pista). 0 = el de sempre (sinte o MIDI). */
    public void posaColor(int c) {
        color = c;
        invalidate();
    }

    private int colorPremuda() {
        if (color != 0) return color;
        return oientPoli != null ? Ui.MIDI : Ui.SINTE;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        int acc = ev.getActionMasked();
        switch (acc) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int i = ev.getActionIndex();
                int id = ev.getPointerId(i);
                notaDeDit.put(id, notaA(ev.getX(i), ev.getY(i)));
                ordre.remove(Integer.valueOf(id));
                ordre.add(id);
                getParent().requestDisallowInterceptTouchEvent(true);
                break;
            }
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < ev.getPointerCount(); i++) {
                    int id = ev.getPointerId(i);
                    int nt = notaA(ev.getX(i), ev.getY(i));
                    if (notaDeDit.indexOfKey(id) >= 0 && notaDeDit.get(id) != nt) {
                        // el dit ha passat a una altra tecla: ara és la més recent
                        notaDeDit.put(id, nt);
                        ordre.remove(Integer.valueOf(id));
                        ordre.add(id);
                    }
                }
                break;
            case MotionEvent.ACTION_POINTER_UP: {
                int id = ev.getPointerId(ev.getActionIndex());
                notaDeDit.delete(id);
                ordre.remove(Integer.valueOf(id));
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                notaDeDit.clear();
                ordre.clear();
                break;
            default:
                return true;
        }
        if (oientPoli != null) {
            // polifònic: sonen totes les tecles que tenen un dit a sobre
            long nanos = ev.getEventTime() * 1_000_000L;
            java.util.Set<Integer> ara = new java.util.HashSet<>();
            for (int k = 0; k < notaDeDit.size(); k++) if (notaDeDit.valueAt(k) >= 0) ara.add(notaDeDit.valueAt(k));
            for (int t : new java.util.ArrayList<>(tecles)) if (!ara.contains(t)) deixaTecla(t, nanos);
            for (int t : ara) if (!tecles.contains(t)) premTecla(t, nanos);
            if (!ara.equals(tecles)) {
                tecles.clear();
                tecles.addAll(ara);
                invalidate();
            }
            return true;
        }
        int nova = -1;
        for (int k = ordre.size() - 1; k >= 0 && nova < 0; k--) nova = notaDeDit.get(ordre.get(k), -1);
        if (nova != sona) {
            boolean lligada = sona >= 0 && nova >= 0;
            sona = nova;
            invalidate();
            if (oient != null) oient.nota(nova, lligada, ev.getEventTime() * 1_000_000L);
        }
        return true;
    }
}
