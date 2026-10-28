package cat.riffs;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.List;

/**
 * Càlculs de tempo: tap tempo, nom del compàs, on cauen els temps i
 * buscar l'atac (la nota) més proper a un moment donat.
 * No depèn d'Android: es pot provar amb Java d'escriptori.
 */
public final class Ritme {

    public static final int BPM_MIN = 30;
    public static final int BPM_MAX = 300;

    private Ritme() {}

    /** «4/4», «3/4», «2/4», «6/8». */
    public static String nomCompas(int temps) {
        return temps == 6 ? "6/8" : temps + "/4";
    }

    public static double limita(double bpm) {
        return Math.max(BPM_MIN, Math.min(BPM_MAX, bpm));
    }

    /** Durada d'un temps, en mil·lisegons. */
    public static double msPerTemps(double bpm) {
        return 60000.0 / bpm;
    }

    /**
     * Tap tempo: a partir dels moments (ms) dels darrers tocs, calcula el BPM.
     * Fa servir com a molt els últims 6 tocs i ignora pauses de més de 2 s (comença de nou).
     * Torna 0 si encara no n'hi ha prou (calen 2 tocs com a mínim).
     */
    public static double bpmDeTocs(List<Long> tocs) {
        if (tocs == null || tocs.size() < 2) return 0;
        int fi = tocs.size() - 1;
        int inici = fi;
        while (inici > 0 && fi - inici < 5 && tocs.get(inici) - tocs.get(inici - 1) <= 2000) inici--;
        if (inici == fi) return 0;
        double interval = (tocs.get(fi) - tocs.get(inici)) / (double) (fi - inici);
        if (interval <= 0) return 0;
        return limita(60000.0 / interval);
    }

    /**
     * Busca l'atac més clar (on el volum puja de cop) a prop d'un moment esperat.
     * Mira des de 80 ms abans fins a 300 ms després. Si no hi ha cap atac clar, torna el moment esperat.
     */
    public static long afinaPrimerTemps(File wav, long esperatMs) {
        Wav.Info info = Wav.llegeixInfo(wav);
        if (info == null || info.bits != 16 || esperatMs < 0) return esperatMs;
        final int finestra = Math.max(1, info.mostreig / 200);   // 5 ms
        long iniciMs = Math.max(0, esperatMs - 80);
        long fiMs = Math.min(info.duradaMs, esperatMs + 300);
        if (fiMs - iniciMs < 30) return esperatMs;

        // Llegeix des de 60 ms abans de l'inici per tenir el nivell previ
        long llegirDesDe = Math.max(0, iniciMs - 60);
        long mostraInici = llegirDesDe * info.mostreig / 1000;
        long mostraFi = fiMs * info.mostreig / 1000;
        int n = (int) (mostraFi - mostraInici);
        if (n <= finestra * 4) return esperatMs;
        float[] env = new float[n / finestra];
        try (RandomAccessFile f = new RandomAccessFile(wav, "r")) {
            long base = f.length() - info.bytesDades;
            int pas = 2 * info.canals;
            f.seek(base + mostraInici * pas);
            byte[] b = new byte[env.length * finestra * pas];
            int llegits = f.read(b);
            if (llegits <= 0) return esperatMs;
            for (int i = 0; i < env.length; i++) {
                int pic = 0;
                for (int k = 0; k < finestra; k++) {
                    int idx = (i * finestra + k) * pas;
                    if (idx + 1 >= llegits) break;
                    int v = Math.abs((short) ((b[idx] & 0xff) | (b[idx + 1] << 8)));
                    if (v > pic) pic = v;
                }
                env[i] = pic / 32768f;
            }
        } catch (IOException e) {
            return esperatMs;
        }

        // Atac = el pic d'ara comparat amb el màxim dels 40 ms anteriors
        int primer = (int) ((iniciMs - llegirDesDe) / 5);
        int millor = -1;
        float millorPuja = 0;
        float max = 0;
        for (float v : env) max = Math.max(max, v);
        for (int i = Math.max(primer, 8); i < env.length; i++) {
            float abans = 0;
            for (int k = i - 8; k < i; k++) abans = Math.max(abans, env[k]);
            float puja = env[i] - abans * 1.4f;
            // els atacs més a prop del moment esperat tenen una mica de preferència
            long ms = llegirDesDe + i * 5L;
            float pes = 1f - Math.min(0.4f, Math.abs(ms - esperatMs) / 750f);
            if (puja * pes > millorPuja) {
                millorPuja = puja * pes;
                millor = i;
            }
        }
        // només si l'atac és clar: puja prou respecte el so més fort de la zona
        if (millor < 0 || millorPuja < Math.max(0.04f, max * 0.25f)) return esperatMs;
        return llegirDesDe + millor * 5L;
    }

    /**
     * Omple un tros de so (que comença a la mostra {@code inici}) amb els clics del metrònom.
     * El primer temps de cada compàs fa servir el clic fort.
     */
    public static void ompleClics(short[] tros, long inici, double mostresPerTemps, int compas,
                                  short[] fort, short[] fluix) {
        int llarg = Math.max(fort.length, fluix.length);
        long primer = (long) Math.floor((inici - llarg) / mostresPerTemps);
        if (primer < 0) primer = 0;
        for (long t = primer; ; t++) {
            long pos = Math.round(t * mostresPerTemps);
            if (pos >= inici + tros.length) break;
            short[] c = (t % compas == 0) ? fort : fluix;
            for (int i = 0; i < tros.length; i++) {
                long k = inici + i - pos;
                if (k >= 0 && k < c.length) tros[i] = (short) Math.max(-32768, Math.min(32767, tros[i] + c[(int) k]));
            }
        }
    }
}
