package cat.riffs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Prova de retard (latència): busca a la gravació on han sonat de debò els clics del metrònom
 * i ho compara amb on l'app es pensava que sonarien. La diferència és l'error que fa que
 * la bandera del primer temps quedi desplaçada.
 * No depèn d'Android: es pot provar amb Java d'escriptori.
 */
public final class Latencia {

    private Latencia() {}

    /** Resultat d'una prova. */
    public static final class Resultat {
        public int clics;                 // clics que s'havien de sentir
        public final List<Long> errors = new ArrayList<>(); // ms (positiu = el so arriba més tard)
        public double mediana, mitjana, desviacio;
        public long minim, maxim;

        public boolean valida() {
            return errors.size() >= Math.max(5, clics * 6 / 10);
        }

        /** Diferència entre la mesura més alta i la més baixa. */
        public long rang() {
            return maxim - minim;
        }

        /** Valoració curta en català. */
        public String valoracio() {
            if (!valida()) return "No s'han sentit prou clics. Apuja el volum o acosta el so al micròfon i torna-ho a provar.";
            if (rang() <= 10) return "Estable: es pot corregir amb un retard fix.";
            if (rang() <= 25) return "Força estable: un retard fix ho arreglaria gairebé del tot.";
            return "Variable: un retard fix no ho arreglaria del tot.";
        }

        /** «retard 42 ms · varia de 38 a 47 ms · 10/10 clics» */
        public String resum() {
            if (errors.isEmpty()) return "cap clic detectat (de " + clics + ")";
            return String.format(Locale.ROOT, "retard %d ms · varia de %d a %d ms (desv. %.1f) · %d/%d clics",
                    Math.round(mediana), minim, maxim, desviacio, errors.size(), clics);
        }

        /** Llista de totes les mesures, per al diagnòstic. */
        public String detall() {
            StringBuilder s = new StringBuilder();
            for (int i = 0; i < errors.size(); i++) {
                if (i > 0) s.append(' ');
                s.append(errors.get(i));
            }
            return s.toString();
        }
    }

    /** Freqüència del clic fort del metrònom (el que es fa servir a la prova). */
    public static final double FREQ_CLIC = 1760;

    /**
     * Mesura el retard de cada clic.
     * Escolta només la nota del clic (1.760 Hz): així el soroll de fons o una veu no l'enganyen tant.
     *
     * @param mostres    el so gravat (mono)
     * @param mostreig   mostres per segon
     * @param esperatsMs on l'app es pensava que sonaria cada clic dins la gravació (ms)
     */
    public static Resultat mesura(short[] mostres, int mostreig, long[] esperatsMs) {
        Resultat r = new Resultat();
        r.clics = esperatsMs.length;
        if (mostres == null || mostres.length == 0 || mostreig <= 0) return r;
        Detector d = new Detector(mostres, mostreig);

        // El mateix detector aplicat al clic net diu quant tarda a «adonar-se» del clic: es descompta
        short[] net = new short[mostreig / 5];
        short[] clic = Ritme.clic(FREQ_CLIC, 0.55, mostreig);
        int posNet = mostreig / 20;
        System.arraycopy(clic, 0, net, posNet, clic.length);
        Detector dn = new Detector(net, mostreig);
        long retardDetector = dn.inici(0, net.length, 0.3f) - posNet;

        // Soroll de fons: el nivell que supera el 30 % de la gravació (els clics en són una part petita)
        float soroll = d.percentil(0.3);

        for (long esperat : esperatsMs) {
            // El clic de debò pot arribar una mica abans o fins a 600 ms després
            long ini = Math.max(0, (esperat - 150) * mostreig / 1000);
            long fi = Math.min(mostres.length, (esperat + 600) * mostreig / 1000);
            if (fi - ini < mostreig / 50) continue;
            float max = d.maxim(ini, fi);
            // Ha de destacar clarament per sobre del soroll
            if (max < Math.max(0.003f, soroll * 6)) continue;
            float llindar = soroll + (max - soroll) * 0.3f;
            long on = d.primer(ini, fi, llindar);
            if (on < 0) continue;
            // Si just a l'inici de la finestra ja hi havia so, no és fiable
            if (on <= ini + mostreig / 500 && ini > 0) continue;
            long realMs = Math.round((on - retardDetector) * 1000.0 / mostreig);
            r.errors.add(realMs - esperat);
        }
        calcula(r);
        return r;
    }

