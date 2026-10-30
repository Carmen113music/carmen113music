package cat.riffs;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/**
 * La graella del drumpad: a l'esquerra el nom de cada fila (tipus de so) i, a la dreta,
 * una casella per semicorxera del compàs que es veu.
 * Tocar una casella posa o treu el cop. Mantenir-la premuda i arrossegar amunt o avall en canvia la força.
 * Tocar el nom d'una fila obre les seves opcions.
 */
public class GraellaView extends View {

    public interface Oients {
        /** El ritme ha canviat ({@code acabat} = s'ha aixecat el dit). */
        void canvi(boolean acabat);

        /** S'ha posat o canviat un cop: que soni perquè se senti. */
        void prova(int fila, int forca);

        void fila(int fila);

        /** S'ha canviat de compàs lliscant amb dos dits. */
        default void compas(int c) {}
    }

    private Patro patro;
    private int compasVisible = 0;
    private int grup = 4;
    private int pasSonant = -1;
    private Oients oients;
    private final java.util.function.IntFunction<String> nomSo;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float d;
    private final float ampleNoms;
    private final float altFila;

    // toc
    private int filaToc = -1, pasToc = -1;
    private float y0;
    private boolean forcant = false;
    private int forcaInicial;
    private final Runnable premut = this::comencaForca;
    // dos dits: lliscar de costat canvia de compàs
    private boolean dosDits = false;
    private float xDosDits;

    public GraellaView(Context c, java.util.function.IntFunction<String> nomSo) {
        super(c);
        this.nomSo = nomSo;
        d = c.getResources().getDisplayMetrics().density;
        ampleNoms = 112 * d;
        altFila = 44 * d;
        p.setTypeface(Ui.MITJA);
    }

    public void posa(Patro patro, Oients o) {
        this.patro = patro;
        this.oients = o;
        requestLayout();
        invalidate();
    }

    public void posaCompas(int c) {
        compasVisible = Math.max(0, Math.min(patro == null ? 0 : patro.compassos - 1, c));
        invalidate();
    }

    public int compasVisible() {
        return compasVisible;
    }

    /** Pas que sona ara (de tot el patró), o -1. */
    public void posaPasSonant(int pas) {
        if (pas != pasSonant) {
            pasSonant = pas;
            invalidate();
        }
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int n = patro == null ? 0 : patro.files.size();
        int h = (int) (Math.max(1, n) * altFila + 8 * d);
        int disponible = MeasureSpec.getSize(hSpec);
        // si hi ha poques files, ocupen tot l'alt de la pantalla
        if (MeasureSpec.getMode(hSpec) != MeasureSpec.UNSPECIFIED && disponible > h) h = disponible;
        setMeasuredDimension(MeasureSpec.getSize(wSpec), h);
    }

    private float altura() {
        int n = patro == null ? 1 : Math.max(1, patro.files.size());
        return Math.max(altFila, (getHeight() - 8 * d) / n);
    }

