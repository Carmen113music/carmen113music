package cat.riffs;

import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Random;

/**
 * Sinte monofònic (una sola nota alhora), fet a mà: dos osciladors, sub-oscilador i soroll,
 * filtre passa-baixos amb ressonància i envolupant, envolupant de volum, LFO (vibrato i filtre),
 * glide (lliscar d'una nota a l'altra quan es toquen) i distorsió.
 * Converteix una llista de notes en so mono a 44.100 Hz. No depèn d'Android.
 */
public final class Sinte {

    public static final int MOSTREIG = 44100;

    public static final int SERRA = 0, QUADRADA = 1, TRIANGLE = 2, SINUS = 3;
    public static final String[] NOMS_ONA = {"Serra", "Quadrada", "Triangle", "Sinus"};

    /** Tots els botons del sinte. Els temps en ms; els nivells de 0 a 1. */
    public static final class So {
        public int ona1 = SERRA, ona2 = SERRA;
        public double mescla2 = 0;        // quant se sent l'oscilador 2 (0 = gens, 1 = només ell)
        public int semitons2 = 0;         // afinació de l'oscilador 2 (−24 a +24)
        public double cents2 = 0;         // desafinació fina (−50 a +50)
        public double pols = 0.5;         // amplada de la quadrada (0,05 a 0,5)
        public double sub = 0;            // sub-oscilador (una octava avall)
        public double soroll = 0;
        public double tallHz = 2000;      // filtre: on talla
        public double resso = 0.2;        // ressonància (0 a 1)
        public double envFiltre = 2;      // quantes octaves obre l'envolupant (−4 a +6)
        public double seguiment = 0.5;    // el filtre segueix la nota (0 a 1)
        public double a = 3, d = 300, s = 0.8, r = 120;      // envolupant de volum
        public double fa = 2, fd = 250, fs = 0.2, fr = 150;  // envolupant del filtre
        public double glideMs = 0;        // >0: les notes que es toquen llisquen (legato)
        public double lfoHz = 5;
        public double lfoTo = 0;          // vibrato, en cents
        public double lfoFiltre = 0;      // en octaves
        public double lfoRetardMs = 0;    // el vibrato entra al cap d'una estona
        public double distorsio = 0;      // 0 a 1
        public double volumDb = 0;

        public So copia() {
            Properties p = new Properties();
            desa(p, "");
            return llegeix(p, "");
        }

        public void desa(Properties p, String k) {
            p.setProperty(k + "ona1", Integer.toString(ona1));
            p.setProperty(k + "ona2", Integer.toString(ona2));
            p.setProperty(k + "semitons2", Integer.toString(semitons2));
            posa(p, k + "mescla2", mescla2);
            posa(p, k + "cents2", cents2);
            posa(p, k + "pols", pols);
            posa(p, k + "sub", sub);
            posa(p, k + "soroll", soroll);
            posa(p, k + "tall", tallHz);
            posa(p, k + "resso", resso);
            posa(p, k + "envFiltre", envFiltre);
            posa(p, k + "seguiment", seguiment);
            posa(p, k + "a", a);
            posa(p, k + "d", d);
            posa(p, k + "s", s);
            posa(p, k + "r", r);
            posa(p, k + "fa", fa);
            posa(p, k + "fd", fd);
            posa(p, k + "fs", fs);
            posa(p, k + "fr", fr);
            posa(p, k + "glide", glideMs);
            posa(p, k + "lfoHz", lfoHz);
            posa(p, k + "lfoTo", lfoTo);
            posa(p, k + "lfoFiltre", lfoFiltre);
            posa(p, k + "lfoRetard", lfoRetardMs);
            posa(p, k + "distorsio", distorsio);
            posa(p, k + "volum", volumDb);
        }

