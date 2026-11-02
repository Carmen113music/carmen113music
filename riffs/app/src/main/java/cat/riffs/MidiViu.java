package cat.riffs;


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
    private SortidaViva sortida;
    private Thread fil;
    private volatile boolean viu;

    MidiViu(Banc banc, int bancMidi, int programa, double guanyPista) {
        this.banc = banc;
        this.prog = banc == null ? null : banc.programa(bancMidi, programa);
        this.guany = (float) (guanyPista > 0 ? guanyPista : 1);
    }

    boolean comenca() {
        if (prog == null) return false;
        sortida = new SortidaViva(MidiSo.MOSTREIG);   // 2.38.3: via ràpida i cua curta
        if (!sortida.obre()) return false;
        viu = true;
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
            if (!sortida.escriu(tros, TROS)) break;
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
        if (sortida != null) {
            sortida.tanca();
            sortida = null;
        }
    }
}
