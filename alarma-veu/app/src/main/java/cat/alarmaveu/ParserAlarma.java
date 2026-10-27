package cat.alarmaveu;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interpreta frases en català i decideix si són una ALARMA o un esdeveniment d'AGENDA.
 *
 * Alarma (pròximes 24 hores, sense dia concret):
 *   "a les 7 i mitja portar el cotxe al taller", "d'aquí a 20 minuts treure el pa",
 *   "demà a les 6 del matí aeroport"
 * Agenda (dia concret, o "demà" a una hora que queda a més de 24 hores):
 *   "divendres tenim sopar a les vuit", "el 15 de novembre a les 5 de la tarda dentista",
 *   "passat demà a les 10 recollir les ulleres"
 */
public final class ParserAlarma {

    public enum Tipus { ALARMA, AGENDA, TEMPORITZADOR }

    public static final class Resultat {
        public final boolean ok;
        public final Tipus tipus;
        /** Data i hora en què sonarà l'alarma o començarà l'esdeveniment. */
        public final LocalDateTime quan;
        public final String titol;
        public final String error;
        /** Minuts que falten fins a l'alarma o l'esdeveniment. */
        public final long minutsFins;
        /** Durada del temporitzador, en segons (només per a TEMPORITZADOR). */
        public int segons;

        private Resultat(boolean ok, Tipus tipus, LocalDateTime quan, String titol, String error, long minutsFins) {
            this.ok = ok;
            this.tipus = tipus;
            this.quan = quan;
            this.titol = titol;
            this.error = error;
            this.minutsFins = minutsFins;
        }

        static Resultat temporitzador(int segons, String titol) {
            Resultat r = new Resultat(true, Tipus.TEMPORITZADOR, null, titol, null, (segons + 59) / 60);
            r.segons = segons;
            return r;
        }

        /** "15 min", "1 h 30 min", "2 min 30 s" */
        public String duradaText() {
            int h = segons / 3600, m = (segons % 3600) / 60, sg = segons % 60;
            StringBuilder sb = new StringBuilder();
            if (h > 0) sb.append(h).append(" h");
            if (m > 0) sb.append(sb.length() > 0 ? " " : "").append(m).append(" min");
            if (sg > 0) sb.append(sb.length() > 0 ? " " : "").append(sg).append(" s");
            return sb.toString();
        }

        static Resultat be(Tipus tipus, LocalDateTime quan, String titol, long minutsFins) {
            return new Resultat(true, tipus, quan, titol, null, minutsFins);
        }

        static Resultat malament(String error) {
            return new Resultat(false, null, null, null, error, -1);
        }

        public int hora() {
            return quan.getHour();
        }

        public int minut() {
            return quan.getMinute();
        }

        public String horaText() {
            return String.format(Locale.ROOT, "%02d:%02d", quan.getHour(), quan.getMinute());
        }

        private static final String[] DIES = {"dilluns", "dimarts", "dimecres", "dijous", "divendres", "dissabte", "diumenge"};

        /** "divendres 30/10 a les 20:00" */
        public String dataText() {
            return String.format(Locale.ROOT, "%s %d/%d a les %s", DIES[quan.getDayOfWeek().getValue() - 1],
                    quan.getDayOfMonth(), quan.getMonthValue(), horaText());
        }
    }

    public static final String ERROR_HORA =
            "No he entès l'hora. Prova a dir, per exemple: «a les 7 i mitja, portar el cotxe al taller».";
    public static final String ERROR_SENSE_HORA =
            "Falta l'hora. Prova a dir, per exemple: «divendres a les 8 del vespre, sopar».";
    public static final String ERROR_DIA =
            "No he entès quin dia. Prova a dir el dia de la setmana o la data, per exemple «el 15 de novembre».";
    public static final String ERROR_PASSAT = "Aquesta data i hora ja han passat.";

    // Android no admet Pattern.UNICODE_CHARACTER_CLASS (l'app es tancava en analitzar la frase).
    // Per això, els límits de paraula s'escriuen amb [\p{L}\p{N}_], que reconeix també les lletres amb accent.
    private static final int F = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    private static final String L = "(?<![\\p{L}\\p{N}_])";
    private static final String R = "(?![\\p{L}\\p{N}_])";

