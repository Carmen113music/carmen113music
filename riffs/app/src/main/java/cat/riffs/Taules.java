package cat.riffs;

/**
 * Les taules d'ones del motor wavetable (2.38), fetes amb codi (no cal cap fitxer): cada taula té {@link #QUADRES}
 * formes d'ona seguides que es transformen d'una a l'altra (la «posició» passa d'una a la següent).
 * Cada forma es desa en {@link #NIVELLS} versions amb menys harmònics (per a les notes agudes), perquè no soni
 * brunzit digital (aliasing). Es fan la primera vegada que es fan servir. No depèn d'Android.
 */
public final class Taules {

    public static final String[] NOMS = {"Bàsica", "Pols", "Sync", "Harmònics", "Vocal", "Metall", "Plegada", "Escombrada"};
    /** Una frase per a l'editor: què fa la posició a cada taula. */
    public static final String[] AJUDA = {
            "sinus → triangle → serra → quadrada",
            "quadrada → pols cada cop més estret",
            "serra sincronitzada: el to «parla»",
            "s'hi van afegint harmònics, d'un a seixanta-quatre",
            "vocals: a, e, i, o, u",
            "FM: d'una sinus a un so de campana",
            "sinus doblegada (wavefolder): cada cop més aspra",
            "filtre ressonant que escombra els harmònics",
    };

    static final int QUADRES = 32;
    static final int MIDA = 2048;
    static final int NIVELLS = 11;            // harmònics màxims: 1024, 512, … 1
    private static final float[][][][] FETES = new float[NOMS.length][][][];

    private Taules() {
    }

    /** La taula {@code i}: [quadre][nivell][mostra]. */
    public static synchronized float[][][] taula(int i) {
        i = Math.max(0, Math.min(NOMS.length - 1, i));
        if (FETES[i] == null) FETES[i] = fes(i);
        return FETES[i];
    }

    /** El nivell (quants harmònics) que cal per a una nota que avança {@code dt} cicles per mostra. */
    static int nivell(double dt) {
        double h = 0.45 / Math.max(1e-6, dt);           // harmònics que hi caben per sota de 0,45 × mostreig
        if (h >= 1024) return 0;
        int k = (int) Math.ceil(Math.log(1024 / h) / Math.log(2));
        return Math.max(0, Math.min(NIVELLS - 1, k));
    }

    /** Una mostra: {@code pos} de 0 a 1 entre els quadres, {@code fase} de 0 a 1 dins el cicle. */
    static double llegeix(float[][][] t, double pos, int niv, double fase) {
        double fp = pos * (QUADRES - 1);
        int q0 = (int) fp;
        if (q0 >= QUADRES - 1) q0 = QUADRES - 2;
        double fq = fp - q0;
        float[] a = t[q0][niv], b = t[q0 + 1][niv];
        int n = a.length;
        double x = fase * n;
        int j = (int) x;
        if (j >= n) j = n - 1;
        double u = x - j;
        int j1 = j + 1 == n ? 0 : j + 1;
        double va = a[j] + (a[j1] - a[j]) * u;
        double vb = b[j] + (b[j1] - b[j]) * u;
        return va + (vb - va) * fq;
    }

    /** La forma d'ona d'un quadre (sense limitar, MIDA mostres), per dibuixar-la a l'editor. */
    public static float[] forma(int i, double pos) {
        float[][][] t = taula(i);
        float[] r = new float[256];
        for (int k = 0; k < r.length; k++) r[k] = (float) llegeix(t, pos, 2, k / (double) r.length);
        return r;
    }

    // ---------- Fer les taules ----------

    private static float[][][] fes(int i) {
        float[][][] r = new float[QUADRES][NIVELLS][];
        double[] re = new double[MIDA], im = new double[MIDA];
        for (int q = 0; q < QUADRES; q++) {
            double m = q / (double) (QUADRES - 1);
            double[] ona = new double[MIDA];
            omple(i, m, ona);
            for (int k = 0; k < MIDA; k++) {
                re[k] = ona[k];
                im[k] = 0;
            }
            fft(re, im, false);
            double[] r0 = re.clone(), i0 = im.clone();
            float guany = 1;
            for (int nv = 0; nv < NIVELLS; nv++) {
                int hMax = 1024 >> nv;
                int mida = Math.max(64, MIDA >> nv);
                double[] a = new double[MIDA], b = new double[MIDA];
                for (int h = 1; h <= hMax && h < MIDA / 2; h++) {
                    a[h] = r0[h];
                    b[h] = i0[h];
                    a[MIDA - h] = r0[MIDA - h];
                    b[MIDA - h] = i0[MIDA - h];
                }
                fft(a, b, true);
                if (nv == 0) {
                    double pic = 1e-9;
                    for (double v : a) pic = Math.max(pic, Math.abs(v));
                    guany = (float) (0.95 / pic);
                }
                float[] o = new float[mida];
                int pas = MIDA / mida;
                for (int k = 0; k < mida; k++) o[k] = (float) (a[k * pas] * guany);
                r[q][nv] = o;
            }
        }
        return r;
    }

