package cat.riffs;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Les gravacions i les carpetes, guardades com a fitxers i carpetes de debò
 * dins la memòria de l'app. Cada gravació són dos fitxers: «AAAAMMDD_HHMMSS.wav» i «.txt» (la fitxa).
 * No depèn d'Android: es pot provar amb Java d'escriptori.
 */
public final class Biblioteca {

    /** Una gravació: el so i la seva fitxa. */
    public static final class Gravacio {
        public final File wav;
        public final Fitxa fitxa;

        Gravacio(File wav, Fitxa fitxa) {
            this.wav = wav;
            this.fitxa = fitxa;
        }

        public File fitxer() {
            return fitxaDe(wav);
        }

        public void desa() throws IOException {
            fitxa.desa(fitxer());
        }
    }

    /** Una carpeta amb el seu nom per mostrar i la profunditat (per sagnar-la en una llista). */
    public static final class Carpeta {
        public final File dir;
        public final String ruta;   // «Cançó nova / Pont»
        public final int nivell;

        Carpeta(File dir, String ruta, int nivell) {
            this.dir = dir;
            this.ruta = ruta;
            this.nivell = nivell;
        }
    }

    public final File arrel;

    public Biblioteca(File arrel) {
        this.arrel = arrel;
        arrel.mkdirs();
    }

    public static File fitxaDe(File wav) {
        String nom = wav.getName();
        int punt = nom.lastIndexOf('.');
        return new File(wav.getParentFile(), (punt > 0 ? nom.substring(0, punt) : nom) + ".txt");
    }

    // ---------- Llistes ----------

    /** Subcarpetes d'una carpeta, per ordre alfabètic. */
    public List<File> carpetes(File dir) {
        List<File> r = new ArrayList<>();
        File[] fs = dir.listFiles();
        if (fs != null) for (File f : fs) if (f.isDirectory()) r.add(f);
        r.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return r;
    }

