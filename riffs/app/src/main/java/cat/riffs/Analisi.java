package cat.riffs;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Anàlisi d'una gravació: notes (melodia d'una sola veu), acords i tonalitat.
 * Tot es calcula al mòbil, sense llibreries. No depèn d'Android: es prova amb Java d'escriptori.
 *
 * - Notes: detecció de to YIN cada 10 ms sobre el so reduït a 11.025 Hz, i després es
 *   parteix en notes (canvi de nota estable o atac nou).
 * - Acords: cromagrama (energia de cada una de les 12 notes) amb FFT, comparat amb
 *   plantilles d'acords que tenen en compte els harmònics de la guitarra, i suavitzat
 *   perquè no canviï d'acord a cada moment (Viterbi).
 * - Tonalitat: perfils de Krumhansl sobre les notes que sonen, ajudat pels acords.
 *
 * El resultat es desa al costat del .wav amb extensió .analisi.
 */
public final class Analisi {

    public static final int VERSIO = 1;

    /** Tipus d'acord que es detecten. */
    public static final String[] TIPUS = {"", "m", "7", "m7"};
    static final int[][] INTERVALS = {{0, 4, 7}, {0, 3, 7}, {0, 4, 7, 10}, {0, 3, 7, 10}};

    static final String[] NOTES_EN = {"C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B"};
    static final String[] NOTES_CAT = {"Do", "Do#", "Re", "Mib", "Mi", "Fa", "Fa#", "Sol", "Lab", "La", "Sib", "Si"};

    // ---------- Resultat ----------

    public static final class Nota {
        public final long iniciMs, fiMs;
        public final int midi;

        public Nota(long iniciMs, long fiMs, int midi) {
            this.iniciMs = iniciMs;
            this.fiMs = fiMs;
            this.midi = midi;
        }

        /** «La3» (Do3 = 60, com al piano roll). */
        public String nom() {
            return Melodia.nomNota(midi);
        }
    }

    public static final class Acord {
        public final long iniciMs, fiMs;
        public final int arrel, tipus;

        public Acord(long iniciMs, long fiMs, int arrel, int tipus) {
            this.iniciMs = iniciMs;
            this.fiMs = fiMs;
            this.arrel = arrel;
            this.tipus = tipus;
        }

        /** «Am», «G7», «C». */
        public String nom() {
            return NOTES_EN[arrel] + TIPUS[tipus];
        }
    }

    public static final class Resultat {
        public final List<Nota> notes = new ArrayList<>();
        public final List<Acord> acords = new ArrayList<>();
        /** Tonalitat: arrel 0–11 (0 = Do) i si és menor. */
        public int to = 0;
        public boolean menor = false;
        /** Segona opció (sovint la relativa). */
        public int to2 = 0;
        public boolean menor2 = false;
        /** 0..1: com de clara és la tonalitat. */
        public double claredat = 0;
        /** Quina vista es mostra: true = acords, false = notes. */
        public boolean vistaAcords = false;
        public long duradaMs = 0;

        public String nomTo() {
            return nomTonalitat(to, menor);
        }

        public String nomTo2() {
            return nomTonalitat(to2, menor2);
        }

        /** «La menor (Am)». */
        public String textTo() {
            return nomTo() + " (" + NOTES_EN[to] + (menor ? "m" : "") + ")";
        }

        /** La tonalitat no és clara si la segona opció està molt a prop. */
        public boolean dubtosa() {
            return claredat < 0.15;
        }

        public Nota notaA(long ms) {
            for (Nota n : notes) if (ms >= n.iniciMs && ms < n.fiMs) return n;
            return null;
        }

        public Acord acordA(long ms) {
            for (Acord a : acords) if (ms >= a.iniciMs && ms < a.fiMs) return a;
            return null;
        }

        /** Les notes de la tonalitat (escala major o menor natural), per a usos futurs (piano roll). */
        public boolean[] escala() {
            int[] e = menor ? new int[]{0, 2, 3, 5, 7, 8, 10} : new int[]{0, 2, 4, 5, 7, 9, 11};
            boolean[] r = new boolean[12];
            for (int i : e) r[(to + i) % 12] = true;
            return r;
        }
    }

