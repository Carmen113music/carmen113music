package cat.riffs;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Les pistes de l'Estudi, una sota l'altra; cada pista pot tenir diversos clips del mateix fitxer.
 * Clip triat: cantonades de baix = retallar o allargar (més enllà del so és silenci), cantonades de dalt = fades, punt de dalt al mig = volum del clip.
 * Arrossegar un clip el mou (amb snap); en mode Lliure la bandera es queda quieta i l'àudio llisca per sota.
 * Mantenir premut un clip i, sense aixecar el dit, arrossegar a dreta o esquerra tria clips consecutius;
 * arrossegar qualsevol clip triat mou tot el grup. Dos dits = desplaçar i zoom.
 */
public class PistesView extends View {

    public interface Oient {
        /** Hi ha hagut una edició (acabat = l'usuari ha deixat anar el dit: cal desar). */
        void editat(boolean acabat);

        /** S'ha tocat un espai buit: posar-hi el capçal. */
        void capcal(long ms);

        /** Mentre s'arrossega, text curt per mostrar (p. ex. «Fade in 0,8 s» o «-6,0 dB»). */
        void info(String text);

        /** Desplaçament vertical amb dos dits (per passar per les pistes). */
        void desplacaVertical(int dy);

        /** La bandera del primer temps d'aquesta presa ha canviat (mode Lliure): cal desar-la a la fitxa. */
        void bandera(String wav);

        /** Han canviat els clips triats. */
        void seleccio();

        /** Ha canviat l'alçada de les pistes (zoom vertical). */
        void alcada(int altPistaPx);
    }

    private static final int CAP = 0, MOU = 1, RETALL_I = 2, RETALL_F = 3, FADE_IN = 4, FADE_OUT = 5, GUANY = 6,
            BUIT = 7, DOS_DITS = 8, TRIA = 9;
    private static final long TEMPS_PREMUT = 400;

    private final Projecte projecte;
    private final Vista vista;
    private final Map<String, float[]> ones = new HashMap<>();
    private int altPista;
    private Oient oient;

    private final Paint ple = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linia = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barra = new Paint();
    private final Paint mansa = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path cami = new Path();
    private final RectF r = new RectF();

    // dos dits: separació horitzontal i vertical (zoom en cada direcció)
    private float focusX, focusY, ampleDits, altDits;

    // estat de l'arrossegament
    private int mode = CAP;
    private int pista = -1;
    private Projecte.Clip clip;                       // el clip tocat
    private final List<Projecte.Clip> grup = new ArrayList<>(); // els que es mouen
    private long[] posGrup0 = new long[0];
    private boolean triatAbans;                       // el clip tocat ja era dins d'un grup triat
    private float x0, y0;
    private boolean mogut;
    private long pos0, ini0, fi0, snap0, visIni0, visFi0, fadeIn0, fadeOut0, primer0;
    private double guany0;
    private boolean banderaFixa;

    private final Runnable premut = this::comencaTria;

    public PistesView(Context c, Projecte p, Vista v, int altPistaPx) {
        super(c);
        projecte = p;
        vista = v;
        altPista = altPistaPx;
        mansa.setStyle(Paint.Style.FILL);
    }

    public static final int ALT_MIN_DP = 48, ALT_MAX_DP = 200;

    // gravació en directe: fila on es dibuixa (pot ser una fila nova, sota l'última) i nivells
    private int filaViva = -1;
    private float[] vivaMs = new float[4096], vivaPic = new float[4096];
    private int vivaN = 0;

    /** Comença a dibuixar una gravació en directe a la fila {@code fila} (= nombre de pistes per a una pista nova). */
    public void comencaViva(int fila) {
        filaViva = fila;
        vivaN = 0;
        requestLayout();
        invalidate();
    }