        public static So llegeix(Properties p, String k) {
            So o = new So();
            o.ona1 = (int) Math.max(0, Math.min(3, nombre(p, k + "ona1", o.ona1)));
            o.ona2 = (int) Math.max(0, Math.min(3, nombre(p, k + "ona2", o.ona2)));
            o.semitons2 = (int) Math.max(-24, Math.min(24, nombre(p, k + "semitons2", o.semitons2)));
            o.mescla2 = entre(nombre(p, k + "mescla2", o.mescla2), 0, 1);
            o.cents2 = entre(nombre(p, k + "cents2", o.cents2), -50, 50);
            o.pols = entre(nombre(p, k + "pols", o.pols), 0.05, 0.5);
            o.sub = entre(nombre(p, k + "sub", o.sub), 0, 1);
            o.soroll = entre(nombre(p, k + "soroll", o.soroll), 0, 1);
            o.tallHz = entre(nombre(p, k + "tall", o.tallHz), 30, 18000);
            o.resso = entre(nombre(p, k + "resso", o.resso), 0, 1);
            o.envFiltre = entre(nombre(p, k + "envFiltre", o.envFiltre), -4, 6);
            o.seguiment = entre(nombre(p, k + "seguiment", o.seguiment), 0, 1);
            o.a = entre(nombre(p, k + "a", o.a), 0, 5000);
            o.d = entre(nombre(p, k + "d", o.d), 1, 5000);
            o.s = entre(nombre(p, k + "s", o.s), 0, 1);
            o.r = entre(nombre(p, k + "r", o.r), 1, 5000);
            o.fa = entre(nombre(p, k + "fa", o.fa), 0, 5000);
            o.fd = entre(nombre(p, k + "fd", o.fd), 1, 5000);
            o.fs = entre(nombre(p, k + "fs", o.fs), 0, 1);
            o.fr = entre(nombre(p, k + "fr", o.fr), 1, 5000);
            o.glideMs = entre(nombre(p, k + "glide", o.glideMs), 0, 1000);
            o.lfoHz = entre(nombre(p, k + "lfoHz", o.lfoHz), 0.05, 20);
            o.lfoTo = entre(nombre(p, k + "lfoTo", o.lfoTo), 0, 100);
            o.lfoFiltre = entre(nombre(p, k + "lfoFiltre", o.lfoFiltre), 0, 3);
            o.lfoRetardMs = entre(nombre(p, k + "lfoRetard", o.lfoRetardMs), 0, 2000);
            o.distorsio = entre(nombre(p, k + "distorsio", o.distorsio), 0, 1);
            o.volumDb = entre(nombre(p, k + "volum", o.volumDb), -30, 6);
            return o;
        }

        /** Temps que pot sonar després de deixar la nota. */
        long cuaMostres() {
            return Math.round(Math.max(r, fr) * 1.2 * MOSTREIG / 1000.0) + MOSTREIG / 50;
        }
    }

    // ---------- Presets ----------

    /** Un so de fàbrica. {@code greu} = per a línies de baix (el piano roll s'obre més avall). */
    public static final class Preset {
        public final String nom;
        public final boolean greu;
        public final So so;

        Preset(String nom, boolean greu, So so) {
            this.nom = nom;
            this.greu = greu;
            this.so = so;
        }
    }

    public static final Preset[] PRESETS = presets();

