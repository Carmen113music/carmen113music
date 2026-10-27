package cat.alarmaveu;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Historial de tot el que s'ha demanat, desat al mòbil. El més recent va primer. */
public final class Historial {

    private static final String PREFS = "historial";
    private static final String CLAU = "entrades";
    private static final int MAXIM = 300;

    public static final class Entrada {
        public final long quan;
        public final String frase;
        public final String resultat;

        Entrada(long quan, String frase, String resultat) {
            this.quan = quan;
            this.frase = frase;
            this.resultat = resultat;
        }

        /** "Avui · 13:19", "Ahir · 20:05", "dv 24/10 · 09:30" */
        public String dataText() {
            LocalDateTime d = Instant.ofEpochMilli(quan).atZone(ZoneId.systemDefault()).toLocalDateTime();
            LocalDate avui = LocalDate.now();
            String hora = String.format(Locale.ROOT, "%02d:%02d", d.getHour(), d.getMinute());
            if (d.toLocalDate().equals(avui)) return "Avui · " + hora;
            if (d.toLocalDate().equals(avui.minusDays(1))) return "Ahir · " + hora;
            String[] dies = {"dl", "dt", "dc", "dj", "dv", "ds", "dg"};
            return dies[d.getDayOfWeek().getValue() - 1] + " " + d.getDayOfMonth() + "/" + d.getMonthValue() + " · " + hora;
        }
    }

    private Historial() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void afegeix(Context c, String frase, String resultat) {
        try {
            JSONArray vell = new JSONArray(prefs(c).getString(CLAU, "[]"));
            JSONArray nou = new JSONArray();
            JSONObject e = new JSONObject();
            e.put("t", System.currentTimeMillis());
            e.put("f", frase == null ? "" : frase);
            e.put("r", resultat == null ? "" : resultat);
            nou.put(e);
            for (int i = 0; i < vell.length() && nou.length() < MAXIM; i++) nou.put(vell.get(i));
            prefs(c).edit().putString(CLAU, nou.toString()).apply();
        } catch (JSONException ignorat) {
            // si l'historial està malmès, no el fem servir
        }
    }

    public static List<Entrada> llegeix(Context c) {
        List<Entrada> llista = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString(CLAU, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject e = a.getJSONObject(i);
                llista.add(new Entrada(e.optLong("t"), e.optString("f"), e.optString("r")));
            }
        } catch (JSONException ignorat) {
            // historial buit
        }
        return llista;
    }

    public static void esborra(Context c) {
        prefs(c).edit().remove(CLAU).apply();
    }
}
