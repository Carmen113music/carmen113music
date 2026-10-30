package cat.riffs;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Un banc de sons SoundFont 2 (.sf2): els sons gravats (mostres) i com fer-los sonar a cada nota (regions).
 * Es llegeix sencer a la memòria (un banc de 30 MB ocupa uns 30 MB). No depèn d'Android.
 * Només fa servir el que cal per a un reproductor senzill: mostres i bucles, afinació, volum, envolupants,
 * filtre i vibrato. No hi ha efectes (reverb, chorus) ni panoràmica (tot és mono).
 */
public final class Banc {

    // números dels generadors de l'SF2 que fem servir
    static final int G_START = 0, G_END = 1, G_LOOP_START = 2, G_LOOP_END = 3, G_START_COARSE = 4,
            G_MODLFO_PITCH = 5, G_VIBLFO_PITCH = 6, G_MODENV_PITCH = 7, G_FC = 8, G_Q = 9, G_MODLFO_FC = 10,
            G_MODENV_FC = 11, G_END_COARSE = 12, G_MODLFO_VOL = 13, G_DELAY_MODLFO = 21, G_FREQ_MODLFO = 22,
            G_DELAY_VIBLFO = 23, G_FREQ_VIBLFO = 24, G_DELAY_MODENV = 25, G_ATTACK_MODENV = 26, G_HOLD_MODENV = 27,
            G_DECAY_MODENV = 28, G_SUSTAIN_MODENV = 29, G_RELEASE_MODENV = 30, G_KEY_MODENV_HOLD = 31,
            G_KEY_MODENV_DECAY = 32, G_DELAY_VOLENV = 33, G_ATTACK_VOLENV = 34, G_HOLD_VOLENV = 35,
            G_DECAY_VOLENV = 36, G_SUSTAIN_VOLENV = 37, G_RELEASE_VOLENV = 38, G_KEY_VOLENV_HOLD = 39,
            G_KEY_VOLENV_DECAY = 40, G_INSTRUMENT = 41, G_KEY_RANGE = 43, G_VEL_RANGE = 44,
            G_LOOP_START_COARSE = 45, G_KEYNUM = 46, G_VELOCITY = 47, G_ATTENUATION = 48, G_LOOP_END_COARSE = 50,
            G_COARSE_TUNE = 51, G_FINE_TUNE = 52, G_SAMPLE_ID = 53, G_SAMPLE_MODES = 54, G_SCALE_TUNING = 56,
            G_EXCLUSIVE = 57, G_ROOT_KEY = 58;
    static final int N_GEN = 61;

    /** Valors per defecte dels generadors (SF2 2.01). */
    static final int[] DEFECTE = new int[N_GEN];

    static {
        DEFECTE[G_FC] = 13500;
        DEFECTE[G_DELAY_MODLFO] = -12000;
        DEFECTE[G_DELAY_VIBLFO] = -12000;
        for (int g = G_DELAY_MODENV; g <= G_RELEASE_MODENV; g++) DEFECTE[g] = -12000;
        DEFECTE[G_SUSTAIN_MODENV] = 0;
        for (int g = G_DELAY_VOLENV; g <= G_RELEASE_VOLENV; g++) DEFECTE[g] = -12000;
        DEFECTE[G_SUSTAIN_VOLENV] = 0;
        DEFECTE[G_KEY_RANGE] = 127 << 8;   // de 0 a 127 (lo al byte baix, hi a l'alt)
        DEFECTE[G_VEL_RANGE] = 127 << 8;
        DEFECTE[G_KEYNUM] = -1;
        DEFECTE[G_VELOCITY] = -1;
        DEFECTE[G_SCALE_TUNING] = 100;
        DEFECTE[G_ROOT_KEY] = -1;
    }

    /** Un so gravat del banc. */
    public static final class Mostra {
        public String nom;
        public int inici, fi, bucleInici, bucleFi, freq, notaOriginal, correccio;
    }

    /** Una zona: de quina nota a quina i de quina força a quina sona aquesta mostra, i amb quins valors. */
    public static final class Regio {
        public int notaMin, notaMax, velMin, velMax;
        public Mostra mostra;
        public final int[] gen = new int[N_GEN];
    }

