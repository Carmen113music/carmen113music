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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;

/**
 * El ritme d'una pista de bateria (el que es dibuixa al drumpad): una fila per tipus de so
 * i una casella per semicorxera. Cada casella té una força: 0 = res, 1 = suau, 2 = normal, 3 = fort.
 * Es desa al costat del seu WAV («.bateria/b1.patro» i «.bateria/b1.wav» dins la carpeta de la cançó):
 * el WAV és el ritme ja barrejat, i l'Estudi el fa sonar com qualsevol altra pista.
 * No depèn d'Android: es pot provar amb Java d'escriptori.
 */
public final class Patro {

    public static final int MAX_COMPASSOS = 4;
    static final float[] FORCA = {0f, 0.45f, 0.75f, 1f};
    public static final double VOLUM_MIN_DB = -30, VOLUM_MAX_DB = 6;
    /** Marge de la barreja (uns -3 dB), perquè bombo, caixa i hihat junts no saturin. */
    static final float MARGE = 0.7f;

    public static final class Fila {
        public String tipus;
        public String so = "";      // «kit3/s05.wav» (relatiu a la carpeta dels kits)
        public double volumDb = 0;
        public boolean mut;
        public int[] cops;
    }

    public String kit = "";          // carpeta del kit triat (p. ex. «kit3»)
    public int compassos = 1;
    public int passosPerCompas = 16;
    /** Compàs amb què es va fer (temps per compàs; 6 = 6/8). 0 = no se sap (fitxers d'abans de la 2.21). */
    public int compas = 4;
    public final List<Fila> files = new ArrayList<>();

    /** Semicorxeres per compàs: 16 en 4/4, 12 en 3/4, 8 en 2/4, 12 en 6/8. */
    public static int passosDe(int compas) {
        return compas == 6 ? 12 : Math.max(1, compas) * 4;
    }

    /** Cada quantes caselles hi ha un temps (per dibuixar la graella): 4, o 6 en 6/8. */
    public static int grupDe(int compas) {
        return compas == 6 ? 6 : 4;
    }

    /** Caselles per temps (4 = semicorxeres en x/4; 8 = fuses; 3 i 6 = tresets; en 6/8, 2 o 4). */
    public int divisio() {
        return Math.max(1, passosPerCompas / Math.max(1, compas));
    }

    /** Cada quantes caselles es marca una línia a la graella: cada temps, o cada mig compàs en 6/8. */
    public int grup() {
        return compas == 6 ? 3 * divisio() : divisio();
    }

    /** Divisions de la graella que es poden triar amb aquest compàs. */
    public static int[] divisions(int compas) {
        return compas == 6 ? new int[]{2, 4} : new int[]{4, 8, 3, 6};
    }

    public static String nomDivisio(int compas, int div) {
        if (compas == 6) return div == 2 ? "2 per temps (semicorxeres)" : "4 per temps (fuses)";
        switch (div) {
            case 8:
                return "8 per temps (fuses)";
            case 3:
                return "3 per temps (tresets de corxera)";
            case 6:
                return "6 per temps (tresets de semicorxera)";
            default:
                return "4 per temps (semicorxeres)";
        }
    }

    /** Canvia les caselles per temps. Cada cop es queda al seu lloc (si dos cauen a la mateixa casella, queda el més fort). */
    public void posaDivisio(int div) {
        canviaPassos(Math.max(1, compas) * Math.max(1, div));
    }

    /** Passa la graella a {@code nouPpc} caselles per compàs mantenint cada cop al mateix lloc proporcional. */
    private void canviaPassos(int nouPpc) {
        if (nouPpc == passosPerCompas) return;
        for (Fila f : files) {
            int[] c = new int[compassos * nouPpc];
            for (int i = 0; i < f.cops.length; i++) {
                if (f.cops[i] == 0) continue;
                int b = i / passosPerCompas, s = i % passosPerCompas;
                int s2 = (int) Math.round((double) s * nouPpc / passosPerCompas);
                if (s2 < nouPpc) c[b * nouPpc + s2] = Math.max(c[b * nouPpc + s2], f.cops[i]);
            }
            f.cops = c;
        }
        passosPerCompas = nouPpc;
    }

