package cat.riffs;

/**
 * Pads d'acords (com els del Cubase): 12 pads, un per nota d'una octava (Do, Do#, Re… Si, de baix a dalt i
 * d'esquerra a dreta com en un piano). Cada pad té un acord: fonamental + tipus + tensions + nota greu (opcional),
 * en notació anglesa (C, Cm7, Bbmaj7, G7(b9), G/B…). Els presets posen els seus acords en ordre als pads 1, 2, 3…
 * (C, C#, D…). No depèn d'Android.
 */
public final class Acords {

    public static final int PADS = 12;

    // ---------- Un acord ----------

    public static final String[] TIPUS = {"maj", "min", "dim", "sus4", "sus2", "aug"};
    private static final int[][] TRIADES = {{0, 4, 7}, {0, 3, 7}, {0, 3, 6}, {0, 5, 7}, {0, 2, 7}, {0, 4, 8}};
    public static final int MAJ = 0, MIN = 1, DIM = 2, SUS4 = 3, SUS2 = 4, AUG = 5;

    /** Tensions (es poden combinar): 7, j7 (sèptima major), b9, 9, #9, 11, b5/#11, #5/b13, 6/13. */
    public static final String[] TENSIONS = {"7", "j7", "b9", "9", "#9", "11", "b5/#11", "#5/b13", "6/13"};
    public static final int T7 = 0, TJ7 = 1, TB9 = 2, T9 = 3, TS9 = 4, T11 = 5, TB5 = 6, TS5 = 7, T6 = 8;

    static final String[] SOSTINGUTS = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
    static final String[] BEMOLLS = {"C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B"};
    /** Com s'escriu cada nota si no es diu res (la manera més habitual). */
    static final String[] NOTES = {"C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B"};
    /** Els noms en català de les notes (per a les tecles). */
    static final String[] NOTES_CAT = {"Do", "Do#", "Re", "Mib", "Mi", "Fa", "Fa#", "Sol", "Lab", "La", "Sib", "Si"};

    public static final class Acord {
        /** 0–11 (C … B); -1 = pad buit. */
        public int arrel = -1;
        /** Com s'escriu l'arrel: 0 = la manera habitual, 1 = amb sostingut, 2 = amb bemoll. */
        public int grafia = 0;
        public int tipus = MAJ;
        /** Bits de {@link #TENSIONS}. */
        public int tensions = 0;
        /** Nota greu (0–11) o -1 = la mateixa arrel. */
        public int baix = -1;
        /**
         * Inversió (2.32.1): quantes vegades la nota més greu de l'acord puja una octava (positiu) o la més aguda
         * en baixa una (negatiu). Així un acord sona més amunt o més avall sense canviar. −4 a +4.
         */
        public int inversio = 0;
        public static final int MAX_INVERSIO = 4;

        public boolean buit() {
            return arrel < 0;
        }

        public boolean te(int t) {
            return (tensions & (1 << t)) != 0;
        }

        public void commuta(int t) {
            tensions ^= 1 << t;
            // 7 i j7 no van junts
            if (t == T7 && te(T7)) tensions &= ~(1 << TJ7);
            if (t == TJ7 && te(TJ7)) tensions &= ~(1 << T7);
        }

        public Acord copia() {
            Acord a = new Acord();
            a.arrel = arrel;
            a.grafia = grafia;
            a.tipus = tipus;
            a.tensions = tensions;
            a.baix = baix;
            a.inversio = inversio;
            return a;
        }

        private static String nota(int pc, int grafia) {
            pc = Math.floorMod(pc, 12);
            return grafia == 1 ? SOSTINGUTS[pc] : grafia == 2 ? BEMOLLS[pc] : NOTES[pc];
        }

