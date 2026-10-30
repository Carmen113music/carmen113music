package cat.riffs;

import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioTimestamp;
import android.media.AudioTrack;

import java.util.Map;

/**
 * Reprodueix la barreja d'un projecte en temps real. Les edicions es noten mentre sona
 * (cada tros es barreja amb l'estat actual del projecte).
 */
public final class Motor {

    private static final int MOSTREIG = Mesclador.MOSTREIG;
    private static final int TROS = 1024;

    private final Projecte projecte;
    private final Map<String, short[]> audio;
    private AudioTrack track;
    private Thread fil;
    private volatile boolean sona = false;
    private volatile long mostraInici;       // on comença a la línia de temps
    private volatile long mostresEscrites;
    private final AudioTimestamp marca = new AudioTimestamp();
    private boolean teMarca = false;
    private long nanoInici;

    // clic del metrònom (per gravar des de l'Estudi); només ha de sonar amb auriculars
    private volatile boolean clic = false;
    private volatile float volumClic = 1f;
    // silenci total (el motor continua corrent per marcar el temps, però no surt cap so)
    private volatile boolean silenci = false;
    private short[] clicFort, clicFluix;

    public Motor(Projecte projecte, Map<String, short[]> audio) {
        this.projecte = projecte;
        this.audio = audio;
    }

    public boolean sona() {
        return sona;
    }

    /** Silencia tot el que surt (pistes i clic). Per gravar sense auriculars: per l'altaveu es gravaria. */
    public void posaSilenci(boolean s) {
        silenci = s;
    }

    /** Volum del clic: 0 (res) a 1,5. */
    public void posaVolumClic(float v) {
        volumClic = Math.max(0f, Math.min(1.5f, v));
    }

    /** Fa sonar (o no) el clic del metrònom al tempo del projecte, barrejat amb les pistes. */
    public void posaClic(boolean actiu) {
        if (actiu && clicFort == null) {
            clicFort = Ritme.clic(Latencia.FREQ_CLIC, 0.55, MOSTREIG);
            clicFluix = Ritme.clic(1175, 0.38, MOSTREIG);
        }
        clic = actiu;
    }

    /** Comença a sonar des de {@code ms} de la línia de temps. */
    public boolean comenca(long ms) {
        atura();
        int min = AudioTrack.getMinBufferSize(MOSTREIG, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) return false;
        try {
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(MOSTREIG)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(Math.max(min * 2, TROS * 8))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();
        } catch (RuntimeException e) {
            return false;
        }
        if (track.getState() != AudioTrack.STATE_INITIALIZED) {
            track.release();
            track = null;
            return false;
        }
        mostraInici = Math.max(0, Mesclador.msAMostra(ms));
        synchronized (this) {
            teMarca = false;
        }
        mostresEscrites = 0;
        teMarca = false;
        nanoInici = System.nanoTime();
        sona = true;
        track.play();
        fil = new Thread(this::bucle, "motor");
        fil.setPriority(Thread.MAX_PRIORITY);
        fil.start();
        return true;
    }

    private void bucle() {
        float[] mescla = new float[TROS];
        short[] tros = new short[TROS];
        short[] clics = new short[TROS];
        while (sona) {
            double mostresPerTemps;
            int compas;
            synchronized (projecte) {
                Mesclador.mescla(projecte, audio, mostraInici + mostresEscrites, mescla, TROS);
                mostresPerTemps = MOSTREIG * 60.0 / projecte.bpm;
                compas = projecte.compas;
            }
            if (clic) {
                java.util.Arrays.fill(clics, (short) 0);
                Ritme.ompleClics(clics, mostraInici + mostresEscrites, mostresPerTemps, compas, clicFort, clicFluix);
                float vc = volumClic / 32768f;
                for (int i = 0; i < TROS; i++) mescla[i] += clics[i] * vc;
            }
            Mesclador.a16bits(mescla, tros, TROS);
            if (silenci) java.util.Arrays.fill(tros, (short) 0);
            int w = track.write(tros, 0, TROS);
            if (w <= 0) break;
            mostresEscrites += w;
            if (!teMarca || mostresEscrites % (MOSTREIG / 2) < TROS) actualitzaMarca();
        }
    }

    private synchronized void actualitzaMarca() {
        try {
            if (track != null && track.getTimestamp(marca) && marca.nanoTime > 0) teMarca = true;
        } catch (IllegalStateException ignored) {
        }
    }

    /** Què s'està sentint ara mateix, en ms de la línia de temps. */
    public synchronized long posicioMs() {
        double mostra;
        long ara = System.nanoTime();
        if (teMarca) {
            mostra = marca.framePosition + (ara - marca.nanoTime) * MOSTREIG / 1e9;
        } else {
            mostra = (ara - nanoInici) * MOSTREIG / 1e9 - MOSTREIG * 0.08;
        }
        mostra = Math.max(0, Math.min(mostra, mostresEscrites));
        return Mesclador.mostraAMs(mostraInici + (long) mostra);
    }

    /**
     * Moment (System.nanoTime) en què surt per l'altaveu o els auriculars el moment {@code ms}
     * de la línia de temps (si sona des d'abans d'aquest moment).
     */
    public synchronized long nanoDeLinia(long ms) {
        long mostra = Mesclador.msAMostra(ms) - mostraInici;   // dins el que ha sonat
        if (teMarca) {
            return marca.nanoTime + Math.round((mostra - marca.framePosition) * 1e9 / MOSTREIG);
        }
        return nanoInici + 80_000_000L + Math.round(mostra * 1e9 / MOSTREIG);
    }

    /** Per on surt el so: «altaveu», «bluetooth», «cable» o «altre» (null si encara no se sap). */
    public synchronized String tipusSortida() {
        AudioDeviceInfo d = track != null ? track.getRoutedDevice() : null;
        return d == null ? null : Metronom.tipus(d);
    }

    /** Nom del dispositiu de sortida (per exemple el dels auriculars), si el mòbil el diu. */
    public synchronized String nomSortida() {
        AudioDeviceInfo d = track != null ? track.getRoutedDevice() : null;
        if (d == null || d.getProductName() == null) return "";
        return d.getProductName().toString();
    }

    public void atura() {
        sona = false;
        if (fil != null) {
            try {
                fil.join(500);
            } catch (InterruptedException ignored) {
            }
            fil = null;
        }
        if (track != null) {
            try {
                track.pause();
                track.flush();
                track.stop();
            } catch (IllegalStateException ignored) {
            }
            track.release();
            track = null;
        }
    }
}
