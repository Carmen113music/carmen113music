package cat.riffs;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Retard del so (ms) desat per a cada sortida concreta: l'altaveu, uns auriculars Bluetooth concrets
 * (pel seu nom), uns de cable… Es pot mesurar amb la prova o ajustar a mà.
 * Es desa com a text (una línia per sortida) a les preferències.
 * No depèn d'Android: es pot provar amb Java d'escriptori.
 */
public final class Retards {

    public static final int MIN = -200, MAX = 1000;

    /** El retard d'una sortida. */
    public static final class Entrada {
        public String tipus;    // «altaveu», «bluetooth», «cable», «altre»
        public String nom;      // nom del dispositiu (buit per a l'altaveu o si no se sap)
        public double ms;
        public long data;       // quan es va mesurar o ajustar
        public boolean manual;  // ajustat a mà

        public String clau() {
            return Retards.clau(tipus, nom);
        }
    }

    private final List<Entrada> entrades = new ArrayList<>();

    public List<Entrada> entrades() {
        return entrades;
    }

    public static String clau(String tipus, String nom) {
        String t = tipus == null ? "altre" : tipus;
        // l'altaveu és sempre el mateix, encara que el mòbil li doni un nom
        String n = "altaveu".equals(t) || nom == null ? "" : nom.trim();
        return t + ":" + n;
    }

    /** La sortida exacta, o null. */
    public Entrada exacta(String tipus, String nom) {
        String k = clau(tipus, nom);
        for (Entrada e : entrades) if (e.clau().equals(k)) return e;
        return null;
    }

    /**
     * El retard que cal fer servir per a aquesta sortida: el seu, si s'ha mesurat o ajustat;
     * si no, la mitjana de les altres del mateix tipus (per exemple, uns altres auriculars Bluetooth);
     * si tampoc n'hi ha, NaN (no es corregeix res).
     */
    public double retard(String tipus, String nom) {
        Entrada e = exacta(tipus, nom);
        if (e != null) return e.ms;
        String t = tipus == null ? "altre" : tipus;
        double suma = 0;
        int n = 0;
        for (Entrada x : entrades) {
            if (x.tipus.equals(t)) {
                suma += x.ms;
                n++;
            }
        }
        return n == 0 ? Double.NaN : suma / n;
    }

    /** Desa (o canvia) el retard d'una sortida. Torna l'entrada. */
    public Entrada posa(String tipus, String nom, double ms, boolean manual, long ara) {
        Entrada e = exacta(tipus, nom);
        if (e == null) {
            e = new Entrada();
            e.tipus = tipus == null ? "altre" : tipus;
            e.nom = "altaveu".equals(e.tipus) || nom == null ? "" : nom.trim();
            entrades.add(e);
        }
        e.ms = Math.max(MIN, Math.min(MAX, ms));
        e.manual = manual;
        e.data = ara;
        return e;
    }

    public void treu(Entrada e) {
        entrades.remove(e);
    }

    // ---------- Text ----------

    /** Llegeix el text desat (línies «tipus \t nom \t ms \t data \t m|a»). */
    public static Retards llegeix(String text) {
        Retards r = new Retards();
        if (text == null || text.isEmpty()) return r;
        for (String linia : text.split("\n")) {
            String[] c = linia.split("\t", -1);
            if (c.length < 3) continue;
            try {
                Entrada e = new Entrada();
                e.tipus = c[0];
                e.nom = c[1];
                e.ms = Double.parseDouble(c[2]);
                e.data = c.length > 3 && !c[3].isEmpty() ? Long.parseLong(c[3]) : 0;
                e.manual = c.length > 4 && "m".equals(c[4]);
                if (r.exacta(e.tipus, e.nom) == null) r.entrades.add(e);
            } catch (NumberFormatException ignored) {
                // línia espatllada: es descarta
            }
        }
        return r;
    }

    public String text() {
        StringBuilder s = new StringBuilder();
        for (Entrada e : entrades) {
            if (s.length() > 0) s.append('\n');
            s.append(e.tipus).append('\t').append(neteja(e.nom)).append('\t')
                    .append(String.format(Locale.ROOT, "%.1f", e.ms)).append('\t')
                    .append(e.data).append('\t').append(e.manual ? "m" : "a");
        }
        return s.toString();
    }

    private static String neteja(String s) {
        return s == null ? "" : s.replace('\t', ' ').replace('\n', ' ');
    }

    /** «Auriculars Bluetooth (WH-1000XM4)», «Altaveu del mòbil»… */
    public static String nomVisible(String tipus, String nom) {
        String base;
        switch (tipus == null ? "" : tipus) {
            case "altaveu":
                return "Altaveu del mòbil";
            case "bluetooth":
                base = "Auriculars Bluetooth";
                break;
            case "cable":
                base = "Auriculars de cable";
                break;
            default:
                base = "Una altra sortida";
                break;
        }
        return nom == null || nom.trim().isEmpty() ? base : base + " (" + nom.trim() + ")";
    }
}
