package cat.riffs;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Kits de bateria: importar-los (llegir qualsevol WAV, convertir-lo a mono 44.100 Hz 16 bits,
 * treure silencis i classificar cada so pel nom del fitxer) i desar-los a la carpeta pròpia de l'app.
 * No depèn d'Android: es pot provar amb Java d'escriptori.
 */
public final class Kits {

    private Kits() {}

    public static final int MOSTREIG = 44100;
    public static final String FITXER = "kit.txt";
    /** Mida màxima d'un sample (per no omplir la memòria amb un fitxer que no és un cop de bateria). */
    static final int MAX_BYTES = 40 * 1024 * 1024;

    // ---------- Tipus de so ----------

    /** Tipus, en l'ordre en què surten (i en què seran les files del drumpad). */
    public static final String[] TIPUS = {
            "kick", "snare", "rim", "clap", "hihat", "hihat-obert", "hihat-pedal",
            "tom-agut", "tom-mig", "tom-greu", "tom", "ride", "crash", "perc", "altres"};

    private static final String[] NOMS_TIPUS = {
            "Kick", "Snare", "Rimshot", "Palmes", "Hihat tancat", "Hihat obert", "Hihat pedal",
            "Tom agut", "Tom mig", "Tom greu", "Tom", "Ride", "Crash", "Percussió", "Altres"};

    public static String nomTipus(String tipus) {
        int i = ordre(tipus);
        return i < NOMS_TIPUS.length ? NOMS_TIPUS[i] : "Altres";
    }

    public static int ordre(String tipus) {
        for (int i = 0; i < TIPUS.length; i++) if (TIPUS[i].equals(tipus)) return i;
        return TIPUS.length - 1;
    }

    /** Parteix un nom en paraules: «Gimme_SnareForFills1» → [gimme, snare, for, fills, 1]. */
    static List<String> paraules(String nom) {
        String s = nom.replaceAll("(?i)\\.wav$", "");
        s = s.replaceAll("([a-z])([A-Z])", "$1 $2")
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2")
                .replaceAll("([A-Za-z])([0-9])", "$1 $2")
                .replaceAll("([0-9])([A-Za-z])", "$1 $2");
        List<String> r = new ArrayList<>();
        for (String p : s.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (!p.isEmpty()) r.add(p);
        }
        return r;
    }

    private static boolean te(List<String> w, String... claus) {
        for (String c : claus) if (w.contains(c)) return true;
        return false;
    }

    private static boolean conte(String junt, String... trossos) {
        for (String t : trossos) if (junt.contains(t)) return true;
        return false;
    }

    /** Endevina el tipus de so a partir del nom del fitxer. */
    public static String classifica(String nomFitxer) {
        List<String> w = paraules(nomFitxer);
        String j = String.join("", w);
        if (te(w, "rim", "rimshot", "sidestick", "stick", "aro") || conte(j, "rimshot", "sidestick", "crossstick")) return "rim";
        if (te(w, "clap", "claps", "handclap", "palmes", "palma", "palmas")) return "clap";
        if (conte(j, "kick", "bassdrum", "bombo") || te(w, "bd", "kik", "kck")) return "kick";
        if (conte(j, "snare", "caixa", "caja") || te(w, "sd", "snr")) return "snare";
        boolean hat = conte(j, "hihat", "hat", "charles", "charleston") || te(w, "hh", "chh", "ohh", "phh");
        if (hat) {
            if (te(w, "open", "opened", "obert", "abierto", "ohh") || conte(j, "openhat", "hatopen")) return "hihat-obert";
            if (te(w, "pedal", "foot", "phh", "peu")) return "hihat-pedal";
            return "hihat";
        }
        if (conte(j, "ride")) return "ride";
        if (conte(j, "crash", "china", "splash", "cymbal", "plat") || te(w, "cym")) return "crash";
        if (conte(j, "tom")) {
            if (te(w, "floor", "low", "lo", "greu", "grave", "baix")) return "tom-greu";
            if (te(w, "mid", "middle", "mig", "medio")) return "tom-mig";
            if (te(w, "high", "hi", "agut", "alto", "rack")) return "tom-agut";
            return "tom";
        }
        if (conte(j, "perc", "shaker", "tamb", "cowbell", "conga", "bongo", "clave", "block", "cabasa", "triang", "guiro"))
            return "perc";
        return "altres";
    }

