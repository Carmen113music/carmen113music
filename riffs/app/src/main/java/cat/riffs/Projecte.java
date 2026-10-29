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
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

/**
 * Un projecte de l'Estudi: una carpeta de la biblioteca on cada gravació és una pista.
 * Cada pista pot tenir diversos clips del mateix fitxer (per exemple, un loop duplicat).
 * Tot és «no destructiu»: els fitxers de so no es toquen mai; només es desa on va cada clip,
 * quina part sona, els fades i els volums. Es desa a la mateixa carpeta, al fitxer «_projecte.riffs».
 * No depèn d'Android: es pot provar amb Java d'escriptori.
 */
public final class Projecte {

    public static final String FITXER = "_projecte.riffs";
    public static final double SILENCI_DB = -60;     // per sota d'això el fader és silenci
    public static final double GUANY_MIN_DB = -24;   // punt de volum del clip

    /**
     * Un clip: una «finestra» a un tros del fitxer de so de la pista. N'hi pot haver diversos
     * del mateix fitxer, cadascun amb la seva posició, retall, fades i volum.
     * Tots els temps en mil·lisegons.
     */
    public static final class Clip {
        public long posMs;          // on cau l'inici del fitxer a la línia de temps (pot ser negatiu)
        public long iniciMs;        // retall de l'inici (dins el fitxer)
        public long fiMs;           // retall del final (dins el fitxer)
        public long fadeInMs;
        public long fadeOutMs;
        public double guanyDb;      // punt de volum del clip: 0 a -24

