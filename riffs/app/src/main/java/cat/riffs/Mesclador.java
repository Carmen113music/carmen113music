package cat.riffs;

import java.util.Map;

/**
 * Barreja les pistes d'un projecte: per a cada moment de la línia de temps, suma el so de cada clip
 * amb el seu retall, fades, punt de volum, fader, mut i solo.
 * No depèn d'Android: es pot provar amb Java d'escriptori.
 */
public final class Mesclador {

    public static final int MOSTREIG = 44100;

    private Mesclador() {}

    public static double lineal(double db) {
        return db <= Projecte.SILENCI_DB ? 0 : Math.pow(10, db / 20.0);
    }

    /**
     * Omple {@code sortida} (mono, valors de -1 a 1 aproximadament) amb la barreja que comença
     * a la mostra {@code inici} de la línia de temps. {@code audio} té les mostres de cada fitxer (pel nom).
     */
    public static void mescla(Projecte p, Map<String, short[]> audio, long inici, float[] sortida, int n) {
        java.util.Arrays.fill(sortida, 0, n, 0f);
        boolean solo = p.hiHaSolo();
        for (Projecte.Pista t : p.pistes) {
            if (t.mut || (solo && !t.solo)) continue;
            short[] so = audio.get(t.wav);
            if (so == null) continue;
            double guany = lineal(t.guanyDb) * lineal(t.volumDb);
            if (guany <= 0) continue;

            long pos = msAMostra(t.posMs);
            long ini = msAMostra(t.iniciMs);
            long fi = Math.min(msAMostra(t.fiMs), so.length);
            long fIn = msAMostra(t.fadeInMs);
            long fOut = msAMostra(t.fadeOutMs);
            // tros de la sortida on sona aquest clip
            long des = Math.max(0, pos + ini - inici);
            long fins = Math.min(n, pos + fi - inici);
            for (long i = des; i < fins; i++) {
                long f = inici + i - pos;              // mostra dins el fitxer
                if (f < ini || f >= fi || f < 0 || f >= so.length) continue;
                double env = 1;
                if (fIn > 0 && f < ini + fIn) env = (f - ini) / (double) fIn;
                if (fOut > 0 && f > fi - fOut) env = Math.min(env, (fi - f) / (double) fOut);
                sortida[(int) i] += (float) (so[(int) f] / 32768.0 * guany * env);
            }
        }
    }

    /** Converteix la barreja a 16 bits, amb un limitador suau perquè no faci «clic» si satura. */
    public static void a16bits(float[] entrada, short[] sortida, int n) {
        for (int i = 0; i < n; i++) {
            float v = entrada[i];
            if (v > 0.9f) v = 0.9f + 0.1f * (float) Math.tanh((v - 0.9f) / 0.1f);
            else if (v < -0.9f) v = -0.9f - 0.1f * (float) Math.tanh((-v - 0.9f) / 0.1f);
            sortida[i] = (short) Math.round(v * 32767);
        }
    }

    public static long msAMostra(long ms) {
        return ms * MOSTREIG / 1000;
    }

    public static long mostraAMs(long mostra) {
        return mostra * 1000 / MOSTREIG;
    }
}