    private float ampleCasella() {
        return (getWidth() - ampleNoms - 8 * d) / Math.max(1, patro.passosPerCompas);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (patro == null) return;
        float ah = altura();
        float ac = ampleCasella();
        int ppc = patro.passosPerCompas;
        grup = Math.max(1, patro.grup());
        int base = compasVisible * ppc;
        float x0 = ampleNoms;
        // columna del pas que sona
        if (pasSonant >= base && pasSonant < base + ppc) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(Ui.alfa(Ui.TEXT, 22));
            float x = x0 + (pasSonant - base) * ac;
            c.drawRect(x, 0, x + ac, patro.files.size() * ah, p);
        }
        for (int i = 0; i < patro.files.size(); i++) {
            Patro.Fila f = patro.files.get(i);
            float top = i * ah;
            // nom de la fila
            p.setStyle(Paint.Style.FILL);
            r.set(4 * d, top + 3 * d, ampleNoms - 6 * d, top + ah - 3 * d);
            p.setColor(i == filaToc && pasToc < 0 ? Ui.LINIA_FORTA : Ui.SUPERFICIE2);
            c.drawRoundRect(r, 10 * d, 10 * d, p);
            p.setColor(f.mut ? Ui.APAGAT : Ui.TEXT);
            p.setTextSize(13 * d * Math.min(1f, ah / altFila));
            String nom = Kits.nomTipus(f.tipus);
            c.drawText(retalla(nom, ampleNoms - 22 * d), 12 * d, top + ah / 2f - 1 * d, p);
            p.setColor(f.mut ? Ui.APAGAT : Ui.SUAU);
            p.setTextSize(10 * d * Math.min(1f, ah / altFila));
            String sub = f.mut ? "Silenciada" : nomSo.apply(i);
            c.drawText(retalla(sub, ampleNoms - 22 * d), 12 * d, top + ah / 2f + 12 * d, p);
            // caselles
            for (int s = 0; s < ppc; s++) {
                float x = x0 + s * ac;
                r.set(x + 2 * d, top + 4 * d, x + ac - 2 * d, top + ah - 4 * d);
                boolean grupFosc = (s / grup) % 2 == 1;
                int v = f.cops[base + s];
                if (v > 0) {
                    int color = f.mut ? Ui.APAGAT : Ui.BATERIA;
                    p.setColor(Ui.alfa(color, v == 1 ? 110 : v == 2 ? 190 : 255));
                } else {
                    p.setColor(grupFosc ? 0xFF1B1B20 : Ui.SUPERFICIE2);
                }
                c.drawRoundRect(r, 6 * d, 6 * d, p);
                if (v > 0) {
                    // la força, en barretes petites
                    p.setColor(Ui.alfa(Ui.SOBRE_COLOR, 160));
                    float bx = r.centerX() - (v - 1) * 3 * d;
                    for (int k = 0; k < v; k++) {
                        c.drawRect(bx + k * 6 * d - 1.5f * d, r.bottom - 8 * d, bx + k * 6 * d + 1.5f * d, r.bottom - 5 * d, p);
                    }
                }
            }
        }
        // línia de cada temps
        p.setColor(Ui.alfa(Ui.TEXT, 40));
        p.setStrokeWidth(1.5f * d);
        for (int s = grup; s < ppc; s += grup) {
            float x = x0 + s * ac;
            c.drawLine(x, 2 * d, x, patro.files.size() * ah - 2 * d, p);
        }
    }

    private String retalla(String s, float max) {
        if (p.measureText(s) <= max) return s;
        while (s.length() > 1 && p.measureText(s + "…") > max) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    // ---------- Toc ----------

    private static float mitjanaX(MotionEvent ev) {
        float x = 0;
        int n = Math.min(2, ev.getPointerCount());
        for (int i = 0; i < n; i++) x += ev.getX(i);
        return x / n;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (patro == null) return false;
        float ah = altura();
        // dos dits: no s'edita res; lliscar de costat passa al compàs següent o a l'anterior
        if (ev.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN && ev.getPointerCount() == 2) {
            removeCallbacks(premut);
            if (forcant && oients != null) oients.canvi(true);
            forcant = false;
            filaToc = -1;
            dosDits = true;
            xDosDits = mitjanaX(ev);
            getParent().requestDisallowInterceptTouchEvent(true);
            invalidate();
            return true;
        }
        if (dosDits) {
            int acc = ev.getActionMasked();
            if (acc == MotionEvent.ACTION_MOVE && ev.getPointerCount() >= 2) {
                float dx = mitjanaX(ev) - xDosDits;
                if (Math.abs(dx) > 60 * d) {
                    int nou = compasVisible + (dx < 0 ? 1 : -1);
                    xDosDits = mitjanaX(ev);
                    if (nou >= 0 && nou < patro.compassos) {
                        compasVisible = nou;
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        invalidate();
                        if (oients != null) oients.compas(nou);
                    }
                }
            } else if (acc == MotionEvent.ACTION_UP || acc == MotionEvent.ACTION_CANCEL) {
                dosDits = false;
                getParent().requestDisallowInterceptTouchEvent(false);
            }
            return true;
        }
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                int fila = (int) (ev.getY() / ah);
                if (fila < 0 || fila >= patro.files.size()) return false;
                filaToc = fila;
                y0 = ev.getY();
                forcant = false;
                if (ev.getX() < ampleNoms) {
                    pasToc = -1;
                } else {
                    int s = (int) ((ev.getX() - ampleNoms) / ampleCasella());
                    if (s < 0 || s >= patro.passosPerCompas) {
                        filaToc = -1;
                        return false;
                    }
                    pasToc = compasVisible * patro.passosPerCompas + s;
                    postDelayed(premut, 350);
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE:
                if (filaToc < 0) return false;
                if (forcant) {
                    int pas = (int) ((y0 - ev.getY()) / (28 * d));
                    int v = Math.max(1, Math.min(3, forcaInicial + pas));
                    int[] cops = patro.files.get(filaToc).cops;
                    if (cops[pasToc] != v) {
                        cops[pasToc] = v;
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        invalidate();
                        if (oients != null) oients.canvi(false);
                    }
                    return true;
                }
                if (Math.abs(ev.getY() - y0) > 10 * d) {
                    // és un desplaçament: el deixem a la llista
                    removeCallbacks(premut);
                    filaToc = -1;
                    invalidate();
                    return false;
                }
                return true;
            case MotionEvent.ACTION_UP: {
                removeCallbacks(premut);
                int fila = filaToc;
                filaToc = -1;
                invalidate();
                if (fila < 0) return false;
                if (forcant) {
                    forcant = false;
                    getParent().requestDisallowInterceptTouchEvent(false);
                    int v = patro.files.get(fila).cops[pasToc];
                    if (oients != null) {
                        oients.canvi(true);
                        oients.prova(fila, v);
                    }
                    return true;
                }
                if (pasToc < 0) {
                    if (oients != null) oients.fila(fila);
                    return true;
                }
                int[] cops = patro.files.get(fila).cops;
                cops[pasToc] = cops[pasToc] > 0 ? 0 : 2;
                invalidate();
                if (oients != null) {
                    oients.canvi(true);
                    if (cops[pasToc] > 0) oients.prova(fila, cops[pasToc]);
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(premut);
                if (forcant && oients != null) oients.canvi(true);
                forcant = false;
                filaToc = -1;
                invalidate();
                return false;
            default:
                return true;
        }
    }

    /** Mantenir premuda una casella: a partir d'ara, amunt i avall en canvia la força. */
    private void comencaForca() {
        if (filaToc < 0 || pasToc < 0) return;
        forcant = true;
        getParent().requestDisallowInterceptTouchEvent(true);
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        int[] cops = patro.files.get(filaToc).cops;
        if (cops[pasToc] == 0) cops[pasToc] = 2;
        forcaInicial = cops[pasToc];
        invalidate();
        if (oients != null) {
            oients.canvi(false);
            oients.prova(filaToc, forcaInicial);
        }
    }
}
