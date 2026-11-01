package cat.riffs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Ritme fet amb la veu (beatbox) → cops de bateria (2.35). Sense calibratge.
 * <ol>
 * <li>Troba els cops: quan l'espectre canvia de cop (flux espectral) i el so és prou fort.</li>
 * <li>Per a cada cop mira com sona els primers 70 ms: on cau l'energia (greus, mitjos, aguts) i el «centre» del so.</li>
 * <li>Els agrupa en tres famílies començant per com sol sonar cadascuna i adaptant-se als sons d'aquella gravació:
 * bombo («bum», «b»: greu), caixa («pf», «ka», «psh»: soroll al mig) i hihat («ts», «t»: agut).
 * Un hihat que dura (més de 200 ms, «tsss») és un hihat obert.</li>
 * <li>La força (1–3) surt del volum de cada cop comparat amb els altres del mateix tipus.</li>
 * </ol>
 * No depèn d'Android: es prova amb Java d'escriptori.
 */
public final class Beatbox {

    private Beatbox() {}

    public static final int BOMBO = 0, CAIXA = 1, HIHAT = 2, OBERT = 3;
    public static final String[] NOMS = {"Bombo", "Caixa", "Hihat", "Hihat obert"};

    /** Sensibilitat: com de fluixos poden ser els cops que es tenen en compte. */
    public static final String[] SENSIBILITATS = {"Baixa", "Mitjana", "Alta"};

    public static final class Cop {
        public final long ms;
        public int tipus;
        public int forca = 2;
        public final double centreHz, greus, aguts, duradaMs, db;

        Cop(long ms, double centreHz, double greus, double aguts, double duradaMs, double db) {
            this.ms = ms;
            this.centreHz = centreHz;
            this.greus = greus;
            this.aguts = aguts;
            this.duradaMs = duradaMs;
            this.db = db;
        }
    }

    static final int FS = 22050;
    static final int N = 512, PAS = 128;       // per trobar els cops (23 ms, cada 5,8 ms)
    static final int N_TRET = 2048;            // per mirar com sona (93 ms amb zeros al final)

