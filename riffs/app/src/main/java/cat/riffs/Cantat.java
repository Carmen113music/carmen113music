package cat.riffs;

import java.util.ArrayList;
import java.util.List;

/**
 * Cantar una melodia i convertir-la en notes d'un instrument (2.41).
 * <ol>
 * <li><b>Tonalitat de la base</b>: es fa amb el que sona a les altres pistes (les notes dels sintes, les notes i els
 * acords de les gravacions), no amb la veu: així, encara que la veu no sigui exacta, l'escala és la bona.</li>
 * <li><b>Afinació</b>: si tota la veu va una mica alta o baixa (per exemple 30 cèntims), es calcula aquest desplaçament
 * i es treu abans d'ajustar les notes a l'escala.</li>
 * <li><b>Notes</b>: les detecta {@link Analisi} a la veu gravada; {@link Conversio} les neteja (inflexions,
 * lliscaments, respiracions), les posa a l'escala i a la graella i en fa frases.</li>
 * </ol>
 * No depèn d'Android: es prova amb Java d'escriptori.
 */
public final class Cantat {

    private Cantat() {}

    /** Una tonalitat i d'on ha sortit. */
    public static final class Tonalitat {
        public int arrel;
        public boolean menor;
        public double claredat;
        /** true = de la base; false = de la mateixa veu (no hi havia res més). */
        public boolean deLaBase;

        public boolean[] escala() {
            int[] e = menor ? new int[]{0, 2, 3, 5, 7, 8, 10} : new int[]{0, 2, 4, 5, 7, 9, 11};
            boolean[] r = new boolean[12];
            for (int i : e) r[(arrel + i) % 12] = true;
            return r;
        }

        /** «La menor (Am)». */
        public String nom() {
            return Analisi.nomTonalitat(arrel, menor) + " (" + Analisi.NOTES_EN[arrel] + (menor ? "m" : "") + ")";
        }
    }

    /** Suma les notes d'una frase (durada en caselles × ms per casella) a l'histograma de notes. */
    public static void sumaMelodia(double[] h, Melodia m, double msPerCompas) {
        if (m == null) return;
        double msPas = msPerCompas / Math.max(1, m.passosPerCompas);
        for (Melodia.Nota n : m.notes) {
            // els baixos compten una mica més: diuen molt de la tonalitat
            double pes = n.nota < 48 ? 1.3 : 1;
            h[Math.floorMod(n.nota, 12)] += n.llarg * msPas * pes;
        }
    }

    /** Suma una gravació analitzada: acords (l'arrel compta més) i notes. */
    public static void sumaAnalisi(double[] h, Analisi.Resultat r) {
        if (r == null) return;
        for (Analisi.Acord a : r.acords) {
            double d = a.fiMs - a.iniciMs;
            for (int iv : Analisi.INTERVALS[a.tipus]) h[(a.arrel + iv) % 12] += d * (iv == 0 ? 1.5 : 1);
        }
        for (Analisi.Nota n : r.notes) h[Math.floorMod(n.midi, 12)] += 0.5 * (n.fiMs - n.iniciMs);
    }

    /**
     * Suma les notes de la veu (amb menys pes que la base: la veu pot anar desafinada, però diu molt de la tonalitat
     * quan la base és ambigua, per exemple només uns acords de La menor, Fa, Do i Sol).
     */
    public static void sumaVeu(double[] h, List<Analisi.Nota> notes, double pes) {
        double base = 0, veu = 0;
        for (double v : h) base += v;
        for (Analisi.Nota n : notes) veu += n.fiMs - n.iniciMs;
        if (veu <= 0) return;
        // la veu compta com una part fixa del total (si no hi ha base, és tota la informació)
        double f = base > 0 ? pes * base / veu : 1;
        // abans, es treu la desafinació general (respecte de les 12 notes), perquè una veu alta no sembli d'una altra tonalitat
        boolean[] totes = new boolean[12];
        java.util.Arrays.fill(totes, true);
        double d = afinacio(notes, totes);
        for (Analisi.Nota n : notes) h[Math.floorMod((int) Math.round(n.to - d), 12)] += f * (n.fiMs - n.iniciMs);
    }