        /** Els intervals des de l'arrel (sense la nota greu). */
        public int[] intervals() {
            boolean altes = te(TB9) || te(T9) || te(TS9) || te(T11);
            int[] tri = TRIADES[Math.max(0, Math.min(TRIADES.length - 1, tipus))];
            java.util.TreeSet<Integer> s = new java.util.TreeSet<>();
            for (int i : tri) s.add(i);
            int quinta = tri[2];
            if (te(TB5)) {
                if (altes || te(TS5)) s.add(18);          // #11
                else { s.remove(quinta); s.add(6); }      // b5
            }
            if (te(TS5)) {
                if (altes) s.add(20);                     // b13
                else { s.remove(quinta); s.add(8); }      // #5
            }
            if (te(T7)) s.add(tipus == DIM ? 9 : 10);
            if (te(TJ7)) s.add(11);
            if (te(T6)) s.add(te(T7) || te(TJ7) ? 21 : 9);
            if (te(TB9)) s.add(13);
            if (te(T9)) s.add(14);
            if (te(TS9)) s.add(15);
            if (te(T11)) s.add(17);
            int[] r = new int[s.size()];
            int k = 0;
            for (int i : s) r[k++] = i;
            return r;
        }

        /** El nom en notació anglesa: «C», «Cm7», «Bbmaj7», «Eb9», «G7(b9)», «C/E»… */
        public String nom() {
            if (buit()) return "";
            StringBuilder sb = new StringBuilder(nota(arrel, grafia));
            boolean s7 = te(T7), j7 = te(TJ7);
            java.util.List<String> extra = new java.util.ArrayList<>();
            boolean nou = te(T9);
            boolean nouAbsorbit = false;
            switch (tipus) {
                case MIN:
                    sb.append('m');
                    if (s7 && te(TB5) && !te(TS5) && !(te(TB9) || nou || te(TS9) || te(T11))) {
                        sb.append("7b5");
                        s7 = false;
                    }
                    break;
                case DIM:
                    sb.append("dim");
                    break;
                case AUG:
                    sb.append("aug");
                    break;
                default:
                    break;
            }
            boolean b5Absorbit = tipus == MIN && te(T7) && !s7;
            if (s7 || j7) {
                String set = j7 ? "maj" : "";
                // 7 + 9 sense res més = novena (C9, Cm9, Cmaj9)
                if (nou && !te(TB9) && !te(TS9) && !te(T11) && !te(T6)) {
                    sb.append(set).append('9');
                    nouAbsorbit = true;
                } else {
                    sb.append(set).append('7');
                }
            } else if (te(T6)) {
                sb.append('6');
            } else if (nou && tensions == (1 << T9)) {
                sb.append("add9");
                nouAbsorbit = true;
            }
            if (tipus == SUS4) sb.append("sus4");
            if (tipus == SUS2) sb.append("sus2");
            boolean seisAbsorbit = te(T6) && !(te(T7) || j7);
            if (te(TB9)) extra.add("b9");
            if (nou && !nouAbsorbit) extra.add("9");
            if (te(TS9)) extra.add("#9");
            if (te(T11)) extra.add("11");
            if (te(TB5) && !b5Absorbit) extra.add(te(TB9) || nou || te(TS9) || te(T11) || te(TS5) ? "#11" : "b5");
            if (te(TS5)) extra.add(te(TB9) || nou || te(TS9) || te(T11) ? "b13" : "#5");
            if (te(T6) && !seisAbsorbit) extra.add("13");
            if (!extra.isEmpty()) sb.append('(').append(String.join(",", extra)).append(')');
            if (baix >= 0 && baix != arrel) sb.append('/').append(nota(baix, grafia == 0 ? 0 : grafia));
            return sb.toString();
        }

        /**
         * Les notes (MIDI) a partir del Do {@code doBase}: l'arrel queda dins l'octava que comença a {@code doBase};
         * la nota greu, si n'hi ha, a l'octava de sota.
         */
        public int[] notes(int doBase) {
            if (buit()) return new int[0];
            int r = doBase + arrel;
            int[] iv = intervals();
            int extra = baix >= 0 && baix != arrel ? 1 : 0;
            int[] n = new int[iv.length + extra];
            int[] ac = new int[iv.length];
            for (int i = 0; i < iv.length; i++) ac[i] = r + iv[i];
            inverteix(ac, inversio);
            for (int i = 0; i < iv.length; i++) n[i] = Math.max(0, Math.min(127, ac[i]));
            if (extra == 1) n[iv.length] = Math.max(0, Math.min(127, doBase - 12 + Math.floorMod(baix, 12)));
            return n;
        }

