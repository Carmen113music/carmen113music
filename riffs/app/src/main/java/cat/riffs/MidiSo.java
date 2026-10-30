package cat.riffs;

import java.util.ArrayList;
import java.util.List;

/**
 * Fa sonar un instrument d'un banc SF2, polifònic: cada nota obre una o més veus (una per regió del banc que hi
 * toca). El fan servir tant el piano roll (per fer el so d'una finestra) com el teclat en directe, perquè sonin igual.
 * No depèn d'Android.
 */
public final class MidiSo {

    public static final int MOSTREIG = 44100;
    /** Guany general, perquè diverses notes juntes no saturin de seguida. */
    static final float GUANY = 0.55f;
    public static final int MAX_VEUS = 48;

    /** Força del piano roll (1–3) → velocitat MIDI. */
    public static int velocitat(int forca) {
        return forca <= 1 ? 62 : forca == 2 ? 96 : 122;
    }

    private static double segons(int timecents) {
        if (timecents <= -12000) return 0;
        return Math.pow(2, timecents / 1200.0);
    }

    private static double hzDeCents(int cents) {
        return 8.176 * Math.pow(2, cents / 1200.0);
    }

    /** Una veu: una mostra que sona a una nota, amb la seva envolupant, filtre i vibrato. */
    static final class Veu {
        final int nota;
        final long ordre;
        final short[] d;
        double pos, inc;
        final int fi, bInici, bFi, mode;
        boolean deixada = false, morta = false;
        final double guany;
        // envolupant del volum (en cB d'atenuació, com a l'SF2)
        int etapa = 0;              // 0 retard, 1 atac, 2 aguant, 3 caiguda, 4 sosteniment, 5 deixar anar
        long compte;
        final long nRetard, nAtac, nAguant;
        final double pasCaiguda, pasDeixa, sostCb;
        double amp = 0, cb = 960;
        // envolupant de modulació (0..1) per al filtre i el to
        int etapaM = 0;
        long compteM;
        final long mRetard, mAtac, mAguant;
        final double mPasCaiguda, mPasDeixa, mSost;
        double mod = 0;
        final int modFc, modTo;
        // filtre
        final boolean filtre;
        final double fcCents, q;
        double b0, b1, b2, a1, a2, x1, x2, y1, y2;
        int recalcula = 0;
        // vibrato
        final double vibInc, vibCents;
        final long vibRetard;
        double vibFase;
        long t = 0;