    /** Troba i classifica els cops. {@code sensibilitat}: 0 baixa, 1 mitjana, 2 alta. */
    public static List<Cop> detecta(short[] mostres, int sr, int sensibilitat) {
        List<Cop> r = new ArrayList<>();
        if (mostres == null || mostres.length < N * 2) return r;
        float[] x = redueix(mostres, sr);
        int n = (x.length - N) / PAS + 1;
        double[] hann = new double[N];
        for (int i = 0; i < N; i++) hann[i] = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / N);
        double[] re = new double[N], im = new double[N];
        double[] ant = new double[N / 2], flux = new double[n], rms = new double[n];
        for (int k = 0; k < n; k++) {
            int s = k * PAS;
            double e = 0;
            for (int i = 0; i < N; i++) {
                double v = x[s + i];
                e += v * v;
                re[i] = v * hann[i];
                im[i] = 0;
            }
            rms[k] = Math.sqrt(e / N);
            Analisi.fft(re, im);
            double f = 0;
            for (int b = 1; b < N / 2; b++) {
                double m = Math.log1p(100 * Math.sqrt(re[b] * re[b] + im[b] * im[b]));
                double d = m - ant[b];
                if (d > 0) f += d;
                ant[b] = m;
            }
            flux[k] = f;
        }
        double fluxMax = 0, rmsMax = 0;
        for (int k = 0; k < n; k++) {
            fluxMax = Math.max(fluxMax, flux[k]);
            rmsMax = Math.max(rmsMax, rms[k]);
        }
        if (fluxMax <= 0 || rmsMax < 0.002) return r;
        double[] rel = {0.16, 0.10, 0.06};
        double[] porta = {-30, -36, -42};
        int sens = Math.max(0, Math.min(2, sensibilitat));
        int minDist = (int) Math.round(0.07 * FS / PAS);     // 70 ms entre cops
        int ultim = -minDist;
        double[] fin = new double[25];
        for (int k = 1; k + 1 < n; k++) {
            if (flux[k] < flux[k - 1] || flux[k] < flux[k + 1]) continue;
            // llindar adaptatiu: mediana dels voltants + un mínim
            int c = 0;
            for (int q = Math.max(0, k - 12); q <= Math.min(n - 1, k + 12); q++) fin[c++] = flux[q];
            Arrays.sort(fin, 0, c);
            double llindar = fin[c / 2] * 1.5 + rel[sens] * fluxMax;
            if (flux[k] < llindar) continue;
            // prou fort (mira el màxim dels 40 ms següents)
            double pic = 0;
            for (int q = k; q < Math.min(n, k + 7); q++) pic = Math.max(pic, rms[q]);
            double db = 20 * Math.log10(pic / rmsMax + 1e-9);
            if (db < porta[sens]) continue;
            if (k - ultim < minDist) {
                // dos cops massa junts: es queda el més fort
                if (!r.isEmpty() && db > r.get(r.size() - 1).db) r.remove(r.size() - 1);
                else continue;
            }
            ultim = k;
            int inici = Math.max(0, k * PAS - PAS / 2);
            r.add(mira(x, inici, db, rmsMax));
        }
        classifica(r);
        forces(r);
        return r;
    }

    /** Com sona un cop: energia per bandes, centre del so, durada. */
    static Cop mira(float[] x, int inici, double db, double rmsMax) {
        int lon = (int) (0.07 * FS);
        double[] re = new double[N_TRET], im = new double[N_TRET];
        for (int i = 0; i < lon && inici + i < x.length; i++) {
            double w = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / lon);
            re[i] = x[inici + i] * w;
        }
        Analisi.fft(re, im);
        double bin = FS / (double) N_TRET;
        double tot = 0, greus = 0, aguts = 0, cent = 0;
        for (int b = 1; b < N_TRET / 2; b++) {
            double hz = b * bin;
            if (hz < 30) continue;
            double p = re[b] * re[b] + im[b] * im[b];
            tot += p;
            cent += p * hz;
            if (hz < 250) greus += p;
            if (hz > 3000) aguts += p;
        }
        double centre = tot > 0 ? cent / tot : 1000;
        // durada: fins que el volum baixa 20 dB per sota del pic (màxim 500 ms)
        int fin = (int) (0.01 * FS);
        double pic = 0;
        int k = inici, fi = Math.min(x.length, inici + (int) (0.5 * FS));
        double[] env = new double[(fi - inici) / fin + 1];
        int ne = 0;
        for (int s = inici; s + fin <= fi; s += fin) {
            double e = 0;
            for (int i = 0; i < fin; i++) e += x[s + i] * x[s + i];
            env[ne] = Math.sqrt(e / fin);
            pic = Math.max(pic, env[ne]);
            ne++;
        }
        int dur = 0;
        for (int i = 0; i < ne; i++) {
            if (env[i] >= pic * 0.1) dur = i + 1;
            else if (i > 2 && env[i] < pic * 0.1) break;
        }
        long ms = Math.round(inici * 1000.0 / FS);
        return new Cop(ms, centre, tot > 0 ? greus / tot : 0, tot > 0 ? aguts / tot : 0, dur * 10.0, db);
    }

    /** Lluminositat d'un cop: com més alt, més agut i més «xiuxiueig» (en octaves aproximades). */
    static double llum(Cop c) {
        return Math.log(Math.max(60, c.centreHz)) / Math.log(2) + 4 * c.aguts - 2 * c.greus;
    }

    /** On sol caure cada família (bombo, caixa, hihat) en lluminositat, si la gravació no diu res més. */
    static final double[] TIPIC = {6, 11, 16};
    /** Dos grups més a prop que això són la mateixa família. */
    static final double SEPARACIO = 1.2;

    /**
     * Agrupa els cops en bombo, caixa i hihat sense calibratge: ordena els cops de la gravació del més greu al
     * més agut i hi busca fins a tres grups (k-means d'una dimensió). Amb tres grups clars: greu = bombo,
     * mig = caixa, agut = hihat. Amb menys, cada grup pren la família on sol caure el seu so.
     */
    static void classifica(List<Cop> cops) {
        int n = cops.size();
        if (n == 0) return;
        double[] v = new double[n];
        for (int i = 0; i < n; i++) v[i] = llum(cops.get(i));
        double[] ord = v.clone();
        Arrays.sort(ord);
        double[] c = {ord[(int) (n * 0.1)], ord[n / 2], ord[Math.min(n - 1, (int) (n * 0.9))]};
        int[] g = new int[n];
        for (int it = 0; it < 30; it++) {
            for (int i = 0; i < n; i++) {
                int m = 0;
                for (int k = 1; k < 3; k++) if (Math.abs(v[i] - c[k]) < Math.abs(v[i] - c[m])) m = k;
                g[i] = m;
            }
            for (int k = 0; k < 3; k++) {
                double s = 0;
                int q = 0;
                for (int i = 0; i < n; i++) if (g[i] == k) {
                    s += v[i];
                    q++;
                }
                if (q > 0) c[k] = s / q;
            }
        }
        // ajunta grups massa propers o buits
        List<double[]> grups = new ArrayList<>();   // {centre, quants, índex vell}
        for (int k = 0; k < 3; k++) {
            int q = 0;
            for (int i = 0; i < n; i++) if (g[i] == k) q++;
            if (q > 0) grups.add(new double[]{c[k], q, k});
        }
        grups.sort((x, y) -> Double.compare(x[0], y[0]));
        int[] nouDe = new int[3];
        List<double[]> fets = new ArrayList<>();
        for (double[] gr : grups) {
            double[] ult = fets.isEmpty() ? null : fets.get(fets.size() - 1);
            if (ult != null && gr[0] - ult[0] < SEPARACIO) {
                ult[0] = (ult[0] * ult[1] + gr[0] * gr[1]) / (ult[1] + gr[1]);
                ult[1] += gr[1];
            } else {
                fets.add(new double[]{gr[0], gr[1]});
            }
            nouDe[(int) gr[2]] = fets.size() - 1;
        }
        // a quina família va cada grup (respectant l'ordre greu → agut)
        int[] familia;
        if (fets.size() == 3) {
            familia = new int[]{BOMBO, CAIXA, HIHAT};
        } else if (fets.size() == 2) {
            familia = new int[]{BOMBO, HIHAT};
            double millor = Double.MAX_VALUE;
            for (int a = 0; a < 3; a++) for (int b = a + 1; b < 3; b++) {
                double d = Math.abs(fets.get(0)[0] - TIPIC[a]) + Math.abs(fets.get(1)[0] - TIPIC[b]);
                if (d < millor) {
                    millor = d;
                    familia = new int[]{a, b};
                }
            }
        } else {
            int m = 0;
            for (int k = 1; k < 3; k++) if (Math.abs(fets.get(0)[0] - TIPIC[k]) < Math.abs(fets.get(0)[0] - TIPIC[m])) m = k;
            familia = new int[]{m};
        }
        for (int i = 0; i < n; i++) {
            Cop cp = cops.get(i);
            int t = familia[nouDe[g[i]]];
            if (t == HIHAT && cp.duradaMs >= 200) t = OBERT;
            cp.tipus = t;
        }
    }

    /** Força 1–3 segons el volum de cada cop respecte de la mitjana del seu tipus. */
    static void forces(List<Cop> cops) {
        for (int t = 0; t < 4; t++) {
            List<Double> dbs = new ArrayList<>();
            for (Cop c : cops) if (c.tipus == t) dbs.add(c.db);
            if (dbs.isEmpty()) continue;
            dbs.sort(null);
            double med = dbs.get(dbs.size() / 2);
            for (Cop c : cops) {
                if (c.tipus != t) continue;
                c.forca = c.db > med + 3 ? 3 : c.db < med - 6 ? 1 : 2;
            }
        }
    }

    static float[] redueix(short[] m, int sr) {
        int f = Math.max(1, (int) Math.round(sr / (double) FS));
        if (f == 1) {
            float[] r = new float[m.length];
            for (int i = 0; i < m.length; i++) r[i] = m[i] / 32768f;
            return r;
        }
        int taps = 8 * f + 1, mig = taps / 2;
        double[] h = new double[taps];
        double fc = 0.45 / f, suma = 0;
        for (int i = 0; i < taps; i++) {
            int k = i - mig;
            double s = k == 0 ? 2 * fc : Math.sin(2 * Math.PI * fc * k) / (Math.PI * k);
            h[i] = s * (0.54 - 0.46 * Math.cos(2 * Math.PI * i / (taps - 1)));
            suma += h[i];
        }
        int n = m.length / f;
        float[] r = new float[n];
        for (int j = 0; j < n; j++) {
            int c = j * f;
            double acc = 0;
            for (int i = 0; i < taps; i++) {
                int idx = c + i - mig;
                if (idx >= 0 && idx < m.length) acc += h[i] * m[idx];
            }
            r[j] = (float) (acc / suma / 32768.0);
        }
        return r;
    }

    // ---------- Cops → ritmes del drumpad ----------

    /** Una finestra de bateria: comença {@code compasDes} compassos després de l'entrada. */
    public static final class Finestra {
        public int compasDes;
        public Patro patro;
    }

    public static final class Resultat {
        public int compasEntrada = 1;
        public final List<Finestra> finestres = new ArrayList<>();
        public final int[] perTipus = new int[4];
        public int cops;
    }

    /** Les files on va cada tipus, per ordre de preferència (si el kit no en té, la següent). */
    static final String[][] FILES = {
            {"kick", "tom-greu", "tom", "perc"},
            {"snare", "clap", "rim", "perc"},
            {"hihat", "hihat-pedal", "ride", "hihat-obert"},
            {"hihat-obert", "crash", "ride", "hihat"}};

    /**
     * Posa els cops del clip a la graella del projecte, en ritmes de fins a 4 compassos (el màxim del drumpad).
     *
     * @param plantilla un ritme amb el kit i les files que es volen (es copia i es buida)
     * @param graella   0 corxeres, 1 semicorxeres, 2 fuses
     */
    public static Resultat ritmes(List<Cop> cops, long posMs, long iniciMs, long fiMs, double msPerCompas, int compas,
                                  int graella, Patro plantilla) {
        Resultat r = new Resultat();
        int semis = Patro.passosDe(compas);
        double msQuant = msPerCompas / (graella == 0 ? semis / 2.0 : graella == 2 ? semis * 2.0 : semis);
        List<double[]> qs = new ArrayList<>();   // {ms a la línia de temps (quantitzat), tipus, força}
        double primer = Double.MAX_VALUE;
        for (Cop c : cops) {
            if (c.ms < iniciMs - 20 || c.ms >= fiMs) continue;
            double t = posMs + c.ms;
            if (t < -msQuant / 2) continue;
            double q = Math.max(0, Math.round(t / msQuant) * msQuant);
            qs.add(new double[]{q, c.tipus, c.forca});
            primer = Math.min(primer, q);
        }
        if (qs.isEmpty()) return r;
        int c0 = (int) Math.floor(primer / msPerCompas + 1e-6);
        r.compasEntrada = c0 + 1;
        double ultim = 0;
        for (double[] q : qs) ultim = Math.max(ultim, q[0]);
        int total = (int) Math.floor(ultim / msPerCompas + 1e-6) - c0 + 1;
        int max = Patro.MAX_COMPASSOS;
        for (int b0 = 0; b0 < total; b0 += max) {
            int nb = Math.min(max, total - b0);
            Patro p = Patro.deText(plantilla.text());
            if (p == null) continue;
            p.compas = compas;
            p.posaCompassos(nb);
            if (graella == 2) p.posaDivisio(compas == 6 ? 4 : 8);
            for (Patro.Fila fl : p.files) Arrays.fill(fl.cops, 0);
            int ppc = p.passosPerCompas;
            double msPas = msPerCompas / ppc;
            double ini = (c0 + b0) * msPerCompas;
            int posats = 0;
            for (double[] q : qs) {
                double rel = q[0] - ini;
                if (rel < -1e-6 || rel >= nb * msPerCompas - 1e-6) continue;
                int pas = (int) Math.round(rel / msPas);
                if (pas < 0 || pas >= nb * ppc) continue;
                int tipus = (int) q[1];
                Patro.Fila fl = null;
                for (String nom : FILES[tipus]) {
                    fl = p.fila(nom);
                    if (fl != null) break;
                }
                if (fl == null) continue;
                fl.cops[pas] = Math.max(fl.cops[pas], (int) q[2]);
                r.perTipus[tipus]++;
                posats++;
            }
            if (posats == 0) continue;
            r.cops += posats;
            Finestra f = new Finestra();
            f.compasDes = b0;
            f.patro = p;
            r.finestres.add(f);
        }
        return r;
    }
}