    /**
     * Posa el ritme al compàs del projecte (si un fitxer antic no el sabia, suposa que és el del projecte).
     * Torna true si ha canviat res.
     */
    public boolean assegura(int compasProjecte) {
        if (compas <= 0) {
            compas = compasProjecte;
            if (passosPerCompas % Math.max(1, compas) != 0) {
                ajustaCompas(compasProjecte);
            }
            return true;
        }
        if (compas != compasProjecte) {
            ajustaCompas(compasProjecte);
            return true;
        }
        return false;
    }

    public int passos() {
        return compassos * passosPerCompas;
    }

    public int compte() {
        int n = 0;
        for (Fila f : files) for (int c : f.cops) if (c > 0) n++;
        return n;
    }

    // ---------- Crear ----------

    /** Ritme nou amb les files del kit i un ritme bàsic (bombo, caixa i hihat) perquè soni de seguida. */
    public static Patro nou(Kits.Kit kit, int compas) {
        Patro p = new Patro();
        p.compas = compas;
        p.passosPerCompas = passosDe(compas);
        if (kit != null) {
            p.kit = kit.dir.getName();
            for (String t : kit.tipus()) {
                Fila f = new Fila();
                f.tipus = t;
                f.so = ref(kit, kit.delTipus(t).get(0));
                f.cops = new int[p.passos()];
                p.files.add(f);
            }
        }
        p.ritmeBasic(compas);
        return p;
    }

    public static String ref(Kits.Kit k, Kits.So s) {
        return k.dir.getName() + "/" + s.fitxer;
    }

    public Fila fila(String tipus) {
        for (Fila f : files) if (f.tipus.equals(tipus)) return f;
        return null;
    }

    private void posa(String tipus, int pas, int forca) {
        Fila f = fila(tipus);
        if (f != null && pas >= 0 && pas < f.cops.length) f.cops[pas] = forca;
    }

    /** Bombo al primer temps (i al del mig), caixa als contratemps i hihat a cada corxera. */
    void ritmeBasic(int compas) {
        int ppc = passosPerCompas;
        for (int b = 0; b < compassos; b++) {
            int o = b * ppc;
            if (compas == 6) {
                posa("kick", o, 3);
                posa("snare", o + 6, 2);
            } else {
                posa("kick", o, 3);
                if (compas >= 4) posa("kick", o + (compas / 2) * 4, 2);
                for (int t = 1; t < compas; t += 2) posa("snare", o + t * 4, 2);
                if (compas == 3) posa("snare", o + 8, 2);
            }
            String hat = fila("hihat") != null ? "hihat" : fila("ride") != null ? "ride" : "hihat-obert";
            for (int s = 0; s < ppc; s += 2) posa(hat, o + s, s % (compas == 6 ? 6 : 4) == 0 ? 2 : 1);
        }
    }

    /** Canvia el kit: cada fila agafa el primer so del seu tipus del kit nou (si en té) i s'afegeixen els tipus que faltin. */
    public void canviaKit(Kits.Kit k) {
        kit = k.dir.getName();
        for (Fila f : files) {
            List<Kits.So> ss = k.delTipus(f.tipus);
            if (!ss.isEmpty()) f.so = ref(k, ss.get(0));
        }
        for (String t : k.tipus()) {
            if (fila(t) != null) continue;
            Fila f = new Fila();
            f.tipus = t;
            f.so = ref(k, k.delTipus(t).get(0));
            f.cops = new int[passos()];
            files.add(f);
        }
        files.sort((a, b) -> Integer.compare(Kits.ordre(a.tipus), Kits.ordre(b.tipus)));
    }

    /** Canvia quants compassos té. Els compassos nous surten en blanc; si n'hi ha menys, es perden els del final. */
    public void posaCompassos(int n) {
        n = Math.max(1, Math.min(MAX_COMPASSOS, n));
        compassos = n;
        int nou = passos();
        for (Fila f : files) {
            int[] c = new int[nou];
            System.arraycopy(f.cops, 0, c, 0, Math.min(nou, f.cops.length));
            f.cops = c;
        }
    }