    private static Preset[] presets() {
        So b1 = new So();          // baix sub: rodó i greu
        b1.ona1 = SINUS;
        b1.ona2 = TRIANGLE;
        b1.mescla2 = 0.35;
        b1.sub = 0.4;
        b1.tallHz = 600;
        b1.resso = 0;
        b1.envFiltre = 1;
        b1.a = 2;
        b1.d = 400;
        b1.s = 0.9;
        b1.r = 80;
        b1.distorsio = 0.1;

        So b2 = new So();          // baix gros: serra filtrada amb cop
        b2.ona1 = SERRA;
        b2.ona2 = QUADRADA;
        b2.mescla2 = 0.4;
        b2.semitons2 = -12;
        b2.sub = 0.3;
        b2.tallHz = 220;
        b2.resso = 0.25;
        b2.envFiltre = 3.2;
        b2.fd = 220;
        b2.fs = 0.15;
        b2.a = 2;
        b2.d = 500;
        b2.s = 0.85;
        b2.r = 90;
        b2.distorsio = 0.2;

        So b3 = new So();          // baix àcid: ressonància i glide
        b3.ona1 = SERRA;
        b3.tallHz = 180;
        b3.resso = 0.82;
        b3.envFiltre = 4;
        b3.fa = 1;
        b3.fd = 180;
        b3.fs = 0;
        b3.fr = 80;
        b3.a = 1;
        b3.d = 300;
        b3.s = 0.8;
        b3.r = 60;
        b3.glideMs = 70;
        b3.distorsio = 0.45;
        b3.volumDb = -2;

        So b4 = new So();          // baix pluck: curt i amb atac
        b4.ona1 = QUADRADA;
        b4.pols = 0.3;
        b4.sub = 0.3;
        b4.tallHz = 300;
        b4.resso = 0.3;
        b4.envFiltre = 3.5;
        b4.fd = 120;
        b4.fs = 0;
        b4.a = 1;
        b4.d = 260;
        b4.s = 0.25;
        b4.r = 80;
        b4.volumDb = -3;

        So l1 = new So();          // lead serra: dues serres desafinades i glide
        l1.ona1 = SERRA;
        l1.ona2 = SERRA;
        l1.mescla2 = 0.5;
        l1.cents2 = 12;
        l1.tallHz = 1800;
        l1.resso = 0.3;
        l1.envFiltre = 1.8;
        l1.fd = 400;
        l1.fs = 0.4;
        l1.a = 4;
        l1.d = 400;
        l1.s = 0.85;
        l1.r = 180;
        l1.glideMs = 60;
        l1.lfoHz = 5.5;
        l1.lfoTo = 18;
        l1.lfoRetardMs = 350;
        l1.distorsio = 0.15;
        l1.volumDb = -3;

        So l2 = new So();          // lead quadrat: tipus videojoc / sinte vell
        l2.ona1 = QUADRADA;
        l2.ona2 = QUADRADA;
        l2.pols = 0.35;
        l2.mescla2 = 0.3;
        l2.semitons2 = 12;
        l2.tallHz = 3500;
        l2.resso = 0.15;
        l2.envFiltre = 1;
        l2.a = 3;
        l2.d = 300;
        l2.s = 0.75;
        l2.r = 120;
        l2.glideMs = 35;
        l2.lfoHz = 6;
        l2.lfoTo = 22;
        l2.lfoRetardMs = 250;
        l2.volumDb = -5;

        So l3 = new So();          // lead suau: flauta de sinte
        l3.ona1 = TRIANGLE;
        l3.ona2 = SINUS;
        l3.mescla2 = 0.5;
        l3.semitons2 = 12;
        l3.soroll = 0.04;
        l3.tallHz = 2500;
        l3.resso = 0;
        l3.envFiltre = 0.5;
        l3.a = 40;
        l3.d = 500;
        l3.s = 0.85;
        l3.r = 250;
        l3.glideMs = 40;
        l3.lfoHz = 5;
        l3.lfoTo = 15;
        l3.lfoRetardMs = 300;

        So l4 = new So();          // pluck: notes curtes i brillants (arpegis)
        l4.ona1 = SERRA;
        l4.ona2 = QUADRADA;
        l4.mescla2 = 0.3;
        l4.semitons2 = 12;
        l4.tallHz = 600;
        l4.resso = 0.35;
        l4.envFiltre = 3.5;
        l4.fd = 160;
        l4.fs = 0;
        l4.a = 1;
        l4.d = 350;
        l4.s = 0;
        l4.r = 200;
        l4.volumDb = 4;

        So l5 = new So();          // pad: entra a poc a poc
        l5.ona1 = SERRA;
        l5.ona2 = SERRA;
        l5.mescla2 = 0.5;
        l5.cents2 = 18;
        l5.tallHz = 900;
        l5.resso = 0.1;
        l5.envFiltre = 1.2;
        l5.fa = 600;
        l5.fd = 1200;
        l5.fs = 0.6;
        l5.fr = 900;
        l5.a = 450;
        l5.d = 800;
        l5.s = 0.9;
        l5.r = 900;
        l5.lfoHz = 0.4;
        l5.lfoFiltre = 0.4;
        l5.volumDb = -4;

        So l6 = new So();          // lead brut: distorsionat, per a riffs
        l6.ona1 = SERRA;
        l6.ona2 = QUADRADA;
        l6.mescla2 = 0.5;
        l6.semitons2 = -12;
        l6.tallHz = 1500;
        l6.resso = 0.45;
        l6.envFiltre = 2;
        l6.fd = 300;
        l6.fs = 0.5;
        l6.a = 2;
        l6.d = 300;
        l6.s = 0.9;
        l6.r = 120;
        l6.glideMs = 25;
        l6.distorsio = 0.75;
        l6.volumDb = -6;

        return new Preset[]{
                new Preset("Baix sub", true, b1),
                new Preset("Baix gros", true, b2),
                new Preset("Baix àcid", true, b3),
                new Preset("Baix pluck", true, b4),
                new Preset("Lead serra", false, l1),
                new Preset("Lead quadrat", false, l2),
                new Preset("Lead suau", false, l3),
                new Preset("Lead brut", false, l6),
                new Preset("Pluck", false, l4),
                new Preset("Pad", false, l5),
        };
    }

