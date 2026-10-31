package cat.riffs;

/**
 * Els efectes del sinte, en aquest ordre: chorus, delay (a temps del projecte) i reverb.
 * Mono, mostra a mostra: els fa servir tant el so de les finestres com el sinte en directe, perquè sonin igual.
 * Amb tots tres a zero no es crea (el so és exactament el de sense efectes). No depèn d'Android.
 */
public final class Efectes {

    private static final int SR = Sinte.MOSTREIG;

    private final Sinte.So o;

    // chorus: línia curta amb dues lectures que es mouen
    private final float[] lc;
    private int ic = 0;
    private double faseC = 0;
    private double dtC;

    // delay: línia llarga amb realimentació (i un passa-baixos perquè les repeticions s'enfosqueixin)
    private final float[] ld;
    private int id = 0;
    private int retard;
    private final double bpm;
    private float filtreD = 0;

    // reverb (tipus Freeverb, mono): 8 filtres en pinta en paral·lel i 4 passa-tot en sèrie
    private static final int[] PINTES = {1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617};
    private static final int[] PASSATOT = {556, 441, 341, 225};
    private final float[][] bp;
    private final int[] ip;
    private final float[] fp;
    private final float[][] ba;
    private final int[] ia;
    private float realim, amort;

    public Efectes(Sinte.So o, double bpm) {
        this(o, bpm, false);
    }

    /**
     * {@code viu}: per sonar en directe amb l'editor de so obert (2.32): les línies es fan de la mida màxima,
     * perquè es puguin encendre i allargar els efectes mentre sona (cal cridar {@link #refresca()}).
     */
    public Efectes(Sinte.So o, double bpm, boolean viu) {
        this.o = o;
        this.bpm = bpm;
        lc = new float[(int) (0.04 * SR)];
        int r0 = retardDe(o, bpm);
        ld = new float[viu ? 2 * SR + 1 : o.delay > 0 ? r0 + 1 : 1];
        bp = new float[PINTES.length][];
        ip = new int[PINTES.length];
        fp = new float[PINTES.length];
        for (int i = 0; i < PINTES.length; i++) bp[i] = new float[viu || o.reverb > 0 ? PINTES[i] : 1];
        ba = new float[PASSATOT.length][];
        ia = new int[PASSATOT.length];
        for (int i = 0; i < PASSATOT.length; i++) ba[i] = new float[viu || o.reverb > 0 ? PASSATOT[i] : 1];
        refresca();
    }

    private static int retardDe(Sinte.So o, double bpm) {
        return Math.max(1, Math.min(4 * SR, (int) Math.round(msDelay(o, bpm) * SR / 1000)));
    }

    /** Torna a llegir els botons dels efectes. */
    public void refresca() {
        dtC = o.chorusHz / SR;
        retard = Math.min(ld.length - 1, retardDe(o, bpm));
        realim = (float) (0.7 + 0.28 * o.reverbMida);
        amort = (float) (0.45 - 0.25 * o.reverbMida);
    }

    /** Durada d'una repetició del delay, en ms. */
    public static double msDelay(Sinte.So o, double bpm) {
        double b = bpm > 0 ? bpm : 120;
        return Math.min(2000, o.delayTemps * 60000.0 / b);
    }

    /** Fins quan poden sonar els efectes després de l'última nota (en mostres). */
    public static long cuaMostres(Sinte.So o, double bpm) {
        double ms = 0;
        if (o.delay > 0) {
            double rep = Math.max(0.05, o.delayRepeticio);
            double n = Math.min(12, Math.log(0.01) / Math.log(rep) + 1);   // repeticions fins a −40 dB
            ms = Math.max(ms, msDelay(o, bpm) * n);
        }
        if (o.reverb > 0) ms = Math.max(ms, 600 + 3400 * o.reverbMida);
        if (o.chorus > 0) ms = Math.max(ms, 40);
        return Math.round(Math.min(6000, ms) * SR / 1000.0);
    }

    public float processa(float x) {
        float y = x;
        if (o.chorus > 0) y = chorus(y);
        if (o.delay > 0) y = delay(y);
        if (o.reverb > 0) y = reverb(y);
        return y;
    }

    private float chorus(float x) {
        lc[ic] = x;
        faseC += dtC;
        if (faseC >= 1) faseC -= 1;
        double m1 = Math.sin(2 * Math.PI * faseC), m2 = Math.sin(2 * Math.PI * (faseC + 0.33));
        float a = llegeix(lc, ic, (12 + 4.5 * m1) * SR / 1000.0);
        float b = llegeix(lc, ic, (17 + 5.5 * m2) * SR / 1000.0);
        ic = (ic + 1) % lc.length;
        double c = o.chorus;
        return (float) ((x + c * 0.8 * (a + b)) / (1 + c * 0.6));
    }

    private static float llegeix(float[] l, int escrit, double enrere) {
        double p = escrit - enrere;
        while (p < 0) p += l.length;
        int i0 = (int) p;
        double f = p - i0;
        int i1 = (i0 + 1) % l.length;
        return (float) (l[i0 % l.length] * (1 - f) + l[i1] * f);
    }

    private float delay(float x) {
        if (retard < 1) return x;
        int r = id - retard;
        if (r < 0) r += ld.length;
        float eco = ld[r];
        filtreD += (eco - filtreD) * 0.45f;
        ld[id] = x + filtreD * (float) o.delayRepeticio;
        id = (id + 1) % ld.length;
        return x + eco * (float) o.delay * 0.8f;
    }

    private float reverb(float x) {
        if (bp[0].length < 2) return x;
        float in = x * 0.015f * 2;
        float s = 0;
        for (int i = 0; i < PINTES.length; i++) {
            float[] b = bp[i];
            float y = b[ip[i]];
            fp[i] = y * (1 - amort) + fp[i] * amort;
            b[ip[i]] = in + fp[i] * realim;
            ip[i] = (ip[i] + 1) % b.length;
            s += y;
        }
        for (int i = 0; i < PASSATOT.length; i++) {
            float[] b = ba[i];
            float bo = b[ia[i]];
            float y = -s + bo;
            b[ia[i]] = s + bo * 0.5f;
            ia[i] = (ia[i] + 1) % b.length;
            s = y;
        }
        double w = o.reverb;
        return (float) (x * (1 - 0.35 * w) + s * w * 1.4);
    }
}
