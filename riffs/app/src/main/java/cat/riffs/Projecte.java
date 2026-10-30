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

    /** Carpeta amagada (dins la de la cançó) on van el ritme i el so de les pistes de bateria. */
    public static final String DIR_BATERIA = ".bateria";

    /** Si un so és d'una pista de bateria (generat pel drumpad, no una gravació). */
    public static boolean esBateria(String wav) {
        return wav != null && wav.startsWith(DIR_BATERIA + "/");
    }

    /** Carpeta amagada on van les notes i el so de les pistes de sinte (piano roll). */
    public static final String DIR_SINTE = ".sinte";

    /** Si un so és d'una pista de sinte (generat pel piano roll). */
    public static boolean esSinte(String wav) {
        return wav != null && wav.startsWith(DIR_SINTE + "/");
    }

    /** Carpeta amagada on van els gestos i el so de les pistes de teremin. */
    public static final String DIR_TEREMIN = ".teremin";

    /** Si un so és d'una pista de teremin (generat a partir d'un gest gravat). */
    public static boolean esTeremin(String wav) {
        return wav != null && wav.startsWith(DIR_TEREMIN + "/");
    }

    /** So generat per l'app (bateria, sinte o teremin), no una gravació: cada finestra és un ritme o una frase sencera. */
    public static boolean esGenerat(String wav) {
        return esBateria(wav) || esSinte(wav) || esTeremin(wav);
    }

    /** Instrument que es toca en directe amb el sinte (sinte de teclat o teremin): té botó de gravar. */
    public static boolean esInstrument(String wav) {
        return esSinte(wav) || esTeremin(wav);
    }

    /** Nom de les preses generades: «Bateria», «Sinte» o «Teremin». */
    public static String nomGenerat(String wav) {
        return esSinte(wav) ? "Sinte" : esTeremin(wav) ? "Teremin" : "Bateria";
    }

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
        public String nom = "";     // títol de la presa principal
        public String nomPropi = ""; // nom que li has posat a l'Estudi (buit = el de la presa)
        public boolean veu = false;
        public double volumDb;      // fader de la pista: -60 (silenci) a +6
        public boolean mut, solo;
        public boolean rec;         // pista on es grava en mantenir premut el play
        /** Els clips, ordenats per on comencen a sonar. */
        public final List<Clip> clips = new ArrayList<>();

        /** Final de l'últim clip (o 0 si no en té). */
        public long finalMs() {
            long f = 0;
            for (Clip c : clips) f = Math.max(f, c.fiVisible());
            return f;
        }

        /** El nom que es veu: el que li has posat, o el de la gravació. */
        public String nomVisible() {
            if (nomPropi != null && !nomPropi.trim().isEmpty()) return nomPropi.trim();
            return nom == null || nom.isEmpty() ? "Pista" : nom;
        }

        /** Pista de bateria (el seu so el fa el drumpad). */
        public boolean esBateria() {
            return Projecte.esBateria(wav);
        }

        /** Pista de sinte (el seu so el fa el piano roll). */
        public boolean esSinte() {
            return Projecte.esSinte(wav);
        }

        /** Pista de teremin. */
        public boolean esTeremin() {
            return Projecte.esTeremin(wav);
        }

        /** Sinte de teclat o teremin (es grava tocant en directe). */
        public boolean esInstrument() {
            return Projecte.esInstrument(wav);
        }

        /** Bateria, sinte o teremin: so fet per l'app; doble toc a una finestra l'edita. */
        public boolean esGenerat() {
            return Projecte.esGenerat(wav);
        }

        /** Pista creada amb el «+», encara sense cap gravació pròpia. */
        public boolean esBuida() {
            return wav == null || wav.isEmpty();
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
        } else if (pista.esBuida()) {
            // la primera gravació d'una pista buida passa a ser la seva
            pista.wav = wav;
            pista.nom = nom;
            pista.veu = veu;
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

    /** Afegeix una pista buida al final («Pista 3»…). */
    public Pista pistaBuida() {
        Pista p = new Pista();
        p.wav = "";
        p.nom = "";
        p.nomPropi = "Pista " + (pistes.size() + 1);
        pistes.add(p);
        return p;
    }

    // ---------- Pistes de bateria ----------

    /** Un nom de fitxer lliure per a una pista de bateria nova: «.bateria/b1.wav», «.bateria/b2.wav»… */
    public String wavBateriaLliure() {
        return wavLliure(DIR_BATERIA + "/b");
    }

    /** Un nom de fitxer lliure per a una frase de sinte nova: «.sinte/s1.wav», «.sinte/s2.wav»… */
    public String wavSinteLliure() {
        return wavLliure(DIR_SINTE + "/s");
    }

    /** Un nom de fitxer lliure per a un gest de teremin nou: «.teremin/t1.wav»… */
    public String wavTereminLliure() {
        return wavLliure(DIR_TEREMIN + "/t");
    }

    /** El mateix tipus de nom lliure que {@code wav} (bateria, sinte o teremin). */
    public String wavLliureCom(String wav) {
        return esSinte(wav) ? wavSinteLliure() : esTeremin(wav) ? wavTereminLliure() : wavBateriaLliure();
    }

    private String wavLliure(String prefix) {
        Set<String> usats = new HashSet<>(preses.keySet());
        for (Pista p : pistes) usats.add(p.wav);
        for (int i = 1; ; i++) {
            String w = prefix + i + ".wav";
            if (!usats.contains(w) && !new File(carpeta, w).exists()) return w;
        }
    }

    /**
     * Afegeix una pista de bateria buida al final. {@code wav} és el seu primer ritme (ja fet),
     * que farà servir la primera finestra que s'hi posi.
     */
    public Pista afegeixBateria(String wav, long duradaMs) {
        Presa pr = new Presa();
        pr.wav = wav;
        pr.nom = "Bateria";
        pr.duradaMs = duradaMs;
        preses.put(wav, pr);
        Pista p = new Pista();
        p.wav = wav;
        p.nom = "Bateria";
        int n = 1;
        for (Pista x : pistes) if (x.esBateria()) n++;
        if (n > 1) p.nomPropi = "Bateria " + n;
        pistes.add(p);
        return p;
    }

    /** Afegeix una pista de sinte buida al final ({@code wav} és la seva primera frase, ja feta). */
    public Pista afegeixSinte(String wav, long duradaMs, String nom) {
        Presa pr = new Presa();
        pr.wav = wav;
        pr.nom = nomGenerat(wav);
        pr.duradaMs = duradaMs;
        preses.put(wav, pr);
        Pista p = new Pista();
        p.wav = wav;
        p.nom = nomGenerat(wav);
        if (nom != null && !nom.isEmpty()) p.nomPropi = nom;
        pistes.add(p);
        return p;
    }

    /**
     * Fa una còpia independent d'un ritme de bateria (el .patro i el seu so) amb un nom nou,
     * perquè una finestra duplicada es pugui editar sense tocar l'original. Torna el nom del so nou.
     */
    public String copiaBateria(String wav) throws IOException {
        String nou = wavLliureCom(wav);
        File de = new File(carpeta, wav), a = new File(carpeta, nou);
        a.getParentFile().mkdirs();
        java.nio.file.Files.copy(de.toPath(), a.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        String ext = esSinte(wav) ? ".melodia" : esTeremin(wav) ? ".gest" : ".patro";
        File pDe = new File(carpeta, wav.replaceAll("\\.wav$", ext));
        File pA = new File(carpeta, nou.replaceAll("\\.wav$", ext));
        if (pDe.isFile()) java.nio.file.Files.copy(pDe.toPath(), pA.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Presa vella = preses.get(wav);
        Presa pr = new Presa();
        pr.wav = nou;
        pr.nom = nomGenerat(wav);
        pr.duradaMs = vella != null ? vella.duradaMs : 0;
        preses.put(nou, pr);
        return nou;
    }

    /** Si algun clip del projecte fa servir aquest so. */
    public boolean enUs(String wav) {
        for (Pista p : pistes) for (Clip c : p.clips) if (c.wav.equals(wav)) return true;
        return false;
    }

    /** El clip de la pista que comença just abans de {@code ms} (o el primer, si no n'hi ha cap abans), o null. */
    public static Clip clipAbans(Pista p, double ms) {
        Clip millor = null;
        for (Clip c : p.clips) {
            if (c.iniciVisible() <= ms && (millor == null || c.iniciVisible() > millor.iniciVisible())) millor = c;
        }
        if (millor == null && !p.clips.isEmpty()) millor = p.clips.get(0);
        return millor;
    }

    /** Posa una finestra de bateria (un ritme sencer) a l'inici del compàs {@code compas}. Torna el clip. */
    public Clip afegeixFinestraBateria(Pista p, String wav, long duradaMs, int compas) {
        Presa pr = preses.get(wav);
        if (pr == null) {
            pr = new Presa();
            pr.wav = wav;
            pr.nom = nomGenerat(wav);
            preses.put(wav, pr);
        }
        pr.duradaMs = duradaMs;
        Clip c = new Clip();
        c.wav = wav;
        c.posMs = Math.round(iniciCompas(Math.max(1, compas)));
        c.iniciMs = 0;
        c.fiMs = duradaMs;
        p.clips.add(c);
        p.ordena();
        return c;
    }

    /**
     * El so d'un ritme ha canviat de llargada: les finestres que el mostraven sencer
     * (acabaven a {@code vellaMs}) passen a mostrar-lo sencer amb la llargada nova.
     */
    public void canviaDurada(String wav, long vellaMs, long novaMs) {
        Presa pr = preses.get(wav);
        if (pr != null) pr.duradaMs = novaMs;
        for (Pista p : pistes) {
            for (Clip c : p.clips) {
                if (!c.wav.equals(wav)) continue;
                if (Math.abs(c.fiMs - vellaMs) <= 3) c.fiMs = novaMs;
                c.normalitza(novaMs);
            }
            p.ordena();
        }
    }

    /**
     * El tempo o el compàs del projecte han canviat: els clips de bateria es mouen perquè continuïn
     * als mateixos compassos ({@code factor} = durada nova d'un compàs / durada vella).
     */
    public void escalaBateria(double factor) {
        for (Pista p : pistes) {
            for (Clip c : p.clips) {
                if (!esGenerat(c.wav)) continue;
                c.posMs = Math.round(c.posMs * factor);
                c.iniciMs = Math.round(c.iniciMs * factor);
                c.fiMs = Math.round(c.fiMs * factor);
                c.fadeInMs = Math.round(c.fadeInMs * factor);
                c.fadeOutMs = Math.round(c.fadeOutMs * factor);
            }
            p.ordena();
        }
    }

    /** Totes les preses de bateria del projecte. */
    public List<String> wavsBateria() {
        List<String> r = new ArrayList<>();
        for (String w : preses.keySet()) if (esBateria(w)) r.add(w);
        for (Pista p : pistes) if (p.esBateria() && !r.contains(p.wav)) r.add(p.wav);
        return r;
    }

    /** Tots els gestos de teremin del projecte. */
    public List<String> wavsTeremin() {
        List<String> r = new ArrayList<>();
        for (String w : preses.keySet()) if (esTeremin(w)) r.add(w);
        for (Pista p : pistes) if (p.esTeremin() && !r.contains(p.wav)) r.add(p.wav);
        return r;
    }

    /** Totes les frases de sinte del projecte. */
    public List<String> wavsSinte() {
        List<String> r = new ArrayList<>();
        for (String w : preses.keySet()) if (esSinte(w)) r.add(w);
        for (Pista p : pistes) if (p.esSinte() && !r.contains(p.wav)) r.add(p.wav);
        return r;
    }

    /** Treu una pista del projecte (les gravacions continuen a la biblioteca). */
    public void esborraPista(int i) {
        if (i >= 0 && i < pistes.size()) pistes.remove(i);
    }

    /** Passa clips d'una pista a una altra sense tocar-ne el temps (cada clip conserva la seva presa). */
    public static void canviaPista(Pista de, Pista a, List<Clip> clips) {
        if (de == a) return;
        de.clips.removeAll(clips);
        a.clips.addAll(clips);
        a.ordena();
    }

    /** Treu els clips del projecte (el fitxer de so no es toca). */
    public static void esborra(Pista p, List<Clip> sel) {
        p.clips.removeAll(sel);
    }

    /** La pista on es grava (la que té la R), o null. */
    public Pista pistaRec() {
        for (Pista p : pistes) if (p.rec) return p;
        return null;
    }

    /** Arma una pista per gravar (només n'hi pot haver una); null = cap. */
    public void armaRec(Pista p) {
        for (Pista x : pistes) x.rec = x == p;
    }

    /** Mou la pista {@code de} a la posició {@code a} (les altres es desplacen). */
    public void mouPista(int de, int a) {
        if (de < 0 || de >= pistes.size()) return;
        a = Math.max(0, Math.min(pistes.size() - 1, a));
        if (a == de) return;
        Pista p = pistes.remove(de);
        pistes.add(a, p);
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
        // la bateria no és a la biblioteca: hi és si hi ha el seu so
        for (String w : new ArrayList<>(preses.keySet())) {
            if (esGenerat(w) && new File(carpeta, w).isFile()) hiSon.add(w);
        }
        for (Pista p : pistes) {
            if (p.esGenerat() && new File(carpeta, p.wav).isFile()) hiSon.add(p.wav);
        }

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
            if (!p.esBuida() && !hiSon.contains(p.wav)) {
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
            t.nomPropi = p.getProperty(k + "etiqueta", "");
            t.veu = "si".equals(p.getProperty(k + "veu"));
            t.volumDb = Math.max(SILENCI_DB, Math.min(6, dec(p.getProperty(k + "volum"), 0)));
            t.mut = "si".equals(p.getProperty(k + "mut"));
            t.solo = "si".equals(p.getProperty(k + "solo"));
            t.rec = "si".equals(p.getProperty(k + "rec"));
            if (!wav.isEmpty() && !pr.preses.containsKey(wav)) {
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
                if (!wav.isEmpty()) t.clips.add(llegeixClip(p, k, wav, pr.preses.get(wav).duradaMs));
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
            p.setProperty(k + "etiqueta", t.nomPropi == null ? "" : t.nomPropi);
            p.setProperty(k + "veu", t.veu ? "si" : "no");
            p.setProperty(k + "volum", String.format(Locale.ROOT, "%.2f", t.volumDb));
            p.setProperty(k + "mut", t.mut ? "si" : "no");
            p.setProperty(k + "solo", t.solo ? "si" : "no");
            p.setProperty(k + "rec", t.rec ? "si" : "no");
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