    /** Un instrument del banc (un «preset» de l'SF2). */
    public static final class Programa {
        public String nom;
        public int banc, numero;
        public final List<Regio> regions = new ArrayList<>();

        /** Les regions que sonen per a aquesta nota i força. */
        public List<Regio> per(int nota, int vel) {
            List<Regio> r = new ArrayList<>();
            for (Regio g : regions) {
                if (nota >= g.notaMin && nota <= g.notaMax && vel >= g.velMin && vel <= g.velMax) r.add(g);
            }
            return r;
        }
    }

    public final String nom;
    public final short[] dades;
    private final Map<Integer, Programa> programes = new HashMap<>();
    private final List<Programa> llista = new ArrayList<>();

    private Banc(String nom, short[] dades) {
        this.nom = nom;
        this.dades = dades;
    }

    /** El programa (instrument) {@code numero} del banc {@code banc} (0 = General MIDI; 128 = bateries). */
    public Programa programa(int banc, int numero) {
        Programa p = programes.get(banc * 128 + numero);
        if (p == null && banc != 128) p = programes.get(numero);   // si no hi és, el del banc 0
        return p;
    }

    public List<Programa> programes() {
        return llista;
    }

    // ---------- Llegir ----------

    private static final Object PANY = new Object();
    private static volatile Banc actual;
    private static volatile File fitxerActual;
    private static volatile File perDefecte;

    /** On és el banc de l'app (ho diu l'app en començar). */
    public static void posaFitxer(File f) {
        perDefecte = f;
    }

    public static File fitxer() {
        return perDefecte;
    }

    public static boolean hiEs() {
        return perDefecte != null && perDefecte.isFile();
    }

    /** El banc carregat (el llegeix el primer cop; pot trigar un segon o dos). Null si no n'hi ha. */
    public static Banc actual() {
        File f = perDefecte;
        if (f == null || !f.isFile()) return null;
        Banc b = actual;
        if (b != null && f.equals(fitxerActual)) return b;
        synchronized (PANY) {
            if (actual != null && f.equals(fitxerActual)) return actual;
            try {
                actual = llegeix(f);
                fitxerActual = f;
            } catch (IOException | RuntimeException | OutOfMemoryError e) {
                actual = null;
            }
            return actual;
        }
    }

    /** Oblida el banc carregat (s'ha canviat el fitxer). */
    public static void oblida() {
        synchronized (PANY) {
            actual = null;
            fitxerActual = null;
        }
    }

