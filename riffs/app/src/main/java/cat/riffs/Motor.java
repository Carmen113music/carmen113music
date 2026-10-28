package cat.riffs;

import android.media.AudioAttributes;
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

    public Motor(Projecte projecte, Map<String, short[]> audio) {
        this.projecte = projecte;
        this.audio = audio;
    }

    public boolean sona() {
        return sona;
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
        while (sona) {
            synchronized (projecte) {
                Mesclador.mescla(projecte, audio, mostraInici + mostresEscrites, mescla, TROS);
            }
            Mesclador.a16bits(mescla, tros, TROS);
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
