package cat.riffs;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * El sinte sonant en directe (per al teclat virtual): un fil que va fent so a trossets petits amb la mateixa
 * veu que fa servir el piano roll, perquè soni igual que després. Sortida de baixa latència.
 */
final class SinteViu {

    private static final int TROS = 128;

    private final Sinte.So so;
    private final float guany;
    private final ConcurrentLinkedQueue<int[]> cua = new ConcurrentLinkedQueue<>();  // {nota, lligada}; nota < 0 = deixa
    private AudioTrack track;
    private Thread fil;
    private volatile boolean viu;

    /** {@code guanyPista}: el fader de la pista (1 = 0 dB). */
    SinteViu(Sinte.So so, double guanyPista) {
        this.so = so;
        this.guany = (float) (0.7 * Math.pow(10, so.volumDb / 20) * (guanyPista > 0 ? guanyPista : 1));
    }

    boolean comenca() {
        int min = AudioTrack.getMinBufferSize(Sinte.MOSTREIG, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) return false;
        try {
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(Sinte.MOSTREIG)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(Math.max(min, TROS * 4))
                    .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
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
        fil = new Thread(this::bucle, "sinte-viu");
        fil.setPriority(Thread.MAX_PRIORITY);
        fil.start();
        return true;
    }

    /** Toca una nota ({@code lligada} = l'anterior encara es manté premuda: legato). */
    void nota(int nota, boolean lligada) {
        cua.add(new int[]{nota, lligada ? 1 : 0});
    }

    void deixa() {
        cua.add(new int[]{-1, 0});
    }

    private void bucle() {
        Sinte.Veu veu = new Sinte.Veu(so);
        short[] tros = new short[TROS];
        while (viu) {
            int[] e;
            while ((e = cua.poll()) != null) {
                if (e[0] < 0) veu.deixa();
                else veu.toca(e[0], 2, e[1] == 1);
            }
            if (veu.callada()) {
                java.util.Arrays.fill(tros, (short) 0);
            } else {
                for (int i = 0; i < TROS; i++) {
                    float v = veu.mostra() * guany;
                    float av = Math.abs(v);
                    if (av > 0.9f) v = Math.signum(v) * (0.9f + 0.1f * (float) Math.tanh((av - 0.9f) / 0.1f));
                    tros[i] = (short) Math.round(v * 32767f);
                }
            }
            int w;
            try {
                w = track.write(tros, 0, TROS);
            } catch (RuntimeException ex) {
                break;
            }
            if (w <= 0) break;
        }
    }

    void atura() {
        viu = false;
        if (fil != null) {
            try {
                fil.join(300);
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
