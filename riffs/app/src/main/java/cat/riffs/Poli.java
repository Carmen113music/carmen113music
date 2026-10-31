package cat.riffs;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Sinte polifònic («Poli», 2.31): fins a {@link #VEUS} notes alhora, cadascuna amb la mateixa veu que el sinte
 * monofònic ({@link Sinte.Veu}), i els efectes ({@link Efectes}) a la sortida. Té 50 sons de fàbrica per famílies
 * (pads, cordes, tecles, metalls, plucks i moderns). No depèn d'Android.
 */
public final class Poli {

    public static final int VEUS = 10;
    /** Guany de la suma de veus (perquè un acord de 3–4 notes soni com una nota del sinte monofònic). */
    static final float GUANY = 0.5f;

    // ---------- Motor ----------

    /** Les veus sonant. Es fa servir per al so de les finestres i per tocar en directe. */
    public static final class Motor {
        private final Sinte.So so;
        private final Sinte.Veu[] veus = new Sinte.Veu[VEUS];
        private final int[] nota = new int[VEUS];
        private final boolean[] premuda = new boolean[VEUS];
        private final long[] ordre = new long[VEUS];
        private long compte = 0;
        private final Efectes fx;

        public Motor(Sinte.So so, double bpm) {
            this.so = so;
            for (int i = 0; i < VEUS; i++) {
                veus[i] = new Sinte.Veu(so);
                nota[i] = -1;
            }
            fx = so.ambEfectes() ? new Efectes(so, bpm) : null;
        }

        /** Comença una nota (si ja sonava, la torna a atacar a la mateixa veu). */
        public void encen(int n, int forca) {
            int v = -1;
            for (int i = 0; i < VEUS; i++) if (nota[i] == n && !veus[i].callada()) v = i;
            if (v < 0) for (int i = 0; i < VEUS && v < 0; i++) if (veus[i].callada()) v = i;
            if (v < 0) {
                // totes ocupades: la més vella que ja s'ha deixat anar, o si no la més vella de totes
                long millor = Long.MAX_VALUE;
                for (int i = 0; i < VEUS; i++) if (!premuda[i] && ordre[i] < millor) {
                    millor = ordre[i];
                    v = i;
                }
                if (v < 0) for (int i = 0; i < VEUS; i++) if (ordre[i] < millor) {
                    millor = ordre[i];
                    v = i;
                }
            }
            veus[v].toca(n, forca, false);
            nota[v] = n;
            premuda[v] = true;
            ordre[v] = ++compte;
        }

        public void apaga(int n) {
            for (int i = 0; i < VEUS; i++) {
                if (nota[i] == n && premuda[i]) {
                    veus[i].deixa();
                    premuda[i] = false;
                }
            }
        }

        public void apagaTot() {
            for (int i = 0; i < VEUS; i++) if (premuda[i]) {
                veus[i].deixa();
                premuda[i] = false;
            }
        }

        /** Si no sona cap veu (els efectes encara poden tenir cua). */
        public boolean callat() {
            for (Sinte.Veu v : veus) if (!v.callada()) return false;
            return true;
        }

        public boolean ambEfectes() {
            return fx != null;
        }

        /** La mostra següent (abans del volum del so). */
        public float mostra() {
            float s = 0;
            for (Sinte.Veu v : veus) if (!v.callada()) s += v.mostra();
            s *= GUANY;
            return fx != null ? fx.processa(s) : s;
        }
    }

    /**
     * Toca les notes (poden trepitjar-se) en un so de {@code n} mostres. Si {@code roda}, la cua que passa del final
     * torna a entrar pel principi (finestres seguides sonen contínues).
     */
    public static float[] toca(Sinte.So so, List<Sinte.Toc> tocs, int n, boolean roda, double bpm) {
        int cua = (int) (so.cuaMostres() + (so.ambEfectes() ? so.cuaEfectes(bpm) : 0));
        int total = n + cua;
        float[] out = new float[total];
        if (tocs.isEmpty()) return roda ? new float[n] : out;
        // esdeveniments ordenats: {mostra, 0 = apaga / 1 = encén, nota, força}; els «apaga» van primer
        List<int[]> ev = new ArrayList<>();
        for (Sinte.Toc t : tocs) {
            ev.add(new int[]{t.inici, 1, t.nota, t.forca});
            ev.add(new int[]{t.fi, 0, t.nota, 0});
        }
        ev.sort((a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]));
        Motor m = new Motor(so, bpm);
        int k = 0, ultim = ev.get(ev.size() - 1)[0];
        for (int i = 0; i < total; i++) {
            while (k < ev.size() && ev.get(k)[0] <= i) {
                int[] e = ev.get(k++);
                if (e[1] == 1) m.encen(e[2], e[3]);
                else m.apaga(e[2]);
            }
            if (i > ultim && i >= n && m.callat() && !m.ambEfectes()) break;
            out[i] = m.mostra();
        }
        if (!roda) return out;
        float[] r = new float[n];
        System.arraycopy(out, 0, r, 0, n);
        for (int i = n; i < total; i++) r[(i - n) % n] += out[i];
        return r;
    }

    /** Un arpegi (Do, Mi, Sol, Do) i un acord de Do, a corxeres de {@code pas} mostres, per escoltar un so. */
    public static List<Sinte.Toc> tocsDeProva(int pas) {
        List<Sinte.Toc> t = new ArrayList<>();
        int[] ns = {60, 64, 67, 72};
        for (int i = 0; i < ns.length; i++) t.add(new Sinte.Toc(i * pas, i * pas + pas * 9 / 10, ns[i], 2));
        for (int n : new int[]{48, 60, 64, 67}) t.add(new Sinte.Toc(4 * pas, 8 * pas, n, 2));
        return t;
    }

    // ---------- Sons de fàbrica ----------

    public static final String[] FAMILIES = {"Pads", "Cordes i cors", "Tecles", "Metalls i stabs", "Plucks i arpegis", "Moderns"};

    /** Un so de fàbrica amb la seva família (índex de {@link #FAMILIES}). */
    public static final class Preset {
        public final Sinte.Preset preset;
        public final int familia;

        Preset(Sinte.Preset p, int familia) {
            this.preset = p;
            this.familia = familia;
        }
    }

    private static final List<Preset> LLISTA = new ArrayList<>();
    public static final Preset[] PRESETS = presets();

    /** Només els {@link Sinte.Preset} (en el mateix ordre). */
    public static Sinte.Preset[] sons() {
        Sinte.Preset[] r = new Sinte.Preset[PRESETS.length];
        for (int i = 0; i < r.length; i++) r[i] = PRESETS[i].preset;
        return r;
    }

    /** Els sons d'una família. */
    public static List<Sinte.Preset> deFamilia(int f) {
        List<Sinte.Preset> r = new ArrayList<>();
        for (Preset p : PRESETS) if (p.familia == f) r.add(p.preset);
        return r;
    }

    public static Sinte.Preset preset(String nom) {
        for (Preset p : PRESETS) if (p.preset.nom.equals(nom)) return p.preset;
        return null;
    }

    public static int familiaDe(String nom) {
        for (Preset p : PRESETS) if (p.preset.nom.equals(nom)) return p.familia;
        return -1;
    }

    /** Base comuna: dues ones, envolupant suau, sense glide. */
    private static Sinte.So base() {
        Sinte.So o = new Sinte.So();
        o.seguiment = 0.5;
        o.resso = 0.1;
        o.envFiltre = 0;
        o.fa = 0;
        o.fs = 1;
        o.a = 5;
        o.d = 400;
        o.s = 0.8;
        o.r = 300;
        return o;
    }

    private static void p(int familia, String nom, Consumer<Sinte.So> c) {
        Sinte.So o = base();
        c.accept(o);
        LLISTA.add(new Preset(new Sinte.Preset(nom, false, o), familia));
    }

    private static final int SE = Sinte.SERRA, QU = Sinte.QUADRADA, TR = Sinte.TRIANGLE, SI = Sinte.SINUS;

    private static Preset[] presets() {
        LLISTA.clear();
        // ----- Pads -----
        p(0, "Pad càlid", o -> {
            o.ona1 = SE; o.ona2 = SE; o.mescla2 = 0.5; o.cents2 = 9; o.unison = 2; o.desafina = 14;
            o.tallHz = 900; o.resso = 0.1; o.envFiltre = 0.8; o.fa = 700; o.fd = 1500; o.fs = 0.5; o.fr = 1200;
            o.a = 600; o.d = 1000; o.s = 0.9; o.r = 1200; o.chorus = 0.5; o.reverb = 0.35; o.reverbMida = 0.65; o.volumDb = 2;
        });
        p(0, "Pad analògic 80", o -> {   // Juno: quadrada amb PWM i chorus
            o.ona1 = QU; o.ona2 = SE; o.mescla2 = 0.3; o.pols = 0.35; o.lfoPols = 0.6; o.lfoHz = 0.7;
            o.tallHz = 1400; o.resso = 0.15; o.a = 350; o.d = 900; o.s = 0.85; o.r = 900;
            o.chorus = 0.8; o.chorusHz = 0.5; o.reverb = 0.3; o.volumDb = 0;
        });
        p(0, "Pad de vidre", o -> {
            o.ona1 = TR; o.ona2 = SI; o.mescla2 = 0.45; o.semitons2 = 12; o.cents2 = 5;
            o.tallHz = 4000; o.resso = 0.05; o.a = 450; o.d = 1200; o.s = 0.8; o.r = 1500;
            o.lfoHz = 4.5; o.lfoTo = 6; o.lfoRetardMs = 600; o.chorus = 0.4; o.delay = 0.25; o.delayTemps = 0.75;
            o.reverb = 0.5; o.reverbMida = 0.8; o.volumDb = 1;
        });
        p(0, "Pad fosc", o -> {
            o.ona1 = SE; o.ona2 = TR; o.mescla2 = 0.5; o.semitons2 = -12; o.sub = 0.2;
            o.tallHz = 380; o.resso = 0.2; o.envFiltre = 0.6; o.fa = 1500; o.fd = 2000; o.fs = 0.4; o.fr = 1500;
            o.a = 900; o.d = 1500; o.s = 0.9; o.r = 1600; o.lfoHz = 0.15; o.lfoFiltre = 0.3; o.reverb = 0.4; o.reverbMida = 0.75; o.volumDb = 2;
        });
        p(0, "Pad que respira", o -> {
            o.ona1 = SE; o.ona2 = SE; o.mescla2 = 0.5; o.cents2 = 14; o.unison = 2; o.desafina = 10;
            o.tallHz = 600; o.resso = 0.35; o.lfoHz = 0.25; o.lfoFiltre = 1.1;
            o.a = 500; o.d = 1000; o.s = 0.9; o.r = 1200; o.chorus = 0.4; o.reverb = 0.35; o.volumDb = 0;
        });
        p(0, "Pad sweep", o -> {
            o.ona1 = SE; o.ona2 = QU; o.mescla2 = 0.35; o.pols = 0.4; o.cents2 = 7;
            o.tallHz = 250; o.resso = 0.45; o.envFiltre = 3.5; o.fa = 2500; o.fd = 3000; o.fs = 0.3; o.fr = 2000;
            o.a = 800; o.d = 1200; o.s = 0.9; o.r = 1500; o.chorus = 0.5; o.reverb = 0.4; o.reverbMida = 0.7; o.volumDb = -1;
        });
        p(0, "Pad espacial", o -> {
            o.ona1 = SE; o.ona2 = TR; o.mescla2 = 0.4; o.semitons2 = 7; o.unison = 3; o.desafina = 18;
            o.tallHz = 1600; o.resso = 0.1; o.a = 1200; o.d = 2000; o.s = 0.85; o.r = 2500;
            o.chorus = 0.6; o.delay = 0.35; o.delayTemps = 1.5; o.delayRepeticio = 0.5; o.reverb = 0.6; o.reverbMida = 0.95; o.volumDb = -1;
        });
        p(0, "Pad d'aire", o -> {
            o.ona1 = SI; o.ona2 = TR; o.mescla2 = 0.4; o.semitons2 = 12; o.soroll = 0.35;
            o.tallHz = 1800; o.resso = 0.3; o.seguiment = 0.9; o.a = 900; o.d = 1500; o.s = 0.85; o.r = 1800;
            o.lfoHz = 0.3; o.lfoFiltre = 0.5; o.reverb = 0.55; o.reverbMida = 0.85; o.volumDb = 1;
        });
        p(0, "Pad de seda", o -> {
            o.ona1 = TR; o.ona2 = SE; o.mescla2 = 0.3; o.cents2 = 6; o.unison = 2; o.desafina = 8;
            o.tallHz = 1200; o.resso = 0.05; o.a = 700; o.d = 1000; o.s = 0.95; o.r = 1400;
            o.chorus = 0.7; o.chorusHz = 0.4; o.reverb = 0.4; o.volumDb = 1;
        });
        p(0, "Pad vell de cinta", o -> {
            o.ona1 = SE; o.ona2 = QU; o.mescla2 = 0.4; o.pols = 0.45; o.cents2 = 10;
            o.tallHz = 1100; o.resso = 0.1; o.lfoHz = 0.6; o.lfoTo = 9; o.soroll = 0.05;
            o.a = 400; o.d = 1000; o.s = 0.85; o.r = 1000; o.chorus = 0.5; o.reverb = 0.3; o.volumDb = 0;
        });
        // ----- Cordes i cors -----
        p(1, "Cordes Solina", o -> {
            o.ona1 = SE; o.ona2 = SE; o.mescla2 = 0.5; o.semitons2 = 12; o.cents2 = 6; o.unison = 2; o.desafina = 12;
            o.tallHz = 2400; o.resso = 0.05; o.a = 250; o.d = 800; o.s = 0.95; o.r = 700;
            o.chorus = 1; o.chorusHz = 0.9; o.reverb = 0.3; o.volumDb = -2;
        });
        p(1, "Cordes 80", o -> {
            o.ona1 = SE; o.ona2 = SE; o.mescla2 = 0.5; o.cents2 = 11; o.unison = 2; o.desafina = 10;
            o.tallHz = 1800; o.resso = 0.15; o.envFiltre = 0.6; o.fa = 300; o.fd = 600; o.fs = 0.6;
            o.a = 180; o.d = 700; o.s = 0.9; o.r = 600; o.lfoHz = 5; o.lfoTo = 5; o.lfoRetardMs = 500;
            o.chorus = 0.6; o.reverb = 0.35; o.volumDb = -1;
        });
        p(1, "Cordes lentes", o -> {
            o.ona1 = SE; o.ona2 = TR; o.mescla2 = 0.35; o.cents2 = 8; o.unison = 3; o.desafina = 12;
            o.tallHz = 1300; o.resso = 0.1; o.a = 900; o.d = 1200; o.s = 0.95; o.r = 1400;
            o.lfoHz = 5.2; o.lfoTo = 7; o.lfoRetardMs = 800; o.chorus = 0.4; o.reverb = 0.5; o.reverbMida = 0.8; o.volumDb = 0;
        });
        p(1, "Cor de sinte", o -> {   // filtre ressonant que fa de vocal
            o.ona1 = SE; o.ona2 = QU; o.mescla2 = 0.3; o.pols = 0.3; o.cents2 = 8; o.unison = 2; o.desafina = 10;
            o.tallHz = 750; o.resso = 0.55; o.seguiment = 0.3; o.soroll = 0.06;
            o.a = 300; o.d = 800; o.s = 0.9; o.r = 900; o.lfoHz = 4.8; o.lfoTo = 7; o.lfoRetardMs = 400;
            o.chorus = 0.6; o.reverb = 0.45; o.volumDb = 0;
        });
        p(1, "Conjunt de cordes", o -> {
            o.ona1 = SE; o.ona2 = SE; o.mescla2 = 0.45; o.semitons2 = -12; o.cents2 = 4; o.unison = 3; o.desafina = 16;
            o.tallHz = 2000; o.resso = 0.05; o.a = 120; o.d = 500; o.s = 0.9; o.r = 500;
            o.chorus = 0.5; o.reverb = 0.35; o.volumDb = -2;
        });
        p(1, "Pizzicato de sinte", o -> {
            o.ona1 = SE; o.ona2 = TR; o.mescla2 = 0.3; o.cents2 = 6;
            o.tallHz = 700; o.resso = 0.15; o.envFiltre = 2.2; o.fd = 160; o.fs = 0;
            o.a = 2; o.d = 260; o.s = 0; o.r = 200; o.reverb = 0.3; o.volumDb = 3;
        });
        // ----- Tecles -----
        p(2, "Piano elèctric", o -> {   // tipus Rhodes: sinus i triangle que s'apaguen, amb chorus
            o.ona1 = SI; o.ona2 = TR; o.mescla2 = 0.3; o.semitons2 = 12;
            o.tallHz = 1800; o.resso = 0; o.envFiltre = 1.2; o.fd = 400; o.fs = 0.2;
            o.a = 2; o.d = 1800; o.s = 0.15; o.r = 350; o.lfoHz = 4; o.lfoFiltre = 0.08;
            o.chorus = 0.45; o.chorusHz = 1.2; o.reverb = 0.2; o.volumDb = 2;
        });
        p(2, "Orgue de rodes", o -> {
            o.ona1 = SI; o.ona2 = SI; o.mescla2 = 0.45; o.semitons2 = 12; o.sub = 0.35;
            o.tallHz = 6000; o.resso = 0; o.a = 4; o.d = 100; o.s = 1; o.r = 60;
            o.lfoHz = 6.5; o.lfoTo = 4; o.chorus = 0.5; o.chorusHz = 6; o.reverb = 0.15; o.volumDb = -2;
        });
        p(2, "Orgue de transistors", o -> {   // tipus Farfisa
            o.ona1 = QU; o.ona2 = QU; o.mescla2 = 0.4; o.pols = 0.25; o.semitons2 = 12;
            o.tallHz = 3500; o.resso = 0.1; o.a = 3; o.d = 100; o.s = 1; o.r = 50;
            o.lfoHz = 6; o.lfoTo = 10; o.reverb = 0.15; o.volumDb = -6;
        });
        p(2, "Orgue d'església", o -> {
            o.ona1 = SI; o.ona2 = TR; o.mescla2 = 0.5; o.semitons2 = 19; o.sub = 0.4;
            o.tallHz = 5000; o.a = 60; o.d = 200; o.s = 1; o.r = 600;
            o.reverb = 0.55; o.reverbMida = 0.95; o.volumDb = -4;
        });
        p(2, "Clavinet de sinte", o -> {
            o.ona1 = QU; o.ona2 = SE; o.mescla2 = 0.2; o.pols = 0.12;
            o.tallHz = 900; o.resso = 0.35; o.envFiltre = 2.5; o.fd = 200; o.fs = 0.2;
            o.a = 1; o.d = 500; o.s = 0.3; o.r = 80; o.distorsio = 0.15; o.volumDb = -3;
        });
        p(2, "Clavecí de sinte", o -> {
            o.ona1 = SE; o.ona2 = QU; o.mescla2 = 0.4; o.pols = 0.2; o.semitons2 = 12;
            o.tallHz = 2500; o.resso = 0.2; o.envFiltre = 1.5; o.fd = 300; o.fs = 0;
            o.a = 1; o.d = 900; o.s = 0; o.r = 250; o.reverb = 0.25; o.volumDb = 2;
        });
        p(2, "Caixa de música", o -> {
            o.ona1 = SI; o.ona2 = SI; o.mescla2 = 0.35; o.semitons2 = 24;
            o.tallHz = 8000; o.a = 1; o.d = 900; o.s = 0; o.r = 900;
            o.delay = 0.2; o.delayTemps = 0.5; o.reverb = 0.4; o.reverbMida = 0.7; o.volumDb = 2;
        });
        p(2, "Vibràfon de sinte", o -> {
            o.ona1 = SI; o.ona2 = TR; o.mescla2 = 0.2; o.semitons2 = 24;
            o.tallHz = 5000; o.a = 2; o.d = 1500; o.s = 0; o.r = 1200; o.lfoHz = 5.5; o.lfoTo = 3;
            o.chorus = 0.5; o.chorusHz = 5; o.reverb = 0.35; o.volumDb = 2;
        });
        p(2, "Piano de joguina", o -> {
            o.ona1 = TR; o.ona2 = QU; o.mescla2 = 0.15; o.pols = 0.5; o.semitons2 = 19;
            o.tallHz = 3500; o.resso = 0.1; o.a = 1; o.d = 500; o.s = 0; o.r = 300; o.reverb = 0.2; o.volumDb = 0;
        });
        p(2, "Marimba de sinte", o -> {
            o.ona1 = SI; o.ona2 = TR; o.mescla2 = 0.25; o.semitons2 = 12;
            o.tallHz = 1500; o.envFiltre = 1.5; o.fd = 100; o.fs = 0; o.a = 1; o.d = 400; o.s = 0; o.r = 250;
            o.reverb = 0.25; o.volumDb = 1;
        });
        // ----- Metalls i stabs -----
        p(3, "Metalls 80", o -> {   // l'envolupant obre el filtre: el «uaa» dels sintes de metalls
            o.ona1 = SE; o.ona2 = SE; o.mescla2 = 0.5; o.cents2 = 10; o.unison = 2; o.desafina = 8;
            o.tallHz = 500; o.resso = 0.15; o.envFiltre = 2.8; o.fa = 80; o.fd = 500; o.fs = 0.45; o.fr = 300;
            o.a = 25; o.d = 500; o.s = 0.85; o.r = 300; o.chorus = 0.3; o.reverb = 0.3; o.volumDb = 0;
        });
        p(3, "Metalls suaus", o -> {
            o.ona1 = SE; o.ona2 = TR; o.mescla2 = 0.4; o.cents2 = 7;
            o.tallHz = 400; o.resso = 0.1; o.envFiltre = 2.2; o.fa = 220; o.fd = 700; o.fs = 0.5; o.fr = 400;
            o.a = 120; o.d = 700; o.s = 0.85; o.r = 400; o.lfoHz = 5; o.lfoTo = 6; o.lfoRetardMs = 400;
            o.chorus = 0.3; o.reverb = 0.35; o.volumDb = 1;
        });
        p(3, "Metalls grossos", o -> {
            o.ona1 = SE; o.ona2 = SE; o.mescla2 = 0.5; o.semitons2 = -12; o.cents2 = 6; o.unison = 3; o.desafina = 14;
            o.tallHz = 600; o.resso = 0.2; o.envFiltre = 3; o.fa = 40; o.fd = 400; o.fs = 0.5; o.fr = 250;
            o.a = 15; o.d = 400; o.s = 0.9; o.r = 250; o.distorsio = 0.15; o.reverb = 0.25; o.volumDb = -3;
        });
        p(3, "Stab d'orgue house", o -> {
            o.ona1 = SI; o.ona2 = QU; o.mescla2 = 0.35; o.pols = 0.5; o.semitons2 = 12; o.sub = 0.3;
            o.tallHz = 1800; o.resso = 0.2; o.envFiltre = 1.2; o.fd = 150; o.fs = 0.2;
            o.a = 1; o.d = 250; o.s = 0.2; o.r = 120; o.reverb = 0.2; o.volumDb = -1;
        });
        p(3, "Stab rave", o -> {
            o.ona1 = SE; o.ona2 = SE; o.mescla2 = 0.5; o.semitons2 = 7; o.unison = 3; o.desafina = 20;
            o.tallHz = 1400; o.resso = 0.3; o.envFiltre = 2; o.fd = 200; o.fs = 0.2;
            o.a = 1; o.d = 300; o.s = 0.25; o.r = 150; o.distorsio = 0.2; o.delay = 0.25; o.delayTemps = 0.75; o.reverb = 0.25; o.volumDb = 0;
        });
        p(3, "Acord de quintes", o -> {
            o.ona1 = SE; o.ona2 = QU; o.mescla2 = 0.45; o.pols = 0.4; o.semitons2 = 7;
            o.tallHz = 1200; o.resso = 0.25; o.envFiltre = 1.5; o.fd = 300; o.fs = 0.5;
            o.a = 2; o.d = 400; o.s = 0.7; o.r = 200; o.distorsio = 0.3; o.reverb = 0.2; o.volumDb = -4;
        });
        p(3, "Poli serra", o -> {
            o.ona1 = SE; o.ona2 = SE; o.mescla2 = 0.5; o.cents2 = 12;
            o.tallHz = 2200; o.resso = 0.2; o.envFiltre = 1; o.fd = 400; o.fs = 0.6;
            o.a = 3; o.d = 400; o.s = 0.85; o.r = 250; o.chorus = 0.3; o.reverb = 0.2; o.volumDb = -3;
        });
        // ----- Plucks i arpegis -----
        p(4, "Pluck brillant", o -> {
            o.ona1 = SE; o.ona2 = QU; o.mescla2 = 0.3; o.semitons2 = 12; o.cents2 = 7;
            o.tallHz = 700; o.resso = 0.3; o.envFiltre = 3.5; o.fd = 180; o.fs = 0;
            o.a = 1; o.d = 400; o.s = 0; o.r = 250; o.reverb = 0.25; o.volumDb = 2;
        });
        p(4, "Pluck de fusta", o -> {
            o.ona1 = TR; o.ona2 = SI; o.mescla2 = 0.3; o.semitons2 = 12; o.soroll = 0.08;
            o.tallHz = 900; o.resso = 0.25; o.envFiltre = 2; o.fd = 90; o.fs = 0;
            o.a = 1; o.d = 250; o.s = 0; o.r = 150; o.reverb = 0.2; o.volumDb = 3;
        });
        p(4, "Arpa de sinte", o -> {
            o.ona1 = TR; o.ona2 = SE; o.mescla2 = 0.2; o.semitons2 = 12;
            o.tallHz = 1500; o.resso = 0.1; o.envFiltre = 1.5; o.fd = 600; o.fs = 0;
            o.a = 1; o.d = 1400; o.s = 0; o.r = 900; o.chorus = 0.3; o.reverb = 0.45; o.reverbMida = 0.75; o.volumDb = 2;
        });
        p(4, "Pluck amb eco", o -> {
            o.ona1 = SE; o.ona2 = SE; o.mescla2 = 0.4; o.cents2 = 10;
            o.tallHz = 600; o.resso = 0.35; o.envFiltre = 3; o.fd = 150; o.fs = 0;
            o.a = 1; o.d = 350; o.s = 0; o.r = 200; o.delay = 0.5; o.delayTemps = 0.75; o.delayRepeticio = 0.45;
            o.reverb = 0.25; o.volumDb = 1;
        });
        p(4, "Kalimba de sinte", o -> {
            o.ona1 = SI; o.ona2 = TR; o.mescla2 = 0.3; o.semitons2 = 24; o.soroll = 0.03;
            o.tallHz = 3000; o.envFiltre = 1; o.fd = 60; o.fs = 0;
            o.a = 1; o.d = 600; o.s = 0; o.r = 500; o.reverb = 0.3; o.volumDb = 2;
        });
        p(4, "Arpegi 80", o -> {
            o.ona1 = QU; o.ona2 = SE; o.mescla2 = 0.3; o.pols = 0.3; o.cents2 = 8;
            o.tallHz = 800; o.resso = 0.4; o.envFiltre = 2.5; o.fd = 220; o.fs = 0.1;
            o.a = 1; o.d = 300; o.s = 0.1; o.r = 180; o.chorus = 0.4; o.delay = 0.3; o.delayTemps = 0.5; o.reverb = 0.2; o.volumDb = -1;
        });
        p(4, "Gota", o -> {
            o.ona1 = SI; o.ona2 = SI; o.mescla2 = 0.25; o.semitons2 = 12;
            o.tallHz = 500; o.resso = 0.7; o.envFiltre = 4; o.fd = 80; o.fs = 0;
            o.a = 1; o.d = 300; o.s = 0; o.r = 250; o.delay = 0.35; o.delayTemps = 0.75; o.reverb = 0.35; o.volumDb = 2;
        });
        p(4, "Campanetes", o -> {
            o.ona1 = SI; o.ona2 = SI; o.mescla2 = 0.4; o.semitons2 = 19; o.cents2 = 8;
            o.tallHz = 7000; o.a = 1; o.d = 1200; o.s = 0; o.r = 1200;
            o.chorus = 0.3; o.delay = 0.25; o.delayTemps = 1; o.reverb = 0.45; o.reverbMida = 0.8; o.volumDb = 2;
        });
        // ----- Moderns -----
        p(5, "Supersaw", o -> {
            o.ona1 = SE; o.ona2 = SE; o.mescla2 = 0.4; o.semitons2 = 12; o.unison = 7; o.desafina = 35;
            o.tallHz = 3500; o.resso = 0.1; o.a = 5; o.d = 500; o.s = 0.85; o.r = 400;
            o.chorus = 0.3; o.reverb = 0.35; o.volumDb = -2;
        });
        p(5, "Future bass", o -> {
            o.ona1 = SE; o.ona2 = SE; o.mescla2 = 0.4; o.cents2 = 10; o.unison = 5; o.desafina = 28;
            o.tallHz = 900; o.resso = 0.2; o.envFiltre = 2.5; o.fa = 150; o.fd = 600; o.fs = 0.5;
            o.a = 60; o.d = 600; o.s = 0.8; o.r = 350; o.lfoHz = 4; o.lfoTo = 4;
            o.chorus = 0.4; o.reverb = 0.4; o.volumDb = -2;
        });
        p(5, "Synthwave", o -> {
            o.ona1 = SE; o.ona2 = SE; o.mescla2 = 0.5; o.cents2 = 14; o.unison = 3; o.desafina = 16;
            o.tallHz = 1600; o.resso = 0.2; o.envFiltre = 1.2; o.fa = 20; o.fd = 600; o.fs = 0.5;
            o.a = 10; o.d = 600; o.s = 0.8; o.r = 600; o.chorus = 0.6; o.delay = 0.3; o.delayTemps = 0.75;
            o.reverb = 0.45; o.reverbMida = 0.8; o.volumDb = -2;
        });
        p(5, "Acord deep house", o -> {
            o.ona1 = SE; o.ona2 = QU; o.mescla2 = 0.4; o.pols = 0.4; o.cents2 = 8;
            o.tallHz = 450; o.resso = 0.35; o.envFiltre = 2; o.fd = 250; o.fs = 0.15;
            o.a = 2; o.d = 350; o.s = 0.2; o.r = 200; o.chorus = 0.3; o.delay = 0.3; o.delayTemps = 0.75; o.reverb = 0.35; o.volumDb = 6;
        });
        p(5, "Tecles lo-fi", o -> {   // una mica desafinades i amb soroll de cinta
            o.ona1 = TR; o.ona2 = SI; o.mescla2 = 0.4; o.semitons2 = 12; o.soroll = 0.05;
            o.tallHz = 1200; o.envFiltre = 0.8; o.fd = 400; o.fs = 0.3;
            o.a = 3; o.d = 1200; o.s = 0.2; o.r = 400; o.lfoHz = 0.9; o.lfoTo = 14;
            o.chorus = 0.4; o.reverb = 0.25; o.volumDb = 3;
        });
        p(5, "Pad lo-fi", o -> {
            o.ona1 = TR; o.ona2 = SE; o.mescla2 = 0.3; o.cents2 = 9; o.soroll = 0.12;
            o.tallHz = 900; o.resso = 0.1; o.a = 400; o.d = 1000; o.s = 0.9; o.r = 1000; o.lfoHz = 0.7; o.lfoTo = 10;
            o.chorus = 0.5; o.reverb = 0.4; o.volumDb = 1;
        });
        p(5, "Pluck tropical", o -> {
            o.ona1 = SI; o.ona2 = TR; o.mescla2 = 0.35; o.semitons2 = 12;
            o.tallHz = 1200; o.resso = 0.25; o.envFiltre = 2; o.fd = 120; o.fs = 0;
            o.a = 1; o.d = 250; o.s = 0; o.r = 200; o.delay = 0.3; o.delayTemps = 0.5; o.reverb = 0.3; o.volumDb = 2;
        });
        p(5, "Ambient infinit", o -> {
            o.ona1 = SI; o.ona2 = TR; o.mescla2 = 0.35; o.semitons2 = 12; o.unison = 2; o.desafina = 10;
            o.tallHz = 2500; o.a = 1500; o.d = 2000; o.s = 0.9; o.r = 3000;
            o.chorus = 0.5; o.delay = 0.4; o.delayTemps = 1.5; o.delayRepeticio = 0.6; o.reverb = 0.75; o.reverbMida = 1; o.volumDb = -4;
        });
        p(5, "Brillantor", o -> {   // shimmer: sinus i octaves amb eco i reverb llarga
            o.ona1 = SI; o.ona2 = SI; o.mescla2 = 0.45; o.semitons2 = 24;
            o.tallHz = 6000; o.a = 200; o.d = 1500; o.s = 0.7; o.r = 2000;
            o.delay = 0.35; o.delayTemps = 0.75; o.delayRepeticio = 0.55; o.reverb = 0.7; o.reverbMida = 1; o.volumDb = -1;
        });
        return LLISTA.toArray(new Preset[0]);
    }
}