    public static String nomTonalitat(int arrel, boolean menor) {
        return NOTES_CAT[Math.floorMod(arrel, 12)] + (menor ? " menor" : " major");
    }

    /** Avís de progrés (0..1). Torna false per cancel·lar. */
    public interface Progres {
        boolean avanca(double f);
    }

    // ---------- Anàlisi ----------

    static final int FS = 11025;          // freqüència de treball
    static final int PAS_TO = 110;        // ~10 ms
    static final int FINESTRA_TO = 512;   // ~46 ms
    static final int N_FFT = 4096;        // ~370 ms
    static final int PAS_FFT = 1024;      // ~93 ms

    /**
     * Analitza les mostres (mono, 16 bits).
     *
     * @param bpm     tempo de la gravació (0 = desconegut): si n'hi ha, els canvis d'acord s'enganxen als temps
     * @param primerMs primer temps (-1 = desconegut)
     */
    public static Resultat analitza(short[] mostres, int sr, boolean veu, double bpm, long primerMs, Progres p) {
        Resultat r = new Resultat();
        r.vistaAcords = !veu;
        if (mostres == null || mostres.length == 0) return r;
        r.duradaMs = Math.round(mostres.length * 1000.0 / sr);
        float[] x = redueix(mostres, sr);
        if (p != null && !p.avanca(0.05)) return null;

        // --- notes ---
        double[][] to = detectaTo(x, veu, p);
        if (to == null) return null;
        r.notes.addAll(notesDe(to[0], to[1]));

        // --- acords ---
        double[][][] crom = cromagrama(x, p);
        if (crom == null) return null;
        r.acords.addAll(acordsDe(crom[0], crom[1], crom[2], bpm, primerMs, r.duradaMs));

        // --- tonalitat ---
        tonalitat(r, crom[0], crom[2]);
        if (p != null) p.avanca(1);
        return r;
    }

    // ---------- Reduir a 11.025 Hz ----------

    static float[] redueix(short[] m, int sr) {
        int f = Math.max(1, (int) Math.round(sr / (double) FS));
        if (f == 1) {
            float[] r = new float[m.length];
            for (int i = 0; i < m.length; i++) r[i] = m[i] / 32768f;
            return r;
        }
        // filtre passa-baixos (sinc amb finestra) i delmar
        int taps = 8 * f + 1;
        double[] h = new double[taps];
        double fc = 0.45 / f, suma = 0;
        int mig = taps / 2;
        for (int i = 0; i < taps; i++) {
            int k = i - mig;
            double s = k == 0 ? 2 * fc : Math.sin(2 * Math.PI * fc * k) / (Math.PI * k);
            double w = 0.54 - 0.46 * Math.cos(2 * Math.PI * i / (taps - 1));
            h[i] = s * w;
            suma += h[i];
        }
        for (int i = 0; i < taps; i++) h[i] /= suma;
        int n = m.length / f;
        float[] r = new float[n];
        for (int j = 0; j < n; j++) {
            int c = j * f;
            double acc = 0;
            for (int i = 0; i < taps; i++) {
                int idx = c + i - mig;
                if (idx >= 0 && idx < m.length) acc += h[i] * m[idx];
            }
            r[j] = (float) (acc / 32768.0);
        }
        return r;
    }

    // ---------- To (YIN) ----------

