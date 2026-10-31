package cat.riffs;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

/**
 * El bucle de l'editor de so (2.32): un fil que va fent la frase en directe ({@link GeneradorBucle}) i la barreja
 * amb la resta de pistes (si el Solo està apagat). Els canvis dels botons se senten de seguida.
 */
final class BucleSo {

    private static final int TROS = 512;

    private final GeneradorBucle gen;
    private final BotoSolo solo;
    private AudioTrack track;
    private Thread fil;
    private volatile boolean viu;
    private volatile int posEscrita;     // on és el generador (mostra de la frase)
    private long escrites = 0;           // mostres escrites des de l'inici

    BucleSo(Melodia m, double bpm, int compas, int pos, BotoSolo solo) {
        gen = new GeneradorBucle(m, bpm, compas, pos);
        this.solo = solo;
    }

    boolean comenca() {
        int min = AudioTrack.getMinBufferSize(Sinte.MOSTREIG, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) return false;
        try {
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(Sinte.MOSTREIG)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    // prou marge perquè no s'entrebanqui, però que els botons responguin de pressa (~0,1 s)
                    .setBufferSizeInBytes(Math.max(min, TROS * 2 * 6))
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
        viu = true;
        track.play();
        fil = new Thread(this::bucle, "bucle-so");
        fil.setPriority(Thread.MAX_PRIORITY);
        fil.start();
        return true;
    }

    void canviat() {
        gen.canviat();
    }

    void posaMelodia(Melodia m) {
        gen.posaMelodia(m);
    }

    /** La mostra de la frase que se sent ara (aprox.: la que s'ha escrit menys el que encara no ha sortit). */
    int posicio() {
        int n = Math.max(1, gen.llargada());
        try {
            long sonat = track == null ? escrites : track.getPlaybackHeadPosition() & 0xFFFFFFFFL;
            long falta = Math.max(0, escrites - sonat);
            return (int) Math.floorMod(posEscrita - falta, (long) n);
        } catch (RuntimeException e) {
            return posEscrita;
        }
    }

    private void bucle() {
        float[] x = new float[TROS];
        short[] tros = new short[TROS];
        while (viu) {
            float[] resta = solo.resta(gen.llargada());   // null amb el Solo encès (la barreja es guarda: no costa)
            int p0 = gen.posicio();
            gen.omple(x, TROS);
            float gp = (float) solo.guanyPropi();
            for (int i = 0; i < TROS; i++) {
                float v = x[i] * gp;
                if (resta != null && resta.length > 0) v += resta[(p0 + i) % resta.length];
                float av = Math.abs(v);
                if (av > 0.9f) v = Math.signum(v) * (0.9f + 0.1f * (float) Math.tanh((av - 0.9f) / 0.1f));
                tros[i] = (short) Math.round(v * 32767f);
            }
            int w;
            try {
                w = track.write(tros, 0, TROS);
            } catch (RuntimeException ex) {
                break;
            }
            if (w <= 0) break;
            escrites += w;
            posEscrita = gen.posicio();
        }
    }

    void atura() {
        viu = false;
        if (fil != null) {
            try {
                fil.join(400);
            } catch (InterruptedException ignorat) {
            }
            fil = null;
        }
        if (track != null) {
            try {
                track.stop();
            } catch (RuntimeException ignorat) {
            }
            track.release();
            track = null;
        }
    }
}
