package cat.riffs;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ScrollView;

/**
 * La graella del drumpad: a l'esquerra el nom de cada fila (tipus de so) i, a la dreta, tots els compassos
 * seguits en una tira contínua (2.29), com el piano roll.
 * Un dit edita: tocar una casella posa o treu el cop; mantenir-la premuda i arrossegar amunt o avall en canvia la força;
 * tocar el nom d'una fila obre les seves opcions. Un dit no desplaça mai la pantalla.
 * Dos dits: arrossegar = desplaçar-se (temps i files), obrir/tancar de costat = zoom del temps,
 * amunt i avall = alçada de les files (com a l'Estudi).
 */
public class GraellaView extends View {

    public interface Oients {
        /** El ritme ha canviat ({@code acabat} = s'ha aixecat el dit). */
        void canvi(boolean acabat);

        /** S'ha posat o canviat un cop: que soni perquè se senti. */
        void prova(int fila, int forca);

        void fila(int fila);

        /** El compàs que es veu a l'esquerra ha canviat (desplaçant-se amb dos dits). */
        default void compas(int c) {}

        /** S'ha canviat l'alçada de les files amb dos dits (en dp, per recordar-la). */
        default void alcada(float dp) {}
    }

    private static final int DD_NOSE = 0, DD_MOU = 1, DD_ZOOM_H = 2, DD_ZOOM_V = 3;
    public static final float FILA_MIN_DP = 28, FILA_MAX_DP = 110;

    private Patro patro;
    /** Vista del temps, en compassos: on comença (a l'esquerra) i quants se'n veuen. */
    private double iniciC = 0, visiblesC = 1;
    private int compasEsq = 0;
    private int grup = 4;
    private int pasSonant = -1;
    private Oients oients;
    private final java.util.function.IntFunction<String> nomSo;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float d;
    private final float ampleNoms;
    private float altFila;

