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
import java.util.Properties;

/**
 * La frase d'una finestra de sinte (el que es dibuixa al piano roll): notes amb el seu inici, llargada,
 * altura (MIDI: 60 = Do central) i força, sobre una graella de caselles com la del drumpad.
 * El sinte és monofònic: dues notes no poden sonar alhora (en posar-ne una, les que trepitja s'escurcen o es treuen).
 * Es desa al costat del seu WAV («.sinte/s1.melodia» i «.sinte/s1.wav»). No depèn d'Android.
 */
public final class Melodia {

    public static final int MAX_COMPASSOS = 8;
    public static final int NOTA_MIN = 24, NOTA_MAX = 96;   // Do0 a Do6 (Do3 = 60, el Do central)

    public static final class Nota {
        public int pas, llarg, nota, forca = 2;

        public Nota(int pas, int llarg, int nota, int forca) {
            this.pas = pas;
            this.llarg = Math.max(1, llarg);
            this.nota = nota;
            this.forca = forca;
        }

        public int fi() {
            return pas + llarg;
        }
    }

    public int compassos = 1;
    public int passosPerCompas = 16;
    public int compas = 4;
    public String preset = "";
    public Sinte.So so = new Sinte.So();
    /** Instrument MIDI del banc (0–127), o -1 si és el sinte propi. Amb instrument MIDI, la frase és polifònica. */
    public int programa = -1;
    public int bancMidi = 0;
    /** Ordenades per inici, sense trepitjar-se. */
    public final List<Nota> notes = new ArrayList<>();

    public static Melodia nova(Sinte.Preset p, int compas) {
        Melodia m = new Melodia();
        m.compas = compas;
        m.passosPerCompas = Patro.passosDe(compas);
        if (p != null) {
            m.preset = p.nom;
            m.so = p.so.copia();
        }
        return m;
    }

    /** Frase buida d'un instrument MIDI (polifònica). */
    public static Melodia novaMidi(int programa, int compas) {
        Melodia m = new Melodia();
        m.compas = compas;
        m.passosPerCompas = Patro.passosDe(compas);
        m.posaInstrument(programa);
        return m;
    }

    /** Si poden sonar diverses notes alhora (instrument MIDI). */
    public boolean poli() {
        return programa >= 0;
    }

    public void posaInstrument(int prog) {
        programa = Math.max(0, Math.min(127, prog));
        preset = Gm.nom(programa);
    }

    /** Frase buida amb el mateix so (i el mateix compàs i graella) que una altra. */
    public Melodia buidaAmbElMateixSo() {
        Melodia m = new Melodia();
        m.compas = compas;
        m.passosPerCompas = passosPerCompas;
        m.compassos = compassos;
        m.preset = preset;
        m.so = so.copia();
        m.programa = programa;
        m.bancMidi = bancMidi;
        return m;
    }

    public Sinte.Preset presetTriat() {
        return Sinte.preset(preset);
    }

    public boolean esGreu() {
        if (poli()) return Gm.esGreu(programa);
        Sinte.Preset p = presetTriat();
        return p != null && p.greu;
    }

    public void posaPreset(Sinte.Preset p) {
        preset = p.nom;
        so = p.so.copia();
    }

    public int passos() {
        return compassos * passosPerCompas;
    }

    public int divisio() {
        return Math.max(1, passosPerCompas / Math.max(1, compas));
    }

    public int grup() {
        return compas == 6 ? 3 * divisio() : divisio();
    }

    // ---------- Editar ----------

    /** La nota que sona al pas {@code pas} amb aquesta altura, o null. */
    public Nota notaA(int pas, int nota) {
        for (Nota n : notes) if (n.nota == nota && pas >= n.pas && pas < n.fi()) return n;
        return null;
    }

    /**
     * Posa una nota (o torna a posar una que s'ha mogut). Les que trepitja s'escurcen
     * (si comencen abans) o es treuen (si comencen dins), perquè només n'hi ha una alhora.
     */
    public void posa(Nota n) {
        notes.remove(n);
        n.pas = Math.max(0, Math.min(passos() - 1, n.pas));
        n.llarg = Math.max(1, Math.min(passos() - n.pas, n.llarg));
        n.nota = Math.max(NOTA_MIN, Math.min(NOTA_MAX, n.nota));
        List<Nota> fora = new ArrayList<>();
        for (Nota x : notes) {
            if (x.fi() <= n.pas || x.pas >= n.fi()) continue;
            if (poli() && x.nota != n.nota) continue;   // polifònic: només es trepitgen les de la mateixa altura
            if (x.pas < n.pas) x.llarg = n.pas - x.pas;
            else fora.add(x);
        }
        notes.removeAll(fora);
        notes.add(n);
        ordena();
    }

