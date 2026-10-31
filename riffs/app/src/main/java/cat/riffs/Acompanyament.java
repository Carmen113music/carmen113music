package cat.riffs;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * La resta de pistes del projecte, per fer-les sonar mentre s'edita una finestra al drumpad o al piano roll:
 * la barreja de tot el que sona a l'Estudi (respectant mut, solo i faders) durant el tros que ocupa la finestra,
 * sense la finestra que s'està editant. El so propi s'hi suma amb el fader i el volum del seu clip.
 * No depèn d'Android: es pot provar amb Java d'escriptori.
 */
public final class Acompanyament {

    private final Projecte projecte;
    private final Map<String, short[]> audio = new HashMap<>();
    private final String wav;
    private final long posMs;
    private double guanyPropi = 1;
    private boolean hiHaAltres = false;
    private float[] resta;   // barreja de la resta per a la llargada actual (es refà si canvia)

    private Acompanyament(Projecte p, String wav, long posMs) {
        this.projecte = p;
        this.wav = wav;
        this.posMs = posMs;
    }

    /**
     * Llegeix el projecte de la carpeta i els sons que sonen entre {@code posMs} i {@code posMs + finsMs}.
     * Torna null si no hi ha projecte. (Pot trigar una mica: millor fora del fil de la pantalla.)
     */
    public static Acompanyament obre(File carpeta, String wav, long posMs, long finsMs) {
        if (!new File(carpeta, Projecte.FITXER).isFile()) return null;
        Projecte p = Projecte.obre(carpeta);
        if (p == null) return null;
        Acompanyament a = new Acompanyament(p, wav, posMs);
        boolean solo = p.hiHaSolo();
        for (Projecte.Pista t : p.pistes) {
            for (Projecte.Clip k : t.clips) {
                if (k.wav.equals(wav)) {
                    // el fader de la pista i el volum del clip, perquè el balanç sigui el de l'Estudi
                    if (k.posMs == posMs) {
                        double g = Mesclador.lineal(t.volumDb) * Mesclador.lineal(k.guanyDb);
                        a.guanyPropi = g > 0 ? g : 1;
                    }
                    continue;
                }
                if (t.mut || (solo && !t.solo) || Mesclador.lineal(t.volumDb) <= 0) continue;
                if (k.fiVisible() <= posMs || k.iniciVisible() >= posMs + finsMs) continue;
                if (!a.audio.containsKey(k.wav)) {
                    short[] so = Wav.llegeixMostres(new File(carpeta, k.wav));
                    if (so != null) a.audio.put(k.wav, so);
                }
                if (a.audio.containsKey(k.wav)) a.hiHaAltres = true;
            }
        }
        return a;
    }

    /** El fader de la pista i el volum del clip que s'edita. */
    public double guanyPropi() {
        return guanyPropi;
    }

    /** Si hi ha alguna altra pista que soni durant la finestra. */
    public boolean hiHaAltres() {
        return hiHaAltres;
    }

    /** La resta de pistes durant {@code n} mostres des de l'inici de la finestra. */
    public synchronized float[] resta(int n) {
        if (resta == null || resta.length != n) {
            float[] r = new float[n];
            Mesclador.mescla(projecte, audio, Mesclador.msAMostra(posMs), r, n, null, wav);
            resta = r;
        }
        return resta;
    }

    /** El so propi barrejat amb la resta (si {@code solo}, només el propi, tal com és). */
    public short[] barreja(short[] propi, boolean solo) {
        if (solo || !hiHaAltres || propi == null) return propi;
        float[] r = resta(propi.length);
        float[] m = new float[propi.length];
        for (int i = 0; i < m.length; i++) m[i] = (float) (propi[i] / 32768.0 * guanyPropi) + r[i];
        short[] out = new short[m.length];
        Mesclador.a16bits(m, out, m.length);
        return out;
    }
}