    private static final String HORES =
            "\\d{1,2}|una|dues|dos|tres|quatre|cinc|sis|set|vuit|nou|deu|onze|dotze";
    private static final String MINUTS =
            "\\d{1,2}|vint[- ]i[- ]cinc|trenta[- ]cinc|quaranta[- ]cinc|cinquanta[- ]cinc"
                    + "|cinc|deu|quinze|vint|trenta|quaranta|cinquanta";
    private static final String QUANTITAT =
            "\\d{1,3}|una|un|dues|dos|tres|quatre|cinc|sis|set|vuit|nou|deu|onze|dotze|quinze"
                    + "|vint[- ]i[- ]cinc|vint|trenta|quaranta[- ]cinc|quaranta|cinquanta";
    private static final String DIES_SETMANA = "dilluns|dimarts|dimecres|dijous|divendres|dissabte|diumenge";
    private static final String MESOS =
            "gener|febrer|març|abril|maig|juny|juliol|agost|setembre|octubre|novembre|desembre";

    // ---------- Dies ----------

    private static final Pattern PASSAT_DEMA = Pattern.compile(L + "(?:passat\\s+demà|demà\\s+passat)" + R, F);

    // "divendres", "aquest divendres", "el dijous que ve", "divendres de la setmana que ve"
    private static final Pattern DIA_SETMANA = Pattern.compile(
            L + "(?:(?:el|aquest|aquest\\s+mateix|per\\s+al|pel)\\s+)?(?<d>" + DIES_SETMANA + ")"
                    + "(?<seg>\\s+(?:de\\s+)?la\\s+setmana\\s+que\\s+ve|\\s+de\\s+la\\s+setmana\\s+vinent)?"
                    + "(?:\\s+(?:que\\s+ve|vinent))?" + R, F);

    // "el 15 de novembre", "el dia 3", "15 d'abril", "el 15/11"
    private static final Pattern DATA = Pattern.compile(
            L + "(?:(?:el\\s+(?:dia\\s+)?)?(?<d3>\\d{1,2})/(?<m3>\\d{1,2})"
                    + "|(?:el\\s+(?:dia\\s+)?|dia\\s+)(?<d>\\d{1,2})(?:\\s+(?:de\\s+|d'\\s*)(?<m>" + MESOS + "))?"
                    + "|(?<d2>\\d{1,2})\\s+(?:de\\s+|d'\\s*)(?<m2>" + MESOS + "))" + R, F);

    private static final Pattern SENSE_DIA = Pattern.compile(
            L + "(?:la\\s+setmana\\s+que\\s+ve|la\\s+setmana\\s+vinent|el\\s+mes\\s+que\\s+ve)" + R, F);

    private static final Pattern DEMA = Pattern.compile(L + "demà" + R, F);
    private static final Pattern AVUI = Pattern.compile(L + "avui" + R, F);

    // ---------- Hores ----------

    // "d'aquí a 20 minuts", "d'aquí a mitja hora", "en una hora i mitja"
    private static final Pattern RELATIU = Pattern.compile(
            L + "(?:d'?aqu[ií](?:\\s+a)?|dins\\s+d(?:e\\s+|')|en)\\s+"
                    + "(?:(?<mitja>mitja)\\s+hora"
                    + "|(?<q>un|tres)\\s+quarts?\\s+d'hora"
                    + "|(?<n>" + QUANTITAT + ")\\s+(?<u>minuts?|min|hora|hores|h)(?:\\s+i\\s+(?<extra>mitja|quart))?)"
                    + R, F);

    // "dos quarts de vuit", "un quart d'onze", "tres quarts i cinc de dues"
    private static final Pattern QUARTS = Pattern.compile(
            L + "(?:(?:a|cap\\s+a)\\s+)?(?<q>un|1|dos|2|tres|3)\\s+quarts?"
                    + "(?:\\s+i\\s+(?<i>cinc|deu|5|10))?\\s+(?:de\\s+|d'\\s*)(?<h>" + HORES + ")" + R, F);