    public void treu(Nota n) {
        notes.remove(n);
    }

    public void ordena() {
        notes.sort((a, b) -> Integer.compare(a.pas, b.pas));
    }

    /** Canvia quants compassos té. Si n'hi ha menys, es perden (o s'escurcen) les notes del final. */
    public void posaCompassos(int n) {
        compassos = Math.max(1, Math.min(MAX_COMPASSOS, n));
        int total = passos();
        notes.removeIf(x -> x.pas >= total);
        for (Nota x : notes) x.llarg = Math.min(x.llarg, total - x.pas);
    }

    /** Canvia les caselles per temps; cada nota es queda al seu lloc proporcional. */
    public void posaDivisio(int div) {
        canviaPassos(Math.max(1, compas) * Math.max(1, div));
    }

    private void canviaPassos(int nouPpc) {
        if (nouPpc == passosPerCompas) return;
        double f = (double) nouPpc / passosPerCompas;
        List<Nota> velles = new ArrayList<>(notes);
        notes.clear();
        passosPerCompas = nouPpc;
        for (Nota x : velles) {
            int ini = (int) Math.round(x.pas * f);
            int fi = Math.max(ini + 1, (int) Math.round(x.fi() * f));
            if (ini >= passos()) continue;
            posa(new Nota(ini, fi - ini, x.nota, x.forca));
        }
    }

    /** Posa la frase al compàs del projecte (mateixa lògica que el drumpad). Torna true si ha canviat. */
    public boolean assegura(int compasProjecte) {
        if (compas == compasProjecte) return false;
        int div;
        if (compas <= 0 || passosPerCompas % compas != 0) div = Patro.passosDe(compasProjecte) / Math.max(1, compasProjecte);
        else if ((compas == 6) == (compasProjecte == 6)) div = divisio();
        else if (compasProjecte == 6) div = Math.max(1, divisio() / 2);
        else div = divisio() * 2;
        canviaPassos(Math.max(1, compasProjecte) * div);
        compas = compasProjecte;
        return true;
    }

    // ---------- So ----------

    public long duradaMs(double bpm, int compas) {
        return Math.round(compassos * Patro.msCompas(bpm, compas));
    }

    public int mostres(double bpm, int compas) {
        return (int) Math.max(1, Math.round(compassos * Patro.msCompas(bpm, compas) * Sinte.MOSTREIG / 1000.0));
    }

    /** El so de la frase, que dura exactament els compassos (la cua torna a entrar pel principi). */
    public short[] renderitza(double bpm, int compas) {
        int n = mostres(bpm, compas);
        int total = passos();
        List<Sinte.Toc> tocs = new ArrayList<>();
        for (Nota x : notes) {
            int ini = (int) Math.round((double) x.pas * n / total);
            int fi = (int) Math.round((double) x.fi() * n / total);
            tocs.add(new Sinte.Toc(ini, fi, x.nota, x.forca));
        }
        if (poli()) return Sinte.a16(MidiSo.toca(Banc.actual(), bancMidi, programa, tocs, n, true), so.volumDb);
        return Sinte.a16(Sinte.toca(so, tocs, n, true), so.volumDb);
    }

    /** Una nota d'aquesta frase (amb el seu so o instrument), per escoltar-la. */
    public short[] prova(int nota, int ms) {
        if (!poli()) return provaNota(so, nota, 2, ms);
        int n = Math.max(1, ms * Sinte.MOSTREIG / 1000);
        List<Sinte.Toc> t = new ArrayList<>();
        t.add(new Sinte.Toc(0, n, nota, 2));
        float[] x = MidiSo.toca(Banc.actual(), bancMidi, programa, t, n, false);
        // sense el silenci del final
        int fi = x.length;
        while (fi > n && Math.abs(x[fi - 1]) < 1e-4f) fi--;
        return Sinte.a16(java.util.Arrays.copyOf(x, fi), so.volumDb);
    }

    /** Una sola nota, per escoltar-la en tocar el teclat o posar-la. */
    public static short[] provaNota(Sinte.So so, int nota, int forca, int ms) {
        int n = Math.max(1, ms * Sinte.MOSTREIG / 1000);
        List<Sinte.Toc> t = new ArrayList<>();
        t.add(new Sinte.Toc(0, n, nota, forca));
        return Sinte.a16(Sinte.toca(so, t, n, false), so.volumDb);
    }

    // ---------- Noms de les notes ----------

    static final String[] NOMS = {"Do", "Do#", "Re", "Re#", "Mi", "Fa", "Fa#", "Sol", "Sol#", "La", "La#", "Si"};

