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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Un projecte de l'Estudi: una carpeta de la biblioteca.
 * Cada gravació de la carpeta és una «presa». Cada pista té clips, que són finestres a una presa
 * (normalment la de la pista; si s'ha gravat a sobre des de l'Estudi, pot tenir clips de preses diferents).
 * Tot és «no destructiu»: els fitxers de so no es toquen mai; només es desa on va cada clip,
 * quina part sona, els fades i els volums. Es desa a la mateixa carpeta, al fitxer «_projecte.riffs».
 * No depèn d'Android: es pot provar amb Java d'escriptori.
 */
public final class Projecte {

    public static final String FITXER = "_projecte.riffs";
    public static final double SILENCI_DB = -60;     // per sota d'això el fader és silenci
    public static final double GUANY_MIN_DB = -24;   // punt de volum del clip
    /** Fins on es pot allargar un clip més enllà del so (és silenci). */
    public static final long ALLARGA_MAX_MS = 10 * 60_000;

    /** Una gravació de la carpeta. La bandera del primer temps és seva (la comparteixen tots els seus clips). */
    public static final class Presa {
        public String wav;
        public String nom = "";
        public boolean veu = false;
        public long duradaMs;
        public long primerMs = -1;  // primer temps dins el fitxer (-1 = no marcat)
    }

    /**
     * Un clip: una finestra a un tros d'una presa, amb la seva posició, retall, fades i volum.
     * La finestra pot anar més enllà del so (abans de l'inici o després del final): allà és silenci.
     * Tots els temps en mil·lisegons.
     */
    public static final class Clip {
        public String wav;          // la presa
        public long posMs;          // on cau l'inici del fitxer a la línia de temps (pot ser negatiu)
        public long iniciMs;        // inici de la finestra (dins el fitxer; pot ser negatiu = silenci)
        public long fiMs;           // final de la finestra (dins el fitxer; pot passar del final = silenci)
        public long fadeInMs;
        public long fadeOutMs;
        public double guanyDb;      // punt de volum del clip: 0 a -24

        /** On comença i acaba la finestra, a la línia de temps. */
        public long iniciVisible() {
            return posMs + iniciMs;
        }

        public long fiVisible() {
            return posMs + fiMs;
        }

        public long llarg() {
            return fiMs - iniciMs;
        }

        public Clip copia() {
            Clip c = new Clip();
            c.wav = wav;
            c.posMs = posMs;
            c.iniciMs = iniciMs;
            c.fiMs = fiMs;
            c.fadeInMs = fadeInMs;
            c.fadeOutMs = fadeOutMs;
            c.guanyDb = guanyDb;
            return c;
        }

        /** Corregeix valors impossibles després d'una edició. */
        public void normalitza(long duradaMs) {
            if (duradaMs < 0) duradaMs = 0;
            iniciMs = Math.max(-ALLARGA_MAX_MS, Math.min(iniciMs, duradaMs + ALLARGA_MAX_MS - 10));
            fiMs = Math.max(iniciMs + 10, Math.min(fiMs, duradaMs + ALLARGA_MAX_MS));
            long llarg = Math.max(0, fiMs - iniciMs);
            fadeInMs = Math.max(0, Math.min(fadeInMs, llarg));
            fadeOutMs = Math.max(0, Math.min(fadeOutMs, llarg - fadeInMs));
            guanyDb = Math.max(GUANY_MIN_DB, Math.min(0, guanyDb));
        }
    }

    /** Una pista: té nom i color de la seva presa principal, un fader, M/S i els clips. */
    public static final class Pista {
        public String wav;          // presa principal (dona nom i color a la pista)
        public String nom = "";
        public boolean veu = false;
        public double volumDb;      // fader de la pista: -60 (silenci) a +6
        public boolean mut, solo;
        /** Els clips, ordenats per on comencen a sonar. */
        public final List<Clip> clips = new ArrayList<>();

        /** Final de l'últim clip (o 0 si no en té). */
        public long finalMs() {
            long f = 0;
            for (Clip c : clips) f = Math.max(f, c.fiVisible());
            return f;
        }

        /** El clip que hi ha al moment {@code ms} (el de més amunt si se'n trepitgen), o null. */
        public Clip clipA(double ms) {
            for (int i = clips.size() - 1; i >= 0; i--) {
                Clip c = clips.get(i);
                if (ms >= c.iniciVisible() && ms <= c.fiVisible()) return c;
            }
            return null;
        }

