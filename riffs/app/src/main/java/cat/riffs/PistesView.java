package cat.riffs;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.util.HashMap;
import java.util.Map;

/**
 * Les pistes de l'Estudi, una sota l'altra, amb el clip de cada take.
 * Clip triat: cantonades de baix = retallar, cantonades de dalt = fades, punt de dalt al mig = volum del clip.
 * Arrossegar el clip el mou (amb snap); arrossegar un espai buit desplaça la vista; dos dits = zoom.
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
    }

    private static final int CAP = 0, MOU = 1, RETALL_I = 2, RETALL_F = 3, FADE_IN = 4, FADE_OUT = 5, GUANY = 6, BUIT = 7, DOS_DITS = 8;

    private final Projecte projecte;
    private final Vista vista;
    private final Map<String, float[]> ones = new HashMap<>();
    private final int altPista;
    private Oient oient;

    private final Paint ple = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linia = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barra = new Paint();
    private final Paint mansa = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path cami = new Path();
    private final RectF r = new RectF();

    // dos dits
    private float focusX, focusY, distancia;

    // estat de l'arrossegament
    private int mode = CAP;
    private int pista = -1;
    private float x0, y0;
    private boolean mogut;
    private double iniciVista0;
    private long pos0, ini0, fi0, snap0, visIni0, visFi0, fadeIn0, fadeOut0;
    private double guany0;

    public PistesView(Context c, Projecte p, Vista v, int altPistaPx) {
        super(c);
        projecte = p;
        vista = v;
        altPista = altPistaPx;
        mansa.setStyle(Paint.Style.FILL);
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
        int h = Math.max(1, projecte.pistes.size()) * altPista;
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

        for (int i = 0; i < n; i++) dibuixaClip(c, i, w);

        // capçal
        float xc = vista.x(vista.capcalMs);
        linia.setColor(Ui.TEXT);
        linia.setStrokeWidth(dp(1.5f));
        c.drawLine(xc, 0, xc, getHeight(), linia);
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

    private void dibuixaClip(Canvas c, int i, float w) {
        Projecte.Pista t = projecte.pistes.get(i);
        boolean sona = projecte.sona(t);
        int color = t.veu ? Ui.VEU : Ui.GUITARRA;
        if (!sona) color = Ui.APAGAT;
        boolean triat = i == vista.triada;

        float top = i * altPista + dp(5);
        float baix = (i + 1) * altPista - dp(5);
        float alt = baix - top;
        float xa = vista.x(t.iniciVisible());
        float xb = vista.x(t.fiVisible());
        if (xb < -dp(30) || xa > w + dp(30)) return;
        float rad = dp(6);

        // cos del clip
        r.set(xa, top, xb, baix);
        ple.setColor(Ui.alfa(color, triat ? 70 : 46));
        c.drawRoundRect(r, rad, rad, ple);

        // ona (escalada pel volum del clip)
        float[] pics = ones.get(t.wav);
        if (pics != null && t.duradaMs > 0) {
            double g = Mesclador.lineal(t.guanyDb);
            barra.setColor(Ui.alfa(color, sona ? 220 : 120));
            float pas = dp(2.5f);
            float mig = top + alt / 2f;
            float desde = Math.max(xa + 1, 0);
            float fins = Math.min(xb - 1, w);
            for (float x = desde; x < fins; x += pas) {
                double ms = vista.ms(x) - t.posMs;          // dins el fitxer
                double ms2 = vista.ms(x + pas) - t.posMs;
                int a = (int) Math.max(0, ms / t.duradaMs * pics.length);
                int b = (int) Math.min(pics.length, Math.ceil(ms2 / t.duradaMs * pics.length));
                float v = 0;
                for (int k = a; k < b; k++) v = Math.max(v, pics[k]);
                if (b <= a && a < pics.length) v = pics[a];
                // fades
                double env = 1;
                if (t.fadeInMs > 0 && ms < t.iniciMs + t.fadeInMs) env = Math.max(0, (ms - t.iniciMs) / t.fadeInMs);
                if (t.fadeOutMs > 0 && ms > t.fiMs - t.fadeOutMs) env = Math.min(env, Math.max(0, (t.fiMs - ms) / t.fadeOutMs));
                float hb = (float) Math.max(dp(1), v * g * env * (alt - dp(10)));
                c.drawRect(x, mig - hb / 2f, x + pas * 0.6f, mig + hb / 2f, barra);
            }
        }

        // fades: zona apagada i línia de -∞ (baix) a 0 dB (dalt)
        linia.setStrokeWidth(dp(1.5f));
        linia.setColor(Ui.TEXT);
        ple.setColor(0x88000000);
        if (t.fadeInMs > 0) {
            float xf = vista.x(t.iniciVisible() + t.fadeInMs);
            cami.reset();
            cami.moveTo(xa, baix);
            cami.lineTo(xa, top);
            cami.lineTo(xf, top);
            cami.close();
            c.drawPath(cami, ple);
            c.drawLine(xa, baix, xf, top, linia);
        }
        if (t.fadeOutMs > 0) {
            float xf = vista.x(t.fiVisible() - t.fadeOutMs);
            cami.reset();
            cami.moveTo(xb, baix);
            cami.lineTo(xb, top);
            cami.lineTo(xf, top);
            cami.close();
            c.drawPath(cami, ple);
            c.drawLine(xf, top, xb, baix, linia);
        }

        // línia de volum del clip
        float yg = yGuany(t, top, alt);
        if (t.guanyDb < -0.05 || triat) {
            linia.setStrokeWidth(dp(1));
            linia.setColor(triat ? 0xCCFFFFFF : 0x77FFFFFF);
            c.drawLine(xa, yg, xb, yg, linia);
        }

        // bandera del primer temps
        if (t.primerMs >= t.iniciMs && t.primerMs <= t.fiMs) {
            float xp = vista.x(t.posMs + t.primerMs);
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
            c.drawText(t.nom, xn, top + dp(15), ple);
            c.restore();
        }

        // manetes (només al clip triat)
        if (triat) {
            float m = dp(7);
            mansa.setColor(Ui.TEXT);
            float xfi = vista.x(t.iniciVisible() + t.fadeInMs);
            float xfo = vista.x(t.fiVisible() - t.fadeOutMs);
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
    private float yGuany(Projecte.Pista t, float top, float alt) {
        return top + dp(3) + (float) (-t.guanyDb / -Projecte.GUANY_MIN_DB) * (alt * 0.8f);
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
                if (Math.abs(x - x0) > dp(4) || Math.abs(y - y0) > dp(4)) mogut = true;
                if (mogut && mode != BUIT && mode != CAP) arrossega(x, y);
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                // en aixecar un dels dos dits, ja no es fa res fins que s'aixequin tots
                if (mode == DOS_DITS) mode = CAP;
                return true;
            case MotionEvent.ACTION_UP:
                acaba(e.getX());
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (mode >= MOU && mode <= GUANY && oient != null) oient.editat(true);
                mode = CAP;
                return true;
            default:
                return true;
        }
    }

    /** Comença el gest de dos dits. Si s'estava editant un clip, el deixa com estava. */
    private void dosDits(MotionEvent e) {
        if (mode >= MOU && mode <= GUANY && pista >= 0 && pista < projecte.pistes.size()) {
            Projecte.Pista t = projecte.pistes.get(pista);
            synchronized (projecte) {
                t.posMs = pos0;
                t.iniciMs = ini0;
                t.fiMs = fi0;
                t.fadeInMs = fadeIn0;
                t.fadeOutMs = fadeOut0;
                t.guanyDb = guany0;
            }
            if (oient != null) {
                oient.info(null);
                oient.editat(false);
            }
        }
        mode = DOS_DITS;
        getParent().requestDisallowInterceptTouchEvent(true);
        focusX = (e.getX(0) + e.getX(1)) / 2f;
        focusY = (e.getY(0) + e.getY(1)) / 2f;
        distancia = (float) Math.hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1));
        invalidate();
    }

    /** Dos dits: desplaçar (horitzontal i vertical) i zoom, tot alhora. */
    private void mouDosDits(MotionEvent e) {
        float fx = (e.getX(0) + e.getX(1)) / 2f;
        float fy = (e.getY(0) + e.getY(1)) / 2f;
        float d = (float) Math.hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1));
        // zoom (només si els dits no són massa junts)
        if (distancia > dp(30) && d > dp(30)) vista.zoom(d / distancia, fx);
        // desplaçament
        vista.iniciMs -= (fx - focusX) / vista.pxPerMs;
        vista.limita();
        int dy = Math.round(fy - focusY);
        if (dy != 0 && oient != null) oient.desplacaVertical(-dy);
        focusX = fx;
        focusY = fy;
        distancia = d;
        vista.avisa();
    }

    private void comenca(float x, float y) {
        x0 = x;
        y0 = y;
        mogut = false;
        mode = BUIT;
        pista = (int) (y / altPista);
        if (pista < 0 || pista >= projecte.pistes.size()) {
            pista = -1;
            return;
        }
        Projecte.Pista t = projecte.pistes.get(pista);
        pos0 = t.posMs;
        ini0 = t.iniciMs;
        fi0 = t.fiMs;
        fadeIn0 = t.fadeInMs;
        fadeOut0 = t.fadeOutMs;
        guany0 = t.guanyDb;
        snap0 = t.puntSnap();
        visIni0 = t.iniciVisible();
        visFi0 = t.fiVisible();

        float top = pista * altPista + dp(5);
        float baix = (pista + 1) * altPista - dp(5);
        float alt = baix - top;
        float xa = vista.x(t.iniciVisible());
        float xb = vista.x(t.fiVisible());

        // manetes del clip triat (la més propera dins el radi)
        if (pista == vista.triada) {
            float radi = dp(26);
            float[][] punts = {
                    {xa, baix, RETALL_I}, {xb, baix, RETALL_F},
                    {vista.x(t.iniciVisible() + t.fadeInMs), top, FADE_IN},
                    {vista.x(t.fiVisible() - t.fadeOutMs), top, FADE_OUT},
                    {(xa + xb) / 2f, yGuany(t, top, alt), GUANY}
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
                getParent().requestDisallowInterceptTouchEvent(true);
                return;
            }
        }
        if (x >= xa && x <= xb && y >= top && y <= baix) {
            mode = MOU;
            if (vista.triada != pista) {
                vista.triada = pista;
                vista.avisa();
            }
            getParent().requestDisallowInterceptTouchEvent(true);
        }
    }

    private void arrossega(float x, float y) {
        if (pista < 0) return;
        Projecte.Pista t = projecte.pistes.get(pista);
        double dms = (x - x0) / vista.pxPerMs;
        synchronized (projecte) {
            switch (mode) {
                case MOU: {
                    long nou = projecte.enganxa(snap0 + dms, vista.snap);
                    t.posMs = pos0 + (nou - snap0);
                    if (oient != null) oient.info("Compàs " + textCompas(t.puntSnap()));
                    break;
                }
                case RETALL_I: {
                    long abs = projecte.enganxa(visIni0 + dms, vista.snap);
                    t.iniciMs = Math.max(0, Math.min(abs - t.posMs, t.fiMs - 50));
                    t.normalitza();
                    if (oient != null) oient.info("Inici " + textCompas(t.iniciVisible()));
                    break;
                }
                case RETALL_F: {
                    long abs = projecte.enganxa(visFi0 + dms, vista.snap);
                    t.fiMs = Math.min(t.duradaMs, Math.max(abs - t.posMs, t.iniciMs + 50));
                    t.normalitza();
                    if (oient != null) oient.info("Final " + textCompas(t.fiVisible()));
                    break;
                }
                case FADE_IN: {
                    double ms = vista.ms(x);
                    t.fadeInMs = Math.max(0, Math.round(ms - t.iniciVisible()));
                    t.normalitza();
                    if (oient != null) oient.info("Fade in " + segons(t.fadeInMs));
                    break;
                }
                case FADE_OUT: {
                    double ms = vista.ms(x);
                    t.fadeOutMs = Math.max(0, Math.round(t.fiVisible() - ms));
                    t.normalitza();
                    if (oient != null) oient.info("Fade out " + segons(t.fadeOutMs));
                    break;
                }
                case GUANY: {
                    float top = pista * altPista + dp(5);
                    float alt = altPista - dp(10);
                    double f = (y - top - dp(3)) / (alt * 0.8f);
                    t.guanyDb = Math.max(Projecte.GUANY_MIN_DB, Math.min(0, -f * -Projecte.GUANY_MIN_DB));
                    if (t.guanyDb > -0.3) t.guanyDb = 0;
                    if (oient != null) oient.info("Volum del clip " + Projecte.textDb(t.guanyDb));
                    break;
                }
                default:
                    break;
            }
        }
        if (oient != null) oient.editat(false);
        invalidate();
    }

    private void acaba(float x) {
        if (mode == DOS_DITS || mode == CAP) {
            mode = CAP;
            return;
        }
        if (!mogut) {
            if (mode == BUIT) {
                // toc en un espai buit: treu la selecció i posa-hi el capçal
                vista.triada = -1;
                if (oient != null) oient.capcal(Math.max(0, Math.round(vista.ms(x))));
                vista.avisa();
            }
        } else if (mode >= MOU && mode <= GUANY && oient != null) {
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
