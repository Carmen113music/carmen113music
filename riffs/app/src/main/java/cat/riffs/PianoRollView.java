package cat.riffs;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/**
 * El piano roll d'una finestra de sinte: a l'esquerra un teclat (de Do0 a Do6, de dalt a baix de més agut a més greu)
 * i, a la dreta, la graella del compàs que es veu, amb una fila per nota i una columna per casella.
 * Tocar una casella buida posa una nota; tocar una nota la treu; arrossegar-la la mou (de costat i amunt/avall);
 * arrossegar-ne la punta dreta l'allarga o l'escurça. Tocar el teclat fa sonar la nota.
 * Va dins un ScrollView (amunt i avall es desplaça); dos dits de costat canvien de compàs.
 */
public class PianoRollView extends View {

    public interface Oients {
        /** Les notes han canviat ({@code acabat} = s'ha aixecat el dit). */
        void canvi(boolean acabat);

        /** Que soni aquesta nota (en tocar el teclat, posar-ne una o moure-la amunt i avall). */
        void prova(int nota);

        /** S'ha canviat de compàs lliscant amb dos dits. */
        default void compas(int c) {}
    }

    private static final int CAP = 0, NOU = 1, MOU = 2, ALLARGA = 3, TECLAT = 4;

    private Melodia melodia;
    private Oients oients;
    private int compasVisible = 0;
    private int pasSonant = -1;
    private int teclaPremuda = -1;
    /** Llargada de les notes noves (la de l'última que s'ha allargat), en caselles; 0 = mitja divisió. */
    private int llargNova = 0;

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
    private boolean dosDits = false;
    private float xDosDits;

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
        requestLayout();
        invalidate();
    }

    /** Alçada de cada fila (perquè hi càpiga una octava i mitja a la pantalla). */
    public void posaAltFila(float px) {
        altFila = Math.max(16 * d, px);
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

    public void posaCompas(int c) {
        compasVisible = Math.max(0, Math.min(melodia == null ? 0 : melodia.compassos - 1, c));
        invalidate();
    }

    public int compasVisible() {
        return compasVisible;
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

    private float ampleCasella() {
        return (getWidth() - ampleTecles - 6 * d) / Math.max(1, melodia.passosPerCompas);
    }

    private int notaDeY(float y) {
        return Melodia.NOTA_MAX - (int) Math.floor(y / altFila);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (melodia == null) return;
        float ac = ampleCasella();
        int ppc = melodia.passosPerCompas;
        int base = compasVisible * ppc;
        int grup = Math.max(1, melodia.grup());
        float x0 = ampleTecles;
        float xFi = x0 + ppc * ac;
        int n = files();
        p.setStyle(Paint.Style.FILL);

        // files de la graella (les de tecla negra, més fosques)
        for (int i = 0; i < n; i++) {
            int nt = Melodia.NOTA_MAX - i;
            float top = i * altFila;
            p.setColor(Melodia.esNegra(nt) ? 0xFF151519 : Ui.SUPERFICIE);
            c.drawRect(x0, top, xFi, top + altFila, p);
            if (Math.floorMod(nt, 12) == 0) {
                // ratlla sota cada Do (separa les octaves)
                p.setColor(Ui.LINIA_FORTA);
                c.drawRect(x0, top + altFila - 1.2f * d, xFi, top + altFila, p);
            } else if (Math.floorMod(nt, 12) == 5) {
                p.setColor(Ui.LINIA);
                c.drawRect(x0, top + altFila - 0.8f * d, xFi, top + altFila, p);
            }
        }
        // columna que sona
        if (pasSonant >= base && pasSonant < base + ppc) {
            p.setColor(Ui.alfa(Ui.TEXT, 22));
            float x = x0 + (pasSonant - base) * ac;
            c.drawRect(x, 0, x + ac, n * altFila, p);
        }
        // línies verticals: fines a cada casella, més marcades a cada temps
        for (int s = 0; s <= ppc; s++) {
            boolean temps = s % grup == 0;
            p.setColor(temps ? Ui.alfa(Ui.TEXT, 46) : Ui.alfa(Ui.TEXT, 12));
            float x = x0 + s * ac;
            float w = (temps ? 1.4f : 0.8f) * d;
            c.drawRect(x - w / 2, 0, x + w / 2, n * altFila, p);
        }
        // notes
        for (Melodia.Nota nt : melodia.notes) {
            if (nt.fi() <= base || nt.pas >= base + ppc) continue;
            float top = (Melodia.NOTA_MAX - nt.nota) * altFila;
            float xa = x0 + (nt.pas - base) * ac, xb = x0 + (nt.fi() - base) * ac;
            boolean tallaEsq = xa < x0, tallaDre = xb > xFi;
            xa = Math.max(x0, xa);
            xb = Math.min(xFi, xb);
            r.set(xa + (tallaEsq ? 0 : 1.5f * d), top + 2 * d, xb - (tallaDre ? 0 : 1.5f * d), top + altFila - 2 * d);
            boolean triada = nt == nota && mode != CAP;
            p.setColor(Ui.alfa(Ui.SINTE, nt.forca == 1 ? 150 : nt.forca == 2 ? 215 : 255));
            c.drawRoundRect(r, 5 * d, 5 * d, p);
            if (triada) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2 * d);
                p.setColor(Ui.TEXT);
                c.drawRoundRect(r, 5 * d, 5 * d, p);
                p.setStyle(Paint.Style.FILL);
            }
            // la punta dreta (per allargar)
            if (!tallaDre && r.width() > 18 * d) {
                p.setColor(Ui.alfa(Ui.SOBRE_COLOR, 90));
                c.drawRect(r.right - 5 * d, r.top + 4 * d, r.right - 3.5f * d, r.bottom - 4 * d, p);
            }
            if (r.width() > 34 * d && altFila >= 16 * d) {
                p.setColor(Ui.SOBRE_COLOR);
                p.setTextSize(Math.min(11 * d, altFila * 0.55f));
                c.drawText(Melodia.nomNota(nt.nota), r.left + 5 * d, r.centerY() + p.getTextSize() * 0.36f, p);
            }
        }
        // teclat
        for (int i = 0; i < n; i++) {
            int nt = Melodia.NOTA_MAX - i;
            float top = i * altFila;
            boolean negra = Melodia.esNegra(nt);
            boolean premuda = nt == teclaPremuda;
            p.setColor(premuda ? Ui.SINTE : negra ? 0xFF26262C : 0xFFCFCED6);
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
        // dos dits: es desfà el que s'estava fent i lliscar de costat canvia de compàs
        if (acc == MotionEvent.ACTION_POINTER_DOWN && ev.getPointerCount() == 2) {
            cancelaToc();
            dosDits = true;
            xDosDits = mitjanaX(ev);
            getParent().requestDisallowInterceptTouchEvent(true);
            invalidate();
            return true;
        }
        if (dosDits) {
            if (acc == MotionEvent.ACTION_MOVE && ev.getPointerCount() >= 2) {
                float dx = mitjanaX(ev) - xDosDits;
                if (Math.abs(dx) > 60 * d) {
                    int nou = compasVisible + (dx < 0 ? 1 : -1);
                    xDosDits = mitjanaX(ev);
                    if (nou >= 0 && nou < melodia.compassos) {
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
        float ac = ampleCasella();
        switch (acc) {
            case MotionEvent.ACTION_DOWN: {
                x0 = ev.getX();
                y0 = ev.getY();
                mogut = false;
                int nt = notaDeY(ev.getY());
                if (nt < Melodia.NOTA_MIN || nt > Melodia.NOTA_MAX) return false;
                if (ev.getX() < ampleTecles) {
                    mode = TECLAT;
                    teclaPremuda = nt;
                    invalidate();
                    if (oients != null) oients.prova(nt);
                    return true;
                }
                int col = (int) ((ev.getX() - ampleTecles) / ac);
                if (col < 0 || col >= melodia.passosPerCompas) return false;
                int pas = compasVisible * melodia.passosPerCompas + col;
                Melodia.Nota n = melodia.notaA(pas, nt);
                if (n != null) {
                    nota = n;
                    pas0 = n.pas;
                    nota0 = n.nota;
                    llarg0 = n.llarg;
                    // a prop de la punta dreta: allargar; si no, moure
                    float xFiNota = ampleTecles + (n.fi() - compasVisible * melodia.passosPerCompas) * ac;
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
                } else if (mode == NOU && mogut) {
                    // és un desplaçament, no una nota nova (amunt i avall ho fa el ScrollView)
                    mode = CAP;
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
                if (m == NOU) {
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

    /** Desfà el que estava fent el dit (la nota torna on era). */
    private void cancelaToc() {
        if ((mode == MOU || mode == ALLARGA) && nota != null) {
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
