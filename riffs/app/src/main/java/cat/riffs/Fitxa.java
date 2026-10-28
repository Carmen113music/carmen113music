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
        File temp = new File(fitxer.getPath() + ".tmp");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(temp), StandardCharsets.UTF_8)) {
            p.store(w, "Llibreta de riffs");
        }
        if (!temp.renameTo(fitxer)) {
            fitxer.delete();
            if (!temp.renameTo(fitxer)) throw new IOException("No s'ha pogut desar " + fitxer);
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
