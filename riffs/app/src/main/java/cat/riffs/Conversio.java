package cat.riffs;

import java.util.List;

/**
 * 2.41: la fa servir {@link Cantat} (cantar → notes). La 2.34–2.35 la feia servir per convertir clips a l'Estudi (descartat).
 * Converteix les notes detectades en un clip d'àudio (veu o riff) en frases per a una pista d'instrument
 * (sinte, Poli o MIDI), ajustades a la graella del projecte i, si es vol, a la tonalitat.
 * No depèn d'Android: es prova amb Java d'escriptori.
 */
public final class Conversio {

    private Conversio() {}

    /** Graella: 0 = corxeres, 1 = semicorxeres, 2 = fuses. */
    public static final String[] GRAELLES = {"Corxeres", "Semicorxeres", "Fuses"};

    /** Corrector de la melodia (2.35): neteja el que fa la veu abans de posar-ho a la graella. */
    public static final String[] CORRECTORS = {"No", "Suau", "Mig", "Fort"};

    public static final class Opcions {
        public int graella = 1;
        public int corrector = 2;
        public boolean tonalitat = true;
        public int octava = 0;      // −2..+2
        public boolean poli = false;
        /** 2.41: l'escala de la base (si no, la de la mateixa anàlisi). */
        public boolean[] escala;
        /** 2.41: desafinació general de la veu (semitons) que es treu abans d'ajustar les notes. */
        public double afinacio = 0;
    }

    public static final class Resultat {
        public int compasEntrada = 1;   // primer compàs (comptant des d'1)
        public List<GravacioNotes.Frase> frases;
        public int ppc;                 // caselles per compàs de les frases
        public int notes, corregides, netejades;
        public double afinacio;
        public int octava;
    }

    /**
     * @param an    l'anàlisi de la presa (temps dins el fitxer)
     * @param posMs on cau l'inici del fitxer a la línia de temps
     * @param iniciMs inici de la finestra del clip (dins el fitxer)
     * @param fiMs  final de la finestra del clip (dins el fitxer)
     */
    public static Resultat notes(Analisi.Resultat an, long posMs, long iniciMs, long fiMs,
                                 double msPerCompas, int compas, Opcions o) {
        Resultat r = new Resultat();
        int semis = Patro.passosDe(compas);                  // caselles de semicorxera per compàs
        r.ppc = o.graella == 2 ? semis * 2 : semis;
        double msQuant = msPerCompas / (o.graella == 0 ? semis / 2.0 : o.graella == 2 ? semis * 2.0 : semis);
        boolean[] escala = o.escala != null ? o.escala : o.tonalitat ? an.escala() : null;

        List<Analisi.Nota> llista = an.notes;
        if (o.afinacio != 0) {
            llista = new java.util.ArrayList<>();
            for (Analisi.Nota n : an.notes) {
                double to = n.to - o.afinacio;
                llista.add(new Analisi.Nota(n.iniciMs, n.fiMs, (int) Math.round(to), to));
            }
        }
        if (o.corrector > 0) {
            int[] corr = new int[1];
            int abans = llista.size();
            llista = corregeix(llista, o.corrector, msQuant, escala, corr);
            r.netejades = abans - llista.size();
            r.corregides = corr[0];
        }

        GravacioNotes g = new GravacioNotes();
        double primer = Double.MAX_VALUE;
        int notes = 0;
        for (Analisi.Nota n : llista) {
            long a = Math.max(n.iniciMs, iniciMs), b = Math.min(n.fiMs, fiMs);
            if (b - a < 40) continue;                            // fora de la finestra del clip
            double ta = posMs + a, tb = posMs + b;
            if (tb <= 0) continue;
            ta = Math.max(0, ta);
            // a la graella
            double qa = Math.round(ta / msQuant) * msQuant, qb = Math.round(tb / msQuant) * msQuant;
            if (qb <= qa) {
                if (tb - ta < msQuant * 0.6) continue;           // massa curta per a aquesta graella (sol ser un lliscament)
                qb = qa + msQuant;
            }
            int nota = n.midi;
            if (escala != null && !escala[Math.floorMod(nota, 12)]) {
                nota = mesPropera(n.to, escala);
                r.corregides++;
            }
            nota += 12 * o.octava;
            while (nota < 24) nota += 12;
            while (nota > 96) nota -= 12;
            g.encen(nota, 2, qa + 0.01);
            g.apaga(nota, qb - 0.01);
            primer = Math.min(primer, qa);
            notes++;
        }
        if (notes == 0) {
            r.frases = new java.util.ArrayList<>();
            return r;
        }
        int compasEntrada = (int) Math.floor(primer / msPerCompas + 1e-6);
        double entradaMs = compasEntrada * msPerCompas;
        r.compasEntrada = compasEntrada + 1;
        r.frases = g.frases(entradaMs, msPerCompas, r.ppc, Melodia.MAX_COMPASSOS, o.poli);
        int n = 0;
        for (GravacioNotes.Frase f : r.frases) n += f.notes.size();
        r.notes = n;
        return r;
    }