    /**
     * El compàs del projecte ha canviat: cada cop es manté al mateix lloc proporcional dins el compàs,
     * i es conserva la finor de la graella (en passar de x/4 a 6/8 o al revés, la meitat o el doble per temps).
     */
    public void ajustaCompas(int nouCompas) {
        int div;
        if (compas <= 0 || passosPerCompas % compas != 0) div = passosDe(nouCompas) / Math.max(1, nouCompas);
        else if ((compas == 6) == (nouCompas == 6)) div = divisio();
        else if (nouCompas == 6) div = Math.max(1, divisio() / 2);
        else div = divisio() * 2;
        canviaPassos(Math.max(1, nouCompas) * div);
        compas = nouCompas;
    }

    // ---------- So ----------

    public static double msCompas(double bpm, int compas) {
        return 60000.0 / bpm * compas;
    }

    public long duradaMs(double bpm, int compas) {
        return Math.round(compassos * msCompas(bpm, compas));
    }

    public static float guany(double db) {
        return (float) Math.pow(10, db / 20);
    }

    /**
     * Barreja el ritme en un so mono a 44.100 Hz que dura exactament els compassos del patró.
     * La cua dels sons que passen del final torna a entrar pel principi, perquè en repetir-lo
     * (clips un darrere l'altre) soni continu.
     *
     * @param sons dona el so d'una referència («kit3/s05.wav»), o null si no hi és
     */
    public short[] renderitza(double bpm, int compas, Function<String, short[]> sons) {
        int n = (int) Math.max(1, Math.round(compassos * msCompas(bpm, compas) * Kits.MOSTREIG / 1000.0));
        float[] mescla = new float[n];
        int total = passos();
        boolean solo = false;
        for (Fila f : files) {
            if (f.mut || f.so == null || f.so.isEmpty()) continue;
            short[] s = sons.apply(f.so);
            if (s == null || s.length == 0) continue;
            float g = guany(f.volumDb);
            int llarg = Math.min(s.length, n);
            for (int p = 0; p < total && p < f.cops.length; p++) {
                int v = f.cops[p];
                if (v <= 0) continue;
                float a = MARGE * g * FORCA[Math.min(3, v)] / 32768f;
                int ini = (int) Math.round((double) p * n / total);
                for (int j = 0; j < llarg; j++) {
                    int k = ini + j;
                    if (k >= n) k -= n;
                    mescla[k] += s[j] * a;
                }
            }
        }
        short[] r = new short[n];
        for (int i = 0; i < n; i++) {
            float x = mescla[i];
            float ax = Math.abs(x);
            if (ax > 0.9f) x = Math.signum(x) * (0.9f + 0.1f * (float) Math.tanh((ax - 0.9f) / 0.1f));
            r[i] = (short) Math.round(x * 32767f);
        }
        return r;
    }

    /** Llegeix els sons dels kits (amb memòria, per no llegir el mateix fitxer dues vegades). */
    public static Function<String, short[]> lectorSons(File arrelKits, Map<String, short[]> cau) {
        return ref -> {
            if (cau.containsKey(ref)) return cau.get(ref);
            short[] s = null;
            File f = new File(arrelKits, ref);
            if (f.isFile()) s = Wav.llegeixMostres(f);
            cau.put(ref, s);
            return s;
        };
    }

    // ---------- Fitxers ----------

    /** «.bateria/b1.wav» → fitxer «.bateria/b1.patro» dins la carpeta. */
    public static File fitxerDe(File carpeta, String wav) {
        return new File(carpeta, wav.replaceAll("\\.wav$", ".patro"));
    }

