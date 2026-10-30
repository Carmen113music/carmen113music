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
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * El que s'ha tocat amb el teremin en una finestra: no són notes, sinó el camí del dit (to i volum a cada moment).
 * Es desa el gest i no el so, així el mateix gest es pot fer sonar amb un altre preset.
 * Els temps van en compassos des de l'inici de la finestra, perquè si canvia el tempo tot es quedi al seu lloc.
 * Es desa al costat del seu WAV («.teremin/t1.gest» i «.teremin/t1.wav»). No depèn d'Android.
 */
public final class Gest {

    /** Un punt del camí: {@code t} en compassos, {@code nota} amb decimals (60 = Do3), {@code vol} de 0 a 1. */
    public static final class Punt {
        public final double t, nota, vol;

        public Punt(double t, double nota, double vol) {
            this.t = t;
            this.nota = nota;
            this.vol = vol;
        }
    }

    public int compassos = 1;
    public int compas = 4;
    public String preset = "";
    public Sinte.So so = new Sinte.So();
    /** Trossos amb el dit posat (de quan es posa fins que s'aixeca), ordenats. */
    public final List<List<Punt>> trossos = new ArrayList<>();

    public static Gest nou(Sinte.Preset p, int compas) {
        Gest g = new Gest();
        g.compas = compas;
        if (p != null) {
            g.preset = p.nom;
            g.so = p.so.copia();
        }
        return g;
    }

    public Gest buitAmbElMateixSo() {
        Gest g = new Gest();
        g.compas = compas;
        g.preset = preset;
        g.so = so.copia();
        return g;
    }

    public void posaPreset(Sinte.Preset p) {
        preset = p.nom;
        so = p.so.copia();
    }

    public boolean buit() {
        return trossos.isEmpty();
    }

    /** La nota més greu i la més aguda (per dibuixar-lo). */
    public double[] marges() {
        double min = 999, max = -999;
        for (List<Punt> tr : trossos) {
            for (Punt p : tr) {
                min = Math.min(min, p.nota);
                max = Math.max(max, p.nota);
            }
        }
        return min > max ? new double[]{60, 72} : new double[]{min, max};
    }

    /** El compàs del projecte ha canviat: el gest es queda als mateixos compassos (com la bateria). */
    public boolean assegura(int compasProjecte) {
        if (compas == compasProjecte) return false;
        compas = compasProjecte;
        return true;
    }

    // ---------- So ----------

    public int mostres(double bpm, int compas) {
        return (int) Math.max(1, Math.round(compassos * Patro.msCompas(bpm, compas) * Sinte.MOSTREIG / 1000.0));
    }

    public long duradaMs(double bpm, int compas) {
        return Math.round(compassos * Patro.msCompas(bpm, compas));
    }

    /** Del volum del dit (0 a 1) al guany del so: més fi a baix, perquè sigui fàcil tocar fluix. */
    public static double guanyDeVolum(double vol) {
        vol = Math.max(0, Math.min(1, vol));
        return Math.pow(vol, 1.6);
    }

    /** El so del gest, que dura exactament els compassos (la cua torna a entrar pel principi). */
    public short[] renderitza(double bpm, int compas) {
        int n = mostres(bpm, compas);
        float[] out = toca(so, trossos, n / (double) compassos, n, true);
        return Sinte.a16(out, so.volumDb);
    }

    /**
     * Fa sonar els trossos. {@code mostresCompas}: quantes mostres té un compàs. Si {@code roda}, el so fa {@code n}
     * mostres i la cua torna a entrar pel principi; si no, s'allarga amb la cua.
     */
    public static float[] toca(Sinte.So so, List<List<Punt>> trossos, double mostresCompas, int n, boolean roda) {
        int cua = (int) so.cuaMostres();
        int total = n + cua;
        float[] out = new float[total];
        Sinte.Veu v = new Sinte.Veu(so);
        double cG = 1 - Math.exp(-1.0 / (0.008 * Sinte.MOSTREIG));   // el volum s'hi acosta en ~8 ms
        double g = 0, objectiu = 0;
        int k = 0, p = 0;          // tros i punt actuals
        boolean actiu = false;
        for (int i = 0; i < total; i++) {
            double t = i / mostresCompas;
            if (!actiu && k < trossos.size()) {
                List<Punt> tr = trossos.get(k);
                if (!tr.isEmpty() && t >= tr.get(0).t) {
                    Punt a = tr.get(0);
                    v.toca((int) Math.round(a.nota), 2, false);
                    v.notaAra = a.nota;
                    v.posaNota(a.nota);
                    g = guanyDeVolum(a.vol);
                    actiu = true;
                    p = 0;
                } else if (tr.isEmpty()) {
                    k++;
                }
            }
            if (actiu) {
                List<Punt> tr = trossos.get(k);
                while (p + 1 < tr.size() && tr.get(p + 1).t <= t) p++;
                Punt a = tr.get(p);
                if (p + 1 < tr.size()) {
                    Punt b = tr.get(p + 1);
                    double f = b.t > a.t ? (t - a.t) / (b.t - a.t) : 1;
                    f = Math.max(0, Math.min(1, f));
                    v.posaNota(a.nota + (b.nota - a.nota) * f);
                    objectiu = guanyDeVolum(a.vol + (b.vol - a.vol) * f);
                } else {
                    v.posaNota(a.nota);
                    objectiu = guanyDeVolum(a.vol);
                }
                if (t >= tr.get(tr.size() - 1).t) {
                    v.deixa();
                    actiu = false;
                    k++;
                }
            }
            if (v.callada()) {
                if (!actiu && k >= trossos.size() && i >= n) break;
                continue;
            }
            g += (objectiu - g) * cG;
            out[i] = (float) (v.mostra() * g);
        }
        if (!roda) return out;
        float[] r = new float[n];
        System.arraycopy(out, 0, r, 0, n);
        for (int i = n; i < total; i++) r[(i - n) % n] += out[i];
        return r;
    }

