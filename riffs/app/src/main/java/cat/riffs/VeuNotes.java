package cat.riffs;

import java.util.ArrayList;
import java.util.List;

/**
 * La veu en directe → notes (2.44): rep el so del micròfon a trossets i diu quan comença i acaba cada nota,
 * ajustada a l'escala de la cançó. Pensat perquè el sinte soni mentre es canta.
 * <ul>
 * <li>Redueix a ~12 kHz (mitjana de blocs) i, cada 64 mostres (~5 ms), busca el to amb YIN en una finestra de ~33 ms
 * (fins a uns 65 Hz).</li>
 * <li>Hi ha veu si el so és prou fort (per sobre del soroll de fons, que es va aprenent) i periòdic.</li>
 * <li>La nota (ajustada a l'escala i amb l'octava triada) comença després de 2 finestres seguides amb veu; canvia
 * quan una altra nota s'aguanta 3 finestres i el to se n'allunya prou; s'acaba després de 4 finestres sense veu.
 * Així les inflexions petites no fan notes noves.</li>
 * </ul>
 * Els moments van en mostres de l'entrada (per saber, amb la marca de temps del micròfon, quan es va cantar).
 * No depèn d'Android: es prova amb Java d'escriptori.
 */
public final class VeuNotes {

    /** Un canvi: comença ({@code nota} ≥ 0) o s'acaba (nota = −1) una nota, al moment {@code mostra} de l'entrada. */
    public static final class Event {
        public final int nota;
        public final long mostra;
        public final int forca;
        public final boolean lligada;
        /**
         * La nota que sona no era la bona (la veu hi ha arribat lliscant des de sota o des de dalt): és la mateixa nota,
         * des del mateix moment, amb una altra altura. Al so es fa com un lligat; a la gravació, es canvia l'altura.
         */
        public final boolean correccio;

        Event(int nota, long mostra, int forca, boolean lligada) {
            this(nota, mostra, forca, lligada, false);
        }

        Event(int nota, long mostra, int forca, boolean lligada, boolean correccio) {
            this.nota = nota;
            this.mostra = mostra;
            this.forca = forca;
            this.lligada = lligada;
            this.correccio = correccio;
        }
    }

    static final int HOP = 64, W = 400, TAUMAX = 190;

    private final int sr, factor;
    private final double srD;          // freqüència reduïda
    private final float[] anell = new float[W];
    private int posAnell = 0;
    private long reduides = 0;          // mostres reduïdes rebudes
    private double acc = 0;
    private int nAcc = 0;
    private int desDelHop = 0;

    private boolean[] escala;          // null = totes les notes
    private int octava = 0;            // −2..+2
    // estat
    private int notaAra = -1;
    private int candidat = -1, vegadesCandidat = 0, ambVeu = 0, senseVeu = 0;
    private long iniciVeu = -1;
    private double fons = -60;         // soroll de fons (dBFS)
    private double picNota = -100;
    // to suavitzat (mediana dels 3 últims) i desafinació de la veu (s'aprèn mentre es canta)
    private final double[] darrers = new double[3];
    private int nDarrers = 0;
    private double desvia = 0;
    private double toSuau = Double.NaN, sumaNota = 0;
    private int nSuma = 0;
    private int hopsNota = 0;   // quantes finestres fa que sona la nota
    /** Desafinació general que s'ha après (semitons), per a la pantalla. */
    public double desviacio() {
        return desvia;
    }
    /** El darrer to detectat (MIDI amb decimals, o NaN) i el nivell (dBFS), per a la pantalla. */
    public volatile double toAra = Double.NaN, dbAra = -100;

    public VeuNotes(int sr) {
        this.sr = sr;
        this.factor = Math.max(1, (int) Math.round(sr / 12000.0));
        this.srD = sr / (double) factor;
    }

    public void posaEscala(boolean[] e) {
        escala = e;
    }

    public void posaOctava(int o) {
        octava = Math.max(-2, Math.min(2, o));
    }

    public int octava() {
        return octava;
    }

    public int notaAra() {
        return notaAra;
    }

    /** Retard propi del càlcul (ms): mitja finestra + un salt + les finestres per decidir. */
    public double retardCalculMs() {
        return (W / 2.0 + HOP * 3) * 1000 / srD;
    }