    /**
     * El corrector (com un Melodyne automàtic, però sobre les notes): treu el que la veu fa sense voler.
     * <ol>
     * <li>Salts d'octava: una nota que salta 10 semitons o més respecte de les del costat i que, una octava amunt
     * o avall, hi queda a prop, és un error del detector (o la veu que es trenca): es torna a l'octava bona.</li>
     * <li>Notes fora de rang: més lluny de 14 semitons (Fort: 10) de l'altura central de la melodia → s'hi acosten per octaves.</li>
     * <li>Notes massa curtes (lliscaments, cops de glota, síl·labes ràpides): s'afegeixen a la nota del costat més
     * semblant. Suau: &lt; 80 ms; Mig: &lt; mitja casella; Fort: &lt; una casella.</li>
     * <li>Notes iguals separades per un forat petit (una consonant, una respiració curta): s'ajunten.
     * Suau: forat &lt; 60 ms; Mig: &lt; 120 ms; Fort: &lt; una casella.</li>
     * <li>Fort: les notes que no són de la tonalitat i duren poc (un pas entre dues notes) es treuen, i els forats
     * curts entre notes s'omplen (línia lligada).</li>
     * </ol>
     */
    /** 2.44: la neteja per a les notes cantades en directe (en aturar la gravació). */
    public static List<Analisi.Nota> corregeixPublic(List<Analisi.Nota> entrada, int nivell, double msCasella, boolean[] escala) {
        return corregeix(entrada, nivell, msCasella, escala, null);
    }