    // toc
    private int filaToc = -1, pasToc = -1;
    private float x0, y0;
    private boolean forcant = false;
    private int forcaInicial;
    private final Runnable premut = this::comencaForca;
    // dos dits
    private boolean dosDits = false;
    private int modeDD;
    private float sepX0, sepY0, cx0, cy0;
    private double iniciC0, visiblesC0;
    private float altFila0;
    private int scrollY0;

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
        limita();
        requestLayout();
        invalidate();
    }

    /** Canvia el ritme (desfer) mantenint el que es veu. */
    public void canviaPatro(Patro p) {
        patro = p;
        filaToc = -1;
        forcant = false;
        limita();
        requestLayout();
        invalidate();
    }

    /** Porta el compàs {@code c} a l'esquerra (amb el mateix zoom). */
    public void posaCompas(int c) {
        iniciC = c;
        limita();
        invalidate();
    }

    public int compasVisible() {
        return compasEsq;
    }

    /** Alçada de cada fila (en px). */
    public void posaAltFila(float px) {
        altFila = Math.max(FILA_MIN_DP * d, Math.min(FILA_MAX_DP * d, px));
        requestLayout();
        invalidate();
    }

    /** Torna a posar la vista dins el ritme (després de canviar-ne la llargada). */
    public void limita() {
        if (patro == null) return;
        double min = 1.0 / Math.max(1, patro.compas == 6 ? 2 : patro.compas);   // un temps (mig compàs en 6/8)
        visiblesC = Math.max(min, Math.min(patro.compassos, visiblesC));
        iniciC = Math.max(0, Math.min(patro.compassos - visiblesC, iniciC));
        int c = (int) Math.floor(iniciC + 1e-6);
        if (c != compasEsq) {
            compasEsq = c;
            if (oients != null) oients.compas(c);
        }
    }

    private ScrollView scroll() {
        return getParent() instanceof ScrollView ? (ScrollView) getParent() : null;
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
        ScrollView sv = scroll();
        if (sv != null && sv.getHeight() > h) h = sv.getHeight();
        setMeasuredDimension(MeasureSpec.getSize(wSpec), h);
    }

    private float altura() {
        int n = patro == null ? 1 : Math.max(1, patro.files.size());
        return Math.max(altFila, (getHeight() - 8 * d) / n);
    }

    private float ampleGraella() {
        return Math.max(1, getWidth() - ampleNoms - 8 * d);
    }

    private float ampleCasella() {
        return (float) (ampleGraella() / (visiblesC * Math.max(1, patro.passosPerCompas)));
    }

    private double pasEsq() {
        return iniciC * patro.passosPerCompas;
    }

    private float xDePas(double pas) {
        return (float) (ampleNoms + (pas - pasEsq()) * ampleCasella());
    }

    private int pasDeX(float x) {
        return (int) Math.floor(pasEsq() + (x - ampleNoms) / ampleCasella());
    }

    @Override
    protected void onDraw(Canvas c) {
        if (patro == null) return;
        float ah = altura();
        float ac = ampleCasella();
        int ppc = patro.passosPerCompas;
        grup = Math.max(1, patro.grup());
        int total = patro.passos();
        float xA = ampleNoms, xFi = ampleNoms + ampleGraella();
        int nf = patro.files.size();
        int s0 = Math.max(0, (int) Math.floor(pasEsq())), s1 = Math.min(total, (int) Math.ceil(pasEsq() + visiblesC * ppc));
        float marge = Math.min(2 * d, ac * 0.12f);
        c.save();
        c.clipRect(xA, 0, xFi, getHeight());
        // columna del pas que sona
        if (pasSonant >= s0 && pasSonant < s1) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(Ui.alfa(Ui.TEXT, 22));
            float x = xDePas(pasSonant);
            c.drawRect(x, 0, x + ac, nf * ah, p);
        }
        for (int i = 0; i < nf; i++) {
            Patro.Fila f = patro.files.get(i);
            float top = i * ah;
            for (int s = s0; s < s1; s++) {
                float x = xDePas(s);
                r.set(x + marge, top + 4 * d, x + ac - marge, top + ah - 4 * d);
                boolean grupFosc = ((s % ppc) / grup) % 2 == 1;
                int v = f.cops[s];
                if (v > 0) {
                    int color = f.mut ? Ui.APAGAT : Ui.BATERIA;
                    p.setColor(Ui.alfa(color, v == 1 ? 110 : v == 2 ? 190 : 255));
                } else {
                    p.setColor(grupFosc ? 0xFF1B1B20 : Ui.SUPERFICIE2);
                }
                c.drawRoundRect(r, Math.min(6 * d, ac / 4), Math.min(6 * d, ac / 4), p);
                if (v > 0 && r.width() > 16 * d) {
                    // la força, en barretes petites
                    p.setColor(Ui.alfa(Ui.SOBRE_COLOR, 160));
                    float bx = r.centerX() - (v - 1) * 3 * d;
                    for (int k = 0; k < v; k++) {
                        c.drawRect(bx + k * 6 * d - 1.5f * d, r.bottom - 8 * d, bx + k * 6 * d + 1.5f * d, r.bottom - 5 * d, p);
                    }
                }
            }
        }
        // línia de cada temps i, més forta, de cada compàs
        for (int s = s0; s <= s1; s++) {
            boolean compas = s % ppc == 0;
            if (!compas && s % grup != 0) continue;
            if (compas && s == 0) continue;
            p.setColor(compas ? Ui.alfa(Ui.TEXT, 150) : Ui.alfa(Ui.TEXT, 40));
            float w = (compas ? 2.2f : 1.5f) * d;
            float x = xDePas(s);
            c.drawRect(x - w / 2, 2 * d, x + w / 2, nf * ah - 2 * d, p);
        }
        c.restore();
        // noms de les files (fixos a l'esquerra)
        for (int i = 0; i < nf; i++) {
            Patro.Fila f = patro.files.get(i);
            float top = i * ah;
            p.setStyle(Paint.Style.FILL);
            r.set(4 * d, top + 3 * d, ampleNoms - 6 * d, top + ah - 3 * d);
            p.setColor(i == filaToc && pasToc < 0 ? Ui.LINIA_FORTA : Ui.SUPERFICIE2);
            c.drawRoundRect(r, 10 * d, 10 * d, p);
            float esc = Math.min(1f, ah / (44 * d));
            p.setColor(f.mut ? Ui.APAGAT : Ui.TEXT);
            p.setTextSize(13 * d * esc);
            String nom = Kits.nomTipus(f.tipus);
            c.drawText(retalla(nom, ampleNoms - 22 * d), 12 * d, top + ah / 2f - 1 * d, p);
            p.setColor(f.mut ? Ui.APAGAT : Ui.SUAU);
            p.setTextSize(10 * d * esc);
            String sub = f.mut ? "Silenciada" : nomSo.apply(i);
            c.drawText(retalla(sub, ampleNoms - 22 * d), 12 * d, top + ah / 2f + 12 * d * esc, p);
        }
    }

    private String retalla(String s, float max) {
        if (p.measureText(s) <= max) return s;
        while (s.length() > 1 && p.measureText(s + "…") > max) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    // ---------- Toc ----------

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (patro == null) return false;
        float ah = altura();
        int acc = ev.getActionMasked();
        // dos dits: no s'edita res; es desplaça o es fa zoom
        if (acc == MotionEvent.ACTION_POINTER_DOWN && ev.getPointerCount() == 2) {
            removeCallbacks(premut);
            if (forcant && oients != null) oients.canvi(true);
            forcant = false;
            filaToc = -1;
            comencaDosDits(ev);
            getParent().requestDisallowInterceptTouchEvent(true);
            invalidate();
            return true;
        }
        if (dosDits) {
            if (acc == MotionEvent.ACTION_MOVE && ev.getPointerCount() >= 2) {
                mouDosDits(ev);
            } else if (acc == MotionEvent.ACTION_POINTER_UP && ev.getPointerCount() == 3) {
                comencaDosDits(ev);
            } else if (acc == MotionEvent.ACTION_UP || acc == MotionEvent.ACTION_CANCEL) {
                dosDits = false;
                getParent().requestDisallowInterceptTouchEvent(false);
                if (modeDD == DD_ZOOM_V && oients != null) oients.alcada(altFila / d);
            }
            return true;
        }
        switch (acc) {
            case MotionEvent.ACTION_DOWN: {
                // un dit no desplaça mai la pantalla
                getParent().requestDisallowInterceptTouchEvent(true);
                int fila = (int) (ev.getY() / ah);
                if (fila < 0 || fila >= patro.files.size()) return true;
                filaToc = fila;
                x0 = ev.getX();
                y0 = ev.getY();
                forcant = false;
                if (ev.getX() < ampleNoms) {
                    pasToc = -1;
                } else {
                    int s = pasDeX(ev.getX());
                    if (s < 0 || s >= patro.passos() || ev.getX() > ampleNoms + ampleGraella()) {
                        filaToc = -1;
                        return true;
                    }
                    pasToc = s;
                    postDelayed(premut, 350);
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE:
                if (filaToc < 0) return true;
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
                if (Math.hypot(ev.getX() - x0, ev.getY() - y0) > 10 * d) {
                    // el dit s'ha mogut: no és un toc (un dit no desplaça)
                    removeCallbacks(premut);
                    filaToc = -1;
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP: {
                removeCallbacks(premut);
                getParent().requestDisallowInterceptTouchEvent(false);
                int fila = filaToc;
                filaToc = -1;
                invalidate();
                if (fila < 0) return true;
                if (forcant) {
                    forcant = false;
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

    // ---------- Dos dits ----------

    private void comencaDosDits(MotionEvent ev) {
        ScrollView sv = scroll();
        int sy = sv == null ? 0 : sv.getScrollY();
        dosDits = true;
        modeDD = DD_NOSE;
        sepX0 = Math.abs(ev.getX(0) - ev.getX(1));
        sepY0 = Math.abs(ev.getY(0) - ev.getY(1));
        cx0 = (ev.getX(0) + ev.getX(1)) / 2f;
        cy0 = (ev.getY(0) + ev.getY(1)) / 2f - sy;
        iniciC0 = iniciC;
        visiblesC0 = visiblesC;
        altFila0 = altura();
        scrollY0 = sy;
    }

    private void mouDosDits(MotionEvent ev) {
        ScrollView sv = scroll();
        int sy = sv == null ? 0 : sv.getScrollY();
        float sepX = Math.abs(ev.getX(0) - ev.getX(1));
        float sepY = Math.abs(ev.getY(0) - ev.getY(1));
        float cx = (ev.getX(0) + ev.getX(1)) / 2f;
        float cy = (ev.getY(0) + ev.getY(1)) / 2f - sy;
        if (modeDD == DD_NOSE) {
            if (Math.abs(sepX - sepX0) > 36 * d) modeDD = DD_ZOOM_H;
            else if (Math.abs(sepY - sepY0) > 36 * d) modeDD = DD_ZOOM_V;
            else if (Math.hypot(cx - cx0, cy - cy0) > 10 * d) modeDD = DD_MOU;
            else return;
            sepX0 = sepX;
            sepY0 = sepY;
        }
        double ppc = patro.passosPerCompas;
        if (modeDD == DD_MOU) {
            double ac0 = ampleGraella() / (visiblesC0 * ppc);
            iniciC = iniciC0 - (cx - cx0) / ac0 / ppc;
            limita();
            if (sv != null) sv.scrollTo(0, Math.max(0, Math.round(scrollY0 - (cy - cy0))));
            invalidate();
        } else if (modeDD == DD_ZOOM_H) {
            if (sepX < 10 * d) return;
            double ac0 = ampleGraella() / (visiblesC0 * ppc);
            double pasCentre = iniciC0 * ppc + (cx0 - ampleNoms) / ac0;
            visiblesC = visiblesC0 * sepX0 / sepX;
            limita();
            double ac = ampleGraella() / (visiblesC * ppc);
            iniciC = (pasCentre - (cx - ampleNoms) / ac) / ppc;
            limita();
            invalidate();
        } else if (modeDD == DD_ZOOM_V) {
            if (sepY < 10 * d) return;
            float fila = (cy0 + scrollY0) / altFila0;
            float nova = Math.max(FILA_MIN_DP * d, Math.min(FILA_MAX_DP * d, altFila0 * sepY / sepY0));
            if (Math.abs(nova - altFila) < 0.5f) return;
            altFila = nova;
            requestLayout();
            invalidate();
            if (sv != null) {
                int y = Math.max(0, Math.round(fila * altura() - cy));
                sv.post(() -> sv.scrollTo(0, y));
            }
        }
    }
}