    // ---------- Gravar ----------

    /**
     * El que es toca en directe: punts en ms de la línia de temps, partits en trossos (dit posat → dit aixecat).
     * En acabar es parteix en gestos de fins a {@code maxCompassos}, un per finestra.
     */
    public static final class Gravacio {
        private final List<List<double[]>> trossos = new ArrayList<>();   // {ms, nota, vol}
        private List<double[]> obert;

        public synchronized void posa(double ms, double nota, double vol) {
            if (obert == null) {
                obert = new ArrayList<>();
                trossos.add(obert);
            } else if (!obert.isEmpty() && ms - obert.get(obert.size() - 1)[0] < 6) {
                // massa a prop de l'anterior: només s'actualitza (no cal tants punts)
                double[] u = obert.get(obert.size() - 1);
                if (obert.size() > 1) {
                    u[0] = ms;
                    u[1] = nota;
                    u[2] = vol;
                    return;
                }
            }
            obert.add(new double[]{ms, nota, vol});
        }

        public synchronized void aixeca(double ms) {
            if (obert != null && !obert.isEmpty()) {
                double[] u = obert.get(obert.size() - 1);
                obert.add(new double[]{Math.max(ms, u[0]), u[1], u[2]});
            }
            obert = null;
        }

        public synchronized int trossos() {
            return trossos.size();
        }

        /** Gestos d'un màxim de {@code maxCompassos} des de l'entrada; cada un amb el compàs (des de l'entrada) on va. */
        public synchronized List<Object[]> gestos(double entradaMs, double msPerCompas, int maxCompassos) {
            // a compassos des de l'entrada, i sense el que és abans de l'entrada
            List<List<Punt>> tots = new ArrayList<>();
            double ultim = 0;
            for (List<double[]> tr : trossos) {
                List<Punt> c = new ArrayList<>();
                for (double[] q : tr) c.add(new Punt((q[0] - entradaMs) / msPerCompas, q[1], q[2]));
                List<Punt> dins = retalla(c, 0, Double.MAX_VALUE);
                if (dins.size() < 2) continue;
                tots.add(dins);
                ultim = Math.max(ultim, dins.get(dins.size() - 1).t);
            }
            List<Object[]> r = new ArrayList<>();
            if (tots.isEmpty()) return r;
            int total = Math.max(1, (int) Math.ceil(ultim - 1e-9));
            maxCompassos = Math.max(1, maxCompassos);
            for (int c0 = 0; c0 < total; c0 += maxCompassos) {
                int nc = Math.min(maxCompassos, total - c0);
                Gest g = new Gest();
                g.compassos = nc;
                for (List<Punt> tr : tots) {
                    List<Punt> part = retalla(tr, c0, c0 + nc);
                    if (part.size() < 2) continue;
                    List<Punt> rel = new ArrayList<>();
                    for (Punt q : part) rel.add(new Punt(q.t - c0, q.nota, q.vol));
                    g.trossos.add(rel);
                }
                if (!g.trossos.isEmpty()) r.add(new Object[]{c0, g});
            }
            return r;
        }
    }

    /** La part d'un tros entre {@code de} i {@code a} (en compassos), amb punts nous just a les vores. */
    static List<Punt> retalla(List<Punt> tr, double de, double a) {
        List<Punt> r = new ArrayList<>();
        for (int i = 0; i < tr.size(); i++) {
            Punt p = tr.get(i);
            if (i > 0) {
                Punt ant = tr.get(i - 1);
                if (ant.t < de && p.t > de) r.add(entre(ant, p, de));
            }
            if (p.t >= de && p.t <= a) r.add(p);
            if (i > 0) {
                Punt ant = tr.get(i - 1);
                if (ant.t < a && p.t > a) {
                    r.add(entre(ant, p, a));
                    break;
                }
            }
            if (p.t > a) break;
        }
        return r;
    }

