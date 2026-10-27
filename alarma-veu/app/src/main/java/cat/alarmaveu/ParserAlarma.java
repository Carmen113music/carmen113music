package cat.alarmaveu;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interpreta frases en català com:
 *   "posa'm una alarma a les 7 i mitja per portar el cotxe al taller"
 *   "dos quarts de vuit trucar a la mare"
 *   "d'aquí a 20 minuts treure el pa del forn"
 *   "demà a les 6 del matí aeroport"
 * i en treu l'hora, els minuts i el títol.
 */
public final class ParserAlarma {

    public static final class Resultat {
        public final boolean ok;
        public final int hora;
        public final int minut;
        public final String titol;
        public final String error;
        /** Minuts que falten perquè soni l'alarma. */
        public final int minutsFins;

        private Resultat(boolean ok, int hora, int minut, String titol, String error, int minutsFins) {
            this.ok = ok;
            this.hora = hora;
            this.minut = minut;
            this.titol = titol;
            this.error = error;
            this.minutsFins = minutsFins;
        }

        static Resultat be(int h, int m, String titol, int minutsFins) {
            return new Resultat(true, h, m, titol, null, minutsFins);
        }

        static Resultat malament(String error) {
            return new Resultat(false, -1, -1, null, error, -1);
        }

        public String horaText() {
            return String.format(Locale.ROOT, "%02d:%02d", hora, minut);
        }
    }

    public static final String ERROR_HORA =
            "No he entès l'hora. Prova a dir, per exemple: «a les 7 i mitja, portar el cotxe al taller».";
    public static final String ERROR_LLUNY =
            "El Rellotge només pot posar alarmes per a les pròximes 24 hores. Digues una hora d'avui o de demà.";

    private static final int F = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    // Android no admet Pattern.UNICODE_CHARACTER_CLASS (l'app es tancava en analitzar la frase).
    // Per això, "lletra o número" s'escriu com a [\\p{L}\\p{N}_], que reconeix també les lletres amb accent.

    private static final String HORES =
            "\\d{1,2}|una|dues|dos|tres|quatre|cinc|sis|set|vuit|nou|deu|onze|dotze";
    private static final String MINUTS =
            "\\d{1,2}|vint[- ]i[- ]cinc|trenta[- ]cinc|quaranta[- ]cinc|cinquanta[- ]cinc"
                    + "|cinc|deu|quinze|vint|trenta|quaranta|cinquanta";
    private static final String QUANTITAT =
            "\\d{1,3}|una|un|dues|dos|tres|quatre|cinc|sis|set|vuit|nou|deu|onze|dotze|quinze"
                    + "|vint[- ]i[- ]cinc|vint|trenta|quaranta[- ]cinc|quaranta|cinquanta";

    // "d'aquí a 20 minuts", "d'aquí a mitja hora", "en una hora i mitja"
    private static final Pattern RELATIU = Pattern.compile(
            "(?<![\\p{L}\\p{N}_])(?:d'?aqu[ií](?:\\s+a)?|dins\\s+d(?:e\\s+|')|en)\\s+"
                    + "(?:(?<mitja>mitja)\\s+hora"
                    + "|(?<q>un|tres)\\s+quarts?\\s+d'hora"
                    + "|(?<n>" + QUANTITAT + ")\\s+(?<u>minuts?|min|hora|hores|h)(?:\\s+i\\s+(?<extra>mitja|quart))?)"
                    + "(?![\\p{L}\\p{N}_])", F);

    // "dos quarts de vuit", "un quart d'onze", "tres quarts i cinc de dues"
    private static final Pattern QUARTS = Pattern.compile(
            "(?<![\\p{L}\\p{N}_])(?:(?:a|cap\\s+a)\\s+)?(?<q>un|1|dos|2|tres|3)\\s+quarts?"
                    + "(?:\\s+i\\s+(?<i>cinc|deu|5|10))?\\s+(?:de\\s+|d'\\s*)(?<h>" + HORES + ")(?![\\p{L}\\p{N}_])", F);

