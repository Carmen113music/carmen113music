package cat.riffs;

import java.util.List;

/**
 * Converteix les notes detectades en un clip d'àudio (veu o riff) en frases per a una pista d'instrument
 * (sinte, Poli o MIDI), ajustades a la graella del projecte i, si es vol, a la tonalitat.
 * No depèn d'Android: es prova amb Java d'escriptori.
 */
public final class Conversio {

    private Conversio() {}

    /** Graella: 0 = corxeres, 1 = semicorxeres, 2 = fuses. */
    public static final String[] GRAELLES = {"Corxeres", "Semicorxeres", "Fuses"};

    public static final class Opcions {
        public int graella = 1;
        public boolean tonalitat = true;
        public int octava = 0;      // −2..+2
        public boolean poli = false;
    }

    public static final class Resultat {
        public int compasEntrada = 1;   // primer compàs (comptant des d'1)
        public List<GravacioNotes.Frase> frases;
        public int ppc;                 // caselles per compàs de les frases
        public int notes, corregides;
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
        boolean[] escala = o.tonalitat ? an.escala() : null;

        GravacioNotes g = new GravacioNotes();
        double primer = Double.MAX_VALUE;
        int notes = 0;
        for (Analisi.Nota n : an.notes) {
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