    /** «Do3» per a la 60 (com es diu a casa nostra: el Do central és el Do3). */
    public static String nomNota(int nota) {
        return NOMS[Math.floorMod(nota, 12)] + (Math.floorDiv(nota, 12) - 2);
    }

    public static boolean esNegra(int nota) {
        int k = Math.floorMod(nota, 12);
        return k == 1 || k == 3 || k == 6 || k == 8 || k == 10;
    }

    // ---------- Fitxers ----------

    /** «.sinte/s1.wav» → «.sinte/s1.melodia». */
    public static File fitxerDe(File carpeta, String wav) {
        return new File(carpeta, wav.replaceAll("\\.wav$", ".melodia"));
    }

    public static Melodia llegeix(File fitxer) {
        Properties p = new Properties();
        try (Reader r = new InputStreamReader(new FileInputStream(fitxer), StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (IOException e) {
            return null;
        }
        return deProps(p);
    }

    private static Melodia deProps(Properties p) {
        Melodia m = new Melodia();
        m.compassos = Math.max(1, Math.min(MAX_COMPASSOS, enter(p.getProperty("compassos"), 1)));
        m.passosPerCompas = Math.max(1, Math.min(96, enter(p.getProperty("passos"), 16)));
        m.compas = enter(p.getProperty("metre"), 4);
        m.preset = p.getProperty("preset", "");
        m.so = Sinte.So.llegeix(p, "so.");
        m.programa = Math.max(-1, Math.min(127, enter(p.getProperty("programa"), -1)));
        m.bancMidi = Math.max(0, Math.min(128, enter(p.getProperty("bancmidi"), 0)));
        String ns = p.getProperty("notes", "").trim();
        if (!ns.isEmpty()) {
            for (String t : ns.split(";")) {
                String[] c = t.split(":");
                if (c.length < 3) continue;
                int pas = enter(c[0], -1), llarg = enter(c[1], 1), nota = enter(c[2], 60);
                int forca = c.length > 3 ? Math.max(1, Math.min(3, enter(c[3], 2))) : 2;
                if (pas < 0 || pas >= m.passos()) continue;
                m.posa(new Nota(pas, llarg, nota, forca));
            }
        }
        return m;
    }

    public void desa(File f) throws IOException {
        Properties p = props();
        File pare = f.getAbsoluteFile().getParentFile();
        if (pare != null) pare.mkdirs();
        File tmp = new File(f.getPath() + ".tmp");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
            p.store(w, "Frase de sinte (Riffs)");
        }
        if (!tmp.renameTo(f)) {
            f.delete();
            if (!tmp.renameTo(f)) throw new IOException("no s'ha pogut desar la frase");
        }
    }

    private Properties props() {
        Properties p = new Properties();
        p.setProperty("compassos", Integer.toString(compassos));
        p.setProperty("passos", Integer.toString(passosPerCompas));
        p.setProperty("metre", Integer.toString(compas));
        p.setProperty("preset", preset == null ? "" : preset);
        so.desa(p, "so.");
        p.setProperty("programa", Integer.toString(programa));
        p.setProperty("bancmidi", Integer.toString(bancMidi));
        StringBuilder sb = new StringBuilder();
        for (Nota x : notes) {
            if (sb.length() > 0) sb.append(';');
            sb.append(x.pas).append(':').append(x.llarg).append(':').append(x.nota).append(':').append(x.forca);
        }
        p.setProperty("notes", sb.toString());
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
    public static Melodia deText(String text) {
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

    /** Torna a fer el WAV d'una frase (al tempo i compàs del projecte). Torna la durada en ms, o -1 si no hi ha frase. */
    public static long genera(File carpeta, String wav, double bpm, int compas) throws IOException {
        File fm = fitxerDe(carpeta, wav);
        Melodia m = llegeix(fm);
        if (m == null) return -1;
        if (m.assegura(compas)) m.desa(fm);
        return m.escriuWav(carpeta, wav, bpm, compas);
    }

    public long escriuWav(File carpeta, String wav, double bpm, int compas) throws IOException {
        short[] so = renderitza(bpm, compas);
        File fw = new File(carpeta, wav);
        fw.getParentFile().mkdirs();
        File tmp = new File(fw.getPath() + ".tmp");
        Wav.Escriptor e = new Wav.Escriptor(tmp, Sinte.MOSTREIG, 1);
        e.escriu(so, so.length);
        e.tanca();
        if (!tmp.renameTo(fw)) {
            fw.delete();
            if (!tmp.renameTo(fw)) throw new IOException("no s'ha pogut desar el so del sinte");
        }
        Ona.fitxerDe(fw).delete();
        return so.length * 1000L / Sinte.MOSTREIG;
    }
}