    /** Gravacions d'una carpeta, la més recent a dalt. */
    public List<Gravacio> gravacions(File dir) {
        List<Gravacio> r = new ArrayList<>();
        File[] fs = dir.listFiles();
        if (fs != null) {
            for (File f : fs) {
                if (f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".wav")) {
                    Fitxa fx = Fitxa.llegeix(fitxaDe(f));
                    if (fx.data == 0) fx.data = f.lastModified();
                    if (fx.titol.isEmpty()) fx.titol = titolPerDefecte(fx.data);
                    r.add(new Gravacio(f, fx));
                }
            }
        }
        r.sort((a, b) -> Long.compare(b.fitxa.data, a.fitxa.data));
        return r;
    }

    public Gravacio gravacio(File wav) {
        Fitxa fx = Fitxa.llegeix(fitxaDe(wav));
        if (fx.data == 0) fx.data = wav.lastModified();
        if (fx.titol.isEmpty()) fx.titol = titolPerDefecte(fx.data);
        return new Gravacio(wav, fx);
    }

    /** Totes les gravacions de dins (incloses les subcarpetes). */
    public int compta(File dir) {
        int n = 0;
        File[] fs = dir.listFiles();
        if (fs == null) return 0;
        for (File f : fs) {
            if (f.isDirectory()) n += compta(f);
            else if (f.getName().toLowerCase(Locale.ROOT).endsWith(".wav")) n++;
        }
        return n;
    }

    /** Totes les carpetes (començant per l'arrel), en ordre d'arbre. */
    public List<Carpeta> arbre() {
        List<Carpeta> r = new ArrayList<>();
        r.add(new Carpeta(arrel, "", 0));
        afegeixArbre(arrel, "", 1, r);
        return r;
    }

    private void afegeixArbre(File dir, String prefix, int nivell, List<Carpeta> r) {
        for (File c : carpetes(dir)) {
            String ruta = prefix.isEmpty() ? c.getName() : prefix + " / " + c.getName();
            r.add(new Carpeta(c, ruta, nivell));
            afegeixArbre(c, ruta, nivell + 1, r);
        }
    }

    /** «Cançó nova / Pont», o "" si és l'arrel. Null si no és dins la biblioteca. */
    public String ruta(File dir) {
        List<String> parts = new ArrayList<>();
        File d = dir;
        try {
            File a = arrel.getCanonicalFile();
            d = d.getCanonicalFile();
            while (d != null && !d.equals(a)) {
                parts.add(d.getName());
                d = d.getParentFile();
            }
            if (d == null) return null;
        } catch (IOException e) {
            return null;
        }
        Collections.reverse(parts);
        return String.join(" / ", parts);
    }

    public boolean esDins(File f) {
        return ruta(f) != null;
    }

    // ---------- Gravacions ----------

    /** Fitxer nou (encara buit) per a una gravació feta en aquest moment. */
    public File nouWav(File dir, long ara) {
        dir.mkdirs();
        String base = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(new Date(ara));
        File f = new File(dir, base + ".wav");
        int n = 2;
        while (f.exists() || fitxaDe(f).exists()) {
            f = new File(dir, base + "_" + n + ".wav");
            n++;
        }
        return f;
    }

    /** Mou una gravació (so i fitxa) a una altra carpeta. Torna el nou .wav. */
    public File mou(File wav, File desti) throws IOException {
        if (wav.getParentFile().getCanonicalFile().equals(desti.getCanonicalFile())) return wav;
        desti.mkdirs();
        File nou = new File(desti, wav.getName());
        if (nou.exists() || fitxaDe(nou).exists()) {
            String nom = wav.getName().replaceAll("\\.wav$", "");
            int n = 2;
            do {
                nou = new File(desti, nom + "_" + n + ".wav");
                n++;
            } while (nou.exists() || fitxaDe(nou).exists());
        }
        File fitxaVella = fitxaDe(wav);
        File onaVella = Ona.fitxerDe(wav);
        if (!wav.renameTo(nou)) throw new IOException("No s'ha pogut moure la gravació.");
        if (fitxaVella.exists() && !fitxaVella.renameTo(fitxaDe(nou))) {
            throw new IOException("S'ha mogut el so, però no la fitxa.");
        }
        if (onaVella.exists() && !onaVella.renameTo(Ona.fitxerDe(nou))) onaVella.delete();
        return nou;
    }

    public boolean esborra(File wav) {
        File fx = fitxaDe(wav);
        File ona = Ona.fitxerDe(wav);
        boolean ok = wav.delete();
        if (fx.exists()) fx.delete();
        if (ona.exists()) ona.delete();
        return ok;
    }

    // ---------- Carpetes ----------

    /**
     * Neteja un nom de carpeta. Llança IllegalArgumentException amb un missatge en català
     * si no és vàlid.
     */
    public static String netejaNom(String nom) {
        String n = nom == null ? "" : nom.trim().replaceAll("\\s+", " ");
        n = n.replaceAll("[/\\\\:*?\"<>|]", "-");
        if (n.isEmpty()) throw new IllegalArgumentException("Escriu un nom per a la carpeta.");
        if (n.equals(".") || n.equals("..") || n.startsWith(".")) {
            throw new IllegalArgumentException("El nom no pot començar amb un punt.");
        }
        if (n.length() > 60) n = n.substring(0, 60).trim();
        return n;
    }

    public File creaCarpeta(File dins, String nom) {
        String n = netejaNom(nom);
        File c = new File(dins, n);
        if (c.exists()) throw new IllegalArgumentException("Ja hi ha una carpeta que es diu «" + n + "».");
        if (!c.mkdirs()) throw new IllegalArgumentException("No s'ha pogut crear la carpeta.");
        return c;
    }

    public File reanomenaCarpeta(File carpeta, String nomNou) {
        String n = netejaNom(nomNou);
        if (n.equals(carpeta.getName())) return carpeta;
        File c = new File(carpeta.getParentFile(), n);
        if (c.exists() && !n.equalsIgnoreCase(carpeta.getName())) {
            throw new IllegalArgumentException("Ja hi ha una carpeta que es diu «" + n + "».");
        }
        if (!carpeta.renameTo(c)) throw new IllegalArgumentException("No s'ha pogut canviar el nom.");
        return c;
    }

    /** Esborra la carpeta i tot el que hi ha a dins. */
    public boolean esborraCarpeta(File carpeta) {
        if (carpeta.equals(arrel)) return false;
        File[] fs = carpeta.listFiles();
        if (fs != null) {
            for (File f : fs) {
                if (f.isDirectory()) esborraCarpeta(f);
                else f.delete();
            }
        }
        return carpeta.delete();
    }

    /** Arregla els WAV que es van quedar a mitges. Torna quants n'ha arreglat. */
    public int reparaTot(File dir) {
        int n = 0;
        File[] fs = dir.listFiles();
        if (fs == null) return 0;
        for (File f : fs) {
            if (f.isDirectory()) n += reparaTot(f);
            else if (f.getName().endsWith(".wav") && Wav.repara(f)) {
                n++;
                Fitxa fx = Fitxa.llegeix(fitxaDe(f));
                Wav.Info info = Wav.llegeixInfo(f);
                if (info != null) {
                    fx.duradaMs = info.duradaMs;
                    if (fx.data == 0) fx.data = f.lastModified();
                    try {
                        fx.desa(fitxaDe(f));
                    } catch (IOException ignored) {
                    }
                }
            }
        }
        return n;
    }

    public long midaTotal(File dir) {
        long n = 0;
        File[] fs = dir.listFiles();
        if (fs == null) return 0;
        for (File f : fs) n += f.isDirectory() ? midaTotal(f) : f.length();
        return n;
    }

    // ---------- Textos ----------

    public static String titolPerDefecte(long data) {
        return "Idea del " + new SimpleDateFormat("d/M 'a les' H:mm", Locale.ROOT).format(new Date(data));
    }

    public static String durada(long ms) {
        long s = Math.max(0, ms) / 1000;
        if (s >= 3600) return String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    public static String data(long data) {
        return new SimpleDateFormat("d/M/yyyy H:mm", Locale.ROOT).format(new Date(data));
    }

    public static String mida(long bytes) {
        if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%d kB", bytes / 1024);
        return String.format(new Locale("ca"), "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    /** Nom de fitxer segur per compartir: «Riff del pont.wav». */
    public static String nomPerCompartir(String titol) {
        String n = titol == null ? "" : titol.replaceAll("[/\\\\:*?\"<>|\\n\\r\\t]", " ").replaceAll("\\s+", " ").trim();
        if (n.isEmpty()) n = "Gravacio";
        if (n.length() > 80) n = n.substring(0, 80).trim();
        return n + ".wav";
    }
}