    static List<Analisi.Nota> corregeix(List<Analisi.Nota> entrada, int nivell, double msCasella, boolean[] escala, int[] corregides) {
        java.util.ArrayList<double[]> v = new java.util.ArrayList<>();   // {inici, fi, to}
        for (Analisi.Nota n : entrada) v.add(new double[]{n.iniciMs, n.fiMs, n.to});
        if (v.isEmpty()) return new java.util.ArrayList<>();

        // 1) salts d'octava respecte dels veïns (mediana de fins a 3 a cada costat)
        for (int i = 0; i < v.size(); i++) {
            double[] vs = new double[6];
            int c = 0;
            for (int k = Math.max(0, i - 3); k <= Math.min(v.size() - 1, i + 3); k++) if (k != i) vs[c++] = v.get(k)[2];
            if (c < 2) continue;
            java.util.Arrays.sort(vs, 0, c);
            double med = c % 2 == 1 ? vs[c / 2] : (vs[c / 2 - 1] + vs[c / 2]) / 2;
            double to = v.get(i)[2];
            if (Math.abs(to - med) >= 10) {
                double millor = to;
                for (int oct = -2; oct <= 2; oct++) if (Math.abs(to + 12 * oct - med) < Math.abs(millor - med)) millor = to + 12 * oct;
                if (Math.abs(millor - med) <= 7) v.get(i)[2] = millor;
            }
        }
        // 2) rang: lluny del centre de la melodia (ponderat per durada)
        double[] tons = new double[v.size()];
        for (int i = 0; i < v.size(); i++) tons[i] = v.get(i)[2];
        double centre = medianaPonderada(v);
        double maxDist = nivell >= 3 ? 10 : 14;
        if (nivell >= 2) {
            for (double[] x : v) {
                while (x[2] - centre > maxDist) x[2] -= 12;
                while (centre - x[2] > maxDist) x[2] += 12;
            }
        }
        // 2b) a la tonalitat (si es vol): cada tros va a la nota de l'escala més propera. Es fa abans d'ajuntar,
        // perquè una nota cantada entre dues (La i La#) que el detector parteix en dos trossos quedi una sola nota
        if (escala != null) {
            for (double[] x : v) {
                if (!escala[Math.floorMod((int) Math.round(x[2]), 12)]) {
                    x[2] = mesPropera(x[2], escala);
                    if (corregides != null) corregides[0]++;
                }
            }
        }
        // 3) notes curtes: s'afegeixen al veí més semblant (si toca) o es treuen
        double minDur = nivell == 1 ? 80 : nivell == 2 ? Math.max(80, msCasella * 0.5) : Math.max(100, msCasella * 0.95);
        boolean canvi = true;
        while (canvi && v.size() > 1) {
            canvi = false;
            int pitjor = -1;
            double durMin = Double.MAX_VALUE;
            for (int i = 0; i < v.size(); i++) {
                double d = v.get(i)[1] - v.get(i)[0];
                if (d < minDur && d < durMin) {
                    durMin = d;
                    pitjor = i;
                }
            }
            if (pitjor < 0) break;
            double[] x = v.get(pitjor);
            double[] ant = pitjor > 0 ? v.get(pitjor - 1) : null, seg = pitjor + 1 < v.size() ? v.get(pitjor + 1) : null;
            boolean tocaAnt = ant != null && x[0] - ant[1] <= Math.max(60, msCasella * 0.5);
            boolean tocaSeg = seg != null && seg[0] - x[1] <= Math.max(60, msCasella * 0.5);
            double[] desti = null;
            if (tocaAnt && tocaSeg) desti = Math.abs(ant[2] - x[2]) <= Math.abs(seg[2] - x[2]) ? ant : seg;
            else if (tocaAnt) desti = ant;
            else if (tocaSeg) desti = seg;
            if (desti == ant && ant != null) ant[1] = Math.max(ant[1], x[1]);
            else if (desti == seg && seg != null) seg[0] = Math.min(seg[0], x[0]);
            v.remove(pitjor);
            canvi = true;
        }
        // 3b) Mig i Fort: entrades des de sota (la veu arriba a la nota lliscant des de més avall): un tros curt
        // just abans d'una nota llarga, 1 o 2 semitons per sota, és part d'aquella nota
        if (nivell >= 2) {
            for (int i = v.size() - 2; i >= 0; i--) {
                double[] a = v.get(i), b = v.get(i + 1);
                double da = a[1] - a[0], db = b[1] - b[0];
                double salt = Math.round(b[2]) - Math.round(a[2]);
                if (b[0] - a[1] <= 40 && salt >= 1 && salt <= 2 && da < Math.min(nivell >= 3 ? 320 : 250, db * 0.6)) {
                    b[0] = a[0];
                    v.remove(i);
                }
            }
        }
        // 4) ajunta notes iguals amb un forat petit
        double forat = nivell == 1 ? 60 : nivell == 2 ? 120 : Math.max(120, msCasella);
        for (int i = v.size() - 1; i > 0; i--) {
            double[] a = v.get(i - 1), b = v.get(i);
            if (Math.round(a[2]) == Math.round(b[2]) && b[0] - a[1] < forat) {
                a[1] = b[1];
                a[2] = (a[2] * (a[1] - a[0]) + b[2] * (b[1] - b[0])) / Math.max(1, (a[1] - a[0]) + (b[1] - b[0]));
                v.remove(i);
            }
        }
        // 5) Fort: notes de pas fora de la tonalitat, i línia lligada
        if (nivell >= 3) {
            if (escala != null) {
                for (int i = v.size() - 1; i >= 0 && v.size() > 1; i--) {
                    double[] x = v.get(i);
                    int nota = (int) Math.round(x[2]);
                    if (escala[Math.floorMod(nota, 12)] || x[1] - x[0] >= msCasella * 2) continue;
                    double[] ant = i > 0 ? v.get(i - 1) : null;
                    if (ant != null && x[0] - ant[1] < msCasella) ant[1] = x[1];
                    v.remove(i);
                }
            }
            for (int i = 0; i + 1 < v.size(); i++) {
                double[] a = v.get(i), b = v.get(i + 1);
                if (b[0] - a[1] > 0 && b[0] - a[1] < msCasella * 1.5) a[1] = b[0];
            }
        }
        java.util.ArrayList<Analisi.Nota> r = new java.util.ArrayList<>();
        for (double[] x : v) {
            r.add(new Analisi.Nota(Math.round(x[0]), Math.round(x[1]), (int) Math.round(x[2]), x[2]));
        }
        return r;
    }

    private static double medianaPonderada(List<double[]> v) {
        double[][] t = new double[v.size()][];
        double total = 0;
        for (int i = 0; i < v.size(); i++) {
            t[i] = new double[]{v.get(i)[2], v.get(i)[1] - v.get(i)[0]};
            total += t[i][1];
        }
        java.util.Arrays.sort(t, (a, b) -> Double.compare(a[0], b[0]));
        double acc = 0;
        for (double[] x : t) {
            acc += x[1];
            if (acc >= total / 2) return x[0];
        }
        return t[t.length / 2][0];
    }

    /** La nota de l'escala més propera a l'altura exacta {@code to}. */
    static int mesPropera(double to, boolean[] escala) {
        int base = (int) Math.round(to);
        int millor = base;
        double dist = Double.MAX_VALUE;
        for (int d = -2; d <= 2; d++) {
            int c = base + d;
            if (!escala[Math.floorMod(c, 12)]) continue;
            double dd = Math.abs(c - to);
            if (dd < dist) {
                dist = dd;
                millor = c;
            }
        }
        return millor;
    }
}