    public static Preset preset(String nom) {
        for (Preset p : PRESETS) if (p.nom.equals(nom)) return p;
        return null;
    }

    // ---------- So ----------

    /** Una nota a tocar, en mostres. {@code forca} de 1 a 3. */
    public static final class Toc {
        public final int inici, fi, nota, forca;

        public Toc(int inici, int fi, int nota, int forca) {
            this.inici = inici;
            this.fi = Math.max(inici + 1, fi);
            this.nota = nota;
            this.forca = forca;
        }
    }

    static final float[] FORCA = {0f, 0.55f, 0.8f, 1f};

    public static double hz(double nota) {
        return 440.0 * Math.pow(2, (nota - 69) / 12.0);
    }

    /**
     * Toca les notes (ordenades per inici, sense trepitjar-se: el sinte és monofònic) en un so de {@code n} mostres.
     * Si {@code roda}, la cua que passa del final torna a entrar pel principi (per a finestres seguides);
     * si no, el so s'allarga fins que acaba la cua.
     */
    public static float[] toca(So o, List<Toc> notes, int n, boolean roda) {
        int cua = (int) o.cuaMostres();
        int total = n + cua;
        float[] out = new float[total];
        if (notes.isEmpty()) return roda ? new float[n] : out;
        Veu v = new Veu(o);
        v.notaAra = notes.get(0).nota;
        v.notaObjectiu = v.notaAra;
        int k = 0;                          // nota següent
        int fiActual = -1;                  // quan s'acaba la nota que sona
        for (int i = 0; i < total; i++) {
            // notes que comencen ara
            while (k < notes.size() && notes.get(k).inici <= i) {
                Toc t = notes.get(k);
                v.toca(t.nota, t.forca, fiActual >= t.inici);
                fiActual = t.fi;
                k++;
            }
            if (fiActual >= 0 && i >= fiActual) v.deixa();
            if (v.callada() && k >= notes.size()) {
                // ja no queda res per sonar
                if (i >= n) break;
                continue;
            }
            out[i] = v.mostra();
        }
        if (!roda) return out;
        float[] r = new float[n];
        System.arraycopy(out, 0, r, 0, n);
        for (int i = n; i < total; i++) r[(i - n) % n] += out[i];
        return r;
    }