    /** Prefix que comparteixen tots els noms i que acaba amb «_», «-» o un espai (p. ex. «Gimme_»). */
    public static String prefixComu(List<String> noms) {
        if (noms.size() < 2) return "";
        String p = noms.get(0);
        for (String n : noms) {
            int i = 0;
            while (i < p.length() && i < n.length() && p.charAt(i) == n.charAt(i)) i++;
            p = p.substring(0, i);
        }
        int tall = Math.max(p.lastIndexOf('_'), Math.max(p.lastIndexOf('-'), p.lastIndexOf(' ')));
        return tall < 0 ? "" : p.substring(0, tall + 1);
    }

    /** Nom per mostrar: sense el prefix ni l'extensió, i amb espais. «Gimme_SnareForFills1» → «Snare For Fills 1». */
    public static String nomNet(String nomFitxer, String prefix) {
        String s = nomFitxer;
        if (!prefix.isEmpty() && s.startsWith(prefix)) s = s.substring(prefix.length());
        s = s.replaceAll("(?i)\\.wav$", "");
        s = s.replaceAll("([a-z])([A-Z])", "$1 $2")
                .replaceAll("([A-Za-z])([0-9])", "$1 $2")
                .replaceAll("[_\\-]+", " ")
                .replaceAll("\\s+", " ").trim();
        if (s.isEmpty()) s = nomFitxer;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ---------- Llegir i convertir l'àudio ----------

    /** Què hi havia al fitxer original (per al resum). */
    public static final class Format {
        public int mostreig, canals, bits;
        public boolean decimal;

        @Override
        public String toString() {
            return (canals == 1 ? "mono" : canals == 2 ? "estèreo" : canals + " canals") + ", "
                    + mostreig + " Hz, " + bits + " bits" + (decimal ? " (decimals)" : "");
        }
    }

    static byte[] llegeixTot(InputStream in) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] b = new byte[65536];
        int n;
        while ((n = in.read(b)) > 0) {
            o.write(b, 0, n);
            if (o.size() > MAX_BYTES) throw new IOException("massa gran");
        }
        return o.toByteArray();
    }

    private static int u16(byte[] b, int i) {
        return (b[i] & 0xff) | (b[i + 1] & 0xff) << 8;
    }

    private static int i32(byte[] b, int i) {
        return (b[i] & 0xff) | (b[i + 1] & 0xff) << 8 | (b[i + 2] & 0xff) << 16 | (b[i + 3] & 0xff) << 24;
    }

    /**
     * Llegeix un WAV de qualsevol tipus habitual (PCM de 8, 16, 24 o 32 bits, o decimal de 32 o 64 bits;
     * mono, estèreo o més canals) i el torna com a so mono en valors de -1 a 1, al mostreig original.
     * Torna null si no és un WAV que entenguem.
     */
    public static float[] descodifica(byte[] b, Format fmt) {
        if (b.length < 12) return null;
        String riff = new String(b, 0, 4, StandardCharsets.US_ASCII);
        String wave = new String(b, 8, 4, StandardCharsets.US_ASCII);
        if (!(riff.equals("RIFF") || riff.equals("RF64")) || !wave.equals("WAVE")) return null;
        int format = -1, canals = 0, mostreig = 0, bits = 0, bloc = 0;
        int pos = 12;
        while (pos + 8 <= b.length) {
            String id = new String(b, pos, 4, StandardCharsets.US_ASCII);
            long mida = i32(b, pos + 4) & 0xffffffffL;
            int dades = pos + 8;
            if (id.equals("fmt ") && dades + 16 <= b.length) {
                format = u16(b, dades);
                canals = u16(b, dades + 2);
                mostreig = i32(b, dades + 4);
                bloc = u16(b, dades + 12);
                bits = u16(b, dades + 14);
                if (format == 0xFFFE && mida >= 40 && dades + 26 <= b.length) {
                    format = u16(b, dades + 24); // subformat: 1 = PCM, 3 = decimal
                }
            } else if (id.equals("data")) {
                if (format < 0 || canals < 1 || mostreig < 1000 || bits < 8) return null;
                long real = b.length - dades;
                if (mida == 0 || mida == 0xffffffffL || mida > real) mida = real;
                int bytesMostra = bits / 8;
                if (bloc < bytesMostra * canals) bloc = bytesMostra * canals;
                int n = (int) (mida / bloc);
                float[] r = new float[n];
                boolean dec = format == 3;
                if (!(format == 1 || dec)) return null;
                if (dec && bits != 32 && bits != 64) return null;
                if (!dec && bits != 8 && bits != 16 && bits != 24 && bits != 32) return null;
                for (int i = 0; i < n; i++) {
                    int base = dades + i * bloc;
                    double suma = 0;
                    for (int c = 0; c < canals; c++) {
                        int k = base + c * bytesMostra;
                        double v;
                        if (dec) {
                            if (bits == 32) v = Float.intBitsToFloat(i32(b, k));
                            else {
                                long l = (i32(b, k) & 0xffffffffL) | ((long) i32(b, k + 4) << 32);
                                v = Double.longBitsToDouble(l);
                            }
                        } else if (bits == 8) {
                            v = ((b[k] & 0xff) - 128) / 128.0;
                        } else if (bits == 16) {
                            v = (short) u16(b, k) / 32768.0;
                        } else if (bits == 24) {
                            int x = (b[k] & 0xff) | (b[k + 1] & 0xff) << 8 | b[k + 2] << 16;
                            v = x / 8388608.0;
                        } else {
                            v = i32(b, k) / 2147483648.0;
                        }
                        if (Double.isNaN(v)) v = 0;
                        suma += v;
                    }
                    r[i] = (float) (suma / canals);
                }
                if (fmt != null) {
                    fmt.mostreig = mostreig;
                    fmt.canals = canals;
                    fmt.bits = bits;
                    fmt.decimal = dec;
                }
                return r;
            }
            pos = dades + (int) Math.min(mida, Integer.MAX_VALUE - dades - 1) + (int) (mida % 2);
            if (pos <= 0) return null;
        }
        return null;
    }

    /** Canvia el mostreig amb un filtre sinc enfinestrat (de bona qualitat, també per baixar de 96 kHz). */
    public static float[] remostreja(float[] x, int de, int a) {
        if (de == a || x.length == 0) return x;
        double ratio = (double) a / de;
        double fc = Math.min(1.0, ratio) * 0.97;   // tall una mica per sota de Nyquist
        final int zeros = 16;
        double ample = zeros / fc;                  // mitja amplada del filtre, en mostres d'entrada
        final int res = 256;                        // punts de la taula per mostra d'entrada
        int midaTaula = (int) Math.ceil(ample * res) + 2;
        float[] taula = new float[midaTaula];
        for (int i = 0; i < midaTaula; i++) {
            double u = (double) i / res;
            if (u >= ample) break;
            double sinc = u == 0 ? 1 : Math.sin(Math.PI * fc * u) / (Math.PI * fc * u);
            double fin = 0.5 + 0.5 * Math.cos(Math.PI * u / ample);
            taula[i] = (float) (fc * sinc * fin);
        }
        int n = (int) Math.floor(x.length * ratio);
        float[] r = new float[Math.max(0, n)];
        for (int i = 0; i < n; i++) {
            double t = i / ratio;
            int k0 = (int) Math.ceil(t - ample);
            int k1 = (int) Math.floor(t + ample);
            double s = 0;
            for (int k = Math.max(0, k0); k <= k1 && k < x.length; k++) {
                double u = Math.abs(k - t) * res;
                int iu = (int) u;
                if (iu + 1 >= midaTaula) continue;
                double f = u - iu;
                s += x[k] * (taula[iu] + (taula[iu + 1] - taula[iu]) * f);
            }
            r[i] = (float) s;
        }
        return r;
    }

    /**
     * Treu el silenci de l'inici (deixant 1 ms abans del cop) i la cua de silenci del final
     * (deixant 10 ms i un petit fade). Torna null si tot és silenci.
     */
    public static float[] retalla(float[] x, int mostreig) {
        float llindarInici = 0.001f;   // -60 dB
        float llindarFinal = 0.0005f;  // -66 dB
        int ini = -1;
        for (int i = 0; i < x.length; i++) {
            if (Math.abs(x[i]) > llindarInici) {
                ini = i;
                break;
            }
        }
        if (ini < 0) return null;
        int fi = x.length - 1;
        while (fi > ini && Math.abs(x[fi]) <= llindarFinal) fi--;
        ini = Math.max(0, ini - mostreig / 1000);
        int fiReal = Math.min(x.length, fi + 1 + mostreig / 100);
        float[] r = Arrays.copyOfRange(x, ini, fiReal);
        if (fiReal < x.length) {
            int fade = Math.min(r.length, mostreig / 200);
            for (int i = 0; i < fade; i++) {
                r[r.length - 1 - i] *= (float) i / fade;
            }
        }
        return r;
    }

    public static short[] aShort(float[] x) {
        short[] r = new short[x.length];
        for (int i = 0; i < x.length; i++) {
            float v = x[i] * 32767f;
            if (v > 32767f) v = 32767f;
            if (v < -32768f) v = -32768f;
            r[i] = (short) Math.round(v);
        }
        return r;
    }

    /** Converteix un WAV qualsevol al format de l'app. Torna null si no s'ha pogut. */
    public static short[] converteix(byte[] wav, Format fmt) {
        Format f = fmt != null ? fmt : new Format();
        float[] x = descodifica(wav, f);
        if (x == null) return null;
        x = remostreja(x, f.mostreig, MOSTREIG);
        x = retalla(x, MOSTREIG);
        if (x == null) return null;
        return aShort(x);
    }

    // ---------- Kits desats ----------

    public static final class So {
        public String fitxer;    // nom del WAV dins la carpeta del kit (p. ex. «s03.wav»)
        public String tipus;
        public String nom;
        public String original;  // nom del fitxer original
        public long ms;
    }

    public static final class Kit {
        public File dir;
        public String nom;
        public final List<So> sons = new ArrayList<>();

        public File fitxer(So s) {
            return new File(dir, s.fitxer);
        }

        /** Tipus que té el kit, en ordre. */
        public List<String> tipus() {
            List<String> r = new ArrayList<>();
            for (String t : TIPUS) {
                for (So s : sons) {
                    if (s.tipus.equals(t)) {
                        r.add(t);
                        break;
                    }
                }
            }
            return r;
        }

        public List<So> delTipus(String t) {
            List<So> r = new ArrayList<>();
            for (So s : sons) if (s.tipus.equals(t)) r.add(s);
            return r;
        }

        public void ordena() {
            Collections.sort(sons, (a, b) -> {
                int c = Integer.compare(ordre(a.tipus), ordre(b.tipus));
                return c != 0 ? c : compara(a.nom, b.nom);
            });
        }
    }

    /** Ordre «natural»: «Kick 2» abans que «Kick 10». */
    static int compara(String a, String b) {
        String na = a.replaceAll("\\d+", ""), nb = b.replaceAll("\\d+", "");
        int c = na.compareToIgnoreCase(nb);
        if (c != 0) return c;
        String da = a.replaceAll("\\D+", ""), db = b.replaceAll("\\D+", "");
        if (!da.isEmpty() && !db.isEmpty() && da.length() < 10 && db.length() < 10) {
            c = Long.compare(Long.parseLong(da), Long.parseLong(db));
            if (c != 0) return c;
        }
        return a.compareToIgnoreCase(b);
    }

    public static Kit llegeix(File dir) {
        File f = new File(dir, FITXER);
        if (!f.isFile()) return null;
        Properties p = new Properties();
        try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (IOException e) {
            return null;
        }
        Kit k = new Kit();
        k.dir = dir;
        k.nom = p.getProperty("nom", dir.getName());
        int n = parseInt(p.getProperty("sons", "0"));
        for (int i = 0; i < n; i++) {
            So s = new So();
            s.fitxer = p.getProperty("so." + i + ".fitxer");
            if (s.fitxer == null || !new File(dir, s.fitxer).isFile()) continue;
            s.tipus = p.getProperty("so." + i + ".tipus", "altres");
            s.nom = p.getProperty("so." + i + ".nom", s.fitxer);
            s.original = p.getProperty("so." + i + ".original", "");
            s.ms = parseInt(p.getProperty("so." + i + ".ms", "0"));
            k.sons.add(s);
        }
        k.ordena();
        return k;
    }

    public static void desa(Kit k) throws IOException {
        Properties p = new Properties();
        p.setProperty("nom", k.nom);
        p.setProperty("sons", String.valueOf(k.sons.size()));
        for (int i = 0; i < k.sons.size(); i++) {
            So s = k.sons.get(i);
            p.setProperty("so." + i + ".fitxer", s.fitxer);
            p.setProperty("so." + i + ".tipus", s.tipus);
            p.setProperty("so." + i + ".nom", s.nom);
            p.setProperty("so." + i + ".original", s.original == null ? "" : s.original);
            p.setProperty("so." + i + ".ms", String.valueOf(s.ms));
        }
        File tmp = new File(k.dir, FITXER + ".tmp");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
            p.store(w, "Kit de bateria de Riffs");
        }
        File f = new File(k.dir, FITXER);
        if (!tmp.renameTo(f)) {
            f.delete();
            if (!tmp.renameTo(f)) throw new IOException("no s'ha pogut desar el kit");
        }
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    /** Tots els kits, per ordre alfabètic. */
    public static List<Kit> llista(File arrel) {
        List<Kit> r = new ArrayList<>();
        File[] dirs = arrel.listFiles(File::isDirectory);
        if (dirs == null) return r;
        for (File d : dirs) {
            Kit k = llegeix(d);
            if (k != null) r.add(k);
        }
        Collections.sort(r, (a, b) -> compara(a.nom, b.nom));
        return r;
    }

    /** Un nom que encara no fa servir cap kit («Gimme», «Gimme 2»…). */
    public static String nomLliure(File arrel, String nom) {
        List<String> usats = new ArrayList<>();
        for (Kit k : llista(arrel)) usats.add(k.nom.toLowerCase(Locale.ROOT));
        String n = nom;
        for (int i = 2; usats.contains(n.toLowerCase(Locale.ROOT)); i++) n = nom + " " + i;
        return n;
    }

    public static void esborra(Kit k) {
        File[] fs = k.dir.listFiles();
        if (fs != null) for (File f : fs) f.delete();
        k.dir.delete();
    }

    // ---------- Importar ----------

    /** Un fitxer per importar (al mòbil ve del selector de carpetes; a l'ordinador, del disc). */
    public interface Font {
        String nom();

        InputStream obre() throws IOException;
    }

    /** Una carpeta amb WAV: es convertirà en un kit amb el seu nom. */
    public static final class Carpeta {
        public String nom;
        public final List<Font> wavs = new ArrayList<>();
    }

    /**
     * Obre un .zip. Els zips de Windows sovint no diuen que els noms són UTF-8:
     * si no es poden llegir així, es llegeixen com a Latin-1 (els noms amb accents poden quedar estranys, però funciona).
     */
    public static ZipFile obreZip(File f) throws IOException {
        ZipFile z;
        try {
            z = new ZipFile(f, StandardCharsets.UTF_8);
        } catch (java.util.zip.ZipException e) {
            // alguns Java ja fallen en obrir-lo si els noms no són UTF-8
            return new ZipFile(f, StandardCharsets.ISO_8859_1);
        }
        try {
            Enumeration<? extends ZipEntry> en = z.entries();
            while (en.hasMoreElements()) en.nextElement();
            return z;
        } catch (IllegalArgumentException e) {
            z.close();
            return new ZipFile(f, StandardCharsets.ISO_8859_1);
        }
    }

    /**
     * Agrupa els WAV d'un zip per carpetes: cada carpeta amb WAV és un kit amb el nom de la carpeta.
     * Ignora les carpetes ocultes i les còpies de Mac («__MACOSX», «._…»).
     */
    public static List<Carpeta> carpetesDeZip(ZipFile z, String nomPerDefecte) {
        java.util.TreeMap<String, Carpeta> per = new java.util.TreeMap<>(Kits::compara);
        Enumeration<? extends ZipEntry> en = z.entries();
        while (en.hasMoreElements()) {
            ZipEntry e = en.nextElement();
            if (e.isDirectory()) continue;
            String ruta = e.getName().replace('\\', '/');
            if (ruta.startsWith("__MACOSX/") || ruta.contains("/__MACOSX/")) continue;
            int barra = ruta.lastIndexOf('/');
            String nom = ruta.substring(barra + 1);
            if (nom.startsWith(".") || !nom.toLowerCase(Locale.ROOT).endsWith(".wav")) continue;
            String pare = barra < 0 ? "" : ruta.substring(0, barra);
            Carpeta c = per.get(pare);
            if (c == null) {
                c = new Carpeta();
                c.nom = pare.isEmpty() ? nomPerDefecte : pare.substring(pare.lastIndexOf('/') + 1);
                per.put(pare, c);
            }
            c.wavs.add(new Font() {
                @Override
                public String nom() {
                    return nom;
                }

                @Override
                public InputStream obre() throws IOException {
                    return z.getInputStream(e);
                }
            });
        }
        for (Carpeta c : per.values()) c.wavs.sort((a, b) -> compara(a.nom(), b.nom()));
        return new ArrayList<>(per.values());
    }

    public interface Progres {
        void va(int fet, int total, String nom);
    }

    public static final class Resultat {
        public Kit kit;
        public final List<String> errors = new ArrayList<>();
        public final List<String> formats = new ArrayList<>();
        public int altres;
    }

    /** Importa un kit: converteix cada WAV, el classifica i ho desa tot en una carpeta nova dins {@code arrel}. */
    public static Resultat importa(File arrel, String nom, List<Font> fonts, Progres progres) throws IOException {
        Resultat res = new Resultat();
        arrel.mkdirs();
        File dir;
        int i = 1;
        do {
            dir = new File(arrel, "kit" + i++);
        } while (dir.exists());
        if (!dir.mkdirs()) throw new IOException("no s'ha pogut crear la carpeta del kit");
        Kit k = new Kit();
        k.dir = dir;
        k.nom = nomLliure(arrel, nom);

        List<String> noms = new ArrayList<>();
        for (Font f : fonts) noms.add(f.nom());
        String prefix = prefixComu(noms);

        int num = 1;
        for (int j = 0; j < fonts.size(); j++) {
            Font f = fonts.get(j);
            if (progres != null) progres.va(j, fonts.size(), f.nom());
            short[] mostres;
            Format fmt = new Format();
            try (InputStream in = f.obre()) {
                mostres = converteix(llegeixTot(in), fmt);
            } catch (IOException | RuntimeException e) {
                mostres = null;
            }
            if (mostres == null || mostres.length == 0) {
                res.errors.add(f.nom());
                continue;
            }
            String desc = fmt.toString();
            if (!res.formats.contains(desc)) res.formats.add(desc);
            So s = new So();
            s.fitxer = String.format(Locale.ROOT, "s%02d.wav", num++);
            s.original = f.nom();
            s.tipus = classifica(f.nom());
            s.nom = nomNet(f.nom(), prefix);
            s.ms = mostres.length * 1000L / MOSTREIG;
            if (s.tipus.equals("altres")) res.altres++;
            Wav.Escriptor w = new Wav.Escriptor(new File(dir, s.fitxer), MOSTREIG, 1);
            w.escriu(mostres, mostres.length);
            w.tanca();
            k.sons.add(s);
        }
        if (progres != null) progres.va(fonts.size(), fonts.size(), "");
        if (k.sons.isEmpty()) {
            esborra(k);
            return res;
        }
        k.ordena();
        desa(k);
        res.kit = k;
        return res;
    }
}
