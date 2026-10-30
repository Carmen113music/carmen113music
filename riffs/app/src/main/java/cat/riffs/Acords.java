package cat.riffs;

/**
 * Pads d'acords (com els del Cubase): a cada nota del teclat (Do, Do#, Re… Si) se li assigna un acord.
 * Quan la toques, sona tot l'acord, amb l'arrel a la nota més propera a la tecla. No depèn d'Android.
 */
public final class Acords {

    public static final class Tipus {
        public final String nom, sufix;
        public final int[] intervals;

        Tipus(String nom, String sufix, int... intervals) {
            this.nom = nom;
            this.sufix = sufix;
            this.intervals = intervals;
        }
    }

    public static final Tipus[] TIPUS = {
            new Tipus("Major", "", 0, 4, 7),
            new Tipus("Menor", "m", 0, 3, 7),
            new Tipus("Sèptima", "7", 0, 4, 7, 10),
            new Tipus("Sèptima major", "maj7", 0, 4, 7, 11),
            new Tipus("Menor sèptima", "m7", 0, 3, 7, 10),
            new Tipus("Sus2", "sus2", 0, 2, 7),
            new Tipus("Sus4", "sus4", 0, 5, 7),
            new Tipus("Sèptima sus4", "7sus4", 0, 5, 7, 10),
            new Tipus("Sexta", "6", 0, 4, 7, 9),
            new Tipus("Menor sexta", "m6", 0, 3, 7, 9),
            new Tipus("Add9", "add9", 0, 4, 7, 14),
            new Tipus("Menor add9", "madd9", 0, 3, 7, 14),
            new Tipus("Novena", "9", 0, 4, 7, 10, 14),
            new Tipus("Disminuït", "dim", 0, 3, 6),
            new Tipus("Semidisminuït", "m7b5", 0, 3, 6, 10),
            new Tipus("Augmentat", "aug", 0, 4, 8),
            new Tipus("Quinta (power)", "5", 0, 7, 12),
    };

    static final String[] NOTES = {"Do", "Do#", "Re", "Mib", "Mi", "Fa", "Fa#", "Sol", "Lab", "La", "Sib", "Si"};

    /** Per a cada nota del teclat (0 = Do … 11 = Si): l'arrel de l'acord (0–11) i el tipus. */
    public final int[] arrel = new int[12];
    public final int[] tipus = new int[12];

    /** Per defecte, els acords de Do major a les tecles blanques i acords majors a les negres. */
    public static Acords perDefecte() {
        Acords a = new Acords();
        int[] t = {0, 0, 1, 0, 1, 0, 0, 0, 0, 1, 0, 13};   // Do, Do#, Rem, Mib, Mim, Fa, Fa#, Sol, Lab, Lam, Sib, Sidim
        for (int i = 0; i < 12; i++) {
            a.arrel[i] = i;
            a.tipus[i] = t[i];
        }
        return a;
    }

    public static String nomNota(int pc) {
        return NOTES[Math.floorMod(pc, 12)];
    }

    /** El nom de l'acord de la tecla {@code pc} (per exemple «Lam», «Sol7»). */
    public String nom(int pc) {
        pc = Math.floorMod(pc, 12);
        return NOTES[arrel[pc]] + TIPUS[Math.max(0, Math.min(TIPUS.length - 1, tipus[pc]))].sufix;
    }

    /** Les notes de l'acord que fa sonar la tecla {@code nota}: l'arrel és la més propera a la tecla. */
    public int[] notes(int nota) {
        int pc = Math.floorMod(nota, 12);
        int dif = Math.floorMod(arrel[pc] - pc, 12);
        if (dif > 6) dif -= 12;
        int r = nota + dif;
        int[] iv = TIPUS[Math.max(0, Math.min(TIPUS.length - 1, tipus[pc]))].intervals;
        int[] n = new int[iv.length];
        for (int i = 0; i < iv.length; i++) n[i] = Math.max(0, Math.min(127, r + iv[i]));
        return n;
    }

    /** En text, per desar-ho: «0:0;1:1;…». */
    public String text() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            if (i > 0) sb.append(';');
            sb.append(arrel[i]).append(':').append(tipus[i]);
        }
        return sb.toString();
    }

    public static Acords deText(String s) {
        Acords a = perDefecte();
        if (s == null || s.isEmpty()) return a;
        String[] p = s.split(";");
        for (int i = 0; i < 12 && i < p.length; i++) {
            String[] c = p[i].split(":");
            try {
                a.arrel[i] = Math.floorMod(Integer.parseInt(c[0].trim()), 12);
                a.tipus[i] = Math.max(0, Math.min(TIPUS.length - 1, Integer.parseInt(c[1].trim())));
            } catch (RuntimeException ignorat) {
            }
        }
        return a;
    }
}