    // "a les 7:30", "19.45", "7h30"
    private static final Pattern DIGITAL = Pattern.compile(
            L + "(?:(?:a|per\\s+a|cap\\s+a)\\s+(?:les|la)\\s+)?(?<h>\\d{1,2})\\s*[:.h]\\s*(?<m>\\d{2})(?:\\s*h)?" + R, F);

    // "a les 7", "a les set i quart", "a les 8 menys deu", "a la una i mitja"
    private static final Pattern A_LES = Pattern.compile(
            L + "(?:(?:a|per\\s+a|cap\\s+a)\\s+)?(?:les|la)\\s+(?<h>" + HORES + ")"
                    + "(?:\\s+(?:en\\s+punt|hores|h))?"
                    + "(?:\\s+i\\s+(?<mes>mitja|quart|tres\\s+quarts|" + MINUTS + ")(?:\\s+minuts)?"
                    + "|\\s+menys\\s+(?<menys>quart|" + MINUTS + ")(?:\\s+minuts)?)?"
                    + "(?:\\s+en\\s+punt)?" + R, F);

    private static final Pattern MIGDIA = Pattern.compile(L + "(?:al\\s+|a\\s+|cap\\s+al\\s+)?migdia" + R, F);
    private static final Pattern MITJANIT = Pattern.compile(L + "(?:a\\s+|cap\\s+a\\s+)?(?:la\\s+)?mitjanit" + R, F);

    private static final Pattern PERIODE = Pattern.compile(
            L + "(?:del|de\\s+la|al|a\\s+la|aquest|aquesta|el|la)\\s+(?<p>matinada|matí|tarda|vespre|nit|migdia)" + R, F);

    // Paraules que fan pensar en vespre quan algú diu "a les 8" sense dir matí o tarda (només per a l'agenda)
    private static final Pattern PISTA_VESPRE = Pattern.compile(
            L + "(?:sopar|sopem|sopa|copes|copa|concert|assaig|bolo|cine|cinema|teatre|festa|pel·lícula)" + R, F);

    // ---------- Títol ----------

    private static final Pattern[] INICI = {
            Pattern.compile("^(?:ei|hola|ok|vale|bé)" + R, F),
            Pattern.compile("^(?:posa'?m|posa-me|posam|posa|fes-me|fes|crea'?m|crea|programa'?m|programa"
                    + "|activa'?m|activa|configura|vull|necessito|si\\s+us\\s+plau|apunta'?m|apunta|anota)" + R, F),
            Pattern.compile("^(?:una|un|l')\\s*(?=alarma|despertador|recordatori|avís|esdeveniment|cita)", F),
            Pattern.compile("^(?:alarma|despertador|recordatori|avís|esdeveniment|cita)" + R, F),
            Pattern.compile("^(?:a\\s+l'agenda|al\\s+calendari|a\\s+l'calendari)" + R, F),
            Pattern.compile("^(?:avisa'?m|avisa-me|recorda'?m|recorda-me|desperta'?m|desperta-me)" + R, F),
            Pattern.compile("^(?:amb\\s+(?:el\\s+)?(?:nom|títol|text)(?:\\s+de)?|que\\s+es\\s+digui|que\\s+digui"
                    + "|que\\s+posi|titulada|anomenada|dient)" + R, F),
            Pattern.compile("^(?:tenim|tinc|tenen|té|hi\\s+ha|hi\\s+haurà|hi\\s+ha\\s+un|hi\\s+ha\\s+una)" + R, F),
            Pattern.compile("^(?:un|una)\\s+(?=temporitzador|compte\\s+enrere|cronòmetre)", F),
            Pattern.compile("^(?:temporitzador|compte\\s+enrere|cronòmetre)" + R, F),
            Pattern.compile("^(?:per\\s+a|per|pels|pel|que|i|de)" + R, F),
            Pattern.compile("^d'\\s*", F),
    };
    private static final Pattern FINAL = Pattern.compile(
            "(?:[\\s,.;:!?¡¿\\-]+|\\s+(?:per\\s+a|per|a|que|i|de|del|al|amb|el|la))$", F);

    private static final Map<String, Integer> NUMS = new HashMap<>();
    private static final Map<String, Integer> NUM_MES = new HashMap<>();