    // "a les 7:30", "19.45", "7h30"
    private static final Pattern DIGITAL = Pattern.compile(
            "(?<![\\p{L}\\p{N}_])(?:(?:a|per\\s+a|cap\\s+a)\\s+(?:les|la)\\s+)?(?<h>\\d{1,2})\\s*[:.h]\\s*(?<m>\\d{2})(?:\\s*h)?(?![\\p{L}\\p{N}_])", F);

    // "a les 7", "a les set i quart", "a les 8 menys deu", "a la una i mitja"
    private static final Pattern A_LES = Pattern.compile(
            "(?<![\\p{L}\\p{N}_])(?:(?:a|per\\s+a|cap\\s+a)\\s+)?(?:les|la)\\s+(?<h>" + HORES + ")"
                    + "(?:\\s+(?:en\\s+punt|hores|h))?"
                    + "(?:\\s+i\\s+(?<mes>mitja|quart|tres\\s+quarts|" + MINUTS + ")(?:\\s+minuts)?"
                    + "|\\s+menys\\s+(?<menys>quart|" + MINUTS + ")(?:\\s+minuts)?)?"
                    + "(?:\\s+en\\s+punt)?(?![\\p{L}\\p{N}_])", F);

    private static final Pattern MIGDIA = Pattern.compile("(?<![\\p{L}\\p{N}_])(?:al\\s+|a\\s+|cap\\s+al\\s+)?migdia(?![\\p{L}\\p{N}_])", F);
    private static final Pattern MITJANIT = Pattern.compile("(?<![\\p{L}\\p{N}_])(?:a\\s+|cap\\s+a\\s+)?(?:la\\s+)?mitjanit(?![\\p{L}\\p{N}_])", F);

    private static final Pattern PERIODE = Pattern.compile(
            "(?<![\\p{L}\\p{N}_])(?:del|de\\s+la|al|a\\s+la|aquest|aquesta|el|la)\\s+(?<p>matinada|matí|tarda|vespre|nit|migdia)(?![\\p{L}\\p{N}_])", F);

    private static final Pattern DEMA = Pattern.compile("(?<![\\p{L}\\p{N}_])demà(?![\\p{L}\\p{N}_])", F);
    private static final Pattern AVUI = Pattern.compile("(?<![\\p{L}\\p{N}_])avui(?![\\p{L}\\p{N}_])", F);
    private static final Pattern LLUNY = Pattern.compile(
            "(?<![\\p{L}\\p{N}_])(?:passat\\s+demà|demà\\s+passat|dilluns|dimarts|dimecres|dijous|divendres|dissabte|diumenge"
                    + "|la\\s+setmana\\s+que\\s+ve|el\\s+mes\\s+que\\s+ve)(?![\\p{L}\\p{N}_])", F);

    private static final Pattern[] INICI = {
            Pattern.compile("^(?:ei|hola|ok|vale|bé)(?![\\p{L}\\p{N}_])", F),
            Pattern.compile("^(?:posa'?m|posa-me|posam|posa|fes-me|fes|crea'?m|crea|programa'?m|programa"
                    + "|activa'?m|activa|configura|vull|necessito|si\\s+us\\s+plau)(?![\\p{L}\\p{N}_])", F),
            Pattern.compile("^(?:una|un|l')\\s*(?=alarma|despertador|recordatori|avís)", F),
            Pattern.compile("^(?:alarma|despertador|recordatori|avís)(?![\\p{L}\\p{N}_])", F),
            Pattern.compile("^(?:avisa'?m|avisa-me|recorda'?m|recorda-me|desperta'?m|desperta-me)(?![\\p{L}\\p{N}_])", F),
            Pattern.compile("^(?:amb\\s+(?:el\\s+)?(?:nom|títol|text)(?:\\s+de)?|que\\s+es\\s+digui|que\\s+digui"
                    + "|que\\s+posi|titulada|anomenada|dient)(?![\\p{L}\\p{N}_])", F),
            Pattern.compile("^(?:per\\s+a|per|que|i|de)(?![\\p{L}\\p{N}_])", F),
            Pattern.compile("^d'(?=[\\p{L}\\p{N}_])", F),
    };
    private static final Pattern FINAL = Pattern.compile(
            "(?:[\\s,.;:!?¡¿\\-]+|\\s+(?:per\\s+a|per|a|que|i|de|del|al|amb))$", F);