        /** Torna a ordenar els clips per on comencen. */
        public void ordena() {
            clips.sort((x, y) -> Long.compare(x.iniciVisible(), y.iniciVisible()));
        }
    }

    public final File carpeta;
    public double bpm = 120;
    public int compas = 4;
    public final List<Pista> pistes = new ArrayList<>();
    /** Totes les preses que coneix el projecte (també les que ja no tenen cap clip). */
    public final Map<String, Presa> preses = new LinkedHashMap<>();

    public Projecte(File carpeta) {
        this.carpeta = carpeta;
    }

    public double msPerTemps() {
        return 60000.0 / bpm;
    }

    public double msPerCompas() {
        return msPerTemps() * compas;
    }

    /** On comença el compàs {@code n} (el compàs 1 comença a 0). */
    public double iniciCompas(int n) {
        return (n - 1) * msPerCompas();
    }

    /** Final del projecte: on acaba l'últim clip. */
    public long finalMs() {
        long f = 0;
        for (Pista p : pistes) f = Math.max(f, p.finalMs());
        return f;
    }

    public boolean hiHaSolo() {
        for (Pista p : pistes) if (p.solo) return true;
        return false;
    }

    /** Si la pista ha de sonar tenint en compte mut i solo. */
    public boolean sona(Pista p) {
        if (p.mut) return false;
        return !hiHaSolo() || p.solo;
    }

    // ---------- Preses ----------

    public Presa presa(Clip c) {
        Presa p = preses.get(c.wav);
        if (p == null) {
            p = new Presa();
            p.wav = c.wav;
            preses.put(c.wav, p);
        }
        return p;
    }

    public long duradaDe(Clip c) {
        return presa(c).duradaMs;
    }

    /** Bandera del primer temps de la presa del clip (dins el fitxer), o -1. */
    public long primerDe(Clip c) {
        return presa(c).primerMs;
    }

    /** El punt que s'enganxa a la graella: la bandera del primer temps, o l'inici visible. */
    public long puntSnap(Clip c) {
        long pr = primerDe(c);
        return pr >= 0 ? c.posMs + pr : c.iniciVisible();
    }

    /** Si la bandera cau dins la finestra d'aquest clip. */
    public boolean banderaVisible(Clip c) {
        long pr = primerDe(c);
        return pr >= 0 && pr >= c.iniciMs && pr <= c.fiMs;
    }

    /** Totes les preses que fan servir els clips (per carregar el so). */
    public Set<String> presesEnUs() {
        Set<String> r = new HashSet<>();
        for (Pista p : pistes) for (Clip c : p.clips) r.add(c.wav);
        return r;
    }

    // ---------- Graella ----------

    public static final int SNAP_COMPAS = 0, SNAP_TEMPS = 1, SNAP_LLIURE = 2;

    /** Arrodoneix un moment al compàs o temps més proper (o el deixa igual si és lliure). */
    public long enganxa(double ms, int snap) {
        if (snap == SNAP_LLIURE) return Math.round(ms);
        double pas = snap == SNAP_COMPAS ? msPerCompas() : msPerTemps();
        return Math.round(Math.round(ms / pas) * pas);
    }

    /**
     * Mode Lliure: la bandera es queda quieta a la línia de temps i l'àudio llisca per sota.
     * Torna on queda la bandera dins l'àudio després de moure el clip a {@code c.posMs}.
     * Si la bandera quedaria fora de la finestra, la deixa com estava (es mou amb el clip).
     *
     * @param pos0    on era el clip abans de moure'l
     * @param primer0 on era la bandera dins l'àudio abans de moure'l
     */
    public static long primerAmbBanderaFixa(Clip c, long pos0, long primer0) {
        if (primer0 < 0) return primer0;
        long nou = pos0 + primer0 - c.posMs;
        if (nou < c.iniciMs || nou > c.fiMs) return primer0;
        return nou;
    }

    // ---------- Gravar des de l'Estudi ----------

    /**
     * On entra la gravació: l'inici del compàs més proper al capçal (com a mínim el compàs 2,
     * perquè hi càpiga el compte d'un compàs abans).
     */
    public long entradaGravacio(long capcalMs) {
        long n = Math.round(capcalMs / msPerCompas()) + 1;
        return Math.round(iniciCompas((int) Math.max(2, n)));
    }