    /** Afegeix un nivell (0..1) que ha entrat pel micròfon quan sonava el moment {@code ms} de la línia de temps. */
    public void afegeixViva(long ms, float pic) {
        if (filaViva < 0) return;
        if (vivaN == vivaMs.length) {
            vivaMs = java.util.Arrays.copyOf(vivaMs, vivaN * 2);
            vivaPic = java.util.Arrays.copyOf(vivaPic, vivaN * 2);
        }
        vivaMs[vivaN] = ms;
        vivaPic[vivaN] = pic;
        vivaN++;
        invalidate();
    }

    public void aturaViva() {
        filaViva = -1;
        vivaN = 0;
        requestLayout();
        invalidate();
    }

    private void dibuixaViva(Canvas c, float w) {
        if (filaViva < 0 || vivaN == 0) return;
        float top = filaViva * altPista + dp(5);
        float baix = (filaViva + 1) * altPista - dp(5);
        float alt = baix - top;
        float xa = vista.x(vivaMs[0]);
        float xb = vista.x(vivaMs[vivaN - 1]);
        if (xb < 0 || xa > w) return;
        r.set(xa, top, Math.max(xa + dp(2), xb), baix);
        ple.setColor(Ui.alfa(Ui.ACCENT, 46));
        c.drawRoundRect(r, dp(6), dp(6), ple);
        barra.setColor(Ui.alfa(Ui.ACCENT, 230));
        float mig = top + alt / 2f;
        float pas = dp(2.5f);
        float ultimaX = -1e9f;
        float pic = 0;
        for (int i = 0; i < vivaN; i++) {
            float x = vista.x(vivaMs[i]);
            pic = Math.max(pic, vivaPic[i]);
            if (x - ultimaX < pas && i < vivaN - 1) continue;
            if (x >= 0 && x <= w) {
                float hb = Math.max(dp(1), pic * (alt - dp(10)));
                c.drawRect(x, mig - hb / 2f, x + pas * 0.6f, mig + hb / 2f, barra);
            }
            ultimaX = x;
            pic = 0;
        }
        linia.setStyle(Paint.Style.STROKE);
        linia.setStrokeWidth(dp(1.5f));
        linia.setColor(Ui.ACCENT);
        c.drawRoundRect(r, dp(6), dp(6), linia);
        linia.setStyle(Paint.Style.FILL_AND_STROKE);
    }

    // pista que s'està arrossegant des de la capçalera (-1 = cap)
    private int arrDe = -1, arrA = -1;
    private float arrDy;

    /** Mentre s'arrossega una pista: la pista {@code de} es desplaça {@code dy} i les altres li fan lloc fins a {@code a}. */
    public void setArrossegant(int de, float dy, int a) {
        arrDe = de;
        arrDy = dy;
        arrA = a;
        invalidate();
    }

    /** Desplaçament vertical amb què es dibuixa la pista {@code i}. */
    private float desplacament(int i) {
        if (arrDe < 0) return 0;
        if (i == arrDe) return arrDy;
        if (arrDe < arrA && i > arrDe && i <= arrA) return -altPista;
        if (arrA < arrDe && i >= arrA && i < arrDe) return altPista;
        return 0;
    }

    public int altPista() {
        return altPista;
    }

    /** Canvia l'alçada de les pistes (zoom vertical). */
    public void setAltPista(int px) {
        int nou = Math.max((int) dp(ALT_MIN_DP), Math.min((int) dp(ALT_MAX_DP), px));
        if (nou == altPista) return;
        altPista = nou;
        requestLayout();
        invalidate();
    }

    public void setOient(Oient o) {
        oient = o;
    }

    public void posaOna(String wav, float[] pics) {
        ones.put(wav, pics);
        invalidate();
    }

    @Override
    protected void onMeasure(int ample, int alt) {
        int files = projecte.pistes.size() + (filaViva >= projecte.pistes.size() ? 1 : 0);
        int h = Math.max(1, files) * altPista;
        setMeasuredDimension(MeasureSpec.getSize(ample), h);
    }

    private float dp(float v) {
        return Ui.dp(getContext(), v);
    }