    private static final Map<String, Integer> NUMS = new HashMap<>();

    static {
        String[][] taula = {
                {"un", "1"}, {"una", "1"}, {"dos", "2"}, {"dues", "2"}, {"tres", "3"}, {"quatre", "4"},
                {"cinc", "5"}, {"sis", "6"}, {"set", "7"}, {"vuit", "8"}, {"nou", "9"}, {"deu", "10"},
                {"onze", "11"}, {"dotze", "12"}, {"quinze", "15"}, {"vint", "20"}, {"vint-i-cinc", "25"},
                {"trenta", "30"}, {"trenta-cinc", "35"}, {"quaranta", "40"}, {"quaranta-cinc", "45"},
                {"cinquanta", "50"}, {"cinquanta-cinc", "55"},
        };
        for (String[] f : taula) NUMS.put(f[0], Integer.parseInt(f[1]));
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

    /** true si l'hora h:m ja ha passat avui (o és ara), és a dir, la propera vegada que arribi serà demà. */
    private static boolean jaHaPassat(LocalTime ara, int h, int m) {
        return h * 60 + m <= ara.getHour() * 60 + ara.getMinute();
    }

    public static Resultat analitza(String text, LocalTime ara) {
        if (text == null || text.trim().isEmpty()) return Resultat.malament("No t'he sentit. Torna-ho a provar.");
        String s = " " + text.replace('’', '\'').replace('‘', '\'').replace('`', '\'').trim() + " ";

        if (LLUNY.matcher(s).find()) return Resultat.malament(ERROR_LLUNY);

        int h = -1, m = 0;
        boolean relatiu = false, explicit24 = false;
        Matcher x;

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
            if (total <= 0 || total > 24 * 60) return Resultat.malament(ERROR_LLUNY);
            LocalTime t = ara.withSecond(0).withNano(0).plusMinutes(total);
            h = t.getHour();
            m = t.getMinute();
            relatiu = true;
            s = treu(s, x);
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

        if (h < 0) return Resultat.malament(ERROR_HORA);

        String periode = null;
        if ((x = PERIODE.matcher(s)).find()) {
            periode = x.group("p").toLowerCase(Locale.ROOT);
            s = treu(s, x);
        }
        boolean dema = false;
        if ((x = DEMA.matcher(s)).find()) {
            dema = true;
            s = treu(s, x);
        }
        if ((x = AVUI.matcher(s)).find()) s = treu(s, x);

        if (!relatiu) {
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
            } else if (!explicit24 && h <= 12) {
                int c1 = h;                      // matí (o les 12 del migdia)
                int c2 = (h == 12) ? 0 : h + 12; // tarda/vespre (o mitjanit)
                if (dema) {
                    if (jaHaPassat(ara, c1, m)) h = c1;
                    else if (jaHaPassat(ara, c2, m)) h = c2;
                    else h = c1; // es detectarà més avall que sonaria avui
                } else {
                    h = minutsEntre(ara, c1, m) <= minutsEntre(ara, c2, m) ? c1 : c2;
                }
            }
            if (dema && !jaHaPassat(ara, h, m)) {
                return Resultat.malament(String.format(Locale.ROOT,
                        "Una alarma a les %02d:%02d sonaria avui, no demà. El Rellotge només pot posar alarmes "
                                + "per a les pròximes 24 hores: posa-la més tard, avui mateix.", h, m));
            }
        }

        return Resultat.be(h, m, netejaTitol(s), minutsEntre(ara, h, m));
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
        if (t.isEmpty()) return "Alarma";
        int primer = t.codePointAt(0);
        return new String(Character.toChars(Character.toUpperCase(primer))) + t.substring(Character.charCount(primer));
    }
}
