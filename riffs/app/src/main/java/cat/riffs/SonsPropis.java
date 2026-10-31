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
 * Els sons desats per en Roger des de l'editor de so (2.32): cada un és un fitxer «.so» dins {@code files/sons}
 * amb el nom, si és del sinte polifònic o del monofònic, i tots els botons. Sobreviuen a les actualitzacions
 * (es perden si es desinstal·la l'app). No depèn d'Android.
 */
public final class SonsPropis {

    /** El que s'afegeix al nom d'un so quan se'n mou algun botó. */
    public static final String MODIFICAT = " (modificat)";

    private SonsPropis() {
    }

    /** El nom sense « (modificat)». */
    public static String base(String nom) {
        if (nom == null) return "";
        return nom.endsWith(MODIFICAT) ? nom.substring(0, nom.length() - MODIFICAT.length()) : nom;
    }

    public static String modificat(String nom) {
        String b = base(nom);
        return b.isEmpty() ? "So propi" + MODIFICAT : b + MODIFICAT;
    }

    /** Els sons desats d'un tipus (polifònic o monofònic), per ordre alfabètic. */
    public static List<Sinte.Preset> llista(File dir, boolean poli) {
        List<Sinte.Preset> r = new ArrayList<>();
        File[] fs = dir == null ? null : dir.listFiles((d, n) -> n.endsWith(".so"));
        if (fs == null) return r;
        for (File f : fs) {
            Properties p = llegeix(f);
            if (p == null) continue;
            if (("1".equals(p.getProperty("poli"))) != poli) continue;
            String nom = p.getProperty("nom", "").trim();
            if (nom.isEmpty()) continue;
            r.add(new Sinte.Preset(nom, "1".equals(p.getProperty("greu")), Sinte.So.llegeix(p, "so.")));
        }
        r.sort((a, b) -> a.nom.compareToIgnoreCase(b.nom));
        return r;
    }

    public static Sinte.Preset troba(File dir, boolean poli, String nom) {
        for (Sinte.Preset p : llista(dir, poli)) if (p.nom.equals(nom)) return p;
        return null;
    }

    /** Desa un so (si ja n'hi ha un amb aquest nom i tipus, el substitueix). */
    public static void desa(File dir, String nom, boolean poli, boolean greu, Sinte.So so) throws IOException {
        nom = nom == null ? "" : nom.trim();
        if (nom.isEmpty()) throw new IOException("cal un nom");
        dir.mkdirs();
        File f = fitxer(dir, nom, poli);
        if (f == null) {
            String b = (poli ? "p_" : "m_") + net(nom);
            f = new File(dir, b + ".so");
            for (int i = 2; f.exists(); i++) f = new File(dir, b + "_" + i + ".so");
        }
        Properties p = new Properties();
        p.setProperty("nom", nom);
        p.setProperty("poli", poli ? "1" : "0");
        p.setProperty("greu", greu ? "1" : "0");
        so.desa(p, "so.");
        File tmp = new File(f.getPath() + ".tmp");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
            p.store(w, "So propi (Riffs)");
        }
        if (!tmp.renameTo(f)) {
            f.delete();
            if (!tmp.renameTo(f)) throw new IOException("no s'ha pogut desar el so");
        }
    }

    public static boolean esborra(File dir, String nom, boolean poli) {
        File f = fitxer(dir, nom, poli);
        return f != null && f.delete();
    }

    /** El fitxer d'un so desat (o null si no n'hi ha cap amb aquest nom i tipus). */
    private static File fitxer(File dir, String nom, boolean poli) {
        File[] fs = dir == null ? null : dir.listFiles((d, n) -> n.endsWith(".so"));
        if (fs == null) return null;
        for (File f : fs) {
            Properties p = llegeix(f);
            if (p != null && nom.equals(p.getProperty("nom", "").trim()) && "1".equals(p.getProperty("poli")) == poli) return f;
        }
        return null;
    }

    private static Properties llegeix(File f) {
        Properties p = new Properties();
        try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            p.load(r);
            return p;
        } catch (IOException e) {
            return null;
        }
    }

    private static String net(String nom) {
        String s = nom.replaceAll("[^A-Za-z0-9]+", "_");
        if (s.length() > 40) s = s.substring(0, 40);
        return s.isEmpty() ? "so" : s;
    }
}