    /** La tonalitat d'un histograma de notes (perfils de Krumhansl). Null si és buit. */
    public static Tonalitat tonalitat(double[] h) {
        double tot = 0;
        for (double v : h) tot += v;
        if (tot <= 0) return null;
        double[] corr = new double[24];
        for (int k = 0; k < 24; k++) {
            int arrel = k % 12;
            double[] perfil = k >= 12 ? Analisi.MENOR : Analisi.MAJOR;
            double[] rot = new double[12];
            for (int i = 0; i < 12; i++) rot[(arrel + i) % 12] = perfil[i];
            corr[k] = Analisi.pearson(h, rot);
        }
        int b1 = 0;
        for (int k = 1; k < 24; k++) if (corr[k] > corr[b1]) b1 = k;
        int b2 = b1 == 0 ? 1 : 0;
        for (int k = 0; k < 24; k++) if (k != b1 && corr[k] > corr[b2]) b2 = k;
        Tonalitat t = new Tonalitat();
        t.arrel = b1 % 12;
        t.menor = b1 >= 12;
        t.claredat = Math.max(0, Math.min(1, (corr[b1] - corr[b2]) * 4));
        t.deLaBase = true;
        return t;
    }

    /**
     * Quant va desafinada tota la veu respecte de l'escala (en semitons, −0,45 a +0,45): el desplaçament que, restat,
     * deixa les notes (ponderades per durada) més a prop de les notes de l'escala.
     */
    public static double afinacio(List<Analisi.Nota> notes, boolean[] escala) {
        if (notes.isEmpty() || escala == null) return 0;
        double millor = 0, errMin = Double.MAX_VALUE;
        for (int k = -45; k <= 45; k++) {
            double d = k / 100.0, err = 0, tot = 0;
            for (Analisi.Nota n : notes) {
                double to = n.to - d, pes = n.fiMs - n.iniciMs;
                int m = Conversio.mesPropera(to, escala);
                err += pes * (to - m) * (to - m);
                tot += pes;
            }
            err = err / Math.max(1, tot) + 0.02 * d * d;   // en cas d'empat, el desplaçament més petit
            if (err < errMin) {
                errMin = err;
                millor = d;
            }
        }
        return millor;
    }

    /**
     * La veu gravada (ja analitzada) → frases per a la pista.
     *
     * @param posMs   on cau l'inici de la gravació de la veu a la línia de temps
     * @param iniciMs a partir d'on es fa servir la gravació (just abans de l'entrada)
     * @param greu    la pista és d'un so de baix: les notes es porten a l'octava dels baixos
     */
    public static Conversio.Resultat converteix(Analisi.Resultat veu, Tonalitat to, long posMs, long iniciMs, long fiMs,
                                                double msPerCompas, int compas, int graella, int corrector, boolean greu) {
        Conversio.Opcions o = new Conversio.Opcions();
        o.graella = graella;
        o.corrector = corrector;
        o.escala = to == null ? null : to.escala();
        o.tonalitat = o.escala != null;
        o.afinacio = afinacio(veu.notes, o.escala);
        // octava: el baix va a l'octava dels baixos; si es canta molt greu per a un lead, una octava amunt
        double med = medianaMidi(veu.notes);
        if (!Double.isNaN(med)) {
            if (greu) {
                while (med + 12 * o.octava > 47) o.octava--;
                while (med + 12 * o.octava < 33) o.octava++;
            } else {
                while (med + 12 * o.octava < 52) o.octava++;
            }
        }
        Conversio.Resultat r = Conversio.notes(veu, posMs, iniciMs, fiMs, msPerCompas, compas, o);
        r.afinacio = o.afinacio;
        r.octava = o.octava;
        return r;
    }

    static double medianaMidi(List<Analisi.Nota> notes) {
        List<double[]> v = new ArrayList<>();
        double tot = 0;
        for (Analisi.Nota n : notes) {
            v.add(new double[]{n.to, n.fiMs - n.iniciMs});
            tot += n.fiMs - n.iniciMs;
        }
        if (v.isEmpty()) return Double.NaN;
        v.sort((a, b) -> Double.compare(a[0], b[0]));
        double acc = 0;
        for (double[] x : v) {
            acc += x[1];
            if (acc >= tot / 2) return x[0];
        }
        return v.get(v.size() / 2)[0];
    }
}
