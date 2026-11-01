package cat.riffs;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.ObjDoubleConsumer;
import java.util.function.ToDoubleFunction;

/**
 * Els controls de l'editor de so (2.32), per seccions, com en un sinte de debò: què mou cada botó, entre quins
 * valors, com es reparteix el recorregut (lineal, logarítmic o «cúbic» per als temps) i com s'escriu el valor.
 * No depèn d'Android.
 */
public final class SoParams {

    /** Lineal; logarítmic (freqüències, mínim > 0); cúbic (temps: més precisió als valors petits); enter; ona; llista de valors. */
    public static final int LIN = 0, LOG = 1, CUB = 2, ENTER = 3, ONA = 4, LLISTA = 5;

    public static final class Param {
        public final String clau, nom;
        public final int tipus;
        public final double min, max;
        final String unitat;
        final ToDoubleFunction<Sinte.So> get;
        final ObjDoubleConsumer<Sinte.So> set;
        /** Per a {@link #LLISTA}: els valors possibles i com es diuen. */
        public final double[] valors;
        public final String[] etiquetes;

        Param(String clau, String nom, int tipus, double min, double max, String unitat,
              ToDoubleFunction<Sinte.So> get, ObjDoubleConsumer<Sinte.So> set, double[] valors, String[] etiquetes) {
            this.clau = clau;
            this.nom = nom;
            this.tipus = tipus;
            this.min = min;
            this.max = max;
            this.unitat = unitat;
            this.get = get;
            this.set = set;
            this.valors = valors;
            this.etiquetes = etiquetes;
        }

        public double llegeix(Sinte.So o) {
            return get.applyAsDouble(o);
        }

        /** Posa el valor (ajustat als límits i, si cal, a un enter o al valor de la llista més proper). */
        public void posa(Sinte.So o, double v) {
            set.accept(o, ajusta(v));
        }

        public double ajustaPub(double v) {
            return ajusta(v);
        }

        double ajusta(double v) {
            if (tipus == LLISTA) return valors[indexDe(v)];
            v = Math.max(min, Math.min(max, v));
            if (tipus == ENTER || tipus == ONA) v = Math.round(v);
            return v;
        }

        int indexDe(double v) {
            int millor = 0;
            for (int i = 1; i < valors.length; i++) if (Math.abs(valors[i] - v) < Math.abs(valors[millor] - v)) millor = i;
            return millor;
        }

        /** On és el valor dins el recorregut del botó (0 a 1). */
        public double fraccio(double v) {
            if (tipus == LLISTA) return valors.length < 2 ? 0 : indexDe(v) / (double) (valors.length - 1);
            v = Math.max(min, Math.min(max, v));
            switch (tipus) {
                case LOG:
                    return Math.log(v / min) / Math.log(max / min);
                case CUB:
                    return Math.cbrt((v - min) / (max - min));
                default:
                    return (v - min) / (max - min);
            }
        }

        /** El valor que correspon a un punt del recorregut (0 a 1). */
        public double deFraccio(double f) {
            f = Math.max(0, Math.min(1, f));
            if (tipus == LLISTA) return valors[(int) Math.round(f * (valors.length - 1))];
            double v;
            switch (tipus) {
                case LOG:
                    v = min * Math.pow(max / min, f);
                    break;
                case CUB:
                    v = min + (max - min) * f * f * f;
                    break;
                default:
                    v = min + (max - min) * f;
                    break;
            }
            return ajusta(v);
        }