    /** Escriu la forma d'ona de la taula {@code i} a la posició {@code m} (0 a 1). */
    private static void omple(int i, double m, double[] y) {
        int n = y.length;
        switch (i) {
            case 1: {   // pols: de 50 % a 3 %
                double pw = 0.5 - 0.47 * m;
                for (int k = 0; k < n; k++) y[k] = (k / (double) n) < pw ? 1 : -1;
                break;
            }
            case 2: {   // sync: serra que es reinicia, amb una proporció que puja de 1 a 8
                double rr = 1 + 7 * m;
                for (int k = 0; k < n; k++) {
                    double t = k / (double) n * rr;
                    y[k] = 2 * (t - Math.floor(t)) - 1;
                }
                break;
            }
            case 3: {   // harmònics que s'hi afegeixen
                double hs = 1 + 63 * m * m;
                for (int h = 1; h <= 64; h++) {
                    double w = Math.max(0, Math.min(1, hs - h + 1));
                    if (w <= 0) break;
                    addSin(y, h, w / h, 0);
                }
                break;
            }
            case 4: {   // vocals: formants (en harmònics, per a una veu d'uns 130 Hz)
                double[][] f = {{6.0, 9.0}, {3.0, 13.0}, {2.0, 17.5}, {3.5, 6.2}, {2.5, 5.4}};
                double p = m * 4;
                int a = Math.min(3, (int) p);
                double u = p - a;
                double f1 = f[a][0] * (1 - u) + f[a + 1][0] * u, f2 = f[a][1] * (1 - u) + f[a + 1][1] * u;
                for (int h = 1; h <= 48; h++) {
                    double g = Math.exp(-sq((h - f1) / 1.3)) + 0.6 * Math.exp(-sq((h - f2) / 2.0)) + 0.15 / h;
                    addSin(y, h, g, 0);
                }
                break;
            }
            case 5: {   // FM: dos operadors, l'índex puja
                double ix = 6 * m;
                for (int k = 0; k < n; k++) {
                    double t = k / (double) n;
                    double md = Math.sin(2 * Math.PI * 3 * t + 0.6 * Math.sin(2 * Math.PI * t));
                    y[k] = Math.sin(2 * Math.PI * t + ix * md);
                }
                break;
            }
            case 6: {   // wavefolder
                double g = 1 + 7 * m;
                for (int k = 0; k < n; k++) y[k] = Math.sin(g * Math.PI / 2 * Math.sin(2 * Math.PI * k / (double) n));
                break;
            }
            case 7: {   // serra amb un pic de ressonància que escombra
                double c = 1.5 + 30 * m;
                for (int h = 1; h <= 64; h++) {
                    double g = 0.35 / h + Math.exp(-sq((h - c) / 1.6)) * 0.9;
                    addSin(y, h, g, 0);
                }
                break;
            }
            default: {  // bàsica: sinus → triangle → serra → quadrada
                double p = m * 3;
                int a = Math.min(2, (int) p);
                double u = p - a;
                for (int k = 0; k < n; k++) {
                    double t = k / (double) n;
                    y[k] = basica(a, t) * (1 - u) + basica(a + 1, t) * u;
                }
                break;
            }
        }
    }

    private static double basica(int tipus, double t) {
        switch (tipus) {
            case 0:
                return Math.sin(2 * Math.PI * t);
            case 1:
                return 1 - 4 * Math.abs(((t + 0.25) % 1.0) - 0.5);   // triangle (comença a 0, com la sinus)
            case 2:
                return 2 * ((t + 0.5) % 1.0) - 1;                    // serra (comença a 0)
            default:
                return t < 0.5 ? 1 : -1;
        }
    }

    private static void addSin(double[] y, int h, double a, double fase) {
        int n = y.length;
        for (int k = 0; k < n; k++) y[k] += a * Sinte.sinT(h * k / (double) n + fase / (2 * Math.PI));
    }

    private static double sq(double x) {
        return x * x;
    }

    /** FFT complexa (radix 2, a lloc). {@code inversa}: divideix per n. */
    static void fft(double[] re, double[] im, boolean inversa) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) {
                double t = re[i];
                re[i] = re[j];
                re[j] = t;
                t = im[i];
                im[i] = im[j];
                im[j] = t;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = 2 * Math.PI / len * (inversa ? 1 : -1);
            double wr = Math.cos(ang), wi = Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                double cr = 1, ci = 0;
                for (int j = 0; j < len / 2; j++) {
                    int a = i + j, b = i + j + len / 2;
                    double ur = re[a], ui = im[a];
                    double vr = re[b] * cr - im[b] * ci, vi = re[b] * ci + im[b] * cr;
                    re[a] = ur + vr;
                    im[a] = ui + vi;
                    re[b] = ur - vr;
                    im[b] = ui - vi;
                    double t = cr * wr - ci * wi;
                    ci = cr * wi + ci * wr;
                    cr = t;
                }
            }
        }
        if (inversa) for (int i = 0; i < n; i++) {
            re[i] /= n;
            im[i] /= n;
        }
    }
}