    private static Punt entre(Punt a, Punt b, double t) {
        double f = b.t > a.t ? (t - a.t) / (b.t - a.t) : 0;
        return new Punt(t, a.nota + (b.nota - a.nota) * f, a.vol + (b.vol - a.vol) * f);
    }

    // ---------- Fitxers ----------

    /** «.teremin/t1.wav» → «.teremin/t1.gest». */
    public static File fitxerDe(File carpeta, String wav) {
        return new File(carpeta, wav.replaceAll("\\.wav$", ".gest"));
    }

    public static Gest llegeix(File fitxer) {
        Properties p = new Properties();
        try (Reader r = new InputStreamReader(new FileInputStream(fitxer), StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (IOException e) {
            return null;
        }
        return deProps(p);
    }

    private static Gest deProps(Properties p) {
        Gest g = new Gest();
        g.compassos = Math.max(1, Math.min(Melodia.MAX_COMPASSOS, enter(p.getProperty("compassos"), 1)));
        g.compas = enter(p.getProperty("metre"), 4);
        g.preset = p.getProperty("preset", "");
        g.so = Sinte.So.llegeix(p, "so.");
        String ts = p.getProperty("trossos", "").trim();
        if (!ts.isEmpty()) {
            for (String tr : ts.split("\\|")) {
                List<Punt> l = new ArrayList<>();
                for (String q : tr.split(";")) {
                    String[] c = q.split(",");
                    if (c.length < 3) continue;
                    try {
                        l.add(new Punt(Double.parseDouble(c[0]), Double.parseDouble(c[1]), Double.parseDouble(c[2])));
                    } catch (NumberFormatException ignorat) {
                    }
                }
                if (l.size() >= 2) g.trossos.add(l);
            }
        }
        return g;
    }

    public void desa(File f) throws IOException {
        Properties p = props();
        File pare = f.getAbsoluteFile().getParentFile();
        if (pare != null) pare.mkdirs();
        File tmp = new File(f.getPath() + ".tmp");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
            p.store(w, "Gest de teremin (Riffs)");
        }
        if (!tmp.renameTo(f)) {
            f.delete();
            if (!tmp.renameTo(f)) throw new IOException("no s'ha pogut desar el gest");
        }
    }

    private Properties props() {
        Properties p = new Properties();
        p.setProperty("compassos", Integer.toString(compassos));
        p.setProperty("metre", Integer.toString(compas));
        p.setProperty("preset", preset == null ? "" : preset);
        so.desa(p, "so.");
        StringBuilder sb = new StringBuilder();
        for (List<Punt> tr : trossos) {
            if (sb.length() > 0) sb.append('|');
            for (int i = 0; i < tr.size(); i++) {
                Punt q = tr.get(i);
                if (i > 0) sb.append(';');
                sb.append(String.format(Locale.ROOT, "%.5f,%.3f,%.3f", q.t, q.nota, q.vol));
            }
        }
        p.setProperty("trossos", sb.toString());
        return p;
    }

    /** L'estat en text (per desfer). */
    public String text() {
        java.io.StringWriter w = new java.io.StringWriter();
        try {
            props().store(w, null);
        } catch (IOException e) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String l : w.toString().split("\n")) if (!l.startsWith("#")) sb.append(l).append('\n');
        return sb.toString();
    }

    /** Llegeix un estat en text (null si no es pot). */
    public static Gest deText(String text) {
        Properties p = new Properties();
        try {
            p.load(new java.io.StringReader(text));
        } catch (IOException e) {
            return null;
        }
        return deProps(p);
    }

    private static int enter(String s, int def) {
        try {
            return s == null ? def : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** Torna a fer el WAV d'un gest (al tempo i compàs del projecte). Torna la durada en ms, o -1 si no hi és. */
    public static long genera(File carpeta, String wav, double bpm, int compas) throws IOException {
        File fg = fitxerDe(carpeta, wav);
        Gest g = llegeix(fg);
        if (g == null) return -1;
        if (g.assegura(compas)) g.desa(fg);
        return g.escriuWav(carpeta, wav, bpm, compas);
    }

    public long escriuWav(File carpeta, String wav, double bpm, int compas) throws IOException {
        short[] s = renderitza(bpm, compas);
        File fw = new File(carpeta, wav);
        fw.getParentFile().mkdirs();
        File tmp = new File(fw.getPath() + ".tmp");
        Wav.Escriptor e = new Wav.Escriptor(tmp, Sinte.MOSTREIG, 1);
        e.escriu(s, s.length);
        e.tanca();
        if (!tmp.renameTo(fw)) {
            fw.delete();
            if (!tmp.renameTo(fw)) throw new IOException("no s'ha pogut desar el so del teremin");
        }
        Ona.fitxerDe(fw).delete();
        return s.length * 1000L / Sinte.MOSTREIG;
    }
}