        Veu(Banc b, Banc.Regio r, int nota, int vel, long ordre) {
            this.nota = nota;
            this.ordre = ordre;
            int[] g = r.gen;
            Banc.Mostra m = r.mostra;
            d = b.dades;
            int inici = m.inici + g[Banc.G_START] + 32768 * g[Banc.G_START_COARSE];
            fi = Math.min(d.length - 2, m.fi + g[Banc.G_END] + 32768 * g[Banc.G_END_COARSE]);
            bInici = m.bucleInici + g[Banc.G_LOOP_START] + 32768 * g[Banc.G_LOOP_START_COARSE];
            bFi = Math.min(fi, m.bucleFi + g[Banc.G_LOOP_END] + 32768 * g[Banc.G_LOOP_END_COARSE]);
            int md = g[Banc.G_SAMPLE_MODES] & 3;
            mode = bFi - bInici > 8 ? md : 0;
            pos = Math.max(0, inici);
            int arrel = g[Banc.G_ROOT_KEY] >= 0 ? g[Banc.G_ROOT_KEY] : m.notaOriginal;
            int clau = g[Banc.G_KEYNUM] >= 0 ? g[Banc.G_KEYNUM] : nota;
            double cents = (clau - arrel) * g[Banc.G_SCALE_TUNING] + g[Banc.G_COARSE_TUNE] * 100 + g[Banc.G_FINE_TUNE] + m.correccio;
            inc = Math.pow(2, cents / 1200.0) * Math.max(1, m.freq) / MOSTREIG;
            // volum: atenuació del banc (amb el factor 0,4 que fan servir els reproductors per sonar com els de l'SF2)
            // i la força (corba còncava, com el modulador per defecte)
            int v = g[Banc.G_VELOCITY] >= 0 ? g[Banc.G_VELOCITY] : vel;
            double atVel = v >= 127 ? 0 : -200 * Math.log10(Math.max(1, v * v) / (127.0 * 127.0));
            double at = Math.max(0, g[Banc.G_ATTENUATION]) * 0.4 + Math.min(960, atVel * 0.5);
            guany = Math.pow(10, -at / 200.0);
            // envolupant del volum
            nRetard = mostres(g[Banc.G_DELAY_VOLENV]);
            nAtac = mostres(g[Banc.G_ATTACK_VOLENV]);
            nAguant = mostres(g[Banc.G_HOLD_VOLENV] + g[Banc.G_KEY_VOLENV_HOLD] * (60 - nota));
            double tCaiguda = segons(g[Banc.G_DECAY_VOLENV] + g[Banc.G_KEY_VOLENV_DECAY] * (60 - nota));
            double tDeixa = segons(g[Banc.G_RELEASE_VOLENV]);
            pasCaiguda = 1000.0 / Math.max(1, tCaiguda * MOSTREIG);   // 1000 cB (100 dB) en el temps de caiguda
            pasDeixa = 1000.0 / Math.max(1, Math.max(0.01, tDeixa) * MOSTREIG);
            sostCb = Math.max(0, Math.min(1440, g[Banc.G_SUSTAIN_VOLENV]));
            // envolupant de modulació
            mRetard = mostres(g[Banc.G_DELAY_MODENV]);
            mAtac = mostres(g[Banc.G_ATTACK_MODENV]);
            mAguant = mostres(g[Banc.G_HOLD_MODENV] + g[Banc.G_KEY_MODENV_HOLD] * (60 - nota));
            mPasCaiguda = 1.0 / Math.max(1, segons(g[Banc.G_DECAY_MODENV] + g[Banc.G_KEY_MODENV_DECAY] * (60 - nota)) * MOSTREIG);
            mPasDeixa = 1.0 / Math.max(1, Math.max(0.005, segons(g[Banc.G_RELEASE_MODENV])) * MOSTREIG);
            mSost = 1 - Math.max(0, Math.min(1000, g[Banc.G_SUSTAIN_MODENV])) / 1000.0;
            modFc = g[Banc.G_MODENV_FC];
            modTo = g[Banc.G_MODENV_PITCH];
            // filtre (si està obert del tot i res no el mou, no cal)
            int fcVel = v < 127 ? (int) (-2400 * (1 - v / 127.0) * 0.5) : 0;   // tocar fluix és més fosc
            fcCents = g[Banc.G_FC] + (g[Banc.G_FC] < 13500 ? fcVel : 0);
            q = Math.pow(10, Math.max(0, g[Banc.G_Q]) / 200.0);
            filtre = g[Banc.G_FC] < 13500 || modFc != 0;
            // vibrato
            vibCents = g[Banc.G_VIBLFO_PITCH] + g[Banc.G_MODLFO_PITCH];
            double fv = hzDeCents(g[Banc.G_VIBLFO_PITCH] != 0 ? g[Banc.G_FREQ_VIBLFO] : g[Banc.G_FREQ_MODLFO]);
            vibInc = fv / MOSTREIG;
            vibRetard = mostres(g[Banc.G_VIBLFO_PITCH] != 0 ? g[Banc.G_DELAY_VIBLFO] : g[Banc.G_DELAY_MODLFO]);
        }

        private static long mostres(int tc) {
            return Math.round(segons(tc) * MOSTREIG);
        }

        void deixa() {
            if (!deixada) {
                deixada = true;
                if (etapa < 5) {
                    // continua des del volum d'ara
                    if (etapa <= 2) cb = amp <= 1e-5 ? 960 : -200 * Math.log10(amp);
                    etapa = 5;
                }
                etapaM = 5;
            }
        }

