package cat.riffs;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Un instrument MIDI del banc sonant en directe (teclat virtual): polifònic, amb el mateix motor que fa el so de
 * les finestres (així sona igual després). Sortida de baixa latència.
 */
final class MidiViu {

    private static final int TROS = 128;

    private final Banc banc;
    private final Banc.Programa prog;
    private final float guany;
    private final ConcurrentLinkedQueue<int[]> cua = new ConcurrentLinkedQueue<>();   // {nota, vel}; vel 0 = apaga
    private AudioTrack track;
    private Thread fil;
    private volatile boolean viu;

    MidiViu(Banc banc, int bancMidi, int programa, double guanyPista) {
        this.banc = banc;
        this.prog = banc == null ? null : banc.programa(bancMidi, programa);
        this.guany = (float) (guanyPista > 0 ? guanyPista : 1);
    }

    boolean comenca() {
        if (prog == null) return false;
        int min = AudioTrack.getMinBufferSize(MidiSo.MOSTREIG, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) return false;
        try {
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(MidiSo.MOSTREIG)
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
        fil = new Thread(this::bucle, "midi-viu");
        fil.setPriority(Thread.MAX_PRIORITY);
        fil.start();
        return true;
    }

    void encen(int nota, int vel) {
        cua.add(new int[]{nota, Math.max(1, vel)});
    }

    void apaga(int nota) {
        cua.add(new int[]{nota, 0});
    }

    private void bucle() {
        MidiSo.Motor m = new MidiSo.Motor(banc, prog);
        float[] mescla = new float[TROS];
        short[] tros = new short[TROS];
        while (viu) {
            int[] e;
            while ((e = cua.poll()) != null) {
                if (e[1] > 0) m.encen(e[0], e[1]);
                else m.apaga(e[0]);
            }
            if (m.callat()) {
                java.util.Arrays.fill(tros, (short) 0);
            } else {
                java.util.Arrays.fill(mescla, 0f);
                m.suma(mescla, 0, TROS);
                for (int i = 0; i < TROS; i++) {
                    float v = mescla[i] * guany;
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
