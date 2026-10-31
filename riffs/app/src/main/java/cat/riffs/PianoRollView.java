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
 * El piano roll d'una finestra de sinte: a l'esquerra un teclat (de Do0 a Do6, de dalt a baix de més agut a més greu)
 * i, a la dreta, la graella del compàs que es veu, amb una fila per nota i una columna per casella.
 * Tocar una casella buida posa una nota; tocar una nota la treu; arrossegar-la la mou (de costat i amunt/avall);
 * arrossegar-ne la punta dreta l'allarga o l'escurça. Tocar el teclat fa sonar la nota.
 * Va dins un ScrollView, però amb un dit no es desplaça mai (2.29). Dos dits: obrir-los o tancar-los de costat = zoom del temps,
 * amunt i avall = alçada de les files, arrossegar = desplaçar-se (un dit edita, dos dits mouen, com a l'Estudi).
 */
public class PianoRollView extends View {

    public interface Oients {
        /** Les notes han canviat ({@code acabat} = s'ha aixecat el dit). */
        void canvi(boolean acabat);

        /** Que soni aquesta nota (en tocar el teclat, posar-ne una o moure-la amunt i avall). */
        void prova(int nota);

        /** El compàs que es veu a l'esquerra ha canviat (desplaçant-se amb dos dits). */
        default void compas(int c) {}

        /** S'ha canviat l'alçada de les files amb dos dits (en dp, per recordar-la). */
        default void alcada(float dp) {}
    }

    private static final int CAP = 0, NOU = 1, MOU = 2, ALLARGA = 3, TECLAT = 4, RECT = 5, GRUP = 6;
    // gest de dos dits: encara no se sap, desplaçar, zoom del temps, zoom de les files
    private static final int DD_NOSE = 0, DD_MOU = 1, DD_ZOOM_H = 2, DD_ZOOM_V = 3;
    public static final float FILA_MIN_DP = 14, FILA_MAX_DP = 56;
    /** Colors de les files (2.29: més clars perquè es vegin les línies del temps). */
    static final int FILA_BLANCA = 0xFF2E2E36, FILA_NEGRA = 0xFF24242B;

    private Melodia melodia;
    private Oients oients;
    /** Vista del temps, en compassos: on comença (a l'esquerra) i quants se'n veuen. */
    private double iniciC = 0, visiblesC = 1;
    private int compasEsq = 0;
    private int pasSonant = -1;
    private int teclaPremuda = -1;
    /** Llargada de les notes noves (la de l'última que s'ha allargat), en caselles; 0 = mitja divisió. */
    private int llargNova = 0;

    private int color = Ui.SINTE;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float d;
    private final float ampleTecles;
    private float altFila;

    // toc
    private int mode = CAP;
    private float x0, y0;
    private boolean mogut;
    private Melodia.Nota nota;
    private int pas0, nota0, llarg0;
    private int pasNou, notaNova;
    // selecció amb rectangle (2.32.1): arrossegar un dit sobre buit tria les notes; arrossegar-ne una les mou totes
    private final java.util.List<Melodia.Nota> triades = new java.util.ArrayList<>();
    private float rx1, ry1;
    private int[] grupPas, grupNota;
    private int grupMinPas, grupMaxFi, grupMinNota, grupMaxNota;
    private boolean dosDits = false;
    private int modeDD;
    private float sepX0, sepY0, cx0, cy0;   // separació i centre dels dits en començar (y de pantalla)
    private double iniciC0, visiblesC0;
    private float altFila0;
    private int scrollY0;

    public PianoRollView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        ampleTecles = 64 * d;
        altFila = 20 * d;
        p.setTypeface(Ui.MITJA);
    }

    public void posa(Melodia m, Oients o) {
        melodia = m;
        oients = o;
        iniciC = 0;
        visiblesC = 1;
        requestLayout();
        invalidate();
    }

    /** Color de les notes (verd el sinte, blau el MIDI). */
    public void posaColor(int c) {
        color = c;
        invalidate();
    }

    /** Canvia la frase (desfer) sense tocar el zoom ni on es mira. */
    public void canviaMelodia(Melodia m) {
        melodia = m;
        triades.clear();
        nota = null;
        mode = CAP;
        limita();
        requestLayout();
        invalidate();
    }

    /** Alçada de cada fila (de 14 a 56 dp). */
    public void posaAltFila(float px) {
        altFila = Math.max(FILA_MIN_DP * d, Math.min(FILA_MAX_DP * d, px));
        requestLayout();
        invalidate();
    }

    public float altFila() {
        return altFila;
    }

    /** On és (en px des de dalt) el centre de la fila d'una nota. */
    public float yDe(int nota) {
        return (Melodia.NOTA_MAX - nota) * altFila + altFila / 2f;
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

    /** Torna a posar la vista dins la frase (després de canviar-ne la llargada). */
    public void limita() {
        if (melodia == null) return;
        double min = 1.0 / Math.max(1, melodia.compas == 6 ? 2 : melodia.compas);   // un temps (mig compàs en 6/8)
        visiblesC = Math.max(min, Math.min(melodia.compassos, visiblesC));
        iniciC = Math.max(0, Math.min(melodia.compassos - visiblesC, iniciC));
        int c = (int) Math.floor(iniciC + 1e-6);
        if (c != compasEsq) {
            compasEsq = c;
            if (oients != null) oients.compas(c);
        }
    }

    private ScrollView scroll() {
        return getParent() instanceof ScrollView ? (ScrollView) getParent() : null;
    }

    public void posaPasSonant(int pas) {
        if (pas != pasSonant) {
            pasSonant = pas;
            invalidate();
        }
    }

    private int files() {
        return Melodia.NOTA_MAX - Melodia.NOTA_MIN + 1;
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        setMeasuredDimension(MeasureSpec.getSize(wSpec), (int) (files() * altFila));
    }

    private float ampleGraella() {
        return Math.max(1, getWidth() - ampleTecles - 6 * d);
    }

    private float ampleCasella() {
        return (float) (ampleGraella() / (visiblesC * Math.max(1, melodia.passosPerCompas)));
    }

    /** Primera casella que es veu (pot ser fraccionària). */
    private double pasEsq() {
        return iniciC * melodia.passosPerCompas;
    }

    private float xDePas(double pas) {
        return (float) (ampleTecles + (pas - pasEsq()) * ampleCasella());
    }

    private int pasDeX(float x) {
        return (int) Math.floor(pasEsq() + (x - ampleTecles) / ampleCasella());
    }

    /** Les notes que toca el rectangle (de temps i d'altura). */
    private void triaRectangle() {
        float ax = Math.min(x0, rx1), bx = Math.max(x0, rx1), ay = Math.min(y0, ry1), by = Math.max(y0, ry1);
        double pa = pasEsq() + (ax - ampleTecles) / ampleCasella(), pb = pasEsq() + (bx - ampleTecles) / ampleCasella();
        int nAlta = notaDeY(ay), nBaixa = notaDeY(by);
        triades.clear();
        for (Melodia.Nota t : melodia.notes) {
            if (t.fi() > pa && t.pas < pb && t.nota <= nAlta && t.nota >= nBaixa) triades.add(t);
        }
    }

    /** Quantes notes hi ha triades. */
    public int quantesTriades() {
        return triades.size();
    }

    private int notaDeY(float y) {
        return Melodia.NOTA_MAX - (int) Math.floor(y / altFila);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (melodia == null) return;
        float ac = ampleCasella();
        int ppc = melodia.passosPerCompas;
        int grup = Math.max(1, melodia.grup());
        float x0 = ampleTecles;
        float xFi = x0 + ampleGraella();
        int total = melodia.passos();
        float xFrase = Math.min(xFi, xDePas(total));   // on s'acaba la frase
        int n = files();
        p.setStyle(Paint.Style.FILL);

        c.save();
        c.clipRect(x0, 0, xFi, n * altFila);
        // files de la graella (les de tecla negra, més fosques)
        for (int i = 0; i < n; i++) {
            int nt = Melodia.NOTA_MAX - i;
            float top = i * altFila;
            p.setColor(Melodia.esNegra(nt) ? FILA_NEGRA : FILA_BLANCA);
            c.drawRect(x0, top, xFrase, top + altFila, p);
            if (Math.floorMod(nt, 12) == 0) {
                // ratlla sota cada Do (separa les octaves)
                p.setColor(Ui.alfa(Ui.TEXT, 70));
                c.drawRect(x0, top + altFila - 1.2f * d, xFrase, top + altFila, p);
            } else if (Math.floorMod(nt, 12) == 5) {
                p.setColor(Ui.alfa(Ui.TEXT, 30));
                c.drawRect(x0, top + altFila - 0.8f * d, xFrase, top + altFila, p);
            }
        }
        // columna que sona
        if (pasSonant >= 0) {
            float x = xDePas(pasSonant);
            if (x + ac > x0 && x < xFi) {
                p.setColor(Ui.alfa(Ui.TEXT, 22));
                c.drawRect(x, 0, x + ac, n * altFila, p);
            }
        }
        // línies verticals: fines a cada casella (si no estan massa juntes), més a cada temps i fortes a cada compàs
        int s0 = Math.max(0, (int) Math.floor(pasEsq())), s1 = Math.min(total, (int) Math.ceil(pasEsq() + visiblesC * ppc));
        for (int s = s0; s <= s1; s++) {
            boolean compas = s % ppc == 0, temps = s % grup == 0;
            if (!compas && !temps && ac < 6 * d) continue;
            p.setColor(compas ? Ui.alfa(Ui.TEXT, 210) : temps ? Ui.alfa(Ui.TEXT, 120) : Ui.alfa(Ui.TEXT, 38));
            float x = xDePas(s);
            float w = (compas ? 2.4f : temps ? 1.6f : 0.8f) * d;
            c.drawRect(x - w / 2, 0, x + w / 2, n * altFila, p);
        }
        // notes
        for (Melodia.Nota nt : melodia.notes) {
            float xa = xDePas(nt.pas), xb = xDePas(nt.fi());
            if (xb <= x0 || xa >= xFi) continue;
            float top = (Melodia.NOTA_MAX - nt.nota) * altFila;
            r.set(xa + 1.5f * d, top + 2 * d, xb - 1.5f * d, top + altFila - 2 * d);
            boolean triada = nt == nota && mode != CAP || triades.contains(nt);
            p.setColor(Ui.alfa(color, nt.forca == 1 ? 150 : nt.forca == 2 ? 215 : 255));
            c.drawRoundRect(r, 5 * d, 5 * d, p);
            if (triada) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2 * d);
                p.setColor(Ui.TEXT);
                c.drawRoundRect(r, 5 * d, 5 * d, p);
                p.setStyle(Paint.Style.FILL);
            }
            // la punta dreta (per allargar)
            if (r.width() > 18 * d) {
                p.setColor(Ui.alfa(Ui.SOBRE_COLOR, 90));
                c.drawRect(r.right - 5 * d, r.top + 4 * d, r.right - 3.5f * d, r.bottom - 4 * d, p);
            }
            if (r.width() > 34 * d && altFila >= 16 * d) {
                p.setColor(Ui.SOBRE_COLOR);
                p.setTextSize(Math.min(11 * d, altFila * 0.55f));
                c.drawText(Melodia.nomNota(nt.nota), Math.max(r.left, x0) + 5 * d, r.centerY() + p.getTextSize() * 0.36f, p);
            }
        }
        if (mode == RECT) {
            float ax = Math.min(this.x0, rx1), bx = Math.max(this.x0, rx1), ay = Math.min(this.y0, ry1), by = Math.max(this.y0, ry1);
            r.set(Math.max(ax, x0), ay, Math.min(bx, xFi), by);
            p.setColor(Ui.alfa(color, 40));
            c.drawRect(r, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.5f * d);
            p.setColor(Ui.alfa(color, 200));
            c.drawRect(r, p);
            p.setStyle(Paint.Style.FILL);
        }
        c.restore();
        // teclat
        for (int i = 0; i < n; i++) {
            int nt = Melodia.NOTA_MAX - i;
            float top = i * altFila;
            boolean negra = Melodia.esNegra(nt);
            boolean premuda = nt == teclaPremuda;
            p.setColor(premuda ? color : negra ? 0xFF26262C : 0xFFCFCED6);
            r.set(0, top + 0.5f * d, negra ? ampleTecles * 0.62f : ampleTecles - 4 * d, top + altFila - 0.5f * d);
            c.drawRoundRect(r, 3 * d, 3 * d, p);
            if (negra && !premuda) {
                // la part blanca que queda al costat de la negra
                p.setColor(0xFFB9B8C0);
                c.drawRect(ampleTecles * 0.62f + 1 * d, top + 0.5f * d, ampleTecles - 4 * d, top + altFila - 0.5f * d, p);
            }
            if (Math.floorMod(nt, 12) == 0 || premuda) {
                p.setColor(Ui.SOBRE_COLOR);
                p.setTextSize(Math.min(11 * d, altFila * 0.58f));
                float tw = p.measureText(Melodia.nomNota(nt));
                c.drawText(Melodia.nomNota(nt), ampleTecles - 8 * d - tw, top + altFila / 2f + p.getTextSize() * 0.36f, p);
            }
        }
    }

    // ---------- Toc ----------

    private static float mitjanaX(MotionEvent ev) {
        float x = 0;
        int n = Math.min(2, ev.getPointerCount());
        for (int i = 0; i < n; i++) x += ev.getX(i);
        return x / n;
    }

    private int llargadaNova() {
        if (llargNova > 0) return llargNova;
        return Math.max(1, melodia.divisio() / 2);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (melodia == null) return false;
        int acc = ev.getActionMasked();
        // dos dits: es desfà el que s'estava fent; obrir/tancar = zoom, arrossegar = desplaçar-se
        if (acc == MotionEvent.ACTION_POINTER_DOWN && ev.getPointerCount() == 2) {
            cancelaToc();
            comencaDosDits(ev);
            getParent().requestDisallowInterceptTouchEvent(true);
            invalidate();
            return true;
        }
        if (dosDits) {
            if (acc == MotionEvent.ACTION_MOVE && ev.getPointerCount() >= 2) {
                mouDosDits(ev);
            } else if (acc == MotionEvent.ACTION_POINTER_UP && ev.getPointerCount() == 3) {
                // s'aixeca un de tres dits: es continua amb els dos que queden
                comencaDosDits(ev);
            } else if (acc == MotionEvent.ACTION_UP || acc == MotionEvent.ACTION_CANCEL) {
                dosDits = false;
                getParent().requestDisallowInterceptTouchEvent(false);
                if (modeDD == DD_ZOOM_V && oients != null) oients.alcada(altFila / d);
            }
            return true;
        }
        float ac = ampleCasella();
        switch (acc) {
            case MotionEvent.ACTION_DOWN: {
                x0 = ev.getX();
                y0 = ev.getY();
                mogut = false;
                // un dit no desplaça mai la pantalla (2.29): el ScrollView no s'ho queda
                getParent().requestDisallowInterceptTouchEvent(true);
                int nt = notaDeY(ev.getY());
                if (nt < Melodia.NOTA_MIN || nt > Melodia.NOTA_MAX) return false;
                if (ev.getX() < ampleTecles) {
                    mode = TECLAT;
                    teclaPremuda = nt;
                    invalidate();
                    if (oients != null) oients.prova(nt);
                    return true;
                }
                if (ev.getX() > ampleTecles + ampleGraella()) return false;
                int pas = pasDeX(ev.getX());
                if (pas < 0 || pas >= melodia.passos()) return false;
                Melodia.Nota n = melodia.notaA(pas, nt);
                if (n != null && triades.size() > 1 && triades.contains(n)) {
                    // una nota de la selecció: es mouen totes
                    triades.retainAll(melodia.notes);
                    nota = n;
                    mode = GRUP;
                    grupPas = new int[triades.size()];
                    grupNota = new int[triades.size()];
                    grupMinPas = Integer.MAX_VALUE;
                    grupMaxFi = 0;
                    grupMinNota = Integer.MAX_VALUE;
                    grupMaxNota = 0;
                    for (int i = 0; i < triades.size(); i++) {
                        Melodia.Nota t = triades.get(i);
                        grupPas[i] = t.pas;
                        grupNota[i] = t.nota;
                        grupMinPas = Math.min(grupMinPas, t.pas);
                        grupMaxFi = Math.max(grupMaxFi, t.fi());
                        grupMinNota = Math.min(grupMinNota, t.nota);
                        grupMaxNota = Math.max(grupMaxNota, t.nota);
                    }
                    nota0 = n.nota;
                } else if (n != null) {
                    if (!triades.contains(n)) triades.clear();
                    nota = n;
                    pas0 = n.pas;
                    nota0 = n.nota;
                    llarg0 = n.llarg;
                    // a prop de la punta dreta: allargar; si no, moure
                    float xFiNota = xDePas(n.fi());
                    float marge = Math.max(14 * d, Math.min(ac * 0.45f, 26 * d));
                    mode = xFiNota - ev.getX() < marge && n.llarg * ac > marge ? ALLARGA : MOU;
                    getParent().requestDisallowInterceptTouchEvent(true);
                } else {
                    mode = NOU;
                    pasNou = pas;
                    notaNova = nt;
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                float dx = ev.getX() - x0, dy = ev.getY() - y0;
                if (!mogut && Math.hypot(dx, dy) > 8 * d) mogut = true;
                if (mode == MOU && mogut) {
                    int np = Math.max(0, Math.min(melodia.passos() - nota.llarg, pas0 + Math.round(dx / ac)));
                    int nn = Math.max(Melodia.NOTA_MIN, Math.min(Melodia.NOTA_MAX, nota0 - Math.round(dy / altFila)));
                    if (np != nota.pas || nn != nota.nota) {
                        boolean canviaTo = nn != nota.nota;
                        nota.pas = np;
                        nota.nota = nn;
                        invalidate();
                        if (oients != null) {
                            oients.canvi(false);
                            if (canviaTo) oients.prova(nn);
                        }
                    }
                } else if (mode == ALLARGA && mogut) {
                    int nl = Math.max(1, Math.min(melodia.passos() - nota.pas, llarg0 + Math.round(dx / ac)));
                    if (nl != nota.llarg) {
                        nota.llarg = nl;
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        invalidate();
                        if (oients != null) oients.canvi(false);
                    }
                } else if ((mode == NOU || mode == RECT) && mogut) {
                    // el dit s'ha mogut sobre buit: no es posa cap nota; es fa un rectangle per triar-ne
                    mode = RECT;
                    rx1 = ev.getX();
                    ry1 = ev.getY();
                    triaRectangle();
                    invalidate();
                } else if (mode == GRUP && mogut) {
                    int dp = Math.max(-grupMinPas, Math.min(melodia.passos() - grupMaxFi, Math.round(dx / ac)));
                    int dn = Math.max(Melodia.NOTA_MIN - grupMinNota, Math.min(Melodia.NOTA_MAX - grupMaxNota, -Math.round(dy / altFila)));
                    int abans = nota.nota;
                    boolean canvi = false;
                    for (int i = 0; i < triades.size(); i++) {
                        Melodia.Nota t = triades.get(i);
                        if (t.pas != grupPas[i] + dp || t.nota != grupNota[i] + dn) canvi = true;
                        t.pas = grupPas[i] + dp;
                        t.nota = grupNota[i] + dn;
                    }
                    if (canvi) {
                        invalidate();
                        if (oients != null) {
                            oients.canvi(false);
                            if (nota.nota != abans) oients.prova(nota.nota);
                        }
                    }
                } else if (mode == TECLAT && mogut) {
                    int nt = notaDeY(ev.getY());
                    if (nt != teclaPremuda && nt >= Melodia.NOTA_MIN && nt <= Melodia.NOTA_MAX) {
                        teclaPremuda = nt;
                        invalidate();
                        if (oients != null) oients.prova(nt);
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_UP: {
                int m = mode;
                mode = CAP;
                teclaPremuda = -1;
                getParent().requestDisallowInterceptTouchEvent(false);
                if (m == NOU && !triades.isEmpty()) {
                    // amb notes triades, tocar un lloc buit només treu la selecció
                    triades.clear();
                } else if (m == RECT) {
                    if (!triades.isEmpty()) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                } else if (m == GRUP) {
                    if (!mogut) {
                        melodia.treu(nota);   // tocar-ne una (sense moure) la treu, com sempre
                        triades.remove(nota);
                    } else {
                        // es tornen a posar en ordre (així les de la selecció no es trepitgen entre elles)
                        java.util.List<Melodia.Nota> g = new java.util.ArrayList<>(triades);
                        for (Melodia.Nota t : g) melodia.treu(t);
                        g.sort((a, b) -> Integer.compare(a.pas, b.pas));
                        for (Melodia.Nota t : g) melodia.posa(t);
                        triades.retainAll(melodia.notes);
                    }
                    if (oients != null) oients.canvi(true);
                } else if (m == NOU) {
                    Melodia.Nota n = new Melodia.Nota(pasNou, llargadaNova(), notaNova, 2);
                    melodia.posa(n);
                    if (oients != null) {
                        oients.canvi(true);
                        oients.prova(n.nota);
                    }
                } else if (m == MOU || m == ALLARGA) {
                    if (!mogut) {
                        melodia.treu(nota);
                    } else {
                        melodia.posa(nota);
                        if (m == ALLARGA) llargNova = nota.llarg;
                    }
                    if (oients != null) oients.canvi(true);
                }
                nota = null;
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                cancelaToc();
                return false;
            default:
                return true;
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
        altFila0 = altFila;
        scrollY0 = sy;
    }

    private void mouDosDits(MotionEvent ev) {
        ScrollView sv = scroll();
        int sy = sv == null ? 0 : sv.getScrollY();
        float sepX = Math.abs(ev.getX(0) - ev.getX(1));
        float sepY = Math.abs(ev.getY(0) - ev.getY(1));
        float cx = (ev.getX(0) + ev.getX(1)) / 2f;
        float cy = (ev.getY(0) + ev.getY(1)) / 2f - sy;   // en coordenades de pantalla (la vista es mou sota els dits)
        if (modeDD == DD_NOSE) {
            // el primer que passa decideix (només un sentit per gest, com a l'Estudi)
            if (Math.abs(sepX - sepX0) > 36 * d) modeDD = DD_ZOOM_H;
            else if (Math.abs(sepY - sepY0) > 36 * d) modeDD = DD_ZOOM_V;
            else if (Math.hypot(cx - cx0, cy - cy0) > 10 * d) modeDD = DD_MOU;
            else return;
            // que el zoom no faci un salt: comença des d'aquí
            sepX0 = sepX;
            sepY0 = sepY;
        }
        double ppc = melodia.passosPerCompas;
        if (modeDD == DD_MOU) {
            double ac0 = ampleGraella() / (visiblesC0 * ppc);
            iniciC = iniciC0 - (cx - cx0) / ac0 / ppc;
            limita();
            if (sv != null) sv.scrollTo(0, Math.max(0, Math.round(scrollY0 - (cy - cy0))));
            invalidate();
        } else if (modeDD == DD_ZOOM_H) {
            if (sepX < 10 * d) return;
            // el moment que hi ha entre els dits es queda entre els dits
            double ac0 = ampleGraella() / (visiblesC0 * ppc);
            double pasCentre = iniciC0 * ppc + (cx0 - ampleTecles) / ac0;
            visiblesC = visiblesC0 * sepX0 / sepX;
            limita();
            double ac = ampleGraella() / (visiblesC * ppc);
            iniciC = (pasCentre - (cx - ampleTecles) / ac) / ppc;
            limita();
            invalidate();
        } else if (modeDD == DD_ZOOM_V) {
            if (sepY < 10 * d) return;
            float fila = (cy0 + scrollY0) / altFila0;   // la nota que hi ha entre els dits
            float nova = Math.max(FILA_MIN_DP * d, Math.min(FILA_MAX_DP * d, altFila0 * sepY / sepY0));
            if (Math.abs(nova - altFila) < 0.5f) return;
            altFila = nova;
            requestLayout();
            invalidate();
            if (sv != null) {
                int y = Math.max(0, Math.round(fila * nova - cy));
                sv.post(() -> sv.scrollTo(0, y));
            }
        }
    }

    /** Desfà el que estava fent el dit (la nota torna on era). */
    private void cancelaToc() {
        if (mode == GRUP && grupPas != null) {
            for (int i = 0; i < triades.size() && i < grupPas.length; i++) {
                triades.get(i).pas = grupPas[i];
                triades.get(i).nota = grupNota[i];
            }
            if (oients != null) oients.canvi(true);
        } else if ((mode == MOU || mode == ALLARGA) && nota != null) {
            nota.pas = pas0;
            nota.nota = nota0;
            nota.llarg = llarg0;
            if (oients != null) oients.canvi(true);
        }
        mode = CAP;
        nota = null;
        teclaPremuda = -1;
        invalidate();
    }
}