    // ---------- Dibuix ----------

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth();
        int n = projecte.pistes.size();

        // fons de les pistes (alternat) i separadors
        for (int i = 0; i < n; i++) {
            ple.setColor(i % 2 == 0 ? Ui.FONS : 0xFF121215);
            c.drawRect(0, i * altPista, w, (i + 1) * altPista, ple);
        }
        dibuixaGraella(c, w, n * altPista);
        linia.setStrokeWidth(1);
        linia.setColor(Ui.LINIA);
        for (int i = 1; i <= n; i++) c.drawLine(0, i * altPista - 0.5f, w, i * altPista - 0.5f, linia);

        synchronized (projecte) {
            for (int i = 0; i < n; i++) {
                if (i != arrDe) dibuixaPista(c, i, w);
            }
            // la que s'arrossega, per sobre de tot
            if (arrDe >= 0 && arrDe < n) {
                c.save();
                c.translate(0, desplacament(arrDe));
                ple.setColor(0x33FFFFFF);
                c.drawRect(0, arrDe * altPista, w, (arrDe + 1) * altPista, ple);
                c.restore();
                dibuixaPista(c, arrDe, w);
            }
        }

        dibuixaViva(c, w);

        // capçal
        float xc = vista.x(vista.capcalMs);
        linia.setColor(Ui.TEXT);
        linia.setStrokeWidth(dp(1.5f));
        c.drawLine(xc, 0, xc, getHeight(), linia);
    }

    private void dibuixaPista(Canvas c, int i, float w) {
        Projecte.Pista t = projecte.pistes.get(i);
        float d = desplacament(i);
        c.save();
        c.translate(0, d);
        // primer els no triats, i els triats a sobre
        for (Projecte.Clip k : t.clips) if (!vista.triats.contains(k)) dibuixaClip(c, i, t, k, w);
        for (Projecte.Clip k : t.clips) if (vista.triats.contains(k)) dibuixaClip(c, i, t, k, w);
        c.restore();
    }

    private void dibuixaGraella(Canvas c, float w, float h) {
        double mpt = projecte.msPerTemps();
        float pxTemps = (float) (mpt * vista.pxPerMs);
        long primer = (long) Math.floor(vista.ms(0) / mpt);
        long darrer = (long) Math.ceil(vista.ms(w) / mpt);
        linia.setStrokeWidth(1);
        for (long t = Math.max(0, primer); t <= darrer; t++) {
            boolean esCompas = t % projecte.compas == 0;
            if (!esCompas && pxTemps < dp(7)) continue;
            float x = vista.x(t * mpt);
            linia.setColor(esCompas ? 0x24FFFFFF : 0x0FFFFFFF);
            c.drawLine(x, 0, x, h, linia);
        }
    }

    private void dibuixaClip(Canvas c, int i, Projecte.Pista t, Projecte.Clip k, float w) {
        boolean sona = projecte.sona(t);
        int color = t.veu ? Ui.VEU : Ui.GUITARRA;
        if (!sona) color = Ui.APAGAT;
        boolean triat = vista.triats.contains(k);
        boolean manetes = triat && vista.triats.size() == 1;

        float top = i * altPista + dp(5);
        float baix = (i + 1) * altPista - dp(5);
        float alt = baix - top;
        float xa = vista.x(k.iniciVisible());
        float xb = vista.x(k.fiVisible());
        if (xb < -dp(30) || xa > w + dp(30)) return;
        float rad = dp(6);

        // cos del clip (opac, perquè si se'n trepitgen dos es vegi el de sobre)
        r.set(xa, top, xb, baix);
        ple.setColor(i % 2 == 0 ? Ui.FONS : 0xFF121215);
        c.drawRoundRect(r, rad, rad, ple);
        ple.setColor(Ui.alfa(color, triat ? 70 : 46));
        c.drawRoundRect(r, rad, rad, ple);

        // ona (escalada pel volum del clip); fora del so (si s'ha allargat la finestra) no n'hi ha
        float[] pics = ones.get(k.wav);
        long durada = projecte.duradaDe(k);
        if (pics != null && durada > 0) {
            double g = Mesclador.lineal(k.guanyDb);
            barra.setColor(Ui.alfa(color, sona ? 220 : 120));
            float pas = dp(2.5f);
            float mig = top + alt / 2f;
            float desde = Math.max(xa + 1, 0);
            float fins = Math.min(xb - 1, w);
            for (float x = desde; x < fins; x += pas) {
                double ms = vista.ms(x) - k.posMs;          // dins el fitxer
                double ms2 = vista.ms(x + pas) - k.posMs;
                if (ms2 < 0 || ms > durada) continue;
                int a = (int) Math.max(0, ms / durada * pics.length);
                int b = (int) Math.min(pics.length, Math.ceil(ms2 / durada * pics.length));
                float v = 0;
                for (int q = a; q < b; q++) v = Math.max(v, pics[q]);
                if (b <= a && a < pics.length) v = pics[a];
                // fades
                double env = 1;
                if (k.fadeInMs > 0 && ms < k.iniciMs + k.fadeInMs) env = Math.max(0, (ms - k.iniciMs) / k.fadeInMs);
                if (k.fadeOutMs > 0 && ms > k.fiMs - k.fadeOutMs) env = Math.min(env, Math.max(0, (k.fiMs - ms) / k.fadeOutMs));
                float hb = (float) Math.max(dp(1), v * g * env * (alt - dp(10)));
                c.drawRect(x, mig - hb / 2f, x + pas * 0.6f, mig + hb / 2f, barra);
            }
        }

        // fades: zona apagada i línia de -∞ (baix) a 0 dB (dalt)
        linia.setStrokeWidth(dp(1.5f));
        linia.setColor(Ui.TEXT);
        ple.setColor(0x88000000);
        if (k.fadeInMs > 0) {
            float xf = vista.x(k.iniciVisible() + k.fadeInMs);
            cami.reset();
            cami.moveTo(xa, baix);
            cami.lineTo(xa, top);
            cami.lineTo(xf, top);
            cami.close();
            c.drawPath(cami, ple);
            c.drawLine(xa, baix, xf, top, linia);
        }
        if (k.fadeOutMs > 0) {
            float xf = vista.x(k.fiVisible() - k.fadeOutMs);
            cami.reset();
            cami.moveTo(xb, baix);
            cami.lineTo(xb, top);
            cami.lineTo(xf, top);
            cami.close();
            c.drawPath(cami, ple);
            c.drawLine(xf, top, xb, baix, linia);
        }

        // línia de volum del clip
        float yg = yGuany(k, top, alt);
        if (k.guanyDb < -0.05 || manetes) {
            linia.setStrokeWidth(dp(1));
            linia.setColor(manetes ? 0xCCFFFFFF : 0x77FFFFFF);
            c.drawLine(xa, yg, xb, yg, linia);
        }

        // bandera del primer temps
        if (projecte.banderaVisible(k)) {
            float xp = vista.x(k.posMs + projecte.primerDe(k));
            linia.setColor(Ui.ACCENT);
            linia.setStrokeWidth(dp(1.5f));
            c.drawLine(xp, top, xp, baix, linia);
            cami.reset();
            cami.moveTo(xp, top);
            cami.lineTo(xp + dp(9), top + dp(4));
            cami.lineTo(xp, top + dp(8));
            cami.close();
            ple.setColor(Ui.ACCENT);
            c.drawPath(cami, ple);
        }

        // vora
        linia.setStyle(Paint.Style.STROKE);
        linia.setStrokeWidth(triat ? dp(2) : dp(1));
        linia.setColor(triat ? Ui.TEXT : Ui.alfa(color, 160));
        c.drawRoundRect(r, rad, rad, linia);
        linia.setStyle(Paint.Style.FILL_AND_STROKE);

        // nom
        ple.setColor(sona ? Ui.TEXT : Ui.SUAU);
        ple.setTextSize(dp(11));
        ple.setTypeface(Ui.MITJA);
        float xn = Math.max(xa, 0) + dp(8);
        if (xn < xb - dp(30)) {
            c.save();
            c.clipRect(xa, top, xb, baix);
            String nomClip = k.wav != null && !k.wav.equals(t.wav) ? projecte.presa(k).nom : t.nomVisible();
            c.drawText(nomClip, xn, top + dp(15), ple);
            c.restore();
        }

        // manetes (només si hi ha un sol clip triat)
        if (manetes) {
            float m = dp(7);
            mansa.setColor(Ui.TEXT);
            float xfi = vista.x(k.iniciVisible() + k.fadeInMs);
            float xfo = vista.x(k.fiVisible() - k.fadeOutMs);
            c.drawRect(xa, baix - m * 2, xa + m * 1.4f, baix, mansa);          // retall inici
            c.drawRect(xb - m * 1.4f, baix - m * 2, xb, baix, mansa);          // retall final
            c.drawRect(xfi - m * 0.7f, top, xfi + m * 0.7f, top + m * 1.4f, mansa); // fade in
            c.drawRect(xfo - m * 0.7f, top, xfo + m * 0.7f, top + m * 1.4f, mansa); // fade out
            c.drawCircle((xa + xb) / 2f, yg, m * 0.8f, mansa);                 // volum
            mansa.setColor(Ui.FONS);
            c.drawCircle((xa + xb) / 2f, yg, m * 0.35f, mansa);
        }
    }

    /** 0 dB a dalt del clip; -24 dB a un 80% de l'alçada. */
    private float yGuany(Projecte.Clip k, float top, float alt) {
        return top + dp(3) + (float) (-k.guanyDb / -Projecte.GUANY_MIN_DB) * (alt * 0.8f);
    }

    // ---------- Tocs ----------

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                comenca(e.getX(), e.getY());
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                if (e.getPointerCount() == 2) dosDits(e);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (mode == DOS_DITS) {
                    if (e.getPointerCount() >= 2) mouDosDits(e);
                    return true;
                }
                float x = e.getX(), y = e.getY();
                if (!mogut && (Math.abs(x - x0) > dp(4) || Math.abs(y - y0) > dp(4))) {
                    mogut = true;
                    removeCallbacks(premut);
                }
                if (mode == TRIA) {
                    estenTria(x);
                } else if (mogut && mode != BUIT && mode != CAP) {
                    arrossega(x, y);
                }
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                // en aixecar un dels dos dits, ja no es fa res fins que s'aixequin tots
                if (mode == DOS_DITS) mode = CAP;
                return true;
            case MotionEvent.ACTION_UP:
                removeCallbacks(premut);
                acaba(e.getX());
                return true;
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(premut);
                if (mode >= MOU && mode <= GUANY && oient != null) {
                    ordena();
                    oient.editat(true);
                }
                mode = CAP;
                return true;
            default:
                return true;
        }
    }

    /** Comença el gest de dos dits. Si s'estava editant un clip, el deixa com estava. */
    private void dosDits(MotionEvent e) {
        removeCallbacks(premut);
        if (mode >= MOU && mode <= GUANY && clip != null && pista >= 0 && pista < projecte.pistes.size()) {
            synchronized (projecte) {
                for (int i = 0; i < grup.size(); i++) grup.get(i).posMs = posGrup0[i];
                clip.posMs = pos0;
                clip.iniciMs = ini0;
                clip.fiMs = fi0;
                clip.fadeInMs = fadeIn0;
                clip.fadeOutMs = fadeOut0;
                clip.guanyDb = guany0;
                projecte.presa(clip).primerMs = primer0;
            }
            if (oient != null) {
                oient.info(null);
                oient.editat(false);
            }
        }
        if (mode == TRIA && oient != null) oient.info(null);
        mode = DOS_DITS;
        getParent().requestDisallowInterceptTouchEvent(true);
        focusX = (e.getX(0) + e.getX(1)) / 2f;
        focusY = (e.getY(0) + e.getY(1)) / 2f;
        ampleDits = Math.abs(e.getX(0) - e.getX(1));
        altDits = Math.abs(e.getY(0) - e.getY(1));
        invalidate();
    }

    /**
     * Dos dits: desplaçar (horitzontal i vertical) i zoom, tot alhora.
     * Obrir o tancar els dits de costat fa el zoom horitzontal (temps); de dalt a baix, el vertical (alçada de les pistes).
     */
    private void mouDosDits(MotionEvent e) {
        float fx = (e.getX(0) + e.getX(1)) / 2f;
        float fy = (e.getY(0) + e.getY(1)) / 2f;
        float aw = Math.abs(e.getX(0) - e.getX(1));
        float ah = Math.abs(e.getY(0) - e.getY(1));
        // zoom horitzontal: només si els dits estan prou separats de costat
        if (ampleDits > dp(40) && aw > dp(40)) vista.zoom(aw / ampleDits, fx);
        // zoom vertical: només si els dits estan prou separats de dalt a baix
        int dy = Math.round(fy - focusY);
        if (altDits > dp(40) && ah > dp(40)) {
            int abans = altPista;
            setAltPista(Math.round(altPista * (ah / altDits)));
            if (altPista != abans) {
                // manté a lloc el que hi ha sota els dits
                float factor = altPista / (float) abans;
                dy -= Math.round(fy * factor - fy);
                if (oient != null) oient.alcada(altPista);
            }
        }
        // desplaçament
        vista.iniciMs -= (fx - focusX) / vista.pxPerMs;
        vista.limita();
        if (dy != 0 && oient != null) oient.desplacaVertical(-dy);
        focusX = fx;
        focusY = fy;
        ampleDits = aw;
        altDits = ah;
        vista.avisa();
    }

    private void comenca(float x, float y) {
        x0 = x;
        y0 = y;
        mogut = false;
        mode = BUIT;
        clip = null;
        grup.clear();
        pista = (int) (y / altPista);
        if (pista < 0 || pista >= projecte.pistes.size()) {
            pista = -1;
            return;
        }
        Projecte.Pista t = projecte.pistes.get(pista);
        float top = pista * altPista + dp(5);
        float baix = (pista + 1) * altPista - dp(5);
        float alt = baix - top;

        // manetes del clip triat (si n'hi ha un de sol en aquesta pista; la més propera dins el radi)
        Projecte.Clip unic = vista.unic();
        if (unic != null && vista.triada == pista && t.clips.contains(unic)) {
            float xa = vista.x(unic.iniciVisible());
            float xb = vista.x(unic.fiVisible());
            float radi = dp(26);
            float[][] punts = {
                    {xa, baix, RETALL_I}, {xb, baix, RETALL_F},
                    {vista.x(unic.iniciVisible() + unic.fadeInMs), top, FADE_IN},
                    {vista.x(unic.fiVisible() - unic.fadeOutMs), top, FADE_OUT},
                    {(xa + xb) / 2f, yGuany(unic, top, alt), GUANY}
            };
            float millor = radi;
            int quin = CAP;
            for (float[] p : punts) {
                float d = (float) Math.hypot(x - p[0], y - p[1]);
                if (d < millor) {
                    millor = d;
                    quin = (int) p[2];
                }
            }
            if (quin != CAP) {
                mode = quin;
                guardaInici(t, unic);
                getParent().requestDisallowInterceptTouchEvent(true);
                return;
            }
        }
        if (y < top || y > baix) return;
        Projecte.Clip k = t.clipA(vista.ms(x));
        if (k == null) return;

        mode = MOU;
        triatAbans = vista.triats.contains(k) && vista.triats.size() > 1;
        if (!vista.triats.contains(k)) {
            vista.tria(pista, k);
            if (oient != null) oient.seleccio();
            vista.avisa();
        }
        guardaInici(t, k);
        // es mou tot el grup triat (o només aquest clip)
        grup.addAll(vista.triats);
        posGrup0 = new long[grup.size()];
        for (int i = 0; i < grup.size(); i++) posGrup0[i] = grup.get(i).posMs;
        // en mode Lliure, si es mou un sol clip i la bandera s'hi veu, la bandera es queda quieta
        banderaFixa = vista.snap == Projecte.SNAP_LLIURE && grup.size() == 1 && projecte.banderaVisible(k);
        getParent().requestDisallowInterceptTouchEvent(true);
        postDelayed(premut, TEMPS_PREMUT);
    }

    private void guardaInici(Projecte.Pista t, Projecte.Clip k) {
        clip = k;
        pos0 = k.posMs;
        ini0 = k.iniciMs;
        fi0 = k.fiMs;
        fadeIn0 = k.fadeInMs;
        fadeOut0 = k.fadeOutMs;
        guany0 = k.guanyDb;
        primer0 = projecte.primerDe(k);
        snap0 = projecte.puntSnap(k);
        visIni0 = k.iniciVisible();
        visFi0 = k.fiVisible();
    }

    /** Mantenir premut sense moure: comença a triar clips consecutius. */
    private void comencaTria() {
        if (mode != MOU || mogut || clip == null || pista < 0) return;
        mode = TRIA;
        grup.clear();
        vista.tria(pista, clip);
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        if (oient != null) {
            oient.info("Arrossega per triar més clips");
            oient.seleccio();
        }
        vista.avisa();
    }

    private void estenTria(float x) {
        Projecte.Pista t = projecte.pistes.get(pista);
        List<Projecte.Clip> nous = Projecte.rang(t, clip, vista.ms(x));
        if (nous.size() == vista.triats.size() && vista.triats.containsAll(nous)) return;
        vista.triats.clear();
        vista.triats.addAll(nous);
        if (oient != null) {
            oient.info(nous.size() == 1 ? "1 clip triat" : nous.size() + " clips triats");
            oient.seleccio();
        }
        vista.avisa();
    }

    private void arrossega(float x, float y) {
        if (pista < 0 || clip == null) return;
        Projecte.Pista t = projecte.pistes.get(pista);
        Projecte.Clip k = clip;
        double dms = (x - x0) / vista.pxPerMs;
        synchronized (projecte) {
            switch (mode) {
                case MOU: {
                    long nou = projecte.enganxa(snap0 + dms, vista.snap);
                    long delta = nou - snap0;
                    for (int i = 0; i < grup.size(); i++) grup.get(i).posMs = posGrup0[i] + delta;
                    if (banderaFixa) {
                        long pr = Projecte.primerAmbBanderaFixa(k, pos0, primer0);
                        projecte.presa(k).primerMs = pr;
                        if (oient != null) {
                            if (pr == primer0 && k.posMs != pos0) {
                                oient.info("La bandera quedaria fora del clip: es mou amb l'àudio");
                            } else {
                                oient.info(String.format(java.util.Locale.ROOT, "Bandera fixa · àudio %+d ms", k.posMs - pos0));
                            }
                        }
                    } else if (oient != null) {
                        String quants = grup.size() > 1 ? grup.size() + " clips · " : "";
                        oient.info(quants + "Compàs " + textCompas(projecte.puntSnap(k)));
                    }
                    break;
                }
                case RETALL_I: {
                    // es pot allargar abans de l'inici del so (silenci)
                    long abs = projecte.enganxa(visIni0 + dms, vista.snap);
                    k.iniciMs = Math.min(abs - k.posMs, k.fiMs - 50);
                    k.normalitza(projecte.duradaDe(k));
                    if (oient != null) oient.info("Inici " + textCompas(k.iniciVisible()));
                    break;
                }
                case RETALL_F: {
                    // es pot allargar més enllà del final del so (silenci)
                    long abs = projecte.enganxa(visFi0 + dms, vista.snap);
                    k.fiMs = Math.max(abs - k.posMs, k.iniciMs + 50);
                    k.normalitza(projecte.duradaDe(k));
                    if (oient != null) oient.info("Final " + textCompas(k.fiVisible()));
                    break;
                }
                case FADE_IN: {
                    double ms = vista.ms(x);
                    k.fadeInMs = Math.max(0, Math.round(ms - k.iniciVisible()));
                    k.normalitza(projecte.duradaDe(k));
                    if (oient != null) oient.info("Fade in " + segons(k.fadeInMs));
                    break;
                }
                case FADE_OUT: {
                    double ms = vista.ms(x);
                    k.fadeOutMs = Math.max(0, Math.round(k.fiVisible() - ms));
                    k.normalitza(projecte.duradaDe(k));
                    if (oient != null) oient.info("Fade out " + segons(k.fadeOutMs));
                    break;
                }
                case GUANY: {
                    float top = pista * altPista + dp(5);
                    float alt = altPista - dp(10);
                    double f = (y - top - dp(3)) / (alt * 0.8f);
                    k.guanyDb = Math.max(Projecte.GUANY_MIN_DB, Math.min(0, -f * -Projecte.GUANY_MIN_DB));
                    if (k.guanyDb > -0.3) k.guanyDb = 0;
                    if (oient != null) oient.info("Volum del clip " + Projecte.textDb(k.guanyDb));
                    break;
                }
                default:
                    break;
            }
        }
        if (oient != null) oient.editat(false);
        invalidate();
    }

    private void ordena() {
        if (pista < 0 || pista >= projecte.pistes.size()) return;
        synchronized (projecte) {
            projecte.pistes.get(pista).ordena();
        }
    }

    private void acaba(float x) {
        if (mode == DOS_DITS || mode == CAP) {
            mode = CAP;
            return;
        }
        if (mode == TRIA) {
            if (oient != null) {
                int n = vista.triats.size();
                oient.info(n == 1 ? "1 clip triat" : n + " clips triats");
            }
            mode = CAP;
            return;
        }
        if (!mogut) {
            if (mode == BUIT) {
                // toc en un espai buit: treu la selecció i posa-hi el capçal
                boolean hiHavia = !vista.triats.isEmpty();
                vista.tria(-1, null);
                if (hiHavia && oient != null) {
                    oient.info(null);
                    oient.seleccio();
                }
                if (oient != null) oient.capcal(Math.max(0, Math.round(vista.ms(x))));
                vista.avisa();
            } else if (mode == MOU && triatAbans) {
                // toc (sense moure) dins d'un grup triat: queda triat només aquest clip
                vista.tria(pista, clip);
                if (oient != null) {
                    oient.info(null);
                    oient.seleccio();
                }
                vista.avisa();
            }
        } else if (mode >= MOU && mode <= GUANY && oient != null) {
            ordena();
            if (mode == MOU && clip != null && projecte.primerDe(clip) != primer0) {
                oient.bandera(clip.wav);
            }
            oient.editat(true);
            oient.info(null);
        }
        mode = CAP;
    }

    /** «3.2» = compàs 3, temps 2. */
    private String textCompas(long ms) {
        double mpt = projecte.msPerTemps();
        long temps = (long) Math.floor(ms / mpt + 1e-6);
        if (temps < 0) return "abans de l'1";
        long comp = temps / projecte.compas + 1;
        long dins = temps % projecte.compas + 1;
        double resta = ms - temps * mpt;
        String r = comp + "." + dins;
        if (vista.snap == Projecte.SNAP_LLIURE && resta > 15) r += String.format(java.util.Locale.ROOT, " +%d ms", Math.round(resta));
        return r;
    }

    private static String segons(long ms) {
        return String.format(java.util.Locale.ROOT, "%.2f s", ms / 1000.0).replace('.', ',');
    }
}