    public static Banc llegeix(File f) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(f, "r"); FileChannel ch = raf.getChannel()) {
            MappedByteBuffer m = ch.map(FileChannel.MapMode.READ_ONLY, 0, ch.size());
            m.order(ByteOrder.LITTLE_ENDIAN);
            if (m.remaining() < 12 || m.getInt(0) != 0x46464952 || m.getInt(8) != 0x6b626673) {   // «RIFF» … «sfbk»
                throw new IOException("no és un banc SoundFont (.sf2)");
            }
            int fiRiff = (int) Math.min(ch.size(), 8L + (m.getInt(4) & 0xffffffffL));
            String nom = f.getName();
            int smplOff = -1, smplLen = 0;
            Map<String, int[]> pdta = new HashMap<>();
            for (int off = 12; off + 8 <= fiRiff; ) {
                String id = id(m, off);
                int len = m.getInt(off + 4);
                if (id.equals("LIST")) {
                    String tipus = id(m, off + 8);
                    for (int o = off + 12; o + 8 <= off + 8 + len; ) {
                        String id2 = id(m, o);
                        int len2 = m.getInt(o + 4);
                        if (tipus.equals("INFO") && id2.equals("INAM")) nom = text(m, o + 8, len2);
                        if (tipus.equals("sdta") && id2.equals("smpl")) {
                            smplOff = o + 8;
                            smplLen = len2;
                        }
                        if (tipus.equals("pdta")) pdta.put(id2, new int[]{o + 8, len2});
                        o += 8 + len2 + (len2 & 1);
                    }
                }
                off += 8 + len + (len & 1);
            }
            if (smplOff < 0 || !pdta.containsKey("phdr") || !pdta.containsKey("shdr")) {
                throw new IOException("al banc li falten parts (no és un SF2 complet o és un SF3 comprimit)");
            }
            short[] dades = new short[smplLen / 2];
            ByteBuffer s = m.duplicate().order(ByteOrder.LITTLE_ENDIAN);
            s.position(smplOff);
            s.limit(smplOff + dades.length * 2);
            s.slice().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(dades);
            Banc b = new Banc(nom, dades);
            b.llegeixPdta(m, pdta);
            return b;
        }
    }

    private void llegeixPdta(ByteBuffer m, Map<String, int[]> p) {
        // mostres
        int[] sh = p.get("shdr");
        int ns = sh[1] / 46;
        Mostra[] mostres = new Mostra[ns];
        for (int i = 0; i < ns; i++) {
            int o = sh[0] + i * 46;
            Mostra x = new Mostra();
            x.nom = text(m, o, 20);
            x.inici = m.getInt(o + 20);
            x.fi = m.getInt(o + 24);
            x.bucleInici = m.getInt(o + 28);
            x.bucleFi = m.getInt(o + 32);
            x.freq = m.getInt(o + 36);
            x.notaOriginal = m.get(o + 40) & 0xff;
            x.correccio = m.get(o + 41);
            mostres[i] = x;
        }
        // instruments: llista de zones (cada zona = generadors)
        List<List<int[]>> zonesInst = zones(m, p.get("inst"), 22, p.get("ibag"), p.get("igen"));
        List<List<int[]>> zonesPres = zones(m, p.get("phdr"), 38, p.get("pbag"), p.get("pgen"));
        int[] ph = p.get("phdr");
        int np = ph[1] / 38 - 1;   // l'últim és el final de la llista
        for (int i = 0; i < np; i++) {
            int o = ph[0] + i * 38;
            Programa pr = new Programa();
            pr.nom = text(m, o, 20);
            pr.numero = m.getShort(o + 20) & 0xffff;
            pr.banc = m.getShort(o + 22) & 0xffff;
            List<int[]> zp = zonesPres.get(i);
            int[] globalP = null;
            for (int z = 0; z < zp.size(); z++) {
                int[] g = zp.get(z);
                if (g[G_INSTRUMENT] < 0) {
                    if (z == 0) globalP = g;
                    continue;
                }
                int[] gp = barreja(globalP, g);
                int ii = g[G_INSTRUMENT];
                if (ii >= zonesInst.size()) continue;
                List<int[]> zi = zonesInst.get(ii);
                int[] globalI = null;
                for (int k = 0; k < zi.size(); k++) {
                    int[] gi0 = zi.get(k);
                    if (gi0[G_SAMPLE_ID] < 0) {
                        if (k == 0) globalI = gi0;
                        continue;
                    }
                    int[] gi = barreja(globalI, gi0);
                    int sid = gi[G_SAMPLE_ID];
                    if (sid >= ns) continue;
                    Regio r = new Regio();
                    r.mostra = mostres[sid];
                    // nota i força: la intersecció dels marges del preset i de l'instrument
                    r.notaMin = Math.max(lo(valor(gi, G_KEY_RANGE)), lo(valor(gp, G_KEY_RANGE)));
                    r.notaMax = Math.min(hi(valor(gi, G_KEY_RANGE)), hi(valor(gp, G_KEY_RANGE)));
                    r.velMin = Math.max(lo(valor(gi, G_VEL_RANGE)), lo(valor(gp, G_VEL_RANGE)));
                    r.velMax = Math.min(hi(valor(gi, G_VEL_RANGE)), hi(valor(gp, G_VEL_RANGE)));
                    if (r.notaMin > r.notaMax || r.velMin > r.velMax) continue;
                    for (int q = 0; q < N_GEN; q++) {
                        int v = valor(gi, q);
                        // al preset, els valors se sumen als de l'instrument (menys els que no tenen sentit)
                        if (q != G_KEY_RANGE && q != G_VEL_RANGE && q != G_SAMPLE_ID && q != G_INSTRUMENT
                                && q != G_SAMPLE_MODES && q != G_ROOT_KEY && q != G_EXCLUSIVE && q != G_KEYNUM && q != G_VELOCITY
                                && q != G_START && q != G_END && q != G_LOOP_START && q != G_LOOP_END && q != G_START_COARSE
                                && q != G_END_COARSE && q != G_LOOP_START_COARSE && q != G_LOOP_END_COARSE) {
                            if (gp[q] != NO) v += gp[q];
                        }
                        r.gen[q] = v;
                    }
                    pr.regions.add(r);
                }
            }
            if (pr.regions.isEmpty()) continue;
            int clau = pr.banc * 128 + pr.numero;
            if (!programes.containsKey(clau)) {
                programes.put(clau, pr);
                llista.add(pr);
            }
        }
    }

    /** Marca de «aquest generador no hi és». */
    static final int NO = Integer.MIN_VALUE;

    private static int valor(int[] g, int q) {
        return g[q] == NO ? DEFECTE[q] : g[q];
    }

    private static int lo(int rang) {
        return rang & 0xff;
    }

    private static int hi(int rang) {
        return (rang >> 8) & 0xff;
    }

    /** La zona global (si n'hi ha) amb la zona a sobre. */
    private static int[] barreja(int[] global, int[] zona) {
        int[] r = new int[N_GEN];
        for (int q = 0; q < N_GEN; q++) r[q] = zona[q] != NO ? zona[q] : global != null ? global[q] : NO;
        return r;
    }

    /**
     * Les zones de cada element (preset o instrument): per a cada zona, un vector amb els generadors que té
     * (NO si no hi és; el de l'instrument/mostra queda a -1 si no hi és).
     */
    private static List<List<int[]>> zones(ByteBuffer m, int[] cap, int midaCap, int[] bag, int[] gen) {
        List<List<int[]>> r = new ArrayList<>();
        int n = cap[1] / midaCap;
        int nBag = bag[1] / 4, nGen = gen[1] / 4;
        int indexBag = midaCap == 38 ? 24 : 20;
        for (int i = 0; i < n - 1; i++) {
            int b0 = m.getShort(cap[0] + i * midaCap + indexBag) & 0xffff;
            int b1 = m.getShort(cap[0] + (i + 1) * midaCap + indexBag) & 0xffff;
            List<int[]> zs = new ArrayList<>();
            for (int b = b0; b < b1 && b + 1 < nBag + 1 && b < nBag; b++) {
                int g0 = m.getShort(bag[0] + b * 4) & 0xffff;
                int g1 = b + 1 < nBag ? m.getShort(bag[0] + (b + 1) * 4) & 0xffff : nGen;
                int[] z = new int[N_GEN];
                java.util.Arrays.fill(z, NO);
                for (int g = g0; g < g1 && g < nGen; g++) {
                    int o = gen[0] + g * 4;
                    int op = m.getShort(o) & 0xffff;
                    if (op >= N_GEN) continue;
                    if (op == G_KEY_RANGE || op == G_VEL_RANGE) {
                        z[op] = (m.get(o + 2) & 0xff) | ((m.get(o + 3) & 0xff) << 8);
                    } else if (op == G_INSTRUMENT || op == G_SAMPLE_ID) {
                        z[op] = m.getShort(o + 2) & 0xffff;
                    } else {
                        z[op] = m.getShort(o + 2);
                    }
                }
                if (z[G_INSTRUMENT] == NO) z[G_INSTRUMENT] = -1;
                if (z[G_SAMPLE_ID] == NO) z[G_SAMPLE_ID] = -1;
                zs.add(z);
            }
            r.add(zs);
        }
        return r;
    }

    private static String id(ByteBuffer m, int off) {
        byte[] b = new byte[4];
        for (int i = 0; i < 4; i++) b[i] = m.get(off + i);
        return new String(b, StandardCharsets.ISO_8859_1);
    }

    private static String text(ByteBuffer m, int off, int len) {
        byte[] b = new byte[len];
        int n = 0;
        for (int i = 0; i < len; i++) {
            byte c = m.get(off + i);
            if (c == 0) break;
            b[n++] = c;
        }
        return new String(b, 0, n, StandardCharsets.ISO_8859_1).trim();
    }
}