    /**
     * Una veu del sinte, mostra a mostra: la fan servir tant el piano roll (per fer el so de la frase)
     * com el teclat per tocar en directe, perquè sonin exactament igual.
     */
    public static final class Veu {
        private final So o;
        private final double sr = MOSTREIG;
        private final Random rnd = new Random(1234);
        private double fase1 = 0, fase2 = 0, faseSub = 0, faseLfo = 0;
        double notaAra = 60, notaObjectiu = 60;
        private double env = 0, envF = 0;
        private int etapa = 0, etapaF = 0;  // 0 res, 1 atac, 2 caiguda/sosteniment, 3 deixar anar
        private double ic1 = 0, ic2 = 0;
        private double vel = 1;
        private long desDeNota = 0;
        private final double atac, atacF, cD, cR, cFD, cFR, cGlide, kRes, guany1, drive, normDrive, pw;

        public Veu(So o) {
            this.o = o;
            atac = Math.max(1, o.a * sr / 1000);
            atacF = Math.max(1, o.fa * sr / 1000);
            cD = coef(o.d);
            cR = coef(o.r);
            cFD = coef(o.fd);
            cFR = coef(o.fr);
            cGlide = o.glideMs > 0 ? coef(o.glideMs) : 1;
            kRes = 2 - 1.96 * o.resso;
            guany1 = 1.0 / (1 + o.sub * 0.6 + o.soroll * 0.5);
            drive = 1 + 9 * o.distorsio;
            normDrive = Math.tanh(drive * 0.6);
            pw = o.pols;
        }

        /**
         * Comença una nota. {@code lligada}: la d'abans encara sona (es toquen); si el so té glide,
         * no es torna a atacar i llisca cap a la nova.
         */
        public void toca(int nota, int forca, boolean lligada) {
            boolean ll = o.glideMs > 0 && lligada && etapa != 0 && etapa != 3;
            notaObjectiu = nota;
            vel = FORCA[Math.max(1, Math.min(3, forca))];
            if (!ll) {
                if (o.glideMs <= 0 || etapa == 0) notaAra = nota;
                etapa = 1;
                etapaF = 1;
                desDeNota = 0;
            }
        }

        /** Deixa anar la nota (comença la cua). */
        public void deixa() {
            if (etapa == 1 || etapa == 2) {
                etapa = 3;
                etapaF = 3;
            }
        }

        public boolean callada() {
            return etapa == 0;
        }

        /** La mostra següent (aprox. de −1 a 1, abans del volum). */
        public float mostra() {
            // envolupants
            switch (etapa) {
                case 1:
                    env += 1.0 / atac;
                    if (env >= 1) {
                        env = 1;
                        etapa = 2;
                    }
                    break;
                case 2:
                    env += (o.s - env) * cD;
                    break;
                case 3:
                    env -= env * cR;
                    if (env < 1e-4) {
                        env = 0;
                        etapa = 0;
                    }
                    break;
                default:
                    break;
            }
            switch (etapaF) {
                case 1:
                    envF += 1.0 / atacF;
                    if (envF >= 1) {
                        envF = 1;
                        etapaF = 2;
                    }
                    break;
                case 2:
                    envF += (o.fs - envF) * cFD;
                    break;
                case 3:
                    envF -= envF * cFR;
                    break;
                default:
                    break;
            }

            // to (glide i vibrato)
            notaAra += (notaObjectiu - notaAra) * cGlide;
            faseLfo += o.lfoHz / sr;
            if (faseLfo >= 1) faseLfo -= 1;
            double lfo = Math.sin(2 * Math.PI * faseLfo);
            double entrada = o.lfoRetardMs <= 0 ? 1 : Math.min(1, desDeNota / (o.lfoRetardMs * sr / 1000));
            desDeNota++;
            double nota = notaAra + lfo * entrada * o.lfoTo / 100.0;
            double f1 = hz(nota);
            double f2 = hz(nota + o.semitons2 + o.cents2 / 100.0);
            double dt1 = f1 / sr, dt2 = f2 / sr, dtSub = dt1 / 2;

            double x = ona(o.ona1, fase1, dt1, pw) * (1 - o.mescla2) + ona(o.ona2, fase2, dt2, pw) * o.mescla2;
            if (o.sub > 0) x += o.sub * 0.8 * quadrada(faseSub, dtSub, 0.5);
            if (o.soroll > 0) x += o.soroll * (rnd.nextFloat() * 2 - 1);
            x *= guany1;
            fase1 += dt1;
            if (fase1 >= 1) fase1 -= 1;
            fase2 += dt2;
            if (fase2 >= 1) fase2 -= 1;
            faseSub += dtSub;
            if (faseSub >= 1) faseSub -= 1;

            // filtre passa-baixos (variable d'estat, estable amb ressonància alta)
            double oct = o.envFiltre * envF * (0.7 + 0.3 * vel) + o.lfoFiltre * lfo + o.seguiment * (nota - 60) / 12.0;
            double fc = o.tallHz * Math.pow(2, oct);
            fc = Math.max(20, Math.min(sr * 0.45, fc));
            double g = Math.tan(Math.PI * fc / sr);
            double a1 = 1 / (1 + g * (g + kRes)), a2 = g * a1, a3 = g * a2;
            double v3 = x - ic2;
            double v1 = a1 * ic1 + a2 * v3;
            double v2 = ic2 + a2 * ic1 + a3 * v3;
            ic1 = 2 * v1 - ic1;
            ic2 = 2 * v2 - ic2;
            double y = v2;

            if (o.distorsio > 0) y = Math.tanh(y * drive * 0.6) / normDrive;
            return (float) (y * env * vel);
        }
    }