    public static Patro llegeix(File fitxer) {
        Properties p = new Properties();
        try (Reader r = new InputStreamReader(new FileInputStream(fitxer), StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (IOException e) {
            return null;
        }
        Patro pt = new Patro();
        pt.kit = p.getProperty("kit", "");
        pt.compassos = Math.max(1, Math.min(MAX_COMPASSOS, enter(p.getProperty("compassos"), 1)));
        pt.passosPerCompas = Math.max(1, Math.min(96, enter(p.getProperty("passos"), 16)));
        pt.compas = enter(p.getProperty("metre"), 0);
        int nf = enter(p.getProperty("files"), 0);
        for (int i = 0; i < nf; i++) {
            String k = "f" + i + ".";
            Fila f = new Fila();
            f.tipus = p.getProperty(k + "tipus", "altres");
            f.so = p.getProperty(k + "so", "");
            try {
                f.volumDb = Double.parseDouble(p.getProperty(k + "volum", "0"));
            } catch (NumberFormatException e) {
                f.volumDb = 0;
            }
            f.mut = "si".equals(p.getProperty(k + "mut"));
            f.cops = new int[pt.passos()];
            String c = p.getProperty(k + "cops", "");
            for (int j = 0; j < f.cops.length && j < c.length(); j++) {
                int v = c.charAt(j) - '0';
                f.cops[j] = v >= 0 && v <= 3 ? v : 0;
            }
            pt.files.add(f);
        }
        return pt;
    }

    public void desa(File f) throws IOException {
        Properties p = new Properties();
        p.setProperty("kit", kit);
        p.setProperty("compassos", Integer.toString(compassos));
        p.setProperty("passos", Integer.toString(passosPerCompas));
        p.setProperty("metre", Integer.toString(compas));
        p.setProperty("files", Integer.toString(files.size()));
        for (int i = 0; i < files.size(); i++) {
            Fila fl = files.get(i);
            String k = "f" + i + ".";
            p.setProperty(k + "tipus", fl.tipus);
            p.setProperty(k + "so", fl.so == null ? "" : fl.so);
            p.setProperty(k + "volum", String.format(Locale.ROOT, "%.1f", fl.volumDb));
            p.setProperty(k + "mut", fl.mut ? "si" : "no");
            StringBuilder sb = new StringBuilder();
            for (int c : fl.cops) sb.append((char) ('0' + c));
            p.setProperty(k + "cops", sb.toString());
        }
        File pare = f.getAbsoluteFile().getParentFile();
        if (pare != null) pare.mkdirs();
        File tmp = new File(f.getPath() + ".tmp");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
            p.store(w, "Ritme de bateria (Riffs)");
        }
        if (!tmp.renameTo(f)) {
            f.delete();
            if (!tmp.renameTo(f)) throw new IOException("no s'ha pogut desar el ritme");
        }
    }

    private static int enter(String s, int def) {
        try {
            return s == null ? def : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /**
     * Torna a fer el WAV d'una pista de bateria a partir del seu ritme (amb el tempo i el compàs del projecte)
     * i esborra la cau de l'ona. Torna la durada nova en ms, o -1 si no hi ha ritme.
     */
    public static long genera(File carpeta, String wav, double bpm, int compas, File arrelKits) throws IOException {
        File fp = fitxerDe(carpeta, wav);
        Patro p = llegeix(fp);
        if (p == null) return -1;
        if (p.assegura(compas)) p.desa(fp);
        return p.escriuWav(carpeta, wav, bpm, compas, lectorSons(arrelKits, new HashMap<>()));
    }

    /** Escriu el WAV del ritme. Torna la durada en ms. */
    public long escriuWav(File carpeta, String wav, double bpm, int compas, Function<String, short[]> sons) throws IOException {
        short[] so = renderitza(bpm, compas, sons);
        File fw = new File(carpeta, wav);
        fw.getParentFile().mkdirs();
        File tmp = new File(fw.getPath() + ".tmp");
        Wav.Escriptor e = new Wav.Escriptor(tmp, Kits.MOSTREIG, 1);
        e.escriu(so, so.length);
        e.tanca();
        if (!tmp.renameTo(fw)) {
            fw.delete();
            if (!tmp.renameTo(fw)) throw new IOException("no s'ha pogut desar el so de la bateria");
        }
        Ona.fitxerDe(fw).delete();
        return so.length * 1000L / Kits.MOSTREIG;
    }
}