    /**
     * Volum de la nota del clic a cada moment: el so passa per un filtre que només deixa
     * la zona dels 1.760 Hz (dues passades) i se'n pren el pic de cada mil·lisegon.
     */
    private static final class Detector {
        final float[] y;      // so filtrat, en valor absolut
        final float[] env;    // pic de cada ms
        final int perMs;

        Detector(short[] m, int mostreig) {
            y = new float[m.length];
            double w0 = 2 * Math.PI * FREQ_CLIC / mostreig;
            double alfa = Math.sin(w0) / (2 * 3.0);   // Q = 3
            double a0 = 1 + alfa;
            double b0 = alfa / a0, b2 = -alfa / a0, a1 = -2 * Math.cos(w0) / a0, a2 = (1 - alfa) / a0;
            double x1 = 0, x2 = 0, y1 = 0, y2 = 0, z1 = 0, z2 = 0, u1 = 0, u2 = 0;
            for (int i = 0; i < m.length; i++) {
                double x = m[i] / 32768.0;
                double v = b0 * x + b2 * x2 - a1 * y1 - a2 * y2;
                x2 = x1; x1 = x; y2 = y1; y1 = v;
                double u = b0 * v + b2 * z2 - a1 * u1 - a2 * u2;
                z2 = z1; z1 = v; u2 = u1; u1 = u;
                y[i] = (float) Math.abs(u);
            }
            perMs = Math.max(1, mostreig / 1000);
            env = new float[m.length / perMs];
            for (int i = 0; i < env.length; i++) {
                float p = 0;
                for (int k = 0; k < perMs; k++) p = Math.max(p, y[i * perMs + k]);
                env[i] = p;
            }
        }

        float maxim(long ini, long fi) {
            float m = 0;
            for (long i = ini / perMs; i < Math.min(env.length, fi / perMs); i++) m = Math.max(m, env[(int) i]);
            return m;
        }

        float percentil(double p) {
            if (env.length == 0) return 0;
            float[] v = env.clone();
            Arrays.sort(v);
            return v[(int) Math.min(v.length - 1, v.length * p)];
        }

        /**
         * Primera mostra on el volum passa el llindar i s'hi manté (un clic dura uns quants ms;
         * un espetec de soroll, no). Torna -1 si no n'hi ha cap.
         */
        long primer(long ini, long fi, float llindar) {
            int a = (int) (ini / perMs), b = (int) Math.min(env.length, fi / perMs);
            for (int i = a; i < b; i++) {
                if (env[i] < llindar) continue;
                int dura = 0;
                for (int k = i; k < Math.min(env.length, i + 6); k++) if (env[k] >= llindar * 0.5f) dura++;
                if (dura < 5) continue;
                // afina a la mostra dins aquest ms
                for (int k = 0; k < perMs; k++) if (y[i * perMs + k] >= llindar) return (long) i * perMs + k;
                return (long) i * perMs;
            }
            return -1;
        }

        /** Inici d'un clic net (per calcular el retard propi del filtre i del detector). */
        long inici(long ini, long fi, float fraccio) {
            float max = maxim(ini, fi);
            return Math.max(0, primer(ini, fi, max * fraccio));
        }
    }

    private static void calcula(Resultat r) {
        if (r.errors.isEmpty()) return;
        long[] v = new long[r.errors.size()];
        double suma = 0;
        for (int i = 0; i < v.length; i++) {
            v[i] = r.errors.get(i);
            suma += v[i];
        }
        Arrays.sort(v);
        r.minim = v[0];
        r.maxim = v[v.length - 1];
        r.mediana = v.length % 2 == 1 ? v[v.length / 2] : (v[v.length / 2 - 1] + v[v.length / 2]) / 2.0;
        r.mitjana = suma / v.length;
        double q = 0;
        for (long x : v) q += (x - r.mitjana) * (x - r.mitjana);
        r.desviacio = Math.sqrt(q / v.length);
    }
}