    private static double coef(double ms) {
        double m = Math.max(1, ms * MOSTREIG / 1000.0);
        return 1 - Math.exp(-4.6 / m);
    }

    private static double ona(int tipus, double t, double dt, double pw) {
        switch (tipus) {
            case QUADRADA:
                return quadrada(t, dt, pw);
            case TRIANGLE:
                return 1 - 4 * Math.abs(t - 0.5);
            case SINUS:
                return Math.sin(2 * Math.PI * t);
            default:
                return 2 * t - 1 - blep(t, dt);
        }
    }

    private static double quadrada(double t, double dt, double pw) {
        double v = t < pw ? 1 : -1;
        v += blep(t, dt);
        double t2 = t - pw;
        if (t2 < 0) t2 += 1;
        v -= blep(t2, dt);
        // centrar-la (amb pols estret no té mitjana zero)
        return v - (2 * pw - 1);
    }

    /** Suavitza el salt de les ones de serra i quadrada (menys brunzit digital a les notes agudes). */
    private static double blep(double t, double dt) {
        if (dt <= 0) return 0;
        if (t < dt) {
            t /= dt;
            return t + t - t * t - 1;
        }
        if (t > 1 - dt) {
            t = (t - 1) / dt;
            return t * t + t + t + 1;
        }
        return 0;
    }

    /** Passa a 16 bits amb marge i un limitador suau (com la bateria). */
    public static short[] a16(float[] x, double volumDb) {
        float g = (float) (0.7 * Math.pow(10, volumDb / 20));
        short[] r = new short[x.length];
        for (int i = 0; i < x.length; i++) {
            float v = x[i] * g;
            float av = Math.abs(v);
            if (av > 0.9f) v = Math.signum(v) * (0.9f + 0.1f * (float) Math.tanh((av - 0.9f) / 0.1f));
            r[i] = (short) Math.round(v * 32767f);
        }
        return r;
    }

    // ---------- Fitxers ----------

    private static void posa(Properties p, String k, double v) {
        p.setProperty(k, String.format(Locale.ROOT, "%.4f", v));
    }

    private static double nombre(Properties p, String k, double def) {
        String s = p.getProperty(k);
        if (s == null) return def;
        try {
            double v = Double.parseDouble(s.trim());
            return Double.isNaN(v) || Double.isInfinite(v) ? def : v;
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static double entre(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
