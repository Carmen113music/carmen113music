package cat.riffs;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;

/**
 * Calcula la forma d'ona d'un WAV (els pics de volum) per dibuixar-la.
 * Es desa al costat del .wav amb extensió .ona perquè no s'hagi de recalcular cada cop.
 * No depèn d'Android: es pot provar amb Java d'escriptori.
 */
public final class Ona {

    public static final int PUNTS = 240;

    private Ona() {}

    public static File fitxerDe(File wav) {
        String nom = wav.getName();
        int punt = nom.lastIndexOf('.');
        return new File(wav.getParentFile(), (punt > 0 ? nom.substring(0, punt) : nom) + ".ona");
    }

    /** Pics (0..1) de la gravació. Fa servir el fitxer .ona si està al dia; si no, el calcula i el desa. */
    public static float[] pics(File wav) {
        File cau = fitxerDe(wav);
        if (cau.exists() && cau.lastModified() >= wav.lastModified() && cau.length() == PUNTS) {
            try {
                byte[] b = Files.readAllBytes(cau.toPath());
                float[] r = new float[b.length];
                for (int i = 0; i < b.length; i++) r[i] = (b[i] & 0xff) / 255f;
                return r;
            } catch (IOException ignored) {
            }
        }
        float[] r = calcula(wav, PUNTS);
        if (r != null) {
            byte[] b = new byte[r.length];
            for (int i = 0; i < r.length; i++) b[i] = (byte) Math.round(r[i] * 255);
            try {
                Files.write(cau.toPath(), b);
            } catch (IOException ignored) {
            }
        }
        return r;
    }

    /**
     * Divideix el so en {@code n} trossos i en treu el pic de cada un.
     * Ho escala perquè el tros més fort valgui 1 (així les gravacions fluixes també es veuen).
     */
    public static float[] calcula(File wav, int n) {
        Wav.Info info = Wav.llegeixInfo(wav);
        if (info == null || info.bits != 16 || info.canals < 1) return null;
        long mostres = info.bytesDades / (2L * info.canals);
        float[] r = new float[n];
        if (mostres == 0) return r;
        try (RandomAccessFile f = new RandomAccessFile(wav, "r")) {
            long inici = f.length() - info.bytesDades; // el bloc de so és al final
            f.seek(inici);
            byte[] tros = new byte[8192 * info.canals];
            long llegides = 0;
            int pas = info.canals * 2;
            while (llegides < mostres) {
                int volen = (int) Math.min(tros.length / pas, mostres - llegides) * pas;
                int n2 = f.read(tros, 0, volen);
                if (n2 <= 0) break;
                for (int i = 0; i + 1 < n2; i += pas) {
                    int v = Math.abs((short) ((tros[i] & 0xff) | (tros[i + 1] << 8)));
                    int idx = (int) (llegides * n / mostres);
                    if (idx >= n) idx = n - 1;
                    float p = v / 32768f;
                    if (p > r[idx]) r[idx] = p;
                    llegides++;
                }
            }
        } catch (IOException e) {
            return null;
        }
        float max = 0;
        for (float v : r) max = Math.max(max, v);
        if (max > 0.001f) for (int i = 0; i < n; i++) r[i] = Math.min(1f, r[i] / max);
        return r;
    }
}