    /** Torna {midi per finestra (NaN = sense to), dB per finestra}. */
    static double[][] detectaTo(float[] x, boolean veu, Progres p) {
        double fMin = veu ? 70 : 65, fMax = veu ? 1000 : 1200;
        int tauMin = (int) Math.floor(FS / fMax), tauMax = (int) Math.ceil(FS / fMin);
        int W = FINESTRA_TO;
        int n = Math.max(0, (x.length - W - tauMax) / PAS_TO + 1);
        double[] midi = new double[n], db = new double[n];
        double[] d = new double[tauMax + 2];
        // nivell general per decidir què és silenci
        double rmsTotal = 0;
        for (float v : x) rmsTotal += v * v;
        rmsTotal = Math.sqrt(rmsTotal / Math.max(1, x.length));
        double llindar = Math.max(0.003, rmsTotal * 0.12);
        for (int k = 0; k < n; k++) {
            if (p != null && (k & 255) == 0 && !p.avanca(0.05 + 0.55 * k / Math.max(1, n))) return null;
            int s = k * PAS_TO;
            double e = 0;
            for (int j = 0; j < W; j++) e += x[s + j] * x[s + j];
            double rms = Math.sqrt(e / W);
            db[k] = 20 * Math.log10(rms + 1e-9);
            midi[k] = Double.NaN;
            if (rms < llindar) continue;
            // funció de diferència
            for (int tau = 1; tau <= tauMax; tau++) {
                double acc = 0;
                for (int j = 0; j < W; j++) {
                    double dif = x[s + j] - x[s + j + tau];
                    acc += dif * dif;
                }
                d[tau] = acc;
            }
            // normalitzada (CMNDF)
            double cum = 0;
            d[0] = 1;
            for (int tau = 1; tau <= tauMax; tau++) {
                cum += d[tau];
                d[tau] = cum > 0 ? d[tau] * tau / cum : 1;
            }
            int tria = -1;
            for (int tau = Math.max(2, tauMin); tau < tauMax; tau++) {
                if (d[tau] < 0.15) {
                    while (tau + 1 < tauMax && d[tau + 1] < d[tau]) tau++;
                    tria = tau;
                    break;
                }
            }
            if (tria < 0) {
                double mn = 1;
                for (int tau = Math.max(2, tauMin); tau < tauMax; tau++) {
                    if (d[tau] < mn) {
                        mn = d[tau];
                        tria = tau;
                    }
                }
                if (mn > 0.3) tria = -1;
            }
            if (tria < 0) continue;
            // interpolació parabòlica
            double t = tria;
            if (tria > 1 && tria < tauMax) {
                double a = d[tria - 1], b = d[tria], c = d[tria + 1];
                double den = a - 2 * b + c;
                if (Math.abs(den) > 1e-12) t = tria + 0.5 * (a - c) / den;
            }
            double f0 = FS / t;
            if (f0 < fMin || f0 > fMax) continue;
            midi[k] = 69 + 12 * Math.log(f0 / 440.0) / Math.log(2);
        }
        return new double[][]{midi, db};
    }

    /** Parteix la línia de to en notes. */
    static List<Nota> notesDe(double[] midiCru, double[] db) {
        int n = midiCru.length;
        List<Nota> r = new ArrayList<>();
        if (n == 0) return r;
        // mediana de 5 sobre els valors amb to
        double[] m = new double[n];
        double[] fin = new double[5];
        for (int i = 0; i < n; i++) {
            if (Double.isNaN(midiCru[i])) {
                m[i] = Double.NaN;
                continue;
            }
            int c = 0;
            for (int k = i - 2; k <= i + 2; k++) {
                if (k >= 0 && k < n && !Double.isNaN(midiCru[k])) fin[c++] = midiCru[k];
            }
            Arrays.sort(fin, 0, c);
            m[i] = fin[c / 2];
        }
        // forats curts (≤ 3 finestres) entre la mateixa nota: s'omplen
        for (int i = 1; i < n; i++) {
            if (!Double.isNaN(m[i]) || Double.isNaN(m[i - 1])) continue;
            int j = i;
            while (j < n && Double.isNaN(m[j]) && j - i < 4) j++;
            if (j < n && j - i <= 3 && !Double.isNaN(m[j]) && Math.abs(m[j] - m[i - 1]) < 0.7) {
                for (int k = i; k < j; k++) m[k] = m[i - 1];
            }
        }
        double msPerPas = PAS_TO * 1000.0 / FS;
        double desfasMs = FINESTRA_TO / 2.0 * 1000.0 / FS;
        int i = 0;
        while (i < n) {
            if (Double.isNaN(m[i])) {
                i++;
                continue;
            }
            int inici = i;
            int nota = (int) Math.round(m[i]);
            int j = i + 1;
            while (j < n && !Double.isNaN(m[j])) {
                int q = (int) Math.round(m[j]);
                if (q != nota) {
                    // canvi només si es manté 3 finestres (30 ms)
                    int k = j, iguals = 0;
                    while (k < n && !Double.isNaN(m[k]) && Math.round(m[k]) == q && iguals < 3) {
                        k++;
                        iguals++;
                    }
                    if (iguals >= 3 || k >= n || Double.isNaN(m[k])) {
                        if (iguals >= 3 || (k - j) >= 2) break;
                    }
                }
                // atac nou de la mateixa nota (cop de pua o síl·laba)
                if (j - inici >= 6 && j + 1 < n) {
                    double minAnt = Double.MAX_VALUE;
                    for (int k = Math.max(inici, j - 6); k < j; k++) minAnt = Math.min(minAnt, db[k]);
                    if (db[j] - minAnt > 7 && db[j] >= db[j - 1] + 3) break;
                }
                j++;
            }
            // nota = mediana dels valors
            double[] vals = new double[j - inici];
            for (int k = inici; k < j; k++) vals[k - inici] = m[k];
            Arrays.sort(vals);
            int midi = (int) Math.round(vals[vals.length / 2]);
            long a = Math.round(inici * msPerPas + desfasMs - msPerPas / 2);
            long b = Math.round(j * msPerPas + desfasMs - msPerPas / 2);
            if (b - a >= 70 && midi >= 28 && midi <= 100) {
                // enganxa amb l'anterior si és la mateixa nota i no hi ha atac (forat petit)
                r.add(new Nota(Math.max(0, a), b, midi));
            }
            i = j;
        }
        return r;
    }

