package cat.riffs;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Escriptura i lectura de fitxers WAV (PCM de 16 bits).
 * No depèn d'Android: es pot provar amb Java d'escriptori.
 */
public final class Wav {

    private Wav() {}

    /** Informació bàsica d'un WAV. */
    public static final class Info {
        public int mostreig;     // mostres per segon (p. ex. 44100)
        public int canals;       // 1 = mono, 2 = estèreo
        public int bits;         // 16
        public long bytesDades;  // mida del bloc de so
        public long duradaMs;
    }

    /** Escriu el so a mesura que arriba i manté la capçalera al dia. */
    public static final class Escriptor {
        private final RandomAccessFile f;
        private final int mostreig;
        private final int canals;
        private long bytesDades = 0;
        private byte[] tampo = new byte[0];

        public Escriptor(File fitxer, int mostreig, int canals) throws IOException {
            this.mostreig = mostreig;
            this.canals = canals;
            this.f = new RandomAccessFile(fitxer, "rw");
            f.setLength(0);
            escriuCapcalera(f, mostreig, canals, 0);
        }

        /** Afegeix n mostres. */
        public void escriu(short[] mostres, int n) throws IOException {
            if (n <= 0) return;
            if (tampo.length < n * 2) tampo = new byte[n * 2];
            for (int i = 0; i < n; i++) {
                short s = mostres[i];
                tampo[2 * i] = (byte) (s & 0xff);
                tampo[2 * i + 1] = (byte) ((s >> 8) & 0xff);
            }
            f.seek(44 + bytesDades);
            f.write(tampo, 0, n * 2);
            bytesDades += n * 2L;
        }

        /** Torna a escriure les mides a la capçalera (per si l'app es tanca de cop). */
        public void actualitzaCapcalera() throws IOException {
            escriuCapcalera(f, mostreig, canals, bytesDades);
        }

        public long duradaMs() {
            return bytesDades * 1000L / (2L * canals * mostreig);
        }

        public void tanca() throws IOException {
            actualitzaCapcalera();
            f.close();
        }
    }

    static void escriuCapcalera(RandomAccessFile f, int mostreig, int canals, long bytesDades) throws IOException {
        int bytesPerSegon = mostreig * canals * 2;
        byte[] c = new byte[44];
        posaText(c, 0, "RIFF");
        posaInt(c, 4, (int) (36 + bytesDades));
        posaText(c, 8, "WAVE");
        posaText(c, 12, "fmt ");
        posaInt(c, 16, 16);
        posaShort(c, 20, 1);            // PCM
        posaShort(c, 22, canals);
        posaInt(c, 24, mostreig);
        posaInt(c, 28, bytesPerSegon);
        posaShort(c, 32, canals * 2);   // bytes per mostra (tots els canals)
        posaShort(c, 34, 16);           // bits
        posaText(c, 36, "data");
        posaInt(c, 40, (int) bytesDades);
        f.seek(0);
        f.write(c);
    }

    /**
     * Llegeix la capçalera. Si la mida declarada no quadra amb el fitxer
     * (per exemple, si l'app es va tancar enmig d'una gravació), fa servir la mida real.
     * Torna null si no és un WAV que entenguem.
     */
    public static Info llegeixInfo(File fitxer) {
        try (RandomAccessFile f = new RandomAccessFile(fitxer, "r")) {
            if (f.length() < 44) return null;
            byte[] cap = new byte[12];
            f.readFully(cap);
            if (!text(cap, 0).equals("RIFF") || !text(cap, 8).equals("WAVE")) return null;
            Info info = null;
            long pos = 12;
            while (pos + 8 <= f.length()) {
                f.seek(pos);
                byte[] tros = new byte[8];
                f.readFully(tros);
                String id = text(tros, 0);
                long mida = llegeixInt(tros, 4) & 0xffffffffL;
                if (id.equals("fmt ")) {
                    byte[] fmt = new byte[16];
                    f.readFully(fmt);
                    info = new Info();
                    info.canals = llegeixShort(fmt, 2);
                    info.mostreig = llegeixInt(fmt, 4);
                    info.bits = llegeixShort(fmt, 14);
                } else if (id.equals("data")) {
                    if (info == null) return null;
                    long real = f.length() - (pos + 8);
                    info.bytesDades = (mida == 0 || mida > real) ? real : mida;
                    int bytesPerSegon = info.mostreig * info.canals * Math.max(1, info.bits / 8);
                    info.duradaMs = bytesPerSegon == 0 ? 0 : info.bytesDades * 1000L / bytesPerSegon;
                    return info;
                }
                pos += 8 + mida + (mida % 2);
            }
            return null;
        } catch (IOException e) {
            return null;
        }
    }