        /** Aplica la inversió a les notes de l'acord (sense la nota greu). */
        static void inverteix(int[] ac, int inv) {
            if (ac.length < 2) return;
            for (int k = 0; k < Math.abs(inv); k++) {
                java.util.Arrays.sort(ac);
                if (inv > 0) ac[0] += 12;
                else ac[ac.length - 1] -= 12;
            }
            java.util.Arrays.sort(ac);
        }

        /** La nota més greu de l'acord (sense la nota greu), 0–11; -1 si és buit. Per dir on comença. */
        public int primeraNota() {
            if (buit()) return -1;
            int[] iv = intervals();
            int[] ac = new int[iv.length];
            for (int i = 0; i < iv.length; i++) ac[i] = 60 + arrel + iv[i];
            inverteix(ac, inversio);
            return Math.floorMod(ac[0], 12);
        }

        /** En text, per desar-ho: «arrel,grafia,tipus,tensions,baix[,inversió]» o «-». */
        public String text() {
            if (buit()) return "-";
            return arrel + "," + grafia + "," + tipus + "," + tensions + "," + baix + (inversio != 0 ? "," + inversio : "");
        }

        public static Acord deText(String s) {
            Acord a = new Acord();
            if (s == null || s.trim().isEmpty() || s.trim().equals("-")) return a;
            String[] c = s.trim().split(",");
            try {
                a.arrel = Math.floorMod(Integer.parseInt(c[0].trim()), 12);
                a.grafia = Math.max(0, Math.min(2, Integer.parseInt(c[1].trim())));
                a.tipus = Math.max(0, Math.min(TIPUS.length - 1, Integer.parseInt(c[2].trim())));
                a.tensions = Integer.parseInt(c[3].trim()) & ((1 << TENSIONS.length) - 1);
                a.baix = Integer.parseInt(c[4].trim());
                if (a.baix >= 0) a.baix = Math.floorMod(a.baix, 12);
                if (c.length > 5) a.inversio = Math.max(-MAX_INVERSIO, Math.min(MAX_INVERSIO, Integer.parseInt(c[5].trim())));
            } catch (RuntimeException e) {
                return new Acord();
            }
            return a;
        }