        private void calculaFiltre(double cents) {
            double fc = Math.max(20, Math.min(MOSTREIG * 0.45, hzDeCents((int) cents)));
            double w = 2 * Math.PI * fc / MOSTREIG;
            double cs = Math.cos(w), sn = Math.sin(w);
            double alfa = sn / (2 * Math.max(0.5, q));
            double a0 = 1 + alfa;
            b0 = (1 - cs) / 2 / a0;
            b1 = (1 - cs) / a0;
            b2 = b0;
            a1 = -2 * cs / a0;
            a2 = (1 - alfa) / a0;
        }

        /** Suma {@code n} mostres d'aquesta veu a {@code out} des de {@code off}. */
        void suma(float[] out, int off, int n) {
            for (int i = 0; i < n && !morta; i++) {
                // envolupant del volum
                switch (etapa) {
                    case 0:
                        if (compte++ >= nRetard) {
                            etapa = 1;
                            compte = 0;
                        }
                        amp = 0;
                        break;
                    case 1:
                        amp = nAtac <= 0 ? 1 : Math.min(1, (double) compte / nAtac);
                        if (compte++ >= nAtac) {
                            etapa = 2;
                            compte = 0;
                            amp = 1;
                        }
                        break;
                    case 2:
                        amp = 1;
                        if (compte++ >= nAguant) {
                            etapa = 3;
                            cb = 0;
                        }
                        break;
                    case 3:
                        cb += pasCaiguda;
                        if (cb >= sostCb) {
                            cb = sostCb;
                            etapa = 4;
                        }
                        amp = Math.pow(10, -cb / 200.0);
                        break;
                    case 4:
                        amp = Math.pow(10, -cb / 200.0);
                        break;
                    default:
                        cb += pasDeixa;
                        if (cb >= 960) {
                            morta = true;
                            return;
                        }
                        amp = Math.pow(10, -cb / 200.0);
                        break;
                }
                // envolupant de modulació
                if (modFc != 0 || modTo != 0) {
                    switch (etapaM) {
                        case 0:
                            if (compteM++ >= mRetard) {
                                etapaM = 1;
                                compteM = 0;
                            }
                            break;
                        case 1:
                            mod = mAtac <= 0 ? 1 : Math.min(1, (double) compteM / mAtac);
                            if (compteM++ >= mAtac) {
                                etapaM = 2;
                                compteM = 0;
                            }
                            break;
                        case 2:
                            mod = 1;
                            if (compteM++ >= mAguant) etapaM = 3;
                            break;
                        case 3:
                            mod -= mPasCaiguda;
                            if (mod <= mSost) {
                                mod = mSost;
                                etapaM = 4;
                            }
                            break;
                        case 4:
                            break;
                        default:
                            mod = Math.max(0, mod - mPasDeixa);
                            break;
                    }
                }
                // to (vibrato i envolupant)
                double pas = inc;
                if (vibCents != 0 && t >= vibRetard) {
                    vibFase += vibInc;
                    if (vibFase >= 1) vibFase -= 1;
                    double tri = vibFase < 0.5 ? 4 * vibFase - 1 : 3 - 4 * vibFase;
                    pas *= Math.pow(2, vibCents * tri / 1200.0);
                }
                if (modTo != 0) pas *= Math.pow(2, modTo * mod / 1200.0);
                t++;
                // la mostra (interpolada)
                int k = (int) pos;
                if (k + 1 >= d.length || k >= fi) {
                    morta = true;
                    return;
                }
                double f = pos - k;
                double x = (d[k] + (d[k + 1] - d[k]) * f) / 32768.0;
                pos += pas;
                boolean enBucle = mode == 1 || (mode == 3 && !deixada);
                if (enBucle && pos >= bFi) pos -= (bFi - bInici);
                // filtre
                if (filtre) {
                    if (recalcula-- <= 0) {
                        calculaFiltre(fcCents + modFc * mod);
                        recalcula = 32;
                    }
                    double y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
                    x2 = x1;
                    x1 = x;
                    y2 = y1;
                    y1 = y;
                    x = y;
                }
                out[off + i] += (float) (x * amp * guany * GUANY);
            }
        }
    }