        /** On comença i acaba el que sona, a la línia de temps. */
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
            iniciMs = Math.max(0, Math.min(iniciMs, duradaMs));
            fiMs = Math.max(iniciMs + 10, Math.min(fiMs, duradaMs));
            if (fiMs > duradaMs) fiMs = duradaMs;
            long llarg = Math.max(0, fiMs - iniciMs);
            fadeInMs = Math.max(0, Math.min(fadeInMs, llarg));
            fadeOutMs = Math.max(0, Math.min(fadeOutMs, llarg - fadeInMs));
            guanyDb = Math.max(GUANY_MIN_DB, Math.min(0, guanyDb));
        }
    }

    /** Una pista: una gravació (un take) amb els seus clips. Tots els temps en mil·lisegons. */
    public static final class Pista {
        public String wav;          // nom del fitxer dins la carpeta
        public String nom = "";     // títol de la gravació
        public boolean veu = false;
        public long duradaMs;       // durada del fitxer
        public long primerMs = -1;  // primer temps dins el fitxer (-1 = no marcat): és de la gravació
        public double volumDb;      // fader de la pista: -60 (silenci) a +6
        public boolean mut, solo;
        /** Els clips, ordenats per on comencen a sonar. */
        public final List<Clip> clips = new ArrayList<>();

        /** El punt que s'enganxa a la graella: la bandera del primer temps, o l'inici visible. */
        public long puntSnap(Clip c) {
            return primerMs >= 0 ? c.posMs + primerMs : c.iniciVisible();
        }

        /** Si la bandera cau dins la part que sona d'aquest clip. */
        public boolean banderaVisible(Clip c) {
            return primerMs >= c.iniciMs && primerMs <= c.fiMs;
        }

        /** Final de l'últim clip (o 0 si no en té). */
        public long finalMs() {
            long f = 0;
            for (Clip c : clips) f = Math.max(f, c.fiVisible());
            return f;
        }

        /** El clip que sona al moment {@code ms} (el de més amunt si se'n trepitgen), o null. */
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

        /** Corregeix valors impossibles després d'una edició. */
        public void normalitza() {
            if (duradaMs < 0) duradaMs = 0;
            for (Clip c : clips) c.normalitza(duradaMs);
            volumDb = Math.max(SILENCI_DB, Math.min(6, volumDb));
        }
    }

    public final File carpeta;
    public double bpm = 120;
    public int compas = 4;
    public final List<Pista> pistes = new ArrayList<>();

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
     * Si la bandera quedaria fora de la part que sona, la deixa com estava (es mou amb el clip).
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

    /** Pista i clip d'un clip (per saber on és). Torna l'índex de la pista o -1. */
    public int pistaDe(Clip c) {
        for (int i = 0; i < pistes.size(); i++) if (pistes.get(i).clips.contains(c)) return i;
        return -1;
    }

    // ---------- Pistes noves ----------

    /** Col·loca una pista nova: la bandera del primer temps al principi d'un compàs (a partir del 2). */
    public void colocaNova(Pista p) {
        p.volumDb = 0;
        p.clips.clear();
        p.clips.add(clipSencer(p));
    }

    /** Un clip amb tota la gravació, amb la bandera a l'inici d'un compàs (a partir del 2). */
    public Clip clipSencer(Pista p) {
        Clip k = new Clip();
        k.iniciMs = 0;
        k.fiMs = p.duradaMs;
        if (p.primerMs >= 0) {
            int c = 2;
            while (iniciCompas(c) - p.primerMs < 0 && c < 1000) c++;
            k.posMs = Math.round(iniciCompas(c) - p.primerMs);
        } else {
            k.posMs = 0;
        }
        return k;
    }

    /**
     * Posa el projecte al dia amb les gravacions que hi ha a la carpeta:
     * afegeix les noves (al final) i treu les que ja no hi són. Torna true si ha canviat res.
     */
    public boolean sincronitza(Biblioteca b) {
        boolean canvi = false;
        List<Biblioteca.Gravacio> gs = b.gravacions(carpeta);
        Set<String> hiSon = new HashSet<>();
        for (Biblioteca.Gravacio g : gs) hiSon.add(g.wav.getName());
        for (int i = pistes.size() - 1; i >= 0; i--) {
            if (!hiSon.contains(pistes.get(i).wav)) {
                pistes.remove(i);
                canvi = true;
            }
        }
        // les més antigues primer, perquè les noves quedin a sota
        for (int k = gs.size() - 1; k >= 0; k--) {
            Biblioteca.Gravacio g = gs.get(k);
            Pista existent = null;
            for (Pista p : pistes) if (p.wav.equals(g.wav.getName())) existent = p;
            if (existent != null) {
                // títol i tipus poden haver canviat a la fitxa
                existent.nom = g.fitxa.titol;
                existent.veu = g.fitxa.esVeu();
                if (g.fitxa.duradaMs > 0 && existent.duradaMs != g.fitxa.duradaMs) {
                    existent.duradaMs = g.fitxa.duradaMs;
                    existent.normalitza();
                }
                if (existent.primerMs != g.fitxa.primerTempsMs) existent.primerMs = g.fitxa.primerTempsMs;
                continue;
            }
            if (pistes.isEmpty() && g.fitxa.teTempo()) {
                bpm = g.fitxa.bpm;
                compas = g.fitxa.compas;
            }
            Pista p = new Pista();
            p.wav = g.wav.getName();
            p.nom = g.fitxa.titol;
            p.veu = g.fitxa.esVeu();
            p.duradaMs = g.fitxa.duradaMs;
            p.primerMs = g.fitxa.primerTempsMs;
            colocaNova(p);
            pistes.add(p);
            canvi = true;
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
        int n = (int) dec(p.getProperty("pistes"), 0);
        for (int i = 0; i < n; i++) {
            String k = "p" + i + ".";
            String wav = p.getProperty(k + "wav");
            if (wav == null) continue;
            Pista t = new Pista();
            t.wav = wav;
            t.nom = p.getProperty(k + "nom", wav);
            t.veu = "si".equals(p.getProperty(k + "veu"));
            t.duradaMs = (long) dec(p.getProperty(k + "durada"), 0);
            t.primerMs = (long) dec(p.getProperty(k + "primer"), -1);
            t.volumDb = dec(p.getProperty(k + "volum"), 0);
            t.mut = "si".equals(p.getProperty(k + "mut"));
            t.solo = "si".equals(p.getProperty(k + "solo"));
            if (p.getProperty(k + "clips") == null) {
                // projecte d'abans de la 2.4: un sol clip per pista
                t.clips.add(llegeixClip(p, k, t.duradaMs));
            } else {
                int nc = (int) dec(p.getProperty(k + "clips"), 0);
                for (int j = 0; j < nc; j++) t.clips.add(llegeixClip(p, k + "c" + j + ".", t.duradaMs));
            }
            t.ordena();
            t.normalitza();
            pr.pistes.add(t);
        }
        return pr;
    }

    private static Clip llegeixClip(Properties p, String k, long durada) {
        Clip c = new Clip();
        c.posMs = (long) dec(p.getProperty(k + "pos"), 0);
        c.iniciMs = (long) dec(p.getProperty(k + "inici"), 0);
        c.fiMs = (long) dec(p.getProperty(k + "fi"), durada);
        c.fadeInMs = (long) dec(p.getProperty(k + "fadein"), 0);
        c.fadeOutMs = (long) dec(p.getProperty(k + "fadeout"), 0);
        c.guanyDb = dec(p.getProperty(k + "guany"), 0);
        return c;
    }

    public void desa() throws IOException {
        Properties p = new Properties();
        p.setProperty("tempo", String.format(Locale.ROOT, "%.2f", bpm));
        p.setProperty("compas", Integer.toString(compas));
        p.setProperty("pistes", Integer.toString(pistes.size()));
        for (int i = 0; i < pistes.size(); i++) {
            Pista t = pistes.get(i);
            String k = "p" + i + ".";
            p.setProperty(k + "wav", t.wav);
            p.setProperty(k + "nom", t.nom == null ? "" : t.nom);
            p.setProperty(k + "veu", t.veu ? "si" : "no");
            p.setProperty(k + "durada", Long.toString(t.duradaMs));
            p.setProperty(k + "primer", Long.toString(t.primerMs));
            p.setProperty(k + "clips", Integer.toString(t.clips.size()));
            for (int j = 0; j < t.clips.size(); j++) {
                Clip c = t.clips.get(j);
                String kc = k + "c" + j + ".";
                p.setProperty(kc + "pos", Long.toString(c.posMs));
                p.setProperty(kc + "inici", Long.toString(c.iniciMs));
                p.setProperty(kc + "fi", Long.toString(c.fiMs));
                p.setProperty(kc + "fadein", Long.toString(c.fadeInMs));
                p.setProperty(kc + "fadeout", Long.toString(c.fadeOutMs));
                p.setProperty(kc + "guany", String.format(Locale.ROOT, "%.2f", c.guanyDb));
            }
            p.setProperty(k + "volum", String.format(Locale.ROOT, "%.2f", t.volumDb));
            p.setProperty(k + "mut", t.mut ? "si" : "no");
            p.setProperty(k + "solo", t.solo ? "si" : "no");
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
