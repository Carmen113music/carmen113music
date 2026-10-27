package cat.alarmaveu;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.speech.RecognizerIntent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Una sola pantalla amb un botó de micròfon. En obrir l'app ja comença a escoltar.
 * - Hora dins les pròximes 24 hores → alarma al Rellotge.
 * - Dia concret (divendres, el 15 de novembre...) → esdeveniment a l'agenda, amb avisos el dia abans a les 20:00
 *   i el mateix dia a les 7:00. Abans de desar-lo, mira si aquell dia ja hi ha alguna cosa i, si n'hi ha, pregunta.
 * - Pregunta ("què tinc divendres?") → mostra el que hi ha apuntat aquell dia.
 * Si tot va bé, mostra un missatge breu i es tanca. Si hi ha algun problema, es queda oberta i ho explica.
 */
public class MainActivity extends Activity {

    private static final int PETICIO_VEU = 1;
    private static final int PERMIS_CALENDARI = 2;
    private static final long DURADA_ESDEVENIMENT_MIN = 60;

    private TextView estat;
    private TextView detall;
    private TextView escoltat;

    private View filaDubte;

    /** Feina pendent mentre es demana permís per al calendari. */
    private Runnable pendent;
    private String frasePendent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        estat = findViewById(R.id.estat);
        detall = findViewById(R.id.detall);
        escoltat = findViewById(R.id.escoltat);
        filaDubte = findViewById(R.id.fila_dubte);

        findViewById(R.id.boto_parla).setOnClickListener(v -> escolta());
        findViewById(R.id.boto_alarmes).setOnClickListener(v -> obreAlarmes());
        findViewById(R.id.boto_calendari).setOnClickListener(v ->
                ambPermisCalendari(() -> triaCalendari(true, null, c ->
                        mostra(getString(R.string.agenda_actual, c.nom), getString(R.string.exemple), null, false)), null));
        actualitzaBotoCalendari();