    /**
     * Afegeix {@code n} mostres de l'entrada (la primera és la mostra número {@code primera} des de l'inici).
     * Torna els canvis de nota que hi ha hagut.
     */
    public List<Event> afegeix(short[] x, int n, long primera) {
        List<Event> ev = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            acc += x[i];
            if (++nAcc < factor) continue;
            anell[posAnell] = (float) (acc / nAcc / 32768.0);
            posAnell = (posAnell + 1) % W;
            acc = 0;
            nAcc = 0;
            reduides++;
            if (++desDelHop >= HOP && reduides >= W) {
                desDelHop = 0;
                analitza(primera + i, ev);
            }
        }
        return ev;
    }

    private final float[] fin = new float[W];
    private final double[] d = new double[TAUMAX + 2];

    /** Analitza la finestra que acaba a la mostra d'entrada {@code fi}. */
    private void analitza(long fi, List<Event> ev) {
        for (int k = 0; k < W; k++) fin[k] = anell[(posAnell + k) % W];
        double e = 0;
        for (int k = 0; k < W; k++) e += fin[k] * fin[k];
        double db = 10 * Math.log10(e / W + 1e-12);
        dbAra = db;
        // YIN
        int lon = W - TAUMAX;
        d[0] = 0;
        double suma = 0;
        int millor = -1;
        double minim = 1;
        double[] cmn = new double[TAUMAX + 1];
        cmn[0] = 1;
        for (int tau = 1; tau <= TAUMAX; tau++) {
            double s = 0;
            for (int j = 0; j < lon; j++) {
                double v = fin[j] - fin[j + tau];
                s += v * v;
            }
            d[tau] = s;
            suma += s;
            cmn[tau] = suma > 0 ? s * tau / suma : 1;
        }
        int tauMin = (int) Math.floor(srD / 1100);
        for (int tau = Math.max(2, tauMin); tau < TAUMAX; tau++) {
            if (cmn[tau] < 0.15) {
                while (tau + 1 < TAUMAX && cmn[tau + 1] < cmn[tau]) tau++;
                millor = tau;
                minim = cmn[tau];
                break;
            }
        }
        if (millor < 0) {
            // cap per sota del llindar: el mínim absolut, si és prou bo
            for (int tau = Math.max(2, tauMin); tau < TAUMAX; tau++) if (cmn[tau] < minim) {
                minim = cmn[tau];
                millor = tau;
            }
            if (minim > 0.3) millor = -1;
        }
        double to = Double.NaN;
        if (millor > 1 && millor < TAUMAX) {
            double a = cmn[millor - 1], b = cmn[millor], c = cmn[millor + 1];
            double den = a - 2 * b + c;
            double t = millor + (den != 0 ? 0.5 * (a - c) / den : 0);
            double f = srD / t;
            if (f >= 65 && f <= 1100) to = 69 + 12 * Math.log(f / 440) / Math.log(2);
        }
        // soroll de fons: baixa de pressa, puja a poc a poc
        if (db < fons) fons = db;
        else fons += Math.min(db - fons, 0.02);
        boolean veu = !Double.isNaN(to) && db > Math.max(-55, fons + 12);
        if (veu) {
            // mediana dels 3 últims (treu els salts d'una sola finestra)
            darrers[nDarrers % 3] = to;
            nDarrers++;
            if (nDarrers >= 3) {
                double x = darrers[0], y = darrers[1], z = darrers[2];
                to = Math.max(Math.min(x, y), Math.min(Math.max(x, y), z));
            }
            // la mitjana de la nota (sense els primers ~80 ms, que són l'entrada), per aprendre la desafinació
            if (notaAra >= 0 && hopsNota * HOP * 1000.0 / srD > 80) {
                sumaNota += to;
                nSuma++;
            }
            to -= desvia;
            // to suavitzat (~40 ms) per decidir els canvis: el vibrat no fa saltar de nota
            toSuau = Double.isNaN(toSuau) ? to : toSuau + 0.124 * (to - toSuau);
        } else {
            nDarrers = 0;
        }
        toAra = veu ? to : Double.NaN;
        // als primers 100 ms de la nota, el to directe (l'entrada s'assenta de pressa); després, el suavitzat
        double toDecideix = Double.isNaN(toSuau) || hopsNota * HOP * 1000.0 / srD < 100 ? to : toSuau;

        long ara = fi - (long) (W / 2.0 * factor);   // el centre de la finestra
        if (veu) {
            senseVeu = 0;
            if (ambVeu == 0) iniciVeu = ara;
            ambVeu++;
            int nota = notaAra < 0 ? ajusta(to) : ajusta(toDecideix);
            if (nota == candidat) vegadesCandidat++;
            else {
                candidat = nota;
                vegadesCandidat = 1;
            }
            picNota = Math.max(picNota, db);
            hopsNota++;
            if (notaAra < 0) {
                if (vegadesCandidat >= 3) {
                    notaAra = candidat;
                    picNota = db;
                    hopsNota = 0;
                    toSuau = to;
                    sumaNota = 0;
                    nSuma = 0;
                    ev.add(new Event(notaAra, Math.max(iniciVeu, ara - 2L * HOP * factor), forca(db), false));
                }
            } else if (candidat != notaAra && vegadesCandidat >= 4 && Math.abs(toDecideix + 12 * octava - notaAra) > 0.85) {
                long quan = ara - 3L * HOP * factor;
                // durant els primers ~120 ms la veu encara s'està posant a la nota: es corregeix, no és una nota nova
                boolean correccio = hopsNota * HOP * 1000.0 / srD < 140;
                notaAra = candidat;
                picNota = db;
                if (!correccio) {
                    aprenDesviacio();
                    hopsNota = 0;
                }
                ev.add(new Event(notaAra, quan, forca(db), true, correccio));
            }
        } else {
            ambVeu = 0;
            candidat = -1;
            vegadesCandidat = 0;
            toSuau = Double.NaN;
            if (notaAra >= 0 && ++senseVeu >= 4) {
                aprenDesviacio();
                ev.add(new Event(-1, ara - 3L * HOP * factor, 0, false));
                notaAra = -1;
                picNota = -100;
            }
        }
    }

    /** En acabar una nota: la seva altura mitjana diu cap on va desafinada la veu (s'hi acosta a poc a poc). */
    private void aprenDesviacio() {
        if (nSuma >= 6) {
            double m = sumaNota / nSuma;
            double err = (m - desvia) - Math.round(m - desvia);
            desvia = Math.max(-0.45, Math.min(0.45, desvia + 0.35 * err));
        }
        sumaNota = 0;
        nSuma = 0;
    }

    /** Talla la nota que soni (en aturar). */
    public Event talla(long mostra) {
        if (notaAra < 0) return null;
        notaAra = -1;
        return new Event(-1, mostra, 0, false);
    }

    private int ajusta(double to) {
        int n = escala == null ? (int) Math.round(to) : Conversio.mesPropera(to, escala);
        n += 12 * octava;
        while (n < 24) n += 12;
        while (n > 96) n -= 12;
        return n;
    }

    private int forca(double db) {
        double sobre = db - fons;
        return sobre > 35 ? 3 : sobre > 22 ? 2 : 1;
    }
}