    /**
     * Afegeix una presa gravada des de l'Estudi: la bandera (el primer temps, {@code primerMs} dins el fitxer)
     * queda just a {@code entradaMs} de la línia de temps. Si {@code pista} és null, crea una pista nova al final.
     * Torna el clip nou.
     */
    public Clip afegeixPresa(Pista pista, String wav, String nom, boolean veu, long duradaMs, long primerMs, long entradaMs) {
        Presa pr = new Presa();
        pr.wav = wav;
        pr.nom = nom;
        pr.veu = veu;
        pr.duradaMs = duradaMs;
        pr.primerMs = primerMs;
        preses.put(wav, pr);
        if (pista == null) {
            pista = new Pista();
            pista.wav = wav;
            pista.nom = nom;
            pista.veu = veu;
            pistes.add(pista);
        }
        Clip c = new Clip();
        c.wav = wav;
        c.iniciMs = 0;
        c.fiMs = duradaMs;
        c.posMs = entradaMs - primerMs;
        pista.clips.add(c);
        pista.ordena();
        return c;
    }

    // ---------- Clips: seleccionar, duplicar, esborrar ----------

    /**
     * Clips consecutius d'una pista entre el clip {@code ancora} i el moment {@code ms}
     * (on és el dit), ambdós inclosos. Torna-los en ordre.
     */
    public static List<Clip> rang(Pista p, Clip ancora, double ms) {
        List<Clip> r = new ArrayList<>();
        int a = p.clips.indexOf(ancora);
        if (a < 0) return r;
        int b = a;
        if (ms > ancora.fiVisible()) {
            // cap a la dreta: fins a l'últim clip que ja ha començat on és el dit
            for (int i = a + 1; i < p.clips.size(); i++) if (p.clips.get(i).iniciVisible() <= ms) b = i;
        } else if (ms < ancora.iniciVisible()) {
            // cap a l'esquerra: fins al primer clip que encara sona on és el dit
            for (int i = a - 1; i >= 0; i--) if (p.clips.get(i).fiVisible() >= ms) b = i;
        }
        for (int i = Math.min(a, b); i <= Math.max(a, b); i++) r.add(p.clips.get(i));
        return r;
    }

    /**
     * Duplica els clips {@code sel} just a continuació (el grup sencer es copia a partir del final
     * de l'últim). Torna els clips nous, que queden afegits a la pista.
     */
    public static List<Clip> duplica(Pista p, List<Clip> sel) {
        List<Clip> nous = new ArrayList<>();
        if (sel == null || sel.isEmpty()) return nous;
        long ini = Long.MAX_VALUE, fi = Long.MIN_VALUE;
        for (Clip c : sel) {
            ini = Math.min(ini, c.iniciVisible());
            fi = Math.max(fi, c.fiVisible());
        }
        long salt = fi - ini;
        for (Clip c : sel) {
            Clip n = c.copia();
            n.posMs += salt;
            nous.add(n);
        }
        p.clips.addAll(nous);
        p.ordena();
        return nous;
    }

    /** Treu els clips del projecte (el fitxer de so no es toca). */
    public static void esborra(Pista p, List<Clip> sel) {
        p.clips.removeAll(sel);
    }

    /** Índex de la pista on és un clip, o -1. */
    public int pistaDe(Clip c) {
        for (int i = 0; i < pistes.size(); i++) if (pistes.get(i).clips.contains(c)) return i;
        return -1;
    }

    // ---------- Pistes noves ----------

    /** Col·loca una pista nova: un clip amb tota la gravació. */
    public void colocaNova(Pista p) {
        p.volumDb = 0;
        p.clips.clear();
        p.clips.add(clipSencer(p.wav));
    }

    /** Un clip amb tota la presa, amb la bandera a l'inici d'un compàs (a partir del 2). */
    public Clip clipSencer(String wav) {
        Clip k = new Clip();
        k.wav = wav;
        Presa pr = presa(k);
        k.iniciMs = 0;
        k.fiMs = pr.duradaMs;
        if (pr.primerMs >= 0) {
            int c = 2;
            while (iniciCompas(c) - pr.primerMs < 0 && c < 1000) c++;
            k.posMs = Math.round(iniciCompas(c) - pr.primerMs);
        } else {
            k.posMs = 0;
        }
        return k;
    }