        /**
         * Llegeix un acord en notació anglesa: «C», «C#m», «Bbm7», «Ebmaj7», «G7», «E9», «Bdim», «Asus4», «C6»,
         * «Cadd9», «Bm7b5», «Caug», «C7(b9)», «G/B»… Retorna null si no s'entén.
         */
        public static Acord llegeix(String s) {
            if (s == null) return null;
            s = s.trim().replace('♭', 'b').replace('♯', '#').replace(" ", "");
            if (s.isEmpty()) return null;
            Acord a = new Acord();
            int[] r = arrelDe(s, 0);
            if (r == null) return null;
            a.arrel = r[0];
            a.grafia = r[2];
            String resta = s.substring(r[1]);
            int barra = resta.lastIndexOf('/');
            if (barra >= 0) {
                int[] b = arrelDe(resta, barra + 1);
                if (b != null && b[1] == resta.length()) {
                    a.baix = b[0];
                    resta = resta.substring(0, barra);
                }
            }
            String q = resta.replace("(", "").replace(")", "").replace(",", "");
            // qualitat
            if (q.startsWith("maj") || q.startsWith("M") || q.startsWith("Δ") || q.startsWith("j")) {
                q = q.startsWith("maj") ? q.substring(3) : q.substring(1);
                if (q.startsWith("7")) { a.commuta(TJ7); q = q.substring(1); }
                else if (q.startsWith("9")) { a.commuta(TJ7); a.commuta(T9); q = q.substring(1); }
                else if (q.startsWith("13")) { a.commuta(TJ7); a.commuta(T6); q = q.substring(2); }
                else if (q.isEmpty()) { /* «Cmaj» = major */ }
                else a.commuta(TJ7);
            } else if (q.startsWith("min") || q.startsWith("m") || q.startsWith("-")) {
                q = q.startsWith("min") ? q.substring(3) : q.substring(1);
                a.tipus = MIN;
                if (q.startsWith("maj7") || q.startsWith("M7")) {
                    a.commuta(TJ7);
                    q = q.substring(q.startsWith("maj7") ? 4 : 2);
                }
            } else if (q.startsWith("dim") || q.startsWith("°") || q.startsWith("o")) {
                q = q.startsWith("dim") ? q.substring(3) : q.substring(1);
                a.tipus = DIM;
            } else if (q.startsWith("aug") || q.startsWith("+")) {
                q = q.startsWith("aug") ? q.substring(3) : q.substring(1);
                a.tipus = AUG;
            }
            // números i alteracions
            while (!q.isEmpty()) {
                if (q.startsWith("sus4") || q.startsWith("sus")) {
                    a.tipus = SUS4;
                    q = q.substring(q.startsWith("sus4") ? 4 : 3);
                } else if (q.startsWith("sus2")) {
                    a.tipus = SUS2;
                    q = q.substring(4);
                } else if (q.startsWith("add9") || q.startsWith("add2")) {
                    a.posa(T9);
                    q = q.substring(4);
                } else if (q.startsWith("add11") || q.startsWith("add4")) {
                    a.posa(T11);
                    q = q.substring(q.startsWith("add11") ? 5 : 4);
                } else if (q.startsWith("7b5")) {
                    a.posa(T7);
                    a.posa(TB5);
                    q = q.substring(3);
                } else if (q.startsWith("13")) {
                    if (!a.te(TJ7)) a.posa(T7);
                    a.posa(T9);
                    a.posa(T6);
                    q = q.substring(2);
                } else if (q.startsWith("11")) {
                    if (!a.te(TJ7)) a.posa(T7);
                    a.posa(T9);
                    a.posa(T11);
                    q = q.substring(2);
                } else if (q.startsWith("9")) {
                    if (!a.te(TJ7)) a.posa(T7);
                    a.posa(T9);
                    q = q.substring(1);
                } else if (q.startsWith("7")) {
                    if (!a.te(TJ7)) a.posa(T7);
                    q = q.substring(1);
                } else if (q.startsWith("69")) {
                    a.posa(T6);
                    a.posa(T9);
                    q = q.substring(2);
                } else if (q.startsWith("6")) {
                    a.posa(T6);
                    q = q.substring(1);
                } else if (q.startsWith("5") && a.tensions == 0 && a.tipus == MAJ) {
                    // power chord: es fa com a sus (sense tercera) no existeix; el deixem major
                    q = q.substring(1);
                } else if (q.startsWith("b9")) { a.posa(TB9); q = q.substring(2); }
                else if (q.startsWith("#9")) { a.posa(TS9); q = q.substring(2); }
                else if (q.startsWith("#11")) { a.posa(TB5); q = q.substring(3); }
                else if (q.startsWith("b13")) { a.posa(TS5); q = q.substring(3); }
                else if (q.startsWith("b5")) { a.posa(TB5); q = q.substring(2); }
                else if (q.startsWith("#5")) { a.posa(TS5); q = q.substring(2); }
                else return null;
            }
            return a;
        }

        private void posa(int t) {
            tensions |= 1 << t;
            if (t == T7) tensions &= ~(1 << TJ7);
            if (t == TJ7) tensions &= ~(1 << T7);
        }

        /** {arrel, posició després de la nota, grafia} o null. */
        private static int[] arrelDe(String s, int i) {
            if (i >= s.length()) return null;
            char ch = s.charAt(i);
            if (ch < 'A' || ch > 'G') return null;
            int pc = "C D EF G A B".indexOf(ch);
            int g = 0;
            i++;
            if (i < s.length() && s.charAt(i) == '#') {
                pc++;
                g = 1;
                i++;
            } else if (i < s.length() && s.charAt(i) == 'b') {
                pc--;
                g = 2;
                i++;
            }
            return new int[]{Math.floorMod(pc, 12), i, g};
        }
    }

    // ---------- Els 12 pads ----------

    public final Acord[] pads = new Acord[PADS];
    /** El preset d'on venen (títol) o buit si s'han tocat a mà. */
    public String preset = "";

    public Acords() {
        for (int i = 0; i < PADS; i++) pads[i] = new Acord();
    }

