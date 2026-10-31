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
                case "dB":
                    return (v > 0 ? "+" : "") + fmt(v, 1) + " dB";
                case "x":
                    return fmt(v, 0);
                default:
                    return fmt(v, 2);
            }
        }
    }

    public static final class Seccio {
        public final String nom;
        public final List<Param> params = new ArrayList<>();

        Seccio(String nom) {
            this.nom = nom;
        }
    }

    private static String fmt(double v, int dec) {
        return String.format(Locale.ROOT, "%." + dec + "f", v).replace('.', ',');
    }

    public static final double[] TEMPS_DELAY = {0.25, 0.5, 0.75, 1, 1.5, 2};
    public static final String[] NOMS_DELAY = {"1/16", "1/8", "1/8 punt", "1/4", "1/4 punt", "1/2"};

    private static final List<Seccio> SECCIONS = crea();

    public static List<Seccio> seccions() {
        return SECCIONS;
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