    /**
     * Posa el projecte al dia amb les gravacions que hi ha a la carpeta:
     * les que el projecte no coneixia fan una pista nova (al final), les que ja no hi són es treuen,
     * i el títol, la durada i la bandera s'agafen de la fitxa. Torna true si ha canviat res.
     */
    public boolean sincronitza(Biblioteca b) {
        boolean canvi = false;
        List<Biblioteca.Gravacio> gs = b.gravacions(carpeta);
        Set<String> hiSon = new HashSet<>();
        for (Biblioteca.Gravacio g : gs) hiSon.add(g.wav.getName());

        // gravacions esborrades de la biblioteca: fora les preses i els seus clips
        for (String w : new ArrayList<>(preses.keySet())) {
            if (!hiSon.contains(w)) {
                preses.remove(w);
                canvi = true;
            }
        }
        for (int i = pistes.size() - 1; i >= 0; i--) {
            Pista p = pistes.get(i);
            if (p.clips.removeIf(c -> !hiSon.contains(c.wav))) canvi = true;
            if (!hiSon.contains(p.wav)) {
                if (p.clips.isEmpty()) {
                    pistes.remove(i);
                } else {
                    // la pista pren el nom d'una altra de les seves preses
                    p.wav = p.clips.get(0).wav;
                }
                canvi = true;
            }
        }

        // les més antigues primer, perquè les noves quedin a sota
        for (int k = gs.size() - 1; k >= 0; k--) {
            Biblioteca.Gravacio g = gs.get(k);
            String w = g.wav.getName();
            Presa pr = preses.get(w);
            boolean nova = pr == null;
            if (nova) {
                pr = new Presa();
                pr.wav = w;
                preses.put(w, pr);
                canvi = true;
            }
            // títol, tipus, durada i bandera poden haver canviat a la fitxa
            pr.nom = g.fitxa.titol;
            pr.veu = g.fitxa.esVeu();
            if (g.fitxa.duradaMs > 0) pr.duradaMs = g.fitxa.duradaMs;
            pr.primerMs = g.fitxa.primerTempsMs;
            if (!nova) continue;
            if (pistes.isEmpty() && g.fitxa.teTempo()) {
                bpm = g.fitxa.bpm;
                compas = g.fitxa.compas;
            }
            Pista p = new Pista();
            p.wav = w;
            colocaNova(p);
            pistes.add(p);
        }
        for (Pista p : pistes) {
            Presa pr = preses.get(p.wav);
            if (pr != null) {
                p.nom = pr.nom;
                p.veu = pr.veu;
            }
            for (Clip c : p.clips) c.normalitza(duradaDe(c));
        }
        return canvi;
    }

    // ---------- Desar i llegir ----------

    public static Projecte obre(File carpeta) {
        Projecte pr = new Projecte(carpeta);
        File f = new File(carpeta, FITXER);
        if (!f.exists()) return pr;
        Properties p = new Properties();
        try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (IOException e) {
            return pr;
        }
        pr.bpm = Ritme.limita(dec(p.getProperty("tempo"), 120));
        pr.compas = (int) Math.max(1, Math.min(12, dec(p.getProperty("compas"), 4)));
        // preses (des de la 2.5)
        int np = (int) dec(p.getProperty("preses"), 0);
        for (int i = 0; i < np; i++) {
            String k = "g" + i + ".";
            String wav = p.getProperty(k + "wav");
            if (wav == null) continue;
            Presa g = new Presa();
            g.wav = wav;
            g.nom = p.getProperty(k + "nom", wav);
            g.veu = "si".equals(p.getProperty(k + "veu"));
            g.duradaMs = (long) dec(p.getProperty(k + "durada"), 0);
            g.primerMs = (long) dec(p.getProperty(k + "primer"), -1);
            pr.preses.put(wav, g);
        }
        int n = (int) dec(p.getProperty("pistes"), 0);
        for (int i = 0; i < n; i++) {
            String k = "p" + i + ".";
            String wav = p.getProperty(k + "wav");
            if (wav == null) continue;
            Pista t = new Pista();
            t.wav = wav;
            t.nom = p.getProperty(k + "nom", wav);
            t.veu = "si".equals(p.getProperty(k + "veu"));
            t.volumDb = Math.max(SILENCI_DB, Math.min(6, dec(p.getProperty(k + "volum"), 0)));
            t.mut = "si".equals(p.getProperty(k + "mut"));
            t.solo = "si".equals(p.getProperty(k + "solo"));
            if (!pr.preses.containsKey(wav)) {
                // projecte d'abans de la 2.5: les dades de la gravació eren a la pista
                Presa g = new Presa();
                g.wav = wav;
                g.nom = t.nom;
                g.veu = t.veu;
                g.duradaMs = (long) dec(p.getProperty(k + "durada"), 0);
                g.primerMs = (long) dec(p.getProperty(k + "primer"), -1);
                pr.preses.put(wav, g);
            }
            if (p.getProperty(k + "clips") == null) {
                // projecte d'abans de la 2.4: un sol clip per pista
                t.clips.add(llegeixClip(p, k, wav, pr.preses.get(wav).duradaMs));
            } else {
                int nc = (int) dec(p.getProperty(k + "clips"), 0);
                for (int j = 0; j < nc; j++) {
                    String kc = k + "c" + j + ".";
                    String w = p.getProperty(kc + "wav", wav);
                    Presa g = pr.preses.get(w);
                    t.clips.add(llegeixClip(p, kc, w, g == null ? 0 : g.duradaMs));
                }
            }
            t.ordena();
            pr.pistes.add(t);
        }
        return pr;
    }