    /** Arregla la capçalera d'un WAV que es va quedar a mitges. Torna true si l'ha tocat. */
    public static boolean repara(File fitxer) {
        Info info = llegeixInfo(fitxer);
        if (info == null) return false;
        try (RandomAccessFile f = new RandomAccessFile(fitxer, "rw")) {
            byte[] cap = new byte[44];
            f.readFully(cap);
            if (!text(cap, 36).equals("data")) return false; // només els nostres WAV simples
            long declarat = llegeixInt(cap, 40) & 0xffffffffL;
            long real = f.length() - 44;
            if (declarat == real) return false;
            escriuCapcalera(f, info.mostreig, info.canals, real);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Llegeix tot el so com a mostres mono de 16 bits (si és estèreo, fa la mitjana dels canals).
     * Torna null si no és un WAV que entenguem.
     */
    public static short[] llegeixMostres(File fitxer) {
        Info info = llegeixInfo(fitxer);
        if (info == null || info.bits != 16 || info.canals < 1) return null;
        long n = info.bytesDades / (2L * info.canals);
        if (n > Integer.MAX_VALUE / 2) return null;
        short[] r = new short[(int) n];
        try (RandomAccessFile f = new RandomAccessFile(fitxer, "r")) {
            f.seek(f.length() - info.bytesDades);
            byte[] b = new byte[65536 - 65536 % (2 * info.canals)];
            int pas = 2 * info.canals;
            int idx = 0;
            while (idx < r.length) {
                int volen = Math.min(b.length, (r.length - idx) * pas);
                int llegits = f.read(b, 0, volen);
                if (llegits <= 0) break;
                for (int i = 0; i + pas - 1 < llegits && idx < r.length; i += pas) {
                    int suma = 0;
                    for (int c = 0; c < info.canals; c++) {
                        suma += (short) ((b[i + 2 * c] & 0xff) | (b[i + 2 * c + 1] << 8));
                    }
                    r[idx++] = (short) (suma / info.canals);
                }
            }
        } catch (IOException e) {
            return null;
        }
        return r;
    }

    // --- ajudes de bytes (little-endian) ---

    private static void posaText(byte[] b, int i, String s) {
        for (int k = 0; k < 4; k++) b[i + k] = (byte) s.charAt(k);
    }

    private static void posaInt(byte[] b, int i, int v) {
        b[i] = (byte) v;
        b[i + 1] = (byte) (v >> 8);
        b[i + 2] = (byte) (v >> 16);
        b[i + 3] = (byte) (v >> 24);
    }

    private static void posaShort(byte[] b, int i, int v) {
        b[i] = (byte) v;
        b[i + 1] = (byte) (v >> 8);
    }

    private static String text(byte[] b, int i) {
        return new String(b, i, 4, java.nio.charset.StandardCharsets.US_ASCII);
    }

    private static int llegeixInt(byte[] b, int i) {
        return (b[i] & 0xff) | (b[i + 1] & 0xff) << 8 | (b[i + 2] & 0xff) << 16 | (b[i + 3] & 0xff) << 24;
    }

    private static int llegeixShort(byte[] b, int i) {
        return (b[i] & 0xff) | (b[i + 1] & 0xff) << 8;
    }
}