        /** El valor escrit per a la pantalla («1,2 kHz», «350 ms», «45 %», «+7 st»…). */
        public String text(double v) {
            v = ajusta(v);
            switch (unitat) {
                case "ona":
                    return Sinte.NOMS_ONA[(int) v];
                case "llista":
                    return etiquetes[indexDe(v)];
                case "Hz":
                    return v >= 1000 ? fmt(v / 1000, v >= 10000 ? 0 : 1) + " kHz" : fmt(v, v < 10 ? 1 : 0) + " Hz";
                case "ms":
                    return v >= 1000 ? fmt(v / 1000, 2) + " s" : fmt(v, 0) + " ms";
                case "%":
                    return fmt(v * 100, 0) + " %";
                case "st":
                    return (v > 0 ? "+" : "") + fmt(v, 0) + " st";
                case "ct":
                    return (v > 0 && min < 0 ? "+" : "") + fmt(v, 0) + " ct";
                case "oct":
                    return (v > 0 && min < 0 ? "+" : "") + fmt(v, 1) + " oct";
                case "s":
                    return fmt(v, v < 10 ? 1 : 0) + " s";
                case "dB":
                    return (v > 0 ? "+" : "") + fmt(v, 1) + " dB";
                case "x":
                    return fmt(v, 0);
                case "rat":
                    return fmt(v, v < 10 ? 1 : 0) + ":1";
                default:
                    return fmt(v, 2);
            }
        }
    }

    public static final class Seccio {
        public final String nom;
        public final List<Param> params = new ArrayList<>();
        /** Fila de l'editor (0 = dalt, 1 = baix). */
        public int fila;
        /** Vista especial (2.38): null = botons rodons; «algorisme», «taula», «tirants», «corda». */
        public String vista;
        /** Les llistes curtes (chorus, Leslie, percussió…) es mostren com a botons en lloc de rodons. */
        public boolean botons;
        /** Títol que depèn del so (p. ex. «Operador 2 · modula»); null = {@link #nom}. */
        public java.util.function.Function<Sinte.So, String> titol;

        Seccio(String nom) {
            this.nom = nom;
        }

        public String titol(Sinte.So o) {
            return titol != null && o != null ? titol.apply(o) : nom;
        }
    }

    private static String fmt(double v, int dec) {
        return String.format(Locale.ROOT, "%." + dec + "f", v).replace('.', ',');
    }

    public static final double[] TEMPS_DELAY = {0.25, 0.5, 0.75, 1, 1.5, 2};
    public static final String[] NOMS_DELAY = {"1/16", "1/8", "1/8 punt", "1/4", "1/4 punt", "1/2"};

    private static final List<Seccio> SECCIONS = crea();
    private static List<List<Seccio>> perMotor;

    /** Les seccions del motor analògic (les de sempre). */
    public static List<Seccio> seccions() {
        return SECCIONS;
    }

    /** Les seccions de l'editor d'un motor (2.38): cada sinte té els seus controls. */
    public static synchronized List<Seccio> seccions(int motor) {
        if (perMotor == null) {
            List<List<Seccio>> r = new ArrayList<>();
            r.add(SECCIONS);
            r.add(creaFm());
            r.add(creaWavetable());
            r.add(creaOrgue());
            r.add(creaCorda());
            perMotor = r;
        }
        return perMotor.get(Math.max(0, Math.min(perMotor.size() - 1, motor)));
    }