    private static Clip llegeixClip(Properties p, String k, String wav, long durada) {
        Clip c = new Clip();
        c.wav = wav;
        c.posMs = (long) dec(p.getProperty(k + "pos"), 0);
        c.iniciMs = (long) dec(p.getProperty(k + "inici"), 0);
        c.fiMs = (long) dec(p.getProperty(k + "fi"), durada);
        c.fadeInMs = (long) dec(p.getProperty(k + "fadein"), 0);
        c.fadeOutMs = (long) dec(p.getProperty(k + "fadeout"), 0);
        c.guanyDb = dec(p.getProperty(k + "guany"), 0);
        c.normalitza(durada);
        return c;
    }

    public void desa() throws IOException {
        Properties p = new Properties();
        p.setProperty("tempo", String.format(Locale.ROOT, "%.2f", bpm));
        p.setProperty("compas", Integer.toString(compas));
        p.setProperty("preses", Integer.toString(preses.size()));
        int gi = 0;
        for (Presa g : preses.values()) {
            String k = "g" + gi++ + ".";
            p.setProperty(k + "wav", g.wav);
            p.setProperty(k + "nom", g.nom == null ? "" : g.nom);
            p.setProperty(k + "veu", g.veu ? "si" : "no");
            p.setProperty(k + "durada", Long.toString(g.duradaMs));
            p.setProperty(k + "primer", Long.toString(g.primerMs));
        }
        p.setProperty("pistes", Integer.toString(pistes.size()));
        for (int i = 0; i < pistes.size(); i++) {
            Pista t = pistes.get(i);
            String k = "p" + i + ".";
            p.setProperty(k + "wav", t.wav);
            p.setProperty(k + "nom", t.nom == null ? "" : t.nom);
            p.setProperty(k + "veu", t.veu ? "si" : "no");
            p.setProperty(k + "volum", String.format(Locale.ROOT, "%.2f", t.volumDb));
            p.setProperty(k + "mut", t.mut ? "si" : "no");
            p.setProperty(k + "solo", t.solo ? "si" : "no");
            p.setProperty(k + "clips", Integer.toString(t.clips.size()));
            for (int j = 0; j < t.clips.size(); j++) {
                Clip c = t.clips.get(j);
                String kc = k + "c" + j + ".";
                p.setProperty(kc + "wav", c.wav);
                p.setProperty(kc + "pos", Long.toString(c.posMs));
                p.setProperty(kc + "inici", Long.toString(c.iniciMs));
                p.setProperty(kc + "fi", Long.toString(c.fiMs));
                p.setProperty(kc + "fadein", Long.toString(c.fadeInMs));
                p.setProperty(kc + "fadeout", Long.toString(c.fadeOutMs));
                p.setProperty(kc + "guany", String.format(Locale.ROOT, "%.2f", c.guanyDb));
            }
        }
        File f = new File(carpeta, FITXER);
        File temp = new File(carpeta, FITXER + ".tmp");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(temp), StandardCharsets.UTF_8)) {
            p.store(w, "Projecte de l'Estudi (Riffs)");
        }
        if (!temp.renameTo(f)) {
            f.delete();
            if (!temp.renameTo(f)) throw new IOException("No s'ha pogut desar el projecte.");
        }
    }

    private static double dec(String s, double perDefecte) {
        if (s == null) return perDefecte;
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return perDefecte;
        }
    }

    // ---------- Textos ----------

    /** «-6,0 dB», «0 dB», «-∞». */
    public static String textDb(double db) {
        if (db <= SILENCI_DB) return "-∞";
        if (Math.abs(db) < 0.05) return "0 dB";
        return String.format(Locale.ROOT, "%+.1f dB", db).replace('.', ',');
    }
}
