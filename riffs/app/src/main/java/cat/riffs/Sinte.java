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

    /** Motors de so (2.38): cadascun imita una família de sintes. */
    public static final int ANALOGIC = 0, FM = 1, WAVETABLE = 2, ORGUE = 3, CORDA = 4;
    public static final String[] NOMS_MOTOR = {"Analògic", "FM", "Wavetable", "Orgue", "Corda pinçada"};
    /** Proporcions de freqüència dels 9 tirants de l'orgue (16', 5 1/3', 8', 4', 2 2/3', 2', 1 3/5', 1 1/3', 1'). */
    static final double[] TIRANTS_RATIO = {0.5, 1.5, 1, 2, 3, 4, 5, 6, 8};
    /** Filtre passa-alts del motor analògic (posicions 0 a 3), en Hz (0 = apagat). */
    static final double[] HPF_HZ = {0, 180, 360, 720};

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
        // 2.31 (amb els valors per defecte, el so és exactament el d'abans)
        public int unison = 1;            // veus per nota (1 a 7), desafinades entre elles
        public double desafina = 0;       // cents entre la veu més greu i la més aguda de l'unison (0 a 100)
        public double lfoPols = 0;        // el LFO mou l'amplada de la quadrada (PWM, 0 a 1)
        public double chorus = 0;         // 0 a 1
        public double chorusHz = 0.8;
        public double delay = 0;          // quant se sent el retorn (0 a 1)
        public double delayTemps = 0.75;  // en temps (negres): 0,75 = corxera amb punt
        public double delayRepeticio = 0.35;   // realimentació (0 a 0,9)
        public double reverb = 0;         // 0 a 1
        public double reverbMida = 0.6;   // 0 a 1 (sala petita → catedral)
        // 2.38: motors nous (amb els valors per defecte, el so és exactament el d'abans)
        public int motor = ANALOGIC;
        public int hpf = 0;               // analògic: passa-alts en 4 posicions (0 = apagat)
        public int chorusMode = 0;        // 0 = chorus normal; 1, 2, 3 = chorus tipus Juno I, II, I+II
        public int leslie = 0;            // altaveu rotatori: 0 apagat, 1 lent, 2 ràpid
        // FM (4 operadors; l'1 és el de baix)
        public int fmAlg = 1;             // algorisme 1 a 8
        public double fmRealim = 0;       // realimentació de l'operador 4 (0 a 1)
        public double fmForca = 0.5;      // com la força de la nota obre el so (0 a 1)
        public final double[] fmRatio = {1, 1, 1, 1};
        public final double[] fmFi = new double[4];          // 0 a 0,99 (s'afegeix a la proporció)
        public final double[] fmDesafina = new double[4];    // cents (−10 a +10)
        public final double[] fmNivell = {1, 0.5, 0, 0};
        public final double[] fmA = {2, 2, 2, 2};
        public final double[] fmD = {1500, 800, 800, 800};
        public final double[] fmS = {0.6, 0.4, 0.4, 0.4};
        public final double[] fmR = {300, 300, 300, 300};
        // wavetable
        public int wtTaula = 0;
        public double wtPos = 0;          // 0 a 1
        public double wtEnv = 0;          // l'envolupant 2 (la del filtre) mou la posició (−1 a 1)
        public double wtLfo = 0;          // el LFO mou la posició (0 a 1)
        // orgue
        public final int[] tirants = {8, 8, 8, 0, 0, 0, 0, 0, 0};   // 0 a 8
        public int orgPerc = 0;           // percussió: 0 apagada, 1 segon harmònic (4'), 2 tercer (2 2/3')
        public double orgPercNivell = 0.6;
        public double orgPercMs = 300;    // què triga a apagar-se la percussió
        public double orgClic = 0.3;      // clic de les tecles
        // corda pinçada (Karplus-Strong)
        public int ksTipus = 0;           // 0 púa, 1 dit, 2 martell, 3 arc
        public double ksBrillant = 0.6;
        public double ksPosicio = 0.2;    // on es pinça (0,05 = a tocar del pont)
        public double ksDurada = 3;       // segons fins a −60 dB
        public double ksAmortit = 0.5;    // com s'apaguen els aguts
        public double ksCos = 0;          // ressonància de la caixa

        /** Si té algun efecte (chorus, delay, reverb o Leslie). */
        public boolean ambEfectes() {
            return chorus > 0 || delay > 0 || reverb > 0 || chorusMode > 0 || leslie > 0;
        }

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
            p.setProperty(k + "unison", Integer.toString(unison));
            posa(p, k + "desafina", desafina);
            posa(p, k + "lfoPols", lfoPols);
            posa(p, k + "chorus", chorus);
            posa(p, k + "chorusHz", chorusHz);
            posa(p, k + "delay", delay);
            posa(p, k + "delayTemps", delayTemps);
            posa(p, k + "delayRep", delayRepeticio);
            posa(p, k + "reverb", reverb);
            posa(p, k + "reverbMida", reverbMida);
            if (motor != ANALOGIC) p.setProperty(k + "motor", Integer.toString(motor));
            if (hpf != 0) p.setProperty(k + "hpf", Integer.toString(hpf));
            if (chorusMode != 0) p.setProperty(k + "chorusMode", Integer.toString(chorusMode));
            if (leslie != 0) p.setProperty(k + "leslie", Integer.toString(leslie));
            if (motor == FM) {
                p.setProperty(k + "fmAlg", Integer.toString(fmAlg));
                posa(p, k + "fmRealim", fmRealim);
                posa(p, k + "fmForca", fmForca);
                for (int i = 0; i < 4; i++) {
                    String q = k + "fm" + (i + 1) + ".";
                    posa(p, q + "ratio", fmRatio[i]);
                    posa(p, q + "fi", fmFi[i]);
                    posa(p, q + "desafina", fmDesafina[i]);
                    posa(p, q + "nivell", fmNivell[i]);
                    posa(p, q + "a", fmA[i]);
                    posa(p, q + "d", fmD[i]);
                    posa(p, q + "s", fmS[i]);
                    posa(p, q + "r", fmR[i]);
                }
            } else if (motor == WAVETABLE) {
                p.setProperty(k + "wtTaula", Integer.toString(wtTaula));
                posa(p, k + "wtPos", wtPos);
                posa(p, k + "wtEnv", wtEnv);
                posa(p, k + "wtLfo", wtLfo);
            } else if (motor == ORGUE) {
                StringBuilder b = new StringBuilder();
                for (int t : tirants) b.append(t);
                p.setProperty(k + "tirants", b.toString());
                p.setProperty(k + "orgPerc", Integer.toString(orgPerc));
                posa(p, k + "orgPercNivell", orgPercNivell);
                posa(p, k + "orgPercMs", orgPercMs);
                posa(p, k + "orgClic", orgClic);
            } else if (motor == CORDA) {
                p.setProperty(k + "ksTipus", Integer.toString(ksTipus));
                posa(p, k + "ksBrillant", ksBrillant);
                posa(p, k + "ksPosicio", ksPosicio);
                posa(p, k + "ksDurada", ksDurada);
                posa(p, k + "ksAmortit", ksAmortit);
                posa(p, k + "ksCos", ksCos);
            }
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
            o.unison = (int) entre(nombre(p, k + "unison", o.unison), 1, 7);
            o.desafina = entre(nombre(p, k + "desafina", o.desafina), 0, 100);
            o.lfoPols = entre(nombre(p, k + "lfoPols", o.lfoPols), 0, 1);
            o.chorus = entre(nombre(p, k + "chorus", o.chorus), 0, 1);
            o.chorusHz = entre(nombre(p, k + "chorusHz", o.chorusHz), 0.05, 8);
            o.delay = entre(nombre(p, k + "delay", o.delay), 0, 1);
            o.delayTemps = entre(nombre(p, k + "delayTemps", o.delayTemps), 0.125, 2);
            o.delayRepeticio = entre(nombre(p, k + "delayRep", o.delayRepeticio), 0, 0.9);
            o.reverb = entre(nombre(p, k + "reverb", o.reverb), 0, 1);
            o.reverbMida = entre(nombre(p, k + "reverbMida", o.reverbMida), 0, 1);
            o.motor = (int) entre(nombre(p, k + "motor", o.motor), 0, 4);
            o.hpf = (int) entre(nombre(p, k + "hpf", o.hpf), 0, 3);
            o.chorusMode = (int) entre(nombre(p, k + "chorusMode", o.chorusMode), 0, 3);
            o.leslie = (int) entre(nombre(p, k + "leslie", o.leslie), 0, 2);
            o.fmAlg = (int) entre(nombre(p, k + "fmAlg", o.fmAlg), 1, 8);
            o.fmRealim = entre(nombre(p, k + "fmRealim", o.fmRealim), 0, 1);
            o.fmForca = entre(nombre(p, k + "fmForca", o.fmForca), 0, 1);
            for (int i = 0; i < 4; i++) {
                String q = k + "fm" + (i + 1) + ".";
                o.fmRatio[i] = entre(nombre(p, q + "ratio", o.fmRatio[i]), 0.5, 16);
                o.fmFi[i] = entre(nombre(p, q + "fi", o.fmFi[i]), 0, 0.99);
                o.fmDesafina[i] = entre(nombre(p, q + "desafina", o.fmDesafina[i]), -10, 10);
                o.fmNivell[i] = entre(nombre(p, q + "nivell", o.fmNivell[i]), 0, 1);
                o.fmA[i] = entre(nombre(p, q + "a", o.fmA[i]), 0, 5000);
                o.fmD[i] = entre(nombre(p, q + "d", o.fmD[i]), 1, 10000);
                o.fmS[i] = entre(nombre(p, q + "s", o.fmS[i]), 0, 1);
                o.fmR[i] = entre(nombre(p, q + "r", o.fmR[i]), 1, 5000);
            }
            o.wtTaula = (int) entre(nombre(p, k + "wtTaula", o.wtTaula), 0, Taules.NOMS.length - 1);
            o.wtPos = entre(nombre(p, k + "wtPos", o.wtPos), 0, 1);
            o.wtEnv = entre(nombre(p, k + "wtEnv", o.wtEnv), -1, 1);
            o.wtLfo = entre(nombre(p, k + "wtLfo", o.wtLfo), 0, 1);
            String ts = p.getProperty(k + "tirants");
            if (ts != null && ts.trim().length() == 9) {
                for (int i = 0; i < 9; i++) {
                    char c = ts.trim().charAt(i);
                    if (c >= '0' && c <= '8') o.tirants[i] = c - '0';
                }
            }
            o.orgPerc = (int) entre(nombre(p, k + "orgPerc", o.orgPerc), 0, 2);
            o.orgPercNivell = entre(nombre(p, k + "orgPercNivell", o.orgPercNivell), 0, 1);
            o.orgPercMs = entre(nombre(p, k + "orgPercMs", o.orgPercMs), 50, 2000);
            o.orgClic = entre(nombre(p, k + "orgClic", o.orgClic), 0, 1);
            o.ksTipus = (int) entre(nombre(p, k + "ksTipus", o.ksTipus), 0, 3);
            o.ksBrillant = entre(nombre(p, k + "ksBrillant", o.ksBrillant), 0, 1);
            o.ksPosicio = entre(nombre(p, k + "ksPosicio", o.ksPosicio), 0.03, 0.5);
            o.ksDurada = entre(nombre(p, k + "ksDurada", o.ksDurada), 0.1, 30);
            o.ksAmortit = entre(nombre(p, k + "ksAmortit", o.ksAmortit), 0, 1);
            o.ksCos = entre(nombre(p, k + "ksCos", o.ksCos), 0, 1);
            return o;
        }

        /** Temps que pot sonar després de deixar la nota. */
        long cuaMostres() {
            double rr = Math.max(r, fr);
            if (motor == FM) for (int i = 0; i < 4; i++) rr = Math.max(rr, fmR[i]);
            return Math.round(rr * 1.2 * MOSTREIG / 1000.0) + MOSTREIG / 50;
        }

        /** Si és un operador de sortida (portador) a l'algorisme FM actual. */
        public boolean fmPortador(int op) {
            return (FM_PORTADORS[Math.max(1, Math.min(8, fmAlg)) - 1] & (1 << op)) != 0;
        }

        /** El que poden allargar els efectes (delay i reverb), en mostres, al tempo {@code bpm}. */
        long cuaEfectes(double bpm) {
            return Efectes.cuaMostres(this, bpm);
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
        for (Preset p : PRESETS_TEREMIN) if (p.nom.equals(nom)) return p;
        return null;
    }

    /** Sons del teremin: el to i el volum els porta el dit; el glide suavitza el to, i l'envolupant només fa d'entrada i sortida. */
    public static final Preset[] PRESETS_TEREMIN = presetsTeremin();

    private static So teremin() {
        So o = new So();
        o.a = 25;
        o.d = 100;
        o.s = 1;
        o.r = 160;
        o.fa = 0;
        o.fs = 1;
        o.envFiltre = 0;
        o.seguiment = 0.6;
        o.resso = 0;
        o.glideMs = 18;
        return o;
    }

    private static Preset[] presetsTeremin() {
        So t1 = teremin();         // clàssic: gairebé pur, amb vibrato
        t1.ona1 = SINUS;
        t1.ona2 = TRIANGLE;
        t1.mescla2 = 0.25;
        t1.tallHz = 3500;
        t1.lfoHz = 5.5;
        t1.lfoTo = 12;
        t1.lfoRetardMs = 300;
        t1.distorsio = 0.08;

        So t2 = teremin();         // de pel·lícula antiga: més aspre
        t2.ona1 = TRIANGLE;
        t2.ona2 = QUADRADA;
        t2.pols = 0.42;
        t2.mescla2 = 0.25;
        t2.tallHz = 1800;
        t2.resso = 0.2;
        t2.lfoHz = 6;
        t2.lfoTo = 22;
        t2.lfoRetardMs = 200;
        t2.distorsio = 0.3;
        t2.volumDb = -2;

        So t3 = teremin();         // vidre: sinus amb l'octava a sobre
        t3.ona1 = SINUS;
        t3.ona2 = SINUS;
        t3.semitons2 = 12;
        t3.mescla2 = 0.3;
        t3.tallHz = 6000;
        t3.r = 350;
        t3.lfoHz = 4.5;
        t3.lfoTo = 8;

        So t4 = teremin();         // veu: serra amb el filtre ressonant (sona com una vocal)
        t4.ona1 = SERRA;
        t4.tallHz = 650;
        t4.resso = 0.6;
        t4.seguiment = 0.8;
        t4.lfoHz = 5;
        t4.lfoTo = 15;
        t4.lfoFiltre = 0.15;
        t4.lfoRetardMs = 250;
        t4.volumDb = -3;

        So t5 = teremin();         // violoncel: dues serres, entrada suau
        t5.ona1 = SERRA;
        t5.ona2 = SERRA;
        t5.mescla2 = 0.5;
        t5.cents2 = 8;
        t5.tallHz = 1300;
        t5.resso = 0.15;
        t5.a = 60;
        t5.r = 220;
        t5.lfoHz = 5.2;
        t5.lfoTo = 18;
        t5.lfoRetardMs = 400;
        t5.volumDb = 1;

        So t6 = teremin();         // sirena: quadrada brillant i distorsionada, llisca molt
        t6.ona1 = QUADRADA;
        t6.pols = 0.5;
        t6.tallHz = 2600;
        t6.resso = 0.25;
        t6.glideMs = 70;
        t6.distorsio = 0.45;
        t6.volumDb = -5;

        return new Preset[]{
                new Preset("Teremin clàssic", false, t1),
                new Preset("Teremin de pel·lícula", false, t2),
                new Preset("Teremin de vidre", false, t3),
                new Preset("Teremin veu", false, t4),
                new Preset("Teremin violoncel", false, t5),
                new Preset("Teremin sirena", false, t6),
        };
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
        return toca(o, notes, n, roda, 120);
    }

    /** Com {@link #toca(So, List, int, boolean)}, amb els efectes al tempo {@code bpm} (el delay va a temps). */
    public static float[] toca(So o, List<Toc> notes, int n, boolean roda, double bpm) {
        Efectes fx = o.ambEfectes() ? new Efectes(o, bpm) : null;
        int cua = (int) o.cuaMostres() + (fx != null ? (int) o.cuaEfectes(bpm) : 0);
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
                // ja no queda res per sonar (més que la cua dels efectes)
                if (fx != null) {
                    out[i] = fx.processa(0f);
                    continue;
                }
                if (i >= n) break;
                continue;
            }
            float x = v.mostra();
            out[i] = fx != null ? fx.processa(x) : x;
        }
        if (!roda) return out;
        float[] r = new float[n];
        System.arraycopy(out, 0, r, 0, n);
        for (int i = n; i < total; i++) r[(i - n) % n] += out[i];
        return r;
    }

    // ---------- FM: algorismes de 4 operadors (com els sintes FM petits de 4 operadors) ----------

    /** Per a cada algorisme (1 a 8) i cada operador (0 = op 1 … 3 = op 4): quins operadors el modulen (bits). */
    static final int[][] FM_MODS = {
            {1 << 1, 1 << 2, 1 << 3, 0},            // 1: 4→3→2→1
            {1 << 1, (1 << 2) | (1 << 3), 0, 0},    // 2: (3+4)→2→1
            {(1 << 1) | (1 << 3), 1 << 2, 0, 0},    // 3: 3→2→1 i 4→1
            {(1 << 1) | (1 << 2), 0, 1 << 3, 0},    // 4: 4→3→1 i 2→1
            {1 << 1, 0, 1 << 3, 0},                 // 5: dues parelles: 2→1 i 4→3
            {1 << 3, 1 << 3, 1 << 3, 0},            // 6: 4 modula 1, 2 i 3
            {0, 0, 1 << 3, 0},                      // 7: 4→3, i 1 i 2 sols
            {0, 0, 0, 0},                           // 8: els quatre sonen (orgue)
    };
    /** Operadors que sonen (portadors) a cada algorisme (bits). */
    static final int[] FM_PORTADORS = {1, 1, 1, 1, 1 | 4, 1 | 2 | 4, 1 | 2 | 4, 15};

    // ---------- Sinus en taula (més ràpid que Math.sin, per a FM i orgue) ----------

    private static final int NSIN = 4096;
    private static final float[] SIN = new float[NSIN + 1];

    static {
        for (int i = 0; i <= NSIN; i++) SIN[i] = (float) Math.sin(2 * Math.PI * i / NSIN);
    }

    /** sin(2π·fase), amb la fase en cicles (qualsevol valor). */
    static double sinT(double fase) {
        fase -= Math.floor(fase);
        double x = fase * NSIN;
        int i = (int) x;
        if (i >= NSIN) i = NSIN - 1;
        return SIN[i] + (SIN[i + 1] - SIN[i]) * (x - i);
    }

    /**
     * Una veu del sinte, mostra a mostra: la fan servir tant el piano roll (per fer el so de la frase)
     * com el teclat per tocar en directe, perquè sonin exactament igual. Segons {@link So#motor}, el so neix
     * d'oscil·ladors analògics, d'operadors FM, d'una taula d'ones, de tirants d'orgue o d'una corda pinçada.
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
        private double atac, atacF, cD, cR, cFD, cFR, cGlide, kRes, guany1, drive, normDrive, pw, sAra;
        // unison (2.31): fases i desafinació (proporció de freqüència) de cada veu
        private int uni;
        private double[] fu1, fu2, du;
        private double guanyUni;
        // 2.38
        private int motor;
        private boolean filtre;
        private double hpfA, hpfX, hpfY;
        private int quiet;
        // FM
        private final double[] fmFase = new double[4], fmEnv = new double[4], fmOut = new double[4];
        private final int[] fmEtapa = new int[4];
        private final double[] fmAtac = new double[4], fmCD = new double[4], fmCR = new double[4], fmMul = new double[4], fmNiv = new double[4];
        private int[] fmMods;
        private int fmCar;
        private double fmGuanyCar, fmFb1, fmFb2;
        // wavetable
        private float[][][] taula;
        // orgue
        private final double[] orgFase = new double[9], orgAmp = new double[9];
        private double orgPerc, orgPercC, orgClicAmp, orgNorm;
        // corda
        private float[] ks;
        private int ksW;
        private double ksAnt, ksG, ksS, ksArc;
        private final double[] cos = new double[12];     // estat dels 3 filtres de la caixa
        private static final double[][] COS = cosCoef();

        public Veu(So o) {
            this.o = o;
            refresca();
        }

        /** Torna a llegir els botons del so (2.32: l'editor de so els mou mentre sona). Les fases es conserven. */
        public void refresca() {
            motor = o.motor;
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
            sAra = o.s;
            filtre = motor != FM && motor != ORGUE;
            double fh = motor == ANALOGIC || motor == WAVETABLE ? HPF_HZ[Math.max(0, Math.min(3, o.hpf))] : 0;
            hpfA = fh > 0 ? 1 / (1 + 2 * Math.PI * fh / sr) : 0;
            int u = Math.max(1, Math.min(7, o.unison));
            if (fu1 == null || u != uni) {
                uni = u;
                fu1 = new double[uni];
                fu2 = new double[uni];
                du = new double[uni];
                Random r0 = new Random(77);
                for (int i = 0; i < uni; i++) {
                    fu1[i] = uni == 1 ? 0 : r0.nextDouble();   // fases repartides: no sonen totes en fase
                    fu2[i] = uni == 1 ? 0 : r0.nextDouble();
                }
            }
            for (int i = 0; i < uni; i++) du[i] = uni == 1 ? 1 : Math.pow(2, (i / (double) (uni - 1) - 0.5) * o.desafina / 1200.0);
            guanyUni = 1 / Math.sqrt(uni);
            if (motor == FM) {
                int al = Math.max(1, Math.min(8, o.fmAlg)) - 1;
                fmMods = FM_MODS[al];
                fmCar = FM_PORTADORS[al];
                double rMax = 1;
                int n = 0;
                for (int i = 0; i < 4; i++) {
                    fmAtac[i] = Math.max(1, o.fmA[i] * sr / 1000);
                    fmCD[i] = coef(o.fmD[i]);
                    fmCR[i] = coef(o.fmR[i]);
                    fmMul[i] = o.fmRatio[i] * (1 + o.fmFi[i]) * Math.pow(2, o.fmDesafina[i] / 1200.0);
                    boolean car = (fmCar & (1 << i)) != 0;
                    fmNiv[i] = car ? o.fmNivell[i] : 2.0 * o.fmNivell[i] * o.fmNivell[i];   // moduladors: índex en cicles
                    if (car) {
                        n++;
                        rMax = Math.max(rMax, o.fmR[i]);
                    }
                }
                fmGuanyCar = 1 / Math.sqrt(Math.max(1, n));
                // l'envolupant general només fa de porta: el so el fan les envolupants dels operadors
                atac = 1;
                sAra = 1;
                cR = coef(rMax * 1.2);
            } else if (motor == WAVETABLE) {
                taula = Taules.taula(o.wtTaula);
            } else if (motor == ORGUE) {
                double s2 = 0;
                for (int i = 0; i < 9; i++) {
                    int t = Math.max(0, Math.min(8, o.tirants[i]));
                    orgAmp[i] = t == 0 ? 0 : Math.pow(10, -(8 - t) * 3 / 20.0);
                    s2 += orgAmp[i];
                }
                orgNorm = 0.9 / Math.max(1.2, 0.6 * s2 + 0.8);
                orgPercC = Math.exp(-6.9 / Math.max(1, o.orgPercMs * sr / 1000));
                sAra = 1;
            } else if (motor == CORDA) {
                if (ks == null) ks = new float[4096];
                ksS = 0.03 + 0.47 * o.ksAmortit;
                sAra = 1;
            }
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
                quiet = 0;
                if (motor == FM) {
                    for (int i = 0; i < 4; i++) fmEtapa[i] = 1;
                } else if (motor == ORGUE) {
                    orgPerc = 1;
                    orgClicAmp = 1;
                } else if (motor == CORDA) {
                    pinca(nota);
                }
            }
        }

        /** Canvia el to que ha de sonar (pot tenir decimals: teremin). Hi llisca amb el glide del so. */
        public void posaNota(double nota) {
            notaObjectiu = nota;
        }

        /** Deixa anar la nota (comença la cua). */
        public void deixa() {
            if (etapa == 1 || etapa == 2) {
                etapa = 3;
                etapaF = 3;
                for (int i = 0; i < 4; i++) fmEtapa[i] = 3;
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
                    env += (sAra - env) * cD;
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

            double x;
            if (motor == FM) {
                x = fm(f1);
            } else if (motor == ORGUE) {
                x = orgue(f1);
            } else if (motor == CORDA) {
                x = corda(f1);
            } else {
                double f2 = hz(nota + o.semitons2 + o.cents2 / 100.0);
                double dt1 = f1 / sr, dt2 = f2 / sr, dtSub = dt1 / 2;
                boolean wt = motor == WAVETABLE;
                double pos = 0;
                int niv = 0;
                if (wt) {
                    pos = Math.max(0, Math.min(1, o.wtPos + o.wtEnv * envF + o.wtLfo * 0.5 * lfo));
                    niv = Taules.nivell(dt1);
                }

                double pwAra = pw;
                if (o.lfoPols > 0) pwAra = Math.max(0.05, Math.min(0.95, pw + o.lfoPols * 0.45 * lfo));
                if (uni == 1) {
                    double a1 = wt ? Taules.llegeix(taula, pos, niv, fase1) : ona(o.ona1, fase1, dt1, pwAra);
                    x = a1 * (1 - o.mescla2) + ona(o.ona2, fase2, dt2, pwAra) * o.mescla2;
                } else {
                    double s1 = 0, s2 = 0;
                    for (int u = 0; u < uni; u++) {
                        double k = du[u];
                        double e1 = dt1 * k, e2 = dt2 * k;
                        s1 += wt ? Taules.llegeix(taula, pos, niv, fu1[u]) : ona(o.ona1, fu1[u], e1, pwAra);
                        if (o.mescla2 > 0) s2 += ona(o.ona2, fu2[u], e2, pwAra);
                        fu1[u] += e1;
                        if (fu1[u] >= 1) fu1[u] -= 1;
                        fu2[u] += e2;
                        if (fu2[u] >= 1) fu2[u] -= 1;
                    }
                    x = (s1 * (1 - o.mescla2) + s2 * o.mescla2) * guanyUni;
                }
                if (o.sub > 0) x += o.sub * 0.8 * quadrada(faseSub, dtSub, 0.5);
                if (o.soroll > 0) x += o.soroll * (rnd.nextFloat() * 2 - 1);
                x *= guany1;
                fase1 += dt1;
                if (fase1 >= 1) fase1 -= 1;
                fase2 += dt2;
                if (fase2 >= 1) fase2 -= 1;
                faseSub += dtSub;
                if (faseSub >= 1) faseSub -= 1;
            }

            double y;
            if (filtre) {
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
                y = v2;
            } else {
                y = x;
            }
            if (hpfA > 0) {
                // passa-alts d'un pol (el del Juno)
                double h = hpfA * (hpfY + y - hpfX);
                hpfX = y;
                hpfY = h;
                y = h;
            }

            if (o.distorsio > 0) y = Math.tanh(y * drive * 0.6) / normDrive;
            float out = (float) (y * env * vel);
            if (motor == FM || motor == CORDA) {
                // el so ja s'ha apagat sol (campana, corda): la veu queda lliure
                if (Math.abs(out) < 1e-5) {
                    if (++quiet > 4410 && desDeNota > 4410) {
                        etapa = 0;
                        env = 0;
                    }
                } else {
                    quiet = 0;
                }
            }
            return out;
        }

        // ----- FM -----

        private double fm(double f) {
            double s = 0;
            double modVel = 1 - o.fmForca * (1 - vel);
            for (int i = 3; i >= 0; i--) {
                // envolupant de l'operador
                switch (fmEtapa[i]) {
                    case 1:
                        fmEnv[i] += 1.0 / fmAtac[i];
                        if (fmEnv[i] >= 1) {
                            fmEnv[i] = 1;
                            fmEtapa[i] = 2;
                        }
                        break;
                    case 2:
                        fmEnv[i] += (o.fmS[i] - fmEnv[i]) * fmCD[i];
                        break;
                    case 3:
                        fmEnv[i] -= fmEnv[i] * fmCR[i];
                        break;
                    default:
                        break;
                }
                double mod = 0;
                int m = fmMods[i];
                if (m != 0) for (int j = i + 1; j < 4; j++) if ((m & (1 << j)) != 0) mod += fmOut[j];
                if (i == 3 && o.fmRealim > 0) mod += o.fmRealim * 0.35 * (fmFb1 + fmFb2);
                double v = sinT(fmFase[i] + mod);
                fmFase[i] += f * fmMul[i] / sr;
                if (fmFase[i] >= 1) fmFase[i] -= Math.floor(fmFase[i]);
                double amp = fmEnv[i];
                if (i == 3) {
                    fmFb2 = fmFb1;
                    fmFb1 = v * amp;
                }
                if ((fmCar & (1 << i)) != 0) {
                    s += v * amp * fmNiv[i];
                    fmOut[i] = v * amp * fmNiv[i] * 2.0 * modVel;
                } else {
                    fmOut[i] = v * amp * fmNiv[i] * modVel;
                }
            }
            return s * fmGuanyCar;
        }

        // ----- orgue de tirants -----

        private double orgue(double f) {
            double s = 0;
            for (int i = 0; i < 9; i++) {
                double fr = f * TIRANTS_RATIO[i];
                if (orgAmp[i] > 0 && fr < 0.45 * sr) s += orgAmp[i] * sinT(orgFase[i]);
                orgFase[i] += fr / sr;
                if (orgFase[i] >= 1) orgFase[i] -= 1;
            }
            if (o.orgPerc > 0 && orgPerc > 1e-4) {
                int h = o.orgPerc == 1 ? 3 : 4;
                if (f * TIRANTS_RATIO[h] < 0.45 * sr) s += orgPerc * o.orgPercNivell * 1.6 * sinT(orgFase[h]);
                orgPerc *= orgPercC;
            }
            s *= orgNorm;
            if (o.orgClic > 0 && orgClicAmp > 1e-3) {
                s += o.orgClic * 0.35 * orgClicAmp * (rnd.nextFloat() * 2 - 1);
                orgClicAmp *= 0.996;   // uns 6 ms
            }
            return s;
        }

        // ----- corda pinçada (Karplus-Strong) -----

        /** Omple la corda amb l'excitació (púa, dit, martell o arc). Se suma al que ja vibrava. */
        private void pinca(int nota) {
            if (ks == null) ks = new float[4096];
            double L = sr / hz(nota);
            int n = (int) Math.min(ks.length - 4, Math.ceil(L));
            double br = Math.max(0, Math.min(1, o.ksBrillant * (0.55 + 0.45 * vel)));
            double c;
            float[] e = new float[n];
            int tipus = o.ksTipus;
            if (tipus == 2) {
                // martell: un cop curt i rodó
                int w = Math.max(2, (int) Math.round(n * (0.25 - 0.2 * br)));
                for (int i = 0; i < w && i < n; i++) e[i] = (float) Math.sin(Math.PI * i / w);
            } else {
                c = 0.04 + 0.96 * br * br;
                if (tipus == 1) c *= 0.3;     // dit: més rodó
                double lp = 0;
                for (int i = 0; i < n; i++) {
                    lp += c * ((rnd.nextFloat() * 2 - 1) - lp);
                    e[i] = (float) lp;
                }
            }
            // on es pinça: es treu la part que es repetiria (filtre en pinta)
            int pp = Math.max(1, (int) Math.round(o.ksPosicio * n));
            float[] e2 = new float[n];
            for (int i = 0; i < n; i++) e2[i] = e[i] - (i >= pp ? e[i - pp] : 0);
            double mitjana = 0, pic = 1e-9;
            for (float v : e2) mitjana += v;
            mitjana /= n;
            for (int i = 0; i < n; i++) pic = Math.max(pic, Math.abs(e2[i] - mitjana));
            double g = (tipus == 3 ? 0.25 : 1) / pic;
            int mask = ks.length - 1;
            for (int i = 0; i < n; i++) ks[(ksW - n + i) & mask] += (float) ((e2[i] - mitjana) * g);
            ksArc = 0;
        }

        private double corda(double f) {
            if (ks == null) return 0;
            double L = sr / f;
            double d = Math.max(2, Math.min(ks.length - 4, L - ksS));
            int mask = ks.length - 1;
            double rp = ksW - d;
            int i0 = (int) Math.floor(rp);
            double fr = rp - i0;
            double a = ks[i0 & mask], b = ks[(i0 + 1) & mask];
            double v = a + (b - a) * fr;
            double y = (1 - ksS) * v + ksS * ksAnt;
            ksAnt = v;
            ksG = Math.pow(10, -3 / (f * Math.max(0.1, o.ksDurada)));
            double ent = 0;
            if (o.ksTipus == 3 && (etapa == 1 || etapa == 2)) {
                // arc: s'hi va afegint soroll mentre es manté la nota (fregar)
                ksArc += 0.15 * ((rnd.nextFloat() * 2 - 1) - ksArc);
                ent = ksArc * (1 - ksG) * 45 * (0.4 + 0.6 * o.ksBrillant);
            }
            ks[ksW] = (float) (ksG * y + ent);
            ksW = (ksW + 1) & mask;
            double out = y;
            if (o.ksCos > 0) {
                double cs = 0;
                for (int k = 0; k < 3; k++) {
                    double[] q = COS[k];
                    int j = k * 4;
                    double w = q[0] * y + q[1] * cos[j] + q[2] * cos[j + 1] - q[3] * cos[j + 2] - q[4] * cos[j + 3];
                    cos[j + 1] = cos[j];
                    cos[j] = y;
                    cos[j + 3] = cos[j + 2];
                    cos[j + 2] = w;
                    cs += w * q[5];
                }
                out = y * (1 - 0.35 * o.ksCos) + cs * o.ksCos * 2.2;
            }
            return out * 0.8;
        }

        /** La caixa: tres ressonàncies (passa-banda) com les d'una guitarra. {b0, b1, b2, a1, a2, pes}. */
        private static double[][] cosCoef() {
            double[][] fq = {{110, 3, 1.0}, {220, 2.5, 0.8}, {480, 2, 0.5}};
            double[][] r = new double[3][];
            for (int k = 0; k < 3; k++) {
                double w0 = 2 * Math.PI * fq[k][0] / MOSTREIG, al = Math.sin(w0) / (2 * fq[k][1]), a0 = 1 + al;
                r[k] = new double[]{al / a0, 0, -al / a0, -2 * Math.cos(w0) / a0, (1 - al) / a0, fq[k][2]};
            }
            return r;
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