    /** Totes les notes que sonen d'un instrument (polifònic). */
    public static final class Motor {
        private final Banc banc;
        private final Banc.Programa prog;
        private final List<Veu> veus = new ArrayList<>();
        private long ordre = 0;

        public Motor(Banc banc, Banc.Programa prog) {
            this.banc = banc;
            this.prog = prog;
        }

        public void encen(int nota, int vel) {
            if (banc == null || prog == null) return;
            for (Banc.Regio r : prog.per(nota, vel)) {
                if (veus.size() >= MAX_VEUS) treuUna();
                veus.add(new Veu(banc, r, nota, vel, ordre++));
            }
        }

        public void apaga(int nota) {
            for (Veu v : veus) if (v.nota == nota && !v.deixada) v.deixa();
        }

        public void apagaTot() {
            for (Veu v : veus) v.deixa();
        }

        /** Massa veus: es treu la més vella (millor si ja l'han deixat anar). */
        private void treuUna() {
            Veu pitjor = null;
            for (Veu v : veus) {
                if (pitjor == null || (v.deixada && !pitjor.deixada) || (v.deixada == pitjor.deixada && v.ordre < pitjor.ordre)) pitjor = v;
            }
            veus.remove(pitjor);
        }

        public boolean callat() {
            return veus.isEmpty();
        }

        /** Suma {@code n} mostres de totes les veus a {@code out} des de {@code off}. */
        public void suma(float[] out, int off, int n) {
            for (int i = veus.size() - 1; i >= 0; i--) {
                Veu v = veus.get(i);
                v.suma(out, off, n);
                if (v.morta) veus.remove(i);
            }
        }
    }

    /**
     * Toca unes notes (en mostres; poden sonar juntes) amb un instrument del banc, en un so de {@code n} mostres.
     * Si {@code roda}, la cua que passa del final torna a entrar pel principi.
     */
    public static float[] toca(Banc banc, int bancMidi, int programa, List<Sinte.Toc> notes, int n, boolean roda) {
        int cua = MOSTREIG * 3;
        float[] out = new float[n + cua];
        Banc.Programa prog = banc == null ? null : banc.programa(bancMidi, programa);
        if (prog == null || notes.isEmpty()) return roda ? new float[n] : out;
        // esdeveniments en ordre: {mostra, 1 = encén / 0 = apaga, nota, vel}
        List<int[]> ev = new ArrayList<>();
        for (Sinte.Toc t : notes) {
            ev.add(new int[]{t.inici, 1, t.nota, velocitat(t.forca)});
            ev.add(new int[]{t.fi, 0, t.nota, 0});
        }
        // abans s'apaga i després s'encén (dues notes iguals seguides)
        ev.sort((a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]));
        Motor m = new Motor(banc, prog);
        int pos = 0, k = 0;
        int total = out.length;
        while (pos < total) {
            while (k < ev.size() && ev.get(k)[0] <= pos) {
                int[] e = ev.get(k++);
                if (e[1] == 1) m.encen(e[2], e[3]);
                else m.apaga(e[2]);
            }
            int seg = k < ev.size() ? Math.min(total, ev.get(k)[0]) : total;
            if (seg <= pos) seg = pos + 1;
            if (m.callat() && k >= ev.size()) break;
            m.suma(out, pos, seg - pos);
            pos = seg;
        }
        if (!roda) return out;
        float[] r = new float[n];
        System.arraycopy(out, 0, r, 0, n);
        for (int i = n; i < total; i++) r[(i - n) % n] += out[i];
        return r;
    }
}
