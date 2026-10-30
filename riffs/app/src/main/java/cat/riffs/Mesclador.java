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
        mescla(p, audio, inici, sortida, n, null);
    }

    /**
     * Com l'altra, i a més deixa a {@code nivells[i]} el pic (0..1 o més) del que ha sonat de la pista {@code i}
     * en aquest tros, ja amb el fader (per als vúmetres). {@code nivells} pot ser null.
     */
    public static void mescla(Projecte p, Map<String, short[]> audio, long inici, float[] sortida, int n, float[] nivells) {
        mescla(p, audio, inici, sortida, n, nivells, null);
    }

    /** Com l'altra, però sense els clips del so {@code excloure} (el que s'està editant al drumpad o al piano roll). */
    public static void mescla(Projecte p, Map<String, short[]> audio, long inici, float[] sortida, int n, float[] nivells,
                              String excloure) {
        java.util.Arrays.fill(sortida, 0, n, 0f);
        if (nivells != null) java.util.Arrays.fill(nivells, 0f);
        boolean solo = p.hiHaSolo();
        for (int ti = 0; ti < p.pistes.size(); ti++) {
            Projecte.Pista t = p.pistes.get(ti);
            if (t.mut || (solo && !t.solo)) continue;
            float pic = 0;
            double volum = lineal(t.volumDb);
            if (volum <= 0) continue;
            for (Projecte.Clip k : t.clips) {
                if (excloure != null && excloure.equals(k.wav)) continue;
                short[] so = audio.get(k.wav);
                if (so == null) continue;
                double guany = lineal(k.guanyDb) * volum;
                if (guany <= 0) continue;
                long pos = msAMostra(k.posMs);
                long ini = msAMostra(k.iniciMs);
                long fi = msAMostra(k.fiMs);            // pot passar del final del so: allà és silenci
                long fIn = msAMostra(k.fadeInMs);
                long fOut = msAMostra(k.fadeOutMs);
                // tros de la sortida on sona aquest clip
                long des = Math.max(0, pos + ini - inici);
                long fins = Math.min(n, pos + fi - inici);
                for (long i = des; i < fins; i++) {
                    long f = inici + i - pos;              // mostra dins el fitxer
                    if (f < ini || f >= fi || f < 0 || f >= so.length) continue; // fora del so: silenci
                    double env = 1;
                    if (fIn > 0 && f < ini + fIn) env = (f - ini) / (double) fIn;
                    if (fOut > 0 && f > fi - fOut) env = Math.min(env, (fi - f) / (double) fOut);
                    float v = (float) (so[(int) f] / 32768.0 * guany * env);
                    sortida[(int) i] += v;
                    if (v > pic) pic = v;
                    else if (-v > pic) pic = -v;
                }
            }
            if (nivells != null && ti < nivells.length) nivells[ti] = pic;
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
