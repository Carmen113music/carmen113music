package cat.riffs;

import java.util.ArrayList;
import java.util.List;

/**
 * El que es toca en directe amb el teclat virtual mentre es grava a l'Estudi: trossos «aquesta nota ha sonat
 * de tal moment a tal altre» (en ms de la línia de temps). En acabar, es quantitzen a la graella (per defecte,
 * semicorxeres) i es parteixen en frases d'un màxim de compassos, una per finestra de la pista de sinte.
 * No depèn d'Android: es pot provar amb Java d'escriptori.
 */
public final class GravacioNotes {

    /** Un tros tocat: de {@code iniciMs} a {@code fiMs} de la línia de temps. */
    public static final class Tros {
        public final double iniciMs;
        public double fiMs = -1;
        public final int nota, forca;

        Tros(double iniciMs, int nota, int forca) {
            this.iniciMs = iniciMs;
            this.nota = nota;
            this.forca = forca;
        }
    }

    /** Una frase per a una finestra: comença {@code compasDes} compassos després de l'entrada. */
    public static final class Frase {
        public int compasDes;
        public int compassos;
        public final List<Melodia.Nota> notes = new ArrayList<>();
    }

    private final List<Tros> trossos = new ArrayList<>();
    private Tros obert;

    /** La nota que sona ha canviat ({@code nota} &lt; 0 = ja no en sona cap) al moment {@code ms}. */
    public synchronized void canvi(int nota, int forca, double ms) {
        if (obert != null) {
            obert.fiMs = ms;
            obert = null;
        }
        if (nota >= 0) {
            obert = new Tros(ms, nota, forca);
            trossos.add(obert);
        }
    }

    /** Tanca la nota que encara sona (en aturar). */
    public synchronized void tanca(double ms) {
        canvi(-1, 0, ms);
    }

    public synchronized int compte() {
        return trossos.size();
    }

    /**
     * Les frases resultants. {@code entradaMs} = inici del primer compàs gravat; {@code ppc} = caselles per compàs.
     * Les notes que s'acaben abans de l'entrada (durant el compte) no compten; les que hi entren, comencen a l'entrada.
     * Si una nota passa d'una frase a la següent, es parteix.
     */
    public synchronized List<Frase> frases(double entradaMs, double msPerCompas, int ppc, int maxCompassos) {
        double msPas = msPerCompas / ppc;
        List<int[]> qs = new ArrayList<>();   // {pas, fi, nota, forca}, en caselles des de l'entrada
        int ultim = 0;
        for (Tros t : trossos) {
            if (t.fiMs < 0) continue;
            int a = (int) Math.round((t.iniciMs - entradaMs) / msPas);
            int b = (int) Math.round((t.fiMs - entradaMs) / msPas);
            if (b <= a) b = a + 1;           // un toc molt curt és, com a mínim, una casella
            if (b <= 0) continue;            // durant el compte
            a = Math.max(0, a);
            qs.add(new int[]{a, b, t.nota, t.forca});
            ultim = Math.max(ultim, b);
        }
        List<Frase> r = new ArrayList<>();
        if (qs.isEmpty()) return r;
        int totalCompassos = Math.max(1, (int) Math.ceil(ultim / (double) ppc));
        maxCompassos = Math.max(1, maxCompassos);
        for (int c0 = 0; c0 < totalCompassos; c0 += maxCompassos) {
            Frase f = new Frase();
            f.compasDes = c0;
            f.compassos = Math.min(maxCompassos, totalCompassos - c0);
            int ini = c0 * ppc, fi = (c0 + f.compassos) * ppc;
            Melodia m = new Melodia();
            m.passosPerCompas = ppc;
            m.compassos = f.compassos;
            for (int[] q : qs) {
                int a = Math.max(ini, q[0]), b = Math.min(fi, q[1]);
                if (b <= a) continue;
                m.posa(new Melodia.Nota(a - ini, b - a, q[2], q[3]));
            }
            if (m.notes.isEmpty()) continue;   // un tros sense res: no cal finestra
            f.notes.addAll(m.notes);
            r.add(f);
        }
        return r;
    }
}