        if (savedInstanceState == null) escolta();
    }

    /** Quan es torna a obrir l'app des de la icona, torna a escoltar directament. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        escolta();
    }

    private void escolta() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ca-ES");
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ca-ES");
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.indicacio_veu));
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        try {
            startActivityForResult(i, PETICIO_VEU);
        } catch (ActivityNotFoundException e) {
            mostra(getString(R.string.sense_reconeixement), getString(R.string.sense_reconeixement_detall), null, true);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PETICIO_VEU) return;
        if (resultCode != RESULT_OK || data == null) {
            mostra(getString(R.string.toca_per_parlar), getString(R.string.exemple), null, false);
            return;
        }
        ArrayList<String> frases = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (frases == null || frases.isEmpty()) {
            mostra(getString(R.string.no_t_he_sentit), getString(R.string.exemple), null, true);
            return;
        }

        LocalDateTime ara = LocalDateTime.now();
        LocalDate diaPreguntat = ParserAlarma.diaConsulta(frases.get(0), ara);
        if (diaPreguntat != null) {
            String frase = frases.get(0);
            ambPermisCalendari(() -> mostraDia(diaPreguntat, frase), frase);
            return;
        }

        ParserAlarma.Resultat primer = null;
        for (String f : frases) {
            ParserAlarma.Resultat r;
            try {
                r = ParserAlarma.analitza(f, ara);
            } catch (RuntimeException | Error e) {
                mostra(getString(R.string.no_ho_he_entes), "Error intern: " + e, f, true);
                return;
            }
            if (primer == null) primer = r;
            if (r.ok) {
                if (r.tipus == ParserAlarma.Tipus.TEMPORITZADOR) posaTemporitzador(r, f);
                else if (r.tipus == ParserAlarma.Tipus.ALARMA) posaAlarma(r, f);
                else posaAgenda(r, f);
                return;
            }
        }
        mostra(getString(R.string.no_ho_he_entes), primer.error, frases.get(0), true);
    }

    // ---------------- Alarma ----------------

    private void posaAlarma(ParserAlarma.Resultat r, String frase) {
        Intent a = new Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, r.hora())
                .putExtra(AlarmClock.EXTRA_MINUTES, r.minut())
                .putExtra(AlarmClock.EXTRA_MESSAGE, r.titol)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true);
        try {
            startActivity(a);
        } catch (ActivityNotFoundException e) {
            mostra(getString(R.string.sense_rellotge), getString(R.string.sense_rellotge_detall), frase, true);
            return;
        }
        acaba("⏰ " + r.horaText() + " · " + r.titol + "\n" + falten(r.minutsFins));
    }

    // ---------------- Temporitzador ----------------

    private void posaTemporitzador(ParserAlarma.Resultat r, String frase) {
        Intent t = new Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, r.segons)
                .putExtra(AlarmClock.EXTRA_MESSAGE, r.titol)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true);
        try {
            startActivity(t);
        } catch (ActivityNotFoundException e) {
            mostra(getString(R.string.sense_rellotge), getString(R.string.sense_rellotge_detall), frase, true);
            return;
        }
        acaba("⏱ " + r.duradaText() + " · " + r.titol);
    }

    // ---------------- Agenda ----------------

    private void posaAgenda(ParserAlarma.Resultat r, String frase) {
        ambPermisCalendari(() -> revisaDia(r, frase), frase);
    }

    private void ambPermisCalendari(Runnable feina, String frase) {
        if (checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
            feina.run();
            return;
        }
        pendent = feina;
        frasePendent = frase;
        requestPermissions(new String[]{Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR},
                PERMIS_CALENDARI);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != PERMIS_CALENDARI || pendent == null) return;
        boolean ok = grantResults.length > 0;
        for (int g : grantResults) ok &= g == PackageManager.PERMISSION_GRANTED;
        Runnable feina = pendent;
        String frase = frasePendent;
        pendent = null;
        frasePendent = null;
        if (ok) feina.run();
        else mostra(getString(R.string.sense_permis_calendari), getString(R.string.sense_permis_calendari_detall), frase, true);
    }

    /** Abans d'apuntar, mira si aquell dia ja hi ha alguna cosa. */
    private void revisaDia(ParserAlarma.Resultat r, String frase) {
        List<String> coses;
        try {
            coses = esdevenimentsDel(r.quan.toLocalDate());
        } catch (RuntimeException e) {
            coses = new ArrayList<>(); // si no es pot llegir l'agenda, apuntem igualment
        }
        if (coses.isEmpty()) {
            desaEsdeveniment(r, frase);
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("El ").append(ParserAlarma.nomDia(r.quan.toLocalDate())).append(" ja tens:\n");
        for (String c : coses) sb.append("• ").append(c).append("\n");
        sb.append("\nVols apuntar «").append(r.titol).append("» a les ").append(r.horaText()).append(" igualment?");
        mostra(getString(R.string.compte), sb.toString(), frase, true);
        filaDubte.setVisibility(View.VISIBLE);
        findViewById(R.id.boto_si).setOnClickListener(v -> desaEsdeveniment(r, frase));
        findViewById(R.id.boto_no).setOnClickListener(v -> acaba("No s'ha apuntat res"));
    }

    /** Resposta a "què tinc divendres?". L'app es queda oberta perquè es pugui llegir. */
    private void mostraDia(LocalDate dia, String frase) {
        List<String> coses;
        try {
            coses = esdevenimentsDel(dia);
        } catch (RuntimeException e) {
            mostra(getString(R.string.sense_calendari), "Error: " + e.getMessage(), frase, true);
            return;
        }
        String nom = ParserAlarma.nomDia(dia);
        nom = nom.substring(0, 1).toUpperCase(Locale.ROOT) + nom.substring(1);
        if (coses.isEmpty()) {
            mostra(nom, getString(R.string.res_apuntat), frase, false);
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (String c : coses) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(c);
        }
        mostra(nom, sb.toString(), frase, false);
    }

    /** Llista de coses apuntades un dia: "14:00 · Dinar amb la Laura", "Tot el dia · Vacances". */
    private List<String> esdevenimentsDel(LocalDate dia) {
        ZoneId zona = ZoneId.systemDefault();
        long inici = dia.atStartOfDay(zona).toInstant().toEpochMilli();
        long fi = dia.plusDays(1).atStartOfDay(zona).toInstant().toEpochMilli();
        long unDia = 24L * 3600 * 1000;

        // Rang una mica més ample: els esdeveniments de tot el dia es desen en hora UTC.
        Uri.Builder b = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(b, inici - unDia);
        ContentUris.appendId(b, fi + unDia);
        String[] camps = {
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
        };
        // Només calendaris visibles on es pot escriure (així no surten festius ni aniversaris)
        String filtre = CalendarContract.Instances.VISIBLE + " = 1 AND "
                + CalendarContract.Instances.CALENDAR_ACCESS_LEVEL + " >= "
                + CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR;

        List<String> llista = new ArrayList<>();
        Cursor c;
        try {
            c = getContentResolver().query(b.build(), camps, filtre, null, CalendarContract.Instances.BEGIN + " ASC");
        } catch (RuntimeException e) {
            c = getContentResolver().query(b.build(), camps, null, null, CalendarContract.Instances.BEGIN + " ASC");
        }
        if (c == null) return llista;
        try {
            while (c.moveToNext()) {
                String titol = c.getString(0);
                if (titol == null || titol.trim().isEmpty()) titol = "(sense títol)";
                long ini = c.getLong(1), end = c.getLong(2);
                boolean totElDia = c.getInt(3) == 1;
                if (totElDia) {
                    LocalDate d0 = Instant.ofEpochMilli(ini).atZone(ZoneOffset.UTC).toLocalDate();
                    LocalDate d1 = Instant.ofEpochMilli(end).atZone(ZoneOffset.UTC).toLocalDate();
                    if (!d1.isAfter(d0)) d1 = d0.plusDays(1);
                    if (!dia.isBefore(d0) && dia.isBefore(d1)) llista.add("Tot el dia · " + titol);
                } else if (ini < fi && (end > inici || ini >= inici)) {
                    LocalDateTime h = Instant.ofEpochMilli(ini).atZone(zona).toLocalDateTime();
                    String hora = ini < inici ? "Des d'abans"
                            : String.format(Locale.ROOT, "%02d:%02d", h.getHour(), h.getMinute());
                    llista.add(hora + " · " + titol);
                }
            }
        } finally {
            c.close();
        }
        return llista;
    }

    private void desaEsdeveniment(ParserAlarma.Resultat r, String frase) {
        triaCalendari(false, frase, cal -> inserta(r, frase, cal));
    }

    private void inserta(ParserAlarma.Resultat r, String frase, Calendari cal) {
        try {
            ZoneId zona = ZoneId.systemDefault();
            long inici = r.quan.atZone(zona).toInstant().toEpochMilli();

            ContentValues ev = new ContentValues();
            ev.put(CalendarContract.Events.CALENDAR_ID, cal.id);
            ev.put(CalendarContract.Events.TITLE, r.titol);
            ev.put(CalendarContract.Events.DTSTART, inici);
            ev.put(CalendarContract.Events.DTEND, inici + DURADA_ESDEVENIMENT_MIN * 60_000L);
            ev.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());
            ev.put(CalendarContract.Events.HAS_ALARM, 1);
            List<LocalDateTime> avisos = ParserAlarma.avisos(r.quan, LocalDateTime.now());
            ev.put(CalendarContract.Events.DESCRIPTION,
                    "Avisos: " + ParserAlarma.descriuAvisos(avisos, r.quan) + "\n(Apuntat amb Alarma per veu)");
            Uri uri = getContentResolver().insert(CalendarContract.Events.CONTENT_URI, ev);
            if (uri == null) throw new IllegalStateException("el calendari no ha acceptat l'esdeveniment");
            long idEsdeveniment = Long.parseLong(uri.getLastPathSegment());

            // Avisos: el dia abans a les 20:00 i el mateix dia a les 7:00 (els que encara siguin possibles)
            for (LocalDateTime a : avisos) {
                long minutsAbans = (inici - a.atZone(zona).toInstant().toEpochMilli()) / 60_000L;
                ContentValues avis = new ContentValues();
                avis.put(CalendarContract.Reminders.EVENT_ID, idEsdeveniment);
                avis.put(CalendarContract.Reminders.MINUTES, (int) minutsAbans);
                avis.put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT);
                getContentResolver().insert(CalendarContract.Reminders.CONTENT_URI, avis);
            }

            acaba("📅 " + r.titol + "\n" + r.dataText()
                    + "\nAvisos: " + ParserAlarma.descriuAvisos(avisos, r.quan)
                    + "\nAgenda: " + cal.nom);
        } catch (RuntimeException e) {
            mostra(getString(R.string.sense_calendari), "Error: " + e.getMessage(), frase, true);
        }
    }

    // ---------------- Triar el calendari ----------------

    private static final String PREFS = "config";
    private static final String CLAU_CALENDARI = "calendari";

    private static final class Calendari {
        long id;
        String nom;
        String compte;
        int punts;
    }

    private interface DespresDeTriar {
        void fes(Calendari cal);
    }

    /** Calendaris visibles on es pot escriure, del més probable al menys (el principal de Google primer). */
    private List<Calendari> calendaris() {
        String[] camps = {
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.ACCOUNT_TYPE,
                CalendarContract.Calendars.OWNER_ACCOUNT,
                CalendarContract.Calendars.IS_PRIMARY,
        };
        String filtre = CalendarContract.Calendars.VISIBLE + " = 1 AND "
                + CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL + " >= "
                + CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR;
        List<Calendari> llista = new ArrayList<>();
        try (Cursor c = getContentResolver().query(CalendarContract.Calendars.CONTENT_URI, camps, filtre, null, null)) {
            if (c == null) return llista;
            while (c.moveToNext()) {
                Calendari cal = new Calendari();
                cal.id = c.getLong(0);
                cal.nom = c.getString(1) != null ? c.getString(1) : "(sense nom)";
                cal.compte = c.getString(2) != null ? c.getString(2) : "";
                String tipus = c.getString(3);
                String propietari = c.getString(4);
                if ("com.google".equals(tipus)) cal.punts += 4;
                if (propietari != null && propietari.equalsIgnoreCase(cal.compte)) cal.punts += 2; // el calendari principal del compte
                if (c.getInt(5) == 1) cal.punts += 1;
                llista.add(cal);
            }
        }
        llista.sort((x, y) -> y.punts - x.punts);
        return llista;
    }

    /**
     * Fa servir el calendari triat l'últim cop. Si no n'hi ha cap de triat i en hi ha més d'un,
     * pregunta on apuntar les coses (i ho recorda per a la propera vegada).
     */
    private void triaCalendari(boolean preguntaSempre, String frase, DespresDeTriar despres) {
        List<Calendari> llista;
        try {
            llista = calendaris();
        } catch (RuntimeException e) {
            mostra(getString(R.string.sense_calendari), "Error: " + e.getMessage(), frase, true);
            return;
        }
        if (llista.isEmpty()) {
            mostra(getString(R.string.sense_calendari), getString(R.string.sense_calendari_detall), frase, true);
            return;
        }
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        long desat = prefs.getLong(CLAU_CALENDARI, -1);
        if (!preguntaSempre) {
            for (Calendari c : llista) {
                if (c.id == desat) {
                    despres.fes(c);
                    return;
                }
            }
            if (llista.size() == 1) {
                prefs.edit().putLong(CLAU_CALENDARI, llista.get(0).id).apply();
                despres.fes(llista.get(0));
                return;
            }
        }
        String[] noms = new String[llista.size()];
        for (int i = 0; i < noms.length; i++) {
            Calendari c = llista.get(i);
            noms[i] = c.compte.isEmpty() || c.compte.equals(c.nom) ? c.nom : c.nom + "  ·  " + c.compte;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.on_apunto)
                .setItems(noms, (d, i) -> {
                    Calendari c = llista.get(i);
                    prefs.edit().putLong(CLAU_CALENDARI, c.id).apply();
                    actualitzaBotoCalendari();
                    despres.fes(c);
                })
                .setOnCancelListener(d -> mostra(getString(R.string.toca_per_parlar), getString(R.string.exemple), null, false))
                .show();
    }

    private void actualitzaBotoCalendari() {
        TextView boto = findViewById(R.id.boto_calendari);
        String nom = null;
        if (checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
            long desat = getSharedPreferences(PREFS, MODE_PRIVATE).getLong(CLAU_CALENDARI, -1);
            try {
                for (Calendari c : calendaris()) if (c.id == desat) nom = c.nom;
            } catch (RuntimeException ignorat) {
                // si no es pot llegir, ensenyem el text genèric
            }
        }
        boto.setText(nom != null ? getString(R.string.agenda_actual, nom) : getString(R.string.tria_agenda));
    }

    // ---------------- Pantalla ----------------

    /** Missatge breu de confirmació i tancar l'app. */
    private void acaba(String missatge) {
        Toast.makeText(getApplicationContext(), missatge, Toast.LENGTH_LONG).show();
        finishAndRemoveTask();
    }

    private static String falten(long minuts) {
        long h = minuts / 60, m = minuts % 60;
        if (h == 0) return "Sonarà d'aquí a " + m + " min";
        if (m == 0) return "Sonarà d'aquí a " + h + " h";
        return "Sonarà d'aquí a " + h + " h " + m + " min";
    }

    private void obreAlarmes() {
        try {
            startActivity(new Intent(AlarmClock.ACTION_SHOW_ALARMS));
        } catch (ActivityNotFoundException e) {
            mostra(getString(R.string.sense_rellotge), getString(R.string.sense_rellotge_detall), null, true);
        }
    }

    private void mostra(String titol, String text, String frase, boolean error) {
        filaDubte.setVisibility(View.GONE);
        estat.setText(titol);
        estat.setTextColor(getColor(error ? R.color.error : R.color.text));
        detall.setText(text);
        if (frase != null) {
            escoltat.setText(getString(R.string.he_sentit, frase));
            escoltat.setVisibility(View.VISIBLE);
        } else {
            escoltat.setVisibility(View.GONE);
        }
    }
}