    /** Per defecte, els acords de Do major a les tecles blanques i majors a les negres (com abans). */
    public static Acords perDefecte() {
        Acords a = new Acords();
        int[] t = {MAJ, MAJ, MIN, MAJ, MIN, MAJ, MAJ, MAJ, MAJ, MIN, MAJ, DIM};
        for (int i = 0; i < PADS; i++) {
            a.pads[i].arrel = i;
            a.pads[i].tipus = t[i];
        }
        return a;
    }

    /** Posa els acords d'un preset als pads 1, 2, 3… (els que sobren queden buits). */
    public static Acords dePreset(int index) {
        Acords a = new Acords();
        String[] cs = AcordsPresets.acords(index);
        for (int i = 0; i < PADS && i < cs.length; i++) {
            Acord ac = Acord.llegeix(cs[i]);
            if (ac != null) a.pads[i] = ac;
        }
        a.preset = AcordsPresets.titol(index);
        return a;
    }

    public static String nomNota(int pc) {
        return NOTES_CAT[Math.floorMod(pc, 12)];
    }

    /** El nom de l'acord del pad (o de la tecla) {@code pc}. */
    public String nom(int pc) {
        return pads[Math.floorMod(pc, 12)].nom();
    }

    /**
     * Les notes que fa sonar la tecla {@code nota} del teclat (mode antic: una tecla, un pad): l'arrel queda a
     * l'octava de la tecla.
     */
    public int[] notes(int nota) {
        Acord a = pads[Math.floorMod(nota, 12)];
        return a.notes(nota - Math.floorMod(nota, 12));
    }

    /** En text, per desar-ho: «preset|pad;pad;…». */
    public String text() {
        StringBuilder sb = new StringBuilder("v2|").append(preset == null ? "" : preset.replace("|", " ")).append('|');
        for (int i = 0; i < PADS; i++) {
            if (i > 0) sb.append(';');
            sb.append(pads[i].text());
        }
        return sb.toString();
    }

    public static Acords deText(String s) {
        if (s == null || s.isEmpty()) return perDefecte();
        if (s.startsWith("v2|")) {
            Acords a = new Acords();
            String[] parts = s.split("\\|", 3);
            a.preset = parts.length > 1 ? parts[1] : "";
            String[] p = parts.length > 2 ? parts[2].split(";", -1) : new String[0];
            for (int i = 0; i < PADS && i < p.length; i++) a.pads[i] = Acord.deText(p[i]);
            return a;
        }
        return deTextVell(s);
    }

    /** El format d'abans de la 2.29 («arrel:tipus;…», amb 17 tipus fixos), convertit als pads nous. */
    static Acords deTextVell(String s) {
        Acords a = perDefecte();
        String[] p = s.split(";");
        for (int i = 0; i < PADS && i < p.length; i++) {
            String[] c = p[i].split(":");
            try {
                int arrel = Math.floorMod(Integer.parseInt(c[0].trim()), 12);
                int tipus = Integer.parseInt(c[1].trim());
                Acord ac = new Acord();
                ac.arrel = arrel;
                switch (tipus) {
                    case 1: ac.tipus = MIN; break;
                    case 2: ac.posa(T7); break;
                    case 3: ac.posa(TJ7); break;
                    case 4: ac.tipus = MIN; ac.posa(T7); break;
                    case 5: ac.tipus = SUS2; break;
                    case 6: ac.tipus = SUS4; break;
                    case 7: ac.tipus = SUS4; ac.posa(T7); break;
                    case 8: ac.posa(T6); break;
                    case 9: ac.tipus = MIN; ac.posa(T6); break;
                    case 10: ac.posa(T9); break;
                    case 11: ac.tipus = MIN; ac.posa(T9); break;
                    case 12: ac.posa(T7); ac.posa(T9); break;
                    case 13: ac.tipus = DIM; break;
                    case 14: ac.tipus = MIN; ac.posa(T7); ac.posa(TB5); break;
                    case 15: ac.tipus = AUG; break;
                    default: break;   // major (i el «power chord», que ara no hi és)
                }
                a.pads[i] = ac;
            } catch (RuntimeException ignorat) {
            }
        }
        return a;
    }
}