    static {
        String[][] taula = {
                {"un", "1"}, {"una", "1"}, {"dos", "2"}, {"dues", "2"}, {"tres", "3"}, {"quatre", "4"},
                {"cinc", "5"}, {"sis", "6"}, {"set", "7"}, {"vuit", "8"}, {"nou", "9"}, {"deu", "10"},
                {"onze", "11"}, {"dotze", "12"}, {"quinze", "15"}, {"vint", "20"}, {"vint-i-cinc", "25"},
                {"trenta", "30"}, {"trenta-cinc", "35"}, {"quaranta", "40"}, {"quaranta-cinc", "45"},
                {"cinquanta", "50"}, {"cinquanta-cinc", "55"},
        };
        for (String[] f : taula) NUMS.put(f[0], Integer.parseInt(f[1]));
        String[] mesos = MESOS.split("\\|");
        for (int i = 0; i < mesos.length; i++) NUM_MES.put(mesos[i], i + 1);
    }

    private ParserAlarma() {
    }

    private static int num(String s) {
        if (s == null) return -1;
        String k = s.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s-]+", "-");
        if (k.matches("\\d+")) return Integer.parseInt(k);
        Integer v = NUMS.get(k);
        return v == null ? -1 : v;
    }

    private static String treu(String s, Matcher m) {
        return s.substring(0, m.start()) + " " + s.substring(m.end());
    }

    private static int minutsEntre(LocalTime ara, int h, int m) {
        int a = ara.getHour() * 60 + ara.getMinute();
        int d = ((h * 60 + m) - a + 1440) % 1440;
        return d == 0 ? 1440 : d;
    }

    /** true si l'hora h:m ja ha passat avui (o és ara): la propera vegada que arribi serà demà. */
    private static boolean jaHaPassat(LocalTime ara, int h, int m) {
        return h * 60 + m <= ara.getHour() * 60 + ara.getMinute();
    }

    private static DayOfWeek diaSetmana(String nom) {
        switch (nom.toLowerCase(Locale.ROOT)) {
            case "dilluns": return DayOfWeek.MONDAY;
            case "dimarts": return DayOfWeek.TUESDAY;
            case "dimecres": return DayOfWeek.WEDNESDAY;
            case "dijous": return DayOfWeek.THURSDAY;
            case "divendres": return DayOfWeek.FRIDAY;
            case "dissabte": return DayOfWeek.SATURDAY;
            default: return DayOfWeek.SUNDAY;
        }
    }

    /** Dia i mes: si ja ha passat aquest any, l'any vinent. Només dia: aquest mes o el següent. */
    private static LocalDate dataDe(int dia, int mes, LocalDate avui) {
        if (dia < 1 || dia > 31) return null;
        if (mes > 0) {
            if (mes > 12) return null;
            for (int any = avui.getYear(); any <= avui.getYear() + 1; any++) {
                YearMonth ym = YearMonth.of(any, mes);
                if (!ym.isValidDay(dia)) continue;
                LocalDate d = ym.atDay(dia);
                if (!d.isBefore(avui)) return d;
            }
            return null;
        }
        YearMonth ym = YearMonth.from(avui);
        for (int i = 0; i < 3; i++, ym = ym.plusMonths(1)) {
            if (!ym.isValidDay(dia)) continue;
            LocalDate d = ym.atDay(dia);
            if (!d.isBefore(avui)) return d;
        }
        return null;
    }

    /** Hora d'un esdeveniment d'agenda quan no s'ha dit matí ni tarda. */
    private static int horaEsdeveniment(int h, String text) {
        if (h == 12) return 12;
        if (h >= 1 && h <= 6) return h + 12;               // "a les 5" → 17:00
        if (h >= 7 && h <= 11 && PISTA_VESPRE.matcher(text).find()) return h + 12; // "sopar a les 8" → 20:00
        return h;                                          // "reunió a les 9" → 09:00
    }

    /** Resultat de buscar un dia a la frase. */
    private static final class DiaTrobat {
        LocalDate dia;     // dia explícit (dia de la setmana, data o passat demà)
        boolean dema;      // ha dit "demà" (i cap altre dia)
        boolean avui;      // ha dit "avui"
        String resta;      // frase sense les paraules del dia
        String error;
    }

    private static DiaTrobat extreuDia(String s, LocalDate avui) {
        DiaTrobat r = new DiaTrobat();
        Matcher x;
        if ((x = PASSAT_DEMA.matcher(s)).find()) {
            r.dia = avui.plusDays(2);
            s = treu(s, x);
        } else if ((x = DIA_SETMANA.matcher(s)).find()) {
            DayOfWeek dow = diaSetmana(x.group("d"));
            if (x.group("seg") != null) {
                r.dia = avui.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).with(TemporalAdjusters.nextOrSame(dow));
            } else {
                r.dia = avui.with(TemporalAdjusters.next(dow)); // mai avui: "divendres" dit un divendres = el següent
            }
            s = treu(s, x);
        } else if ((x = DATA.matcher(s)).find()) {
            int d, m;
            if (x.group("d") != null) {
                d = Integer.parseInt(x.group("d"));
                m = x.group("m") != null ? NUM_MES.get(x.group("m").toLowerCase(Locale.ROOT)) : 0;
            } else if (x.group("d2") != null) {
                d = Integer.parseInt(x.group("d2"));
                m = NUM_MES.get(x.group("m2").toLowerCase(Locale.ROOT));
            } else {
                d = Integer.parseInt(x.group("d3"));
                m = Integer.parseInt(x.group("m3"));
            }
            r.dia = dataDe(d, m, avui);
            if (r.dia == null) {
                r.error = ERROR_DIA;
                return r;
            }
            s = treu(s, x);
        } else if (SENSE_DIA.matcher(s).find()) {
            r.error = ERROR_DIA;
            return r;
        }
        if ((x = DEMA.matcher(s)).find()) {
            if (r.dia == null) r.dema = true;
            s = treu(s, x);
        }
        if ((x = AVUI.matcher(s)).find()) {
            r.avui = true;
            s = treu(s, x);
        }
        r.resta = s;
        return r;
    }

    // ---------------- Consultes: "què tinc divendres?" ----------------

    private static final Pattern CONSULTA = Pattern.compile(
            L + "(?:què|que)\\s+(?:tinc|tenim|hi\\s+ha|faig|fem)" + R
                    + "|" + L + "(?:tinc|tenim)\\s+(?:alguna\\s+cosa|res|algo|coses|plans?)" + R
                    + "|" + L + "(?:estic|estem)\\s+(?:lliures?|ocupad[ae]s?)" + R
                    + "|" + L + "(?:mira|consulta|digues-me)\\s+(?:l'agenda|el\\s+calendari|què)" + R, F);

    /**
     * Si la frase és una pregunta sobre l'agenda ("què tinc divendres?", "tinc alguna cosa demà?"),
     * retorna el dia per consultar. Si no ho és, retorna null.
     */
    public static LocalDate diaConsulta(String text, LocalDateTime ara) {
        if (text == null) return null;
        String s = " " + text.replace('’', '\'').replace('‘', '\'').trim() + " ";
        if (!CONSULTA.matcher(s).find()) return null;
        DiaTrobat dt = extreuDia(s, ara.toLocalDate());
        if (dt.error != null) return null;
        if (dt.dia != null) return dt.dia;
        if (dt.dema) return ara.toLocalDate().plusDays(1);
        return ara.toLocalDate();
    }

    // ---------------- Avisos de l'agenda ----------------

    /**
     * Moments dels avisos d'un esdeveniment: el dia abans a les 20:00 i el mateix dia a les 7:00.
     * Només els que encara no han passat i són abans de l'inici. Si no en queda cap,
     * 1 hora abans (o a l'hora, si ja no hi som a temps).
     */
    public static List<LocalDateTime> avisos(LocalDateTime inici, LocalDateTime ara) {
        List<LocalDateTime> llista = new ArrayList<>();
        LocalDateTime vespreAbans = inici.toLocalDate().minusDays(1).atTime(20, 0);
        LocalDateTime matiMateixDia = inici.toLocalDate().atTime(7, 0);
        for (LocalDateTime a : new LocalDateTime[]{vespreAbans, matiMateixDia}) {
            if (a.isAfter(ara) && a.isBefore(inici)) llista.add(a);
        }
        if (llista.isEmpty()) {
            LocalDateTime unaHora = inici.minusHours(1);
            llista.add(unaHora.isAfter(ara) ? unaHora : inici);
        }
        return llista;
    }

    /** "dia abans a les 20:00 i mateix dia a les 07:00" */
    public static String descriuAvisos(List<LocalDateTime> avisos, LocalDateTime inici) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < avisos.size(); i++) {
            LocalDateTime a = avisos.get(i);
            if (i > 0) sb.append(i == avisos.size() - 1 ? " i " : ", ");
            String hora = String.format(Locale.ROOT, "%02d:%02d", a.getHour(), a.getMinute());
            if (a.equals(inici)) sb.append("a l'hora");
            else if (a.toLocalDate().equals(inici.toLocalDate().minusDays(1))) sb.append("dia abans a les ").append(hora);
            else if (a.toLocalDate().equals(inici.toLocalDate())) sb.append("mateix dia a les ").append(hora);
            else sb.append(hora);
        }
        return sb.toString();
    }

    /** "divendres 30/10" */
    public static String nomDia(LocalDate d) {
        String[] dies = {"dilluns", "dimarts", "dimecres", "dijous", "divendres", "dissabte", "diumenge"};
        return dies[d.getDayOfWeek().getValue() - 1] + " " + d.getDayOfMonth() + "/" + d.getMonthValue();
    }

    // ---------------- Temporitzadors: "temporitzador de 15 minuts per l'arròs" ----------------

    private static final String DURADA =
            "(?:(?<n>" + QUANTITAT + ")\\s+(?<u>minuts?|min|hora|hores|h|segons?|seg)"
                    + "(?:\\s+i\\s+(?:(?<extra>mitja|quart)|(?<n2>" + QUANTITAT + ")\\s+(?<u2>minuts?|min|segons?|seg)))?"
                    + "|(?<mitja>mitja)\\s+hora"
                    + "|(?<q>un|tres)\\s+quarts?(?:\\s+d'hora|(?!\\s+(?:i\\s+|de\\s+|d'))))"; // no "un quart d'onze"
    private static final Pattern PARAULA_TEMPORITZADOR = Pattern.compile(
            L + "(?:temporitzador|compte\\s+enrere|cronòmetre)" + R, F);
    // "posa 15 minuts per bullir l'aigua", "15 minuts per l'arròs"
    private static final Pattern POSA_DURADA = Pattern.compile(
            "^\\s*(?:(?:posa'?m|posa-me|posa|fes-me|fes|programa'?m|programa|activa)\\s+)?(?:uns\\s+)?" + DURADA + R, F);
    private static final Pattern DURADA_P = Pattern.compile(L + "(?:d'\\s*|de\\s+)?" + DURADA + R, F);

    private static int segonsDe(String n, String u) {
        int v = num(n);
        if (v < 0 || u == null) return -1;
        String k = u.toLowerCase(Locale.ROOT);
        if (k.startsWith("h")) return v * 3600;
        if (k.startsWith("s")) return v;
        return v * 60;
    }

    private static int segonsDe(Matcher x) {
        if (x.group("mitja") != null) return 30 * 60;
        if (x.group("q") != null) return num(x.group("q")) * 15 * 60;
        int t = segonsDe(x.group("n"), x.group("u"));
        if (t < 0) return -1;
        String extra = x.group("extra");
        if (extra != null) {
            boolean hores = x.group("u").toLowerCase(Locale.ROOT).startsWith("h");
            int base = hores ? 3600 : 60;
            t += extra.equalsIgnoreCase("mitja") ? base / 2 : base / 4;
        } else if (x.group("n2") != null) {
            int t2 = segonsDe(x.group("n2"), x.group("u2"));
            if (t2 < 0) return -1;
            t += t2;
        }
        return t;
    }

    /** Si la frase demana un temporitzador, el retorna; si no, null. */
    private static Resultat temporitzador(String s) {
        Matcher x;
        if (PARAULA_TEMPORITZADOR.matcher(s).find()) {
            x = DURADA_P.matcher(s);
            if (!x.find()) return Resultat.malament("Quant de temps? Prova a dir, per exemple: «temporitzador de 15 minuts per l'arròs».");
        } else {
            x = POSA_DURADA.matcher(s);
            if (!x.find()) return null;
        }
        int segons = segonsDe(x);
        if (segons <= 0 || segons > 24 * 3600) return Resultat.malament("El temporitzador ha de ser de menys de 24 hores.");
        String resta = s.substring(0, x.start()) + " " + s.substring(x.end());
        String titol = netejaTitol(resta);
        if (titol.equals("Recordatori")) titol = "Temporitzador";
        return Resultat.temporitzador(segons, titol);
    }

    public static Resultat analitza(String text, LocalDateTime araComplet) {
        if (text == null || text.trim().isEmpty()) return Resultat.malament("No t'he sentit. Torna-ho a provar.");
        LocalDateTime araMinut = araComplet.truncatedTo(ChronoUnit.MINUTES);
        LocalTime ara = araMinut.toLocalTime();
        LocalDate avui = araMinut.toLocalDate();
        String original = text.replace('’', '\'').replace('‘', '\'').replace('`', '\'').trim();
        String s = " " + original + " ";

        // ---- 0. Temporitzador? ----
        Resultat temps = temporitzador(s);
        if (temps != null) return temps;
        Matcher x;

        // ---- 1. Dia concret? ----
        DiaTrobat dt = extreuDia(s, avui);
        if (dt.error != null) return Resultat.malament(dt.error);
        LocalDate dia = dt.dia;   // dia dit explícitament (dia de la setmana, data, passat demà)
        boolean dema = dt.dema;
        s = dt.resta;

        // ---- 2. Hora ----
        int h = -1, m = 0;
        boolean explicit24 = false;

        if ((x = RELATIU.matcher(s)).find()) {
            int total;
            if (x.group("mitja") != null) {
                total = 30;
            } else if (x.group("q") != null) {
                total = num(x.group("q")) * 15;
            } else {
                int n = num(x.group("n"));
                if (n < 0) return Resultat.malament(ERROR_HORA);
                boolean hores = x.group("u").toLowerCase(Locale.ROOT).startsWith("h");
                total = hores ? n * 60 : n;
                String extra = x.group("extra");
                if (extra != null) total += extra.equalsIgnoreCase("mitja") ? 30 : 15;
            }
            if (total <= 0) return Resultat.malament(ERROR_HORA);
            s = treu(s, x);
            String titol = netejaTitol(s);
            // "d'aquí a mitja hora" → temporitzador (compte enrere). Més de 24 hores → agenda.
            if (total <= 24 * 60) {
                return Resultat.temporitzador(total * 60, titol.equals("Recordatori") ? "Temporitzador" : titol);
            }
            return Resultat.be(Tipus.AGENDA, araMinut.plusMinutes(total), titol, total);
        } else if ((x = QUARTS.matcher(s)).find()) {
            int q = num(x.group("q"));
            int hh = num(x.group("h"));
            if (hh < 1 || hh > 12) return Resultat.malament(ERROR_HORA);
            h = hh - 1;
            if (h == 0) h = 12;
            m = q * 15 + (x.group("i") != null ? num(x.group("i")) : 0);
            s = treu(s, x);
        } else if ((x = DIGITAL.matcher(s)).find()) {
            h = Integer.parseInt(x.group("h"));
            m = Integer.parseInt(x.group("m"));
            if (h > 23 || m > 59) return Resultat.malament(ERROR_HORA);
            explicit24 = h > 12 || h == 0 || x.group("h").startsWith("0");
            s = treu(s, x);
        } else if ((x = A_LES.matcher(s)).find()) {
            h = num(x.group("h"));
            if (h < 0 || h > 23) return Resultat.malament(ERROR_HORA);
            explicit24 = h > 12 || h == 0;
            String mes = x.group("mes");
            String menys = x.group("menys");
            if (mes != null) {
                String k = mes.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
                if (k.equals("mitja")) m = 30;
                else if (k.equals("quart")) m = 15;
                else if (k.equals("tres quarts")) m = 45;
                else m = num(mes);
                if (m < 0 || m > 59) return Resultat.malament(ERROR_HORA);
            } else if (menys != null) {
                int resta = menys.equalsIgnoreCase("quart") ? 15 : num(menys);
                if (resta <= 0 || resta > 59) return Resultat.malament(ERROR_HORA);
                int total = h * 60 - resta;
                if (total < 0 || (total < 60 && !explicit24)) total += 12 * 60;
                h = total / 60;
                m = total % 60;
            }
            s = treu(s, x);
        } else if ((x = MITJANIT.matcher(s)).find()) {
            h = 0;
            explicit24 = true;
            s = treu(s, x);
        } else if ((x = MIGDIA.matcher(s)).find()) {
            h = 12;
            explicit24 = true;
            s = treu(s, x);
        }

        if (h < 0) return Resultat.malament(dia != null ? ERROR_SENSE_HORA : ERROR_HORA);

        String periode = null;
        if ((x = PERIODE.matcher(s)).find()) {
            periode = x.group("p").toLowerCase(Locale.ROOT);
            s = treu(s, x);
        }
        if (periode != null && !explicit24) {
            switch (periode) {
                case "tarda":
                case "vespre":
                    if (h < 12) h += 12;
                    break;
                case "nit":
                    if (h == 12) h = 0;
                    else if (h >= 6) h += 12;
                    break;
                case "migdia":
                    if (h <= 5) h += 12;
                    break;
                default: // matí, matinada
                    break;
            }
            explicit24 = true;
        }
        String titol = netejaTitol(s);

        // ---- 3. Alarma o agenda ----
        if (dia != null) {
            if (!explicit24 && h <= 12) h = horaEsdeveniment(h, original);
            LocalDateTime quan = dia.atTime(h, m);
            if (!quan.isAfter(araMinut)) return Resultat.malament(ERROR_PASSAT);
            return Resultat.be(Tipus.AGENDA, quan, titol, ChronoUnit.MINUTES.between(araMinut, quan));
        }

        if (dema) {
            int hDema = h;
            if (!explicit24 && h <= 12) {
                int c1 = h, c2 = (h == 12) ? 0 : h + 12;
                if (jaHaPassat(ara, c1, m)) hDema = c1;
                else if (jaHaPassat(ara, c2, m)) hDema = c2;
                else hDema = -1;
            } else if (!jaHaPassat(ara, h, m)) {
                hDema = -1;
            }
            if (hDema >= 0) {
                LocalDateTime quan = avui.plusDays(1).atTime(hDema, m);
                return Resultat.be(Tipus.ALARMA, quan, titol, ChronoUnit.MINUTES.between(araMinut, quan));
            }
            // Demà, però a una hora que queda a més de 24 hores: no cap al Rellotge, va a l'agenda.
            if (!explicit24 && h <= 12) h = horaEsdeveniment(h, original);
            LocalDateTime quan = avui.plusDays(1).atTime(h, m);
            return Resultat.be(Tipus.AGENDA, quan, titol, ChronoUnit.MINUTES.between(araMinut, quan));
        }

        if (!explicit24 && h <= 12) {
            int c1 = h, c2 = (h == 12) ? 0 : h + 12;
            h = minutsEntre(ara, c1, m) <= minutsEntre(ara, c2, m) ? c1 : c2;
        }
        int falten = minutsEntre(ara, h, m);
        return Resultat.be(Tipus.ALARMA, araMinut.plusMinutes(falten), titol, falten);
    }

    static String netejaTitol(String s) {
        String t = s.replaceAll("\\s+", " ").trim();
        String abans;
        do {
            abans = t;
            t = t.replaceAll("^[\\s,.;:!?¡¿\\-]+", "");
            for (Pattern p : INICI) t = p.matcher(t).replaceFirst("").trim();
            Matcher f;
            while ((f = FINAL.matcher(t)).find()) t = t.substring(0, f.start());
            t = t.replaceAll("\\s+([,.;:!?])", "$1").replaceAll("\\s+", " ").trim();
        } while (!t.equals(abans));
        if (t.isEmpty()) return "Recordatori";
        int primer = t.codePointAt(0);
        return new String(Character.toChars(Character.toUpperCase(primer))) + t.substring(Character.charCount(primer));
    }
}