    public static final double[] RATIOS_FM = {0.5, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
    public static final String[] NOMS_TIRANTS = {"16'", "5\u2153'", "8'", "4'", "2\u2154'", "2'", "1\u2157'", "1\u2153'", "1'"};

    private static double[] indexs(int n) {
        double[] r = new double[n];
        for (int i = 0; i < n; i++) r[i] = i;
        return r;
    }

    private static Param llista(String clau, String nom, String[] noms, ToDoubleFunction<Sinte.So> get, ObjDoubleConsumer<Sinte.So> set) {
        return new Param(clau, nom, LLISTA, 0, noms.length - 1, "llista", get, set, indexs(noms.length), noms);
    }

    private static Seccio seccio(List<Seccio> r, String nom, int fila) {
        Seccio s = new Seccio(nom);
        s.fila = fila;
        r.add(s);
        return s;
    }

    // ----- peces comunes -----

    private static void lfo(List<Seccio> r, boolean filtre, boolean pols) {
        Seccio s = seccio(r, "LFO", 1);
        s.params.add(p("lfoHz", "Velocitat", LOG, 0.05, 20, "Hz", o -> o.lfoHz, (o, v) -> o.lfoHz = v));
        s.params.add(p("lfoTo", "Vibrato", LIN, 0, 100, "ct", o -> o.lfoTo, (o, v) -> o.lfoTo = v));
        if (filtre) s.params.add(p("lfoFiltre", "Filtre", LIN, 0, 3, "oct", o -> o.lfoFiltre, (o, v) -> o.lfoFiltre = v));
        if (pols) s.params.add(p("lfoPols", "PWM", LIN, 0, 1, "%", o -> o.lfoPols, (o, v) -> o.lfoPols = v));
        s.params.add(p("lfoRetard", "Retard", CUB, 0, 2000, "ms", o -> o.lfoRetardMs, (o, v) -> o.lfoRetardMs = v));
    }

    private static void efectes(List<Seccio> r, boolean delay) {
        Seccio s = seccio(r, "Chorus", 1);
        s.params.add(p("chorus", "Quantitat", LIN, 0, 1, "%", o -> o.chorus, (o, v) -> o.chorus = v));
        s.params.add(p("chorusHz", "Velocitat", LOG, 0.05, 8, "Hz", o -> o.chorusHz, (o, v) -> o.chorusHz = v));
        if (delay) {
            s = seccio(r, "Delay", 1);
            s.params.add(p("delay", "Quantitat", LIN, 0, 1, "%", o -> o.delay, (o, v) -> o.delay = v));
            s.params.add(new Param("delayTemps", "Temps", LLISTA, 0.25, 2, "llista", o -> o.delayTemps, (o, v) -> o.delayTemps = v,
                    TEMPS_DELAY, NOMS_DELAY));
            s.params.add(p("delayRep", "Repeteix", LIN, 0, 0.9, "%", o -> o.delayRepeticio, (o, v) -> o.delayRepeticio = v));
        }
        s = seccio(r, "Reverb", 1);
        s.params.add(p("reverb", "Quantitat", LIN, 0, 1, "%", o -> o.reverb, (o, v) -> o.reverb = v));
        s.params.add(p("reverbMida", "Sala", LIN, 0, 1, "%", o -> o.reverbMida, (o, v) -> o.reverbMida = v));
        s = seccio(r, "Sortida", 1);
        s.params.add(p("volum", "Volum", LIN, -30, 6, "dB", o -> o.volumDb, (o, v) -> o.volumDb = v));
    }

    private static Seccio juno(List<Seccio> r) {
        Seccio s = seccio(r, "Chorus I·II i passa-alts", 1);
        s.botons = true;
        s.params.add(llista("chorusMode", "Chorus", new String[]{"Normal", "I", "II", "I+II"}, o -> o.chorusMode, (o, v) -> o.chorusMode = (int) v));
        s.params.add(llista("hpf", "Passa-alts", new String[]{"0", "1", "2", "3"}, o -> o.hpf, (o, v) -> o.hpf = (int) v));
        return s;
    }

    // ----- FM -----

    private static List<Seccio> creaFm() {
        List<Seccio> r = new ArrayList<>();
        Seccio s = seccio(r, "Algorisme", 0);
        s.vista = "algorisme";
        s.params.add(p("fmAlg", "Algorisme", ENTER, 1, 8, "x", o -> o.fmAlg, (o, v) -> o.fmAlg = (int) v));
        s = seccio(r, "FM", 0);
        s.params.add(p("fmRealim", "Realim.", LIN, 0, 1, "%", o -> o.fmRealim, (o, v) -> o.fmRealim = v));
        s.params.add(p("fmForca", "Força", LIN, 0, 1, "%", o -> o.fmForca, (o, v) -> o.fmForca = v));
        for (int i = 0; i < 4; i++) {
            int k = i;
            s = seccio(r, "Operador " + (i + 1), i < 2 ? 0 : 1);
            s.titol = o -> "Operador " + (k + 1) + (o.fmPortador(k) ? " · sona" : " · modula");
            s.params.add(new Param("fm" + i + "ratio", "Ràtio", LLISTA, 0.5, 16, "llista", o -> o.fmRatio[k], (o, v) -> o.fmRatio[k] = v,
                    RATIOS_FM, nomsRatios()));
            s.params.add(p("fm" + i + "fi", "Fi", LIN, 0, 0.99, "%", o -> o.fmFi[k], (o, v) -> o.fmFi[k] = v));
            s.params.add(p("fm" + i + "desafina", "Desafina", LIN, -10, 10, "ct", o -> o.fmDesafina[k], (o, v) -> o.fmDesafina[k] = v));
            s.params.add(p("fm" + i + "nivell", "Nivell", LIN, 0, 1, "%", o -> o.fmNivell[k], (o, v) -> o.fmNivell[k] = v));
            s.params.add(p("fm" + i + "a", "Atac", CUB, 0, 5000, "ms", o -> o.fmA[k], (o, v) -> o.fmA[k] = v));
            s.params.add(p("fm" + i + "d", "Caiguda", CUB, 1, 10000, "ms", o -> o.fmD[k], (o, v) -> o.fmD[k] = v));
            s.params.add(p("fm" + i + "s", "Sost.", LIN, 0, 1, "%", o -> o.fmS[k], (o, v) -> o.fmS[k] = v));
            s.params.add(p("fm" + i + "r", "Final", CUB, 1, 5000, "ms", o -> o.fmR[k], (o, v) -> o.fmR[k] = v));
        }
        lfo(r, false, false);
        s = seccio(r, "Glide i drive", 1);
        s.params.add(p("glide", "Glide", CUB, 0, 1000, "ms", o -> o.glideMs, (o, v) -> o.glideMs = v));
        s.params.add(p("distorsio", "Distorsió", LIN, 0, 1, "%", o -> o.distorsio, (o, v) -> o.distorsio = v));
        efectes(r, true);
        return r;
    }

    private static String[] nomsRatios() {
        String[] n = new String[RATIOS_FM.length];
        for (int i = 0; i < n.length; i++) n[i] = "\u00d7" + (RATIOS_FM[i] == 0.5 ? "0,5" : Integer.toString((int) RATIOS_FM[i]));
        return n;
    }

    // ----- Wavetable -----

    private static List<Seccio> creaWavetable() {
        List<Seccio> r = new ArrayList<>();
        Seccio s = seccio(r, "Taula", 0);
        s.vista = "taula";
        s.params.add(llista("wtTaula", "Taula", Taules.NOMS, o -> o.wtTaula, (o, v) -> o.wtTaula = (int) v));
        s.params.add(p("wtPos", "Posició", LIN, 0, 1, "%", o -> o.wtPos, (o, v) -> o.wtPos = v));
        s = seccio(r, "Oscil·lador A", 0);
        s.params.add(p("wtPos", "Posició", LIN, 0, 1, "%", o -> o.wtPos, (o, v) -> o.wtPos = v));
        s.params.add(p("wtEnv", "Env→pos.", LIN, -1, 1, "%", o -> o.wtEnv, (o, v) -> o.wtEnv = v));
        s.params.add(p("wtLfo", "LFO→pos.", LIN, 0, 1, "%", o -> o.wtLfo, (o, v) -> o.wtLfo = v));
        s = seccio(r, "Unison", 0);
        s.params.add(p("unison", "Veus", ENTER, 1, 7, "x", o -> o.unison, (o, v) -> o.unison = (int) v));
        s.params.add(p("desafina", "Desafina", LIN, 0, 100, "ct", o -> o.desafina, (o, v) -> o.desafina = v));
        s = seccio(r, "Oscil·lador B", 0);
        s.params.add(p("ona2", "Ona", ONA, 0, 3, "ona", o -> o.ona2, (o, v) -> o.ona2 = (int) v));
        s.params.add(p("semitons2", "Semitons", ENTER, -24, 24, "st", o -> o.semitons2, (o, v) -> o.semitons2 = (int) v));
        s.params.add(p("cents2", "Fi", LIN, -50, 50, "ct", o -> o.cents2, (o, v) -> o.cents2 = v));
        s.params.add(p("mescla2", "Mescla", LIN, 0, 1, "%", o -> o.mescla2, (o, v) -> o.mescla2 = v));
        s = seccio(r, "Sub i soroll", 0);
        s.params.add(p("sub", "Sub", LIN, 0, 1, "%", o -> o.sub, (o, v) -> o.sub = v));
        s.params.add(p("soroll", "Soroll", LIN, 0, 1, "%", o -> o.soroll, (o, v) -> o.soroll = v));
        s = seccio(r, "Filtre", 1);
        s.params.add(p("tall", "Tall", LOG, 30, 18000, "Hz", o -> o.tallHz, (o, v) -> o.tallHz = v));
        s.params.add(p("resso", "Èmfasi", LIN, 0, 1, "%", o -> o.resso, (o, v) -> o.resso = v));
        s.params.add(p("envFiltre", "Env.", LIN, -4, 6, "oct", o -> o.envFiltre, (o, v) -> o.envFiltre = v));
        s.params.add(p("seguiment", "Segueix", LIN, 0, 1, "%", o -> o.seguiment, (o, v) -> o.seguiment = v));
        s = seccio(r, "Envolupant 2 · filtre i posició", 1);
        s.params.add(p("fa", "Atac", CUB, 0, 5000, "ms", o -> o.fa, (o, v) -> o.fa = v));
        s.params.add(p("fd", "Caiguda", CUB, 1, 5000, "ms", o -> o.fd, (o, v) -> o.fd = v));
        s.params.add(p("fs", "Sost.", LIN, 0, 1, "%", o -> o.fs, (o, v) -> o.fs = v));
        s.params.add(p("fr", "Final", CUB, 1, 5000, "ms", o -> o.fr, (o, v) -> o.fr = v));
        s = seccio(r, "Envolupant del volum", 1);
        s.params.add(p("a", "Atac", CUB, 0, 5000, "ms", o -> o.a, (o, v) -> o.a = v));
        s.params.add(p("d", "Caiguda", CUB, 1, 5000, "ms", o -> o.d, (o, v) -> o.d = v));
        s.params.add(p("s", "Sost.", LIN, 0, 1, "%", o -> o.s, (o, v) -> o.s = v));
        s.params.add(p("r", "Final", CUB, 1, 5000, "ms", o -> o.r, (o, v) -> o.r = v));
        lfo(r, true, false);
        s = seccio(r, "Glide i drive", 1);
        s.params.add(p("glide", "Glide", CUB, 0, 1000, "ms", o -> o.glideMs, (o, v) -> o.glideMs = v));
        s.params.add(p("distorsio", "Distorsió", LIN, 0, 1, "%", o -> o.distorsio, (o, v) -> o.distorsio = v));
        juno(r);
        efectes(r, true);
        return r;
    }

    // ----- Orgue -----

    private static List<Seccio> creaOrgue() {
        List<Seccio> r = new ArrayList<>();
        Seccio s = seccio(r, "Tirants", 0);
        s.vista = "tirants";
        for (int i = 0; i < 9; i++) {
            int k = i;
            s.params.add(p("tirant" + i, NOMS_TIRANTS[i], ENTER, 0, 8, "x", o -> o.tirants[k], (o, v) -> o.tirants[k] = (int) v));
        }
        s = seccio(r, "Percussió", 0);
        s.botons = true;
        s.params.add(llista("orgPerc", "Percussió", new String[]{"Apagada", "2a", "3a"}, o -> o.orgPerc, (o, v) -> o.orgPerc = (int) v));
        s.params.add(p("orgPercNivell", "Nivell", LIN, 0, 1, "%", o -> o.orgPercNivell, (o, v) -> o.orgPercNivell = v));
        s.params.add(p("orgPercMs", "Decau", CUB, 50, 2000, "ms", o -> o.orgPercMs, (o, v) -> o.orgPercMs = v));
        s = seccio(r, "Leslie", 0);
        s.botons = true;
        s.params.add(llista("leslie", "Velocitat", new String[]{"Apagat", "Lent", "Ràpid"}, o -> o.leslie, (o, v) -> o.leslie = (int) v));
        s = seccio(r, "Clic i drive", 1);
        s.params.add(p("orgClic", "Clic", LIN, 0, 1, "%", o -> o.orgClic, (o, v) -> o.orgClic = v));
        s.params.add(p("distorsio", "Drive", LIN, 0, 1, "%", o -> o.distorsio, (o, v) -> o.distorsio = v));
        s = seccio(r, "Tecles", 1);
        s.params.add(p("a", "Atac", CUB, 0, 1000, "ms", o -> o.a, (o, v) -> o.a = v));
        s.params.add(p("r", "Final", CUB, 1, 3000, "ms", o -> o.r, (o, v) -> o.r = v));
        s = seccio(r, "Vibrato", 1);
        s.params.add(p("lfoHz", "Velocitat", LOG, 0.05, 20, "Hz", o -> o.lfoHz, (o, v) -> o.lfoHz = v));
        s.params.add(p("lfoTo", "Vibrato", LIN, 0, 100, "ct", o -> o.lfoTo, (o, v) -> o.lfoTo = v));
        efectes(r, false);
        return r;
    }

    // ----- Corda pinçada -----

    public static final String[] NOMS_EXCITACIO = {"Púa", "Dit", "Martell", "Arc"};

    private static List<Seccio> creaCorda() {
        List<Seccio> r = new ArrayList<>();
        Seccio s = seccio(r, "Corda", 0);
        s.vista = "corda";
        s.params.add(p("ksPosicio", "Posició", LIN, 0.03, 0.5, "%", o -> o.ksPosicio, (o, v) -> o.ksPosicio = v));
        s.params.add(llista("ksTipus", "Com es toca", NOMS_EXCITACIO, o -> o.ksTipus, (o, v) -> o.ksTipus = (int) v));
        s = seccio(r, "Com es toca", 0);
        s.botons = true;
        s.params.add(llista("ksTipus", "Com es toca", NOMS_EXCITACIO, o -> o.ksTipus, (o, v) -> o.ksTipus = (int) v));
        s.params.add(p("ksBrillant", "Brillantor", LIN, 0, 1, "%", o -> o.ksBrillant, (o, v) -> o.ksBrillant = v));
        s.params.add(p("ksPosicio", "Posició", LIN, 0.03, 0.5, "%", o -> o.ksPosicio, (o, v) -> o.ksPosicio = v));
        s = seccio(r, "La corda", 0);
        s.params.add(p("ksDurada", "Durada", LOG, 0.1, 30, "s", o -> o.ksDurada, (o, v) -> o.ksDurada = v));
        s.params.add(p("ksAmortit", "Apaga aguts", LIN, 0, 1, "%", o -> o.ksAmortit, (o, v) -> o.ksAmortit = v));
        s.params.add(p("ksCos", "Caixa", LIN, 0, 1, "%", o -> o.ksCos, (o, v) -> o.ksCos = v));
        s = seccio(r, "Tecles", 1);
        s.params.add(p("a", "Atac", CUB, 0, 2000, "ms", o -> o.a, (o, v) -> o.a = v));
        s.params.add(p("r", "Final", CUB, 1, 5000, "ms", o -> o.r, (o, v) -> o.r = v));
        s = seccio(r, "Filtre", 1);
        s.params.add(p("tall", "Tall", LOG, 30, 18000, "Hz", o -> o.tallHz, (o, v) -> o.tallHz = v));
        s.params.add(p("resso", "Èmfasi", LIN, 0, 1, "%", o -> o.resso, (o, v) -> o.resso = v));
        lfo(r, false, false);
        s = seccio(r, "Glide i drive", 1);
        s.params.add(p("glide", "Glide", CUB, 0, 1000, "ms", o -> o.glideMs, (o, v) -> o.glideMs = v));
        s.params.add(p("distorsio", "Distorsió", LIN, 0, 1, "%", o -> o.distorsio, (o, v) -> o.distorsio = v));
        efectes(r, true);
        return r;
    }

    /** Totes les seccions, en l'ordre de la pantalla. */
    private static List<Seccio> crea() {
        List<Seccio> r = new ArrayList<>();
        Seccio s;

        s = new Seccio("Oscil·lador 1");
        s.params.add(p("ona1", "Ona", ONA, 0, 3, "ona", o -> o.ona1, (o, v) -> o.ona1 = (int) v));
        s.params.add(p("pols", "Pols", LIN, 0.05, 0.5, "%", o -> o.pols, (o, v) -> o.pols = v));
        r.add(s);

        s = new Seccio("Oscil·lador 2");
        s.params.add(p("ona2", "Ona", ONA, 0, 3, "ona", o -> o.ona2, (o, v) -> o.ona2 = (int) v));
        s.params.add(p("semitons2", "Semitons", ENTER, -24, 24, "st", o -> o.semitons2, (o, v) -> o.semitons2 = (int) v));
        s.params.add(p("cents2", "Fi", LIN, -50, 50, "ct", o -> o.cents2, (o, v) -> o.cents2 = v));
        s.params.add(p("mescla2", "Mescla", LIN, 0, 1, "%", o -> o.mescla2, (o, v) -> o.mescla2 = v));
        r.add(s);

        s = new Seccio("Sub i soroll");
        s.params.add(p("sub", "Sub", LIN, 0, 1, "%", o -> o.sub, (o, v) -> o.sub = v));
        s.params.add(p("soroll", "Soroll", LIN, 0, 1, "%", o -> o.soroll, (o, v) -> o.soroll = v));
        r.add(s);

        s = new Seccio("Unison");
        s.params.add(p("unison", "Veus", ENTER, 1, 7, "x", o -> o.unison, (o, v) -> o.unison = (int) v));
        s.params.add(p("desafina", "Desafina", LIN, 0, 100, "ct", o -> o.desafina, (o, v) -> o.desafina = v));
        r.add(s);

        s = new Seccio("Filtre");
        s.params.add(p("tall", "Tall", LOG, 30, 18000, "Hz", o -> o.tallHz, (o, v) -> o.tallHz = v));
        s.params.add(p("resso", "Èmfasi", LIN, 0, 1, "%", o -> o.resso, (o, v) -> o.resso = v));
        s.params.add(p("envFiltre", "Env.", LIN, -4, 6, "oct", o -> o.envFiltre, (o, v) -> o.envFiltre = v));
        s.params.add(p("seguiment", "Segueix", LIN, 0, 1, "%", o -> o.seguiment, (o, v) -> o.seguiment = v));
        r.add(s);

        s = new Seccio("Envolupant del filtre");
        s.params.add(p("fa", "Atac", CUB, 0, 5000, "ms", o -> o.fa, (o, v) -> o.fa = v));
        s.params.add(p("fd", "Caiguda", CUB, 1, 5000, "ms", o -> o.fd, (o, v) -> o.fd = v));
        s.params.add(p("fs", "Sost.", LIN, 0, 1, "%", o -> o.fs, (o, v) -> o.fs = v));
        s.params.add(p("fr", "Final", CUB, 1, 5000, "ms", o -> o.fr, (o, v) -> o.fr = v));
        r.add(s);

        s = new Seccio("Envolupant del volum");
        s.params.add(p("a", "Atac", CUB, 0, 5000, "ms", o -> o.a, (o, v) -> o.a = v));
        s.params.add(p("d", "Caiguda", CUB, 1, 5000, "ms", o -> o.d, (o, v) -> o.d = v));
        s.params.add(p("s", "Sost.", LIN, 0, 1, "%", o -> o.s, (o, v) -> o.s = v));
        s.params.add(p("r", "Final", CUB, 1, 5000, "ms", o -> o.r, (o, v) -> o.r = v));
        r.add(s);

        s = new Seccio("LFO");
        s.params.add(p("lfoHz", "Velocitat", LOG, 0.05, 20, "Hz", o -> o.lfoHz, (o, v) -> o.lfoHz = v));
        s.params.add(p("lfoTo", "Vibrato", LIN, 0, 100, "ct", o -> o.lfoTo, (o, v) -> o.lfoTo = v));
        s.params.add(p("lfoFiltre", "Filtre", LIN, 0, 3, "oct", o -> o.lfoFiltre, (o, v) -> o.lfoFiltre = v));
        s.params.add(p("lfoPols", "PWM", LIN, 0, 1, "%", o -> o.lfoPols, (o, v) -> o.lfoPols = v));
        s.params.add(p("lfoRetard", "Retard", CUB, 0, 2000, "ms", o -> o.lfoRetardMs, (o, v) -> o.lfoRetardMs = v));
        r.add(s);

        s = new Seccio("Glide i drive");
        s.params.add(p("glide", "Glide", CUB, 0, 1000, "ms", o -> o.glideMs, (o, v) -> o.glideMs = v));
        s.params.add(p("distorsio", "Distorsió", LIN, 0, 1, "%", o -> o.distorsio, (o, v) -> o.distorsio = v));
        r.add(s);

        r.add(null);   // aquí va el chorus Juno i el passa-alts (2.38)

        s = new Seccio("Chorus");
        s.params.add(p("chorus", "Quantitat", LIN, 0, 1, "%", o -> o.chorus, (o, v) -> o.chorus = v));
        s.params.add(p("chorusHz", "Velocitat", LOG, 0.05, 8, "Hz", o -> o.chorusHz, (o, v) -> o.chorusHz = v));
        r.add(s);

        s = new Seccio("Delay");
        s.params.add(p("delay", "Quantitat", LIN, 0, 1, "%", o -> o.delay, (o, v) -> o.delay = v));
        s.params.add(new Param("delayTemps", "Temps", LLISTA, 0.25, 2, "llista", o -> o.delayTemps, (o, v) -> o.delayTemps = v,
                TEMPS_DELAY, NOMS_DELAY));
        s.params.add(p("delayRep", "Repeteix", LIN, 0, 0.9, "%", o -> o.delayRepeticio, (o, v) -> o.delayRepeticio = v));
        r.add(s);

        s = new Seccio("Reverb");
        s.params.add(p("reverb", "Quantitat", LIN, 0, 1, "%", o -> o.reverb, (o, v) -> o.reverb = v));
        s.params.add(p("reverbMida", "Sala", LIN, 0, 1, "%", o -> o.reverbMida, (o, v) -> o.reverbMida = v));
        r.add(s);

        s = new Seccio("Sortida");
        s.params.add(p("volum", "Volum", LIN, -30, 6, "dB", o -> o.volumDb, (o, v) -> o.volumDb = v));
        r.add(s);
        int i = r.indexOf(null);
        List<Seccio> j = new ArrayList<>();
        juno(j);
        r.set(i, j.get(0));
        for (int k = 0; k < r.size(); k++) r.get(k).fila = k < 6 ? 0 : 1;   // sis seccions a la primera fila, com abans
        return r;
    }

    private static Param p(String clau, String nom, int tipus, double min, double max, String unitat,
                           ToDoubleFunction<Sinte.So> get, ObjDoubleConsumer<Sinte.So> set) {
        return new Param(clau, nom, tipus, min, max, unitat, get, set, null, null);
    }

    /** Totes les claus, en ordre (per comprovar que no se n'oblida cap). */
    public static List<Param> tots() {
        List<Param> r = new ArrayList<>();
        for (Seccio s : SECCIONS) r.addAll(s.params);
        return r;
    }
}