    // ---------- Cromagrama ----------

    /** Torna {croma[finestra][12], baix[finestra][12], energia[finestra][1]}. */
    static double[][][] cromagrama(float[] x, Progres p) {
        int n = x.length < N_FFT ? (x.length > 0 ? 1 : 0) : (x.length - N_FFT) / PAS_FFT + 1;
        double[][] croma = new double[n][12], baix = new double[n][12], en = new double[n][1];
        double[] hann = new double[N_FFT];
        for (int i = 0; i < N_FFT; i++) hann[i] = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / N_FFT);
        double[] re = new double[N_FFT], im = new double[N_FFT], mag = new double[N_FFT / 2];
        double binHz = FS / (double) N_FFT;
        int kMin = (int) Math.floor(55 / binHz), kMax = Math.min(N_FFT / 2 - 2, (int) Math.ceil(1800 / binHz));
        // 1a passada: afinació general (els instruments poden estar una mica desafinats)
        double sx = 0, sy = 0;
        List<double[]> pics = new ArrayList<>();
        for (int k = 0; k < n; k++) {
            if (p != null && (k & 31) == 0 && !p.avanca(0.6 + 0.3 * k / Math.max(1, n))) return null;
            int s = k * PAS_FFT;
            double e = 0;
            for (int i = 0; i < N_FFT; i++) {
                double v = s + i < x.length ? x[s + i] : 0;
                e += v * v;
                re[i] = v * hann[i];
                im[i] = 0;
            }
            en[k][0] = Math.sqrt(e / N_FFT);
            fft(re, im);
            double mx = 0;
            for (int b = 0; b < N_FFT / 2; b++) {
                mag[b] = Math.sqrt(re[b] * re[b] + im[b] * im[b]);
                if (b >= kMin && b <= kMax) mx = Math.max(mx, mag[b]);
            }
            if (mx <= 0) continue;
            for (int b = kMin; b <= kMax; b++) {
                if (mag[b] < mx * 0.02 || mag[b] < mag[b - 1] || mag[b] < mag[b + 1]) continue;
                // posició exacta del pic (parabòlica sobre el log)
                double a = Math.log(mag[b - 1] + 1e-12), c0 = Math.log(mag[b] + 1e-12), c = Math.log(mag[b + 1] + 1e-12);
                double den = a - 2 * c0 + c;
                double off = Math.abs(den) > 1e-12 ? 0.5 * (a - c) / den : 0;
                double f = (b + off) * binHz;
                double mf = 69 + 12 * Math.log(f / 440.0) / Math.log(2);
                double pes = Math.sqrt(mag[b] / mx);
                pics.add(new double[]{k, mf, pes});
                double desv = mf - Math.round(mf);
                sx += pes * Math.cos(2 * Math.PI * desv);
                sy += pes * Math.sin(2 * Math.PI * desv);
            }
        }
        double afina = Math.atan2(sy, sx) / (2 * Math.PI); // −0,5..0,5 semitons
        for (double[] pk : pics) {
            int k = (int) pk[0];
            double mf = pk[1] - afina;
            int q = (int) Math.round(mf);
            double d = mf - q;
            double w = pk[2] * Math.pow(Math.cos(Math.PI * d), 2);
            int pc = Math.floorMod(q, 12);
            croma[k][pc] += w;
            if (mf < 53) baix[k][pc] += w; // per sota de Fa2 (~175 Hz)
        }
        return new double[][][]{croma, baix, en};
    }

    /** FFT radix-2 en el lloc. */
    static void fft(double[] re, double[] im) {
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
            double ang = -2 * Math.PI / len;
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
    }

    // ---------- Acords ----------

    static final double[] HARM_PES = {1, 0.6, 0.36, 0.22, 0.13};
    static final int[] HARM_PC = {0, 0, 7, 0, 4};

    /** Plantilla d'un acord amb els harmònics de cada nota. */
    static double[] plantilla(int arrel, int tipus) {
        double[] t = new double[12];
        for (int iv : INTERVALS[tipus]) {
            double pesNota = iv == 0 ? 1.1 : (iv == 10 ? 0.8 : 1.0);
            for (int h = 0; h < HARM_PES.length; h++) t[(arrel + iv + HARM_PC[h]) % 12] += pesNota * HARM_PES[h];
        }
        return normalitza(t);
    }

    static double[] normalitza(double[] v) {
        double s = 0;
        for (double d : v) s += d * d;
        s = Math.sqrt(s);
        double[] r = new double[v.length];
        if (s > 0) for (int i = 0; i < v.length; i++) r[i] = v[i] / s;
        return r;
    }

    static double cosinus(double[] a, double[] b) {
        double s = 0;
        for (int i = 0; i < a.length; i++) s += a[i] * b[i];
        return s;
    }

    static List<Acord> acordsDe(double[][] croma, double[][] baix, double[][] en, double bpm, long primerMs, long duradaMs) {
        int n = croma.length;
        List<Acord> r = new ArrayList<>();
        if (n == 0) return r;
        int nTip = TIPUS.length;
        int S = 12 * nTip + 1; // l'últim estat = sense acord
        double[][] plant = new double[S - 1][];
        for (int a = 0; a < 12; a++) for (int t = 0; t < nTip; t++) plant[a * nTip + t] = plantilla(a, t);
        double enMax = 0;
        for (double[] e : en) enMax = Math.max(enMax, e[0]);
        double[][] puntua = new double[n][S];
        for (int k = 0; k < n; k++) {
            // suavitza amb les finestres del costat
            double[] c = new double[12], bx = new double[12];
            for (int d = -1; d <= 1; d++) {
                int kk = k + d;
                if (kk < 0 || kk >= n) continue;
                double pes = d == 0 ? 1 : 0.5;
                for (int i = 0; i < 12; i++) {
                    c[i] += pes * croma[kk][i];
                    bx[i] += pes * baix[kk][i];
                }
            }
            double[] cn = normalitza(c);
            double bMax = 0;
            for (double v : bx) bMax = Math.max(bMax, v);
            boolean silenci = en[k][0] < enMax * 0.06 || en[k][0] < 0.002;
            for (int s = 0; s < S - 1; s++) {
                int arrel = s / nTip, tip = s % nTip;
                double v = cosinus(cn, plant[s]);
                if (bMax > 0) v += 0.10 * bx[arrel] / bMax;      // l'arrel al baix ajuda
                if (tip >= 2) v -= 0.04;                           // les setenes, només si es noten
                puntua[k][s] = silenci ? 0 : v;
            }
            puntua[k][S - 1] = silenci ? 1 : 0.45;
        }
        // Viterbi: canviar d'acord costa
        double K = 14, CANVI = 3.2;
        double[] prev = new double[S], cur = new double[S];
        int[][] des = new int[n][S];
        for (int s = 0; s < S; s++) prev[s] = K * puntua[0][s];
        for (int k = 1; k < n; k++) {
            int millor = 0;
            for (int s = 1; s < S; s++) if (prev[s] > prev[millor]) millor = s;
            for (int s = 0; s < S; s++) {
                double quedar = prev[s], canviar = prev[millor] - CANVI;
                if (quedar >= canviar || millor == s) {
                    cur[s] = quedar;
                    des[k][s] = s;
                } else {
                    cur[s] = canviar;
                    des[k][s] = millor;
                }
                cur[s] += K * puntua[k][s];
            }
            double[] t = prev;
            prev = cur;
            cur = t;
        }
        int[] cami = new int[n];
        int fi = 0;
        for (int s = 1; s < S; s++) if (prev[s] > prev[fi]) fi = s;
        cami[n - 1] = fi;
        for (int k = n - 1; k > 0; k--) cami[k - 1] = des[k][cami[k]];

        // trossos
        double msPerPas = PAS_FFT * 1000.0 / FS;
        double centre = N_FFT / 2.0 * 1000.0 / FS;
        List<long[]> trossos = new ArrayList<>(); // inici, fi, estat
        int i = 0;
        while (i < n) {
            int j = i;
            while (j < n && cami[j] == cami[i]) j++;
            long a = i == 0 ? 0 : Math.round(centre + (i - 0.5) * msPerPas);
            long b = j >= n ? duradaMs : Math.round(centre + (j - 0.5) * msPerPas);
            trossos.add(new long[]{a, Math.min(b, duradaMs), cami[i]});
            i = j;
        }
        // enganxa els canvis als temps si es coneix el tempo
        if (bpm > 0) {
            double mpt = 60000.0 / bpm;
            double origen = primerMs >= 0 ? primerMs : 0;
            for (int t = 1; t < trossos.size(); t++) {
                long b = trossos.get(t)[0];
                long q = Math.round(origen + Math.round((b - origen) / mpt) * mpt);
                if (Math.abs(q - b) <= Math.min(180, mpt * 0.4) && q > trossos.get(t - 1)[0] && q < trossos.get(t)[1]) {
                    trossos.get(t - 1)[1] = q;
                    trossos.get(t)[0] = q;
                }
            }
        }
        // treu els trossos massa curts (< 0,4 s): s'afegeixen al veí més llarg
        boolean canvi = true;
        while (canvi && trossos.size() > 1) {
            canvi = false;
            for (int t = 0; t < trossos.size(); t++) {
                long[] tr = trossos.get(t);
                if (tr[1] - tr[0] >= 400) continue;
                long[] ant = t > 0 ? trossos.get(t - 1) : null, seg = t + 1 < trossos.size() ? trossos.get(t + 1) : null;
                if (ant != null && (seg == null || ant[1] - ant[0] >= seg[1] - seg[0])) ant[1] = tr[1];
                else if (seg != null) seg[0] = tr[0];
                trossos.remove(t);
                canvi = true;
                break;
            }
            // ajunta veïns iguals
            for (int t = trossos.size() - 1; t > 0; t--) {
                if (trossos.get(t)[2] == trossos.get(t - 1)[2]) {
                    trossos.get(t - 1)[1] = trossos.get(t)[1];
                    trossos.remove(t);
                    canvi = true;
                }
            }
        }
        for (long[] tr : trossos) {
            int s = (int) tr[2];
            if (s == S - 1) continue;
            r.add(new Acord(tr[0], tr[1], s / nTip, s % nTip));
        }
        return r;
    }

    // ---------- Tonalitat ----------

    static final double[] MAJOR = {6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88};
    static final double[] MENOR = {6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17};

    static void tonalitat(Resultat r, double[][] croma, double[][] en) {
        double[] h = new double[12];
        // 1) el que sona (cromagrama)
        double[] c = new double[12];
        for (double[] f : croma) {
            double[] fn = normalitza(f);
            for (int i = 0; i < 12; i++) c[i] += fn[i];
        }
        c = normalitza(c);
        // 2) les notes de la melodia, per durada
        double[] nt = new double[12];
        for (Nota n : r.notes) nt[n.midi % 12] += (n.fiMs - n.iniciMs);
        nt = normalitza(nt);
        // 3) els acords, per durada (l'arrel compta més)
        double[] ac = new double[12];
        for (Acord a : r.acords) {
            double d = a.fiMs - a.iniciMs;
            for (int iv : INTERVALS[a.tipus]) ac[(a.arrel + iv) % 12] += d * (iv == 0 ? 1.5 : 1);
        }
        ac = normalitza(ac);
        // Si el que sona és sobretot una melodia (una sola nota alhora), mana el que diuen les notes:
        // el cromagrama hi afegeix els harmònics (la 5a i la 3a major de cada nota) i confon major i menor.
        long ocupat = 0, notesMs = 0;
        double enMax = 0;
        for (double[] e : en) enMax = Math.max(enMax, e[0]);
        for (double[] e : en) if (e[0] > enMax * 0.06) ocupat++;
        ocupat = Math.round(ocupat * PAS_FFT * 1000.0 / FS);
        for (Nota n : r.notes) notesMs += n.fiMs - n.iniciMs;
        boolean melodia = ocupat > 0 && notesMs > 0.55 * ocupat && r.notes.size() >= 4;
        for (int i = 0; i < 12; i++) h[i] = melodia ? nt[i] + 0.15 * c[i] : c[i] + 1.0 * ac[i] + 0.3 * nt[i];
        // acord més llarg i últim acord
        double[] durRel = new double[24];
        for (Acord a : r.acords) {
            if (a.tipus == 0 || a.tipus == 2) durRel[a.arrel] += a.fiMs - a.iniciMs;
            else durRel[12 + a.arrel] += a.fiMs - a.iniciMs;
        }
        double durMax = 0;
        for (double d : durRel) durMax = Math.max(durMax, d);
        Acord ultim = r.acords.isEmpty() ? null : r.acords.get(r.acords.size() - 1);
        Acord primer = r.acords.isEmpty() ? null : r.acords.get(0);

        double[] corr = new double[24];
        for (int k = 0; k < 24; k++) {
            int arrel = k % 12;
            boolean menor = k >= 12;
            double[] perfil = menor ? MENOR : MAJOR;
            double[] rot = new double[12];
            for (int i = 0; i < 12; i++) rot[(arrel + i) % 12] = perfil[i];
            corr[k] = pearson(h, rot);
            if (melodia) {
                // les melodies solen acabar (i sovint començar) a la tònica
                Nota u = r.notes.get(r.notes.size() - 1), p0 = r.notes.get(0);
                if (u.midi % 12 == arrel) corr[k] += 0.05;
                if (p0.midi % 12 == arrel) corr[k] += 0.02;
            } else {
                if (durMax > 0) corr[k] += 0.06 * durRel[k] / durMax;  // l'acord de la tònica sona molt
                if (ultim != null && ultim.arrel == arrel && ((ultim.tipus == 1 || ultim.tipus == 3) == menor)) corr[k] += 0.03;
                if (primer != null && primer.arrel == arrel && ((primer.tipus == 1 || primer.tipus == 3) == menor)) corr[k] += 0.03;
            }
        }
        int b1 = 0;
        for (int k = 1; k < 24; k++) if (corr[k] > corr[b1]) b1 = k;
        int b2 = b1 == 0 ? 1 : 0;
        for (int k = 0; k < 24; k++) if (k != b1 && corr[k] > corr[b2]) b2 = k;
        r.to = b1 % 12;
        r.menor = b1 >= 12;
        r.to2 = b2 % 12;
        r.menor2 = b2 >= 12;
        r.claredat = Math.max(0, Math.min(1, (corr[b1] - corr[b2]) * 4));
    }

    static double pearson(double[] a, double[] b) {
        double ma = 0, mb = 0;
        for (int i = 0; i < 12; i++) {
            ma += a[i];
            mb += b[i];
        }
        ma /= 12;
        mb /= 12;
        double s = 0, sa = 0, sb = 0;
        for (int i = 0; i < 12; i++) {
            s += (a[i] - ma) * (b[i] - mb);
            sa += (a[i] - ma) * (a[i] - ma);
            sb += (b[i] - mb) * (b[i] - mb);
        }
        return sa > 0 && sb > 0 ? s / Math.sqrt(sa * sb) : 0;
    }

    // ---------- Fitxer ----------

    public static File fitxerDe(File wav) {
        String nom = wav.getName();
        int punt = nom.lastIndexOf('.');
        return new File(wav.getParentFile(), (punt > 0 ? nom.substring(0, punt) : nom) + ".analisi");
    }

    /** Llegeix l'anàlisi desada (null si no n'hi ha o si el so ha canviat després). */
    public static Resultat llegeix(File wav) {
        File f = fitxerDe(wav);
        if (!f.exists() || f.lastModified() < wav.lastModified()) return null;
        Properties p = new Properties();
        try (Reader rd = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            p.load(rd);
        } catch (IOException e) {
            return null;
        }
        try {
            if (Integer.parseInt(p.getProperty("versio", "0")) != VERSIO) return null;
            Resultat r = new Resultat();
            r.to = Integer.parseInt(p.getProperty("to", "0"));
            r.menor = "1".equals(p.getProperty("menor"));
            r.to2 = Integer.parseInt(p.getProperty("to2", "0"));
            r.menor2 = "1".equals(p.getProperty("menor2"));
            r.claredat = Double.parseDouble(p.getProperty("claredat", "0"));
            r.vistaAcords = "acords".equals(p.getProperty("vista"));
            r.duradaMs = Long.parseLong(p.getProperty("durada", "0"));
            for (String s : p.getProperty("notes", "").split(";")) {
                String[] t = s.split(":");
                if (t.length == 3) r.notes.add(new Nota(Long.parseLong(t[0]), Long.parseLong(t[1]), Integer.parseInt(t[2])));
            }
            for (String s : p.getProperty("acords", "").split(";")) {
                String[] t = s.split(":");
                if (t.length == 4) {
                    int a = Integer.parseInt(t[2]), tp = Integer.parseInt(t[3]);
                    if (a >= 0 && a < 12 && tp >= 0 && tp < TIPUS.length) {
                        r.acords.add(new Acord(Long.parseLong(t[0]), Long.parseLong(t[1]), a, tp));
                    }
                }
            }
            return r;
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static void desa(File wav, Resultat r) throws IOException {
        Properties p = new Properties();
        p.setProperty("versio", Integer.toString(VERSIO));
        p.setProperty("to", Integer.toString(r.to));
        p.setProperty("menor", r.menor ? "1" : "0");
        p.setProperty("to2", Integer.toString(r.to2));
        p.setProperty("menor2", r.menor2 ? "1" : "0");
        p.setProperty("claredat", String.format(Locale.ROOT, "%.3f", r.claredat));
        p.setProperty("vista", r.vistaAcords ? "acords" : "notes");
        p.setProperty("durada", Long.toString(r.duradaMs));
        StringBuilder sb = new StringBuilder();
        for (Nota n : r.notes) sb.append(n.iniciMs).append(':').append(n.fiMs).append(':').append(n.midi).append(';');
        p.setProperty("notes", sb.toString());
        sb.setLength(0);
        for (Acord a : r.acords) sb.append(a.iniciMs).append(':').append(a.fiMs).append(':').append(a.arrel).append(':').append(a.tipus).append(';');
        p.setProperty("acords", sb.toString());
        File f = fitxerDe(wav);
        File temp = new File(f.getPath() + ".tmp");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(temp), StandardCharsets.UTF_8)) {
            p.store(w, "Riffs: notes, acords i tonalitat");
        }
        if (!temp.renameTo(f)) {
            f.delete();
            if (!temp.renameTo(f)) throw new IOException("No s'ha pogut desar l'anàlisi");
        }
    }
}
