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
import java.util.Properties;

/**
 * Dades d'una gravació (títol, tipus, lletra, notes...).
 * Es desa en un fitxer de text al costat del .wav, amb el mateix nom i extensió .txt.
 */
public final class Fitxa {

    public static final String GUITARRA = "guitarra";
    public static final String VEU = "veu";

    public String titol = "";
    public String mode = GUITARRA;
    public long data = 0;        // quan es va gravar (mil·lisegons)
    public long duradaMs = 0;
    public String lletra = "";
    public String notes = "";

    // Tempo (0 = desconegut)
    public double bpm = 0;
    public int compas = 4;           // temps per compàs
    public long primerTempsMs = -1;  // on cau el primer temps dins l'àudio (-1 = no marcat)
    public boolean claqueta = false; // gravat amb metrònom

    public boolean teTempo() {
        return bpm > 0;
    }

    public boolean tePrimerTemps() {
        return primerTempsMs >= 0;
    }

    /** «92 BPM · 4/4» o «92,5 BPM · 3/4». */
    public String textTempo() {
        String b = (Math.abs(bpm - Math.round(bpm)) < 0.05)
                ? Long.toString(Math.round(bpm))
                : String.format(java.util.Locale.ROOT, "%.1f", bpm).replace('.', ',');
        return b + " BPM · " + Ritme.nomCompas(compas);
    }

    public boolean esVeu() {
        return VEU.equals(mode);
    }

    public String emoji() {
        return esVeu() ? "🎤" : "🎸";
    }

    public static Fitxa llegeix(File fitxer) {
        Fitxa f = new Fitxa();
        if (fitxer == null || !fitxer.exists()) return f;
        Properties p = new Properties();
        try (Reader r = new InputStreamReader(new FileInputStream(fitxer), StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (IOException e) {
            return f;
        }
        f.titol = p.getProperty("titol", "");
        f.mode = VEU.equals(p.getProperty("mode")) ? VEU : GUITARRA;
        f.data = llarg(p.getProperty("data"));
        f.duradaMs = llarg(p.getProperty("durada"));
        f.lletra = p.getProperty("lletra", "");
        f.notes = p.getProperty("notes", "");
        f.bpm = decimal(p.getProperty("bpm"));
        f.compas = (int) Math.max(1, Math.min(12, llarg(p.getProperty("compas", "4"))));
        f.primerTempsMs = p.getProperty("primer") == null ? -1 : llarg(p.getProperty("primer"));
        f.claqueta = "si".equals(p.getProperty("claqueta"));
        return f;
    }

    public void desa(File fitxer) throws IOException {
        Properties p = new Properties();
        p.setProperty("titol", titol == null ? "" : titol);
        p.setProperty("mode", mode == null ? GUITARRA : mode);
        p.setProperty("data", Long.toString(data));
        p.setProperty("durada", Long.toString(duradaMs));
        p.setProperty("lletra", lletra == null ? "" : lletra);
        p.setProperty("notes", notes == null ? "" : notes);
        p.setProperty("bpm", bpm > 0 ? String.format(java.util.Locale.ROOT, "%.2f", bpm) : "0");
        p.setProperty("compas", Integer.toString(compas));
        p.setProperty("primer", Long.toString(primerTempsMs));
        p.setProperty("claqueta", claqueta ? "si" : "no");
        File temp = new File(fitxer.getPath() + ".tmp");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(temp), StandardCharsets.UTF_8)) {
            p.store(w, "Llibreta de riffs");
        }
        if (!temp.renameTo(fitxer)) {
            fitxer.delete();
            if (!temp.renameTo(fitxer)) throw new IOException("No s'ha pogut desar " + fitxer);
        }
    }

    private static double decimal(String s) {
        try {
            double d = s == null ? 0 : Double.parseDouble(s.trim());
            return (d >= 20 && d <= 400) ? d : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static long llarg(String s) {
        try {
            return s == null ? 0 : Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
