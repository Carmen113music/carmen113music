package cat.alarmaveu;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorDescription;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.content.ContentResolver;
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
    /** Totes les alarmes que posa l'app comencen així, per trobar-les i esborrar-les fàcilment al Rellotge. */
    private static final String PREFIX_ALARMA = "VR-";

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
        findViewById(R.id.boto_agenda).setOnClickListener(v -> obreAgenda());
        findViewById(R.id.boto_historial).setOnClickListener(v ->
                startActivity(new Intent(this, HistorialActivity.class)));
        findViewById(R.id.boto_calendari).setOnClickListener(v ->
                ambPermisCalendari(() -> triaCalendari(true, null, c ->
                        mostra(getString(R.string.agenda_desada), c.curt(), null, false)), null));
        actualitzaBotoCalendari();
        sincronitzaCalendariTriat();

        if (savedInstanceState == null) escolta();
    }

    /** Quan es torna a obrir l'app des de la icona, torna a escoltar directament. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        sincronitzaCalendariTriat();
        escolta();
    }

    // ---------------- Escoltar ----------------

    /** Obre la finestra de dictat de Google (la que funcionava millor). */
    private void escolta() {
        filaDubte.setVisibility(View.GONE);
        try {
            startActivityForResult(intentVeu(), PETICIO_VEU);
        } catch (ActivityNotFoundException e) {
            mostra(getString(R.string.sense_reconeixement), getString(R.string.sense_reconeixement_detall), null, true);
        }
    }

    private Intent intentVeu() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ca-ES");
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ca-ES");
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.indicacio_veu));
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        return i;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PETICIO_VEU) return;
        if (resultCode != RESULT_OK || data == null) {
            mostra(getString(R.string.toca_per_parlar), getString(R.string.exemple), null, false);
            return;
        }
        processa(data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS));
    }

    /** Decideix què fer amb el que s'ha sentit. */
    private void processa(ArrayList<String> frases) {
        if (frases == null || frases.isEmpty()) {
            mostra(getString(R.string.no_t_he_sentit), getString(R.string.toca_per_tornar), null, false);
            return;
        }

        LocalDateTime ara = LocalDateTime.now();
        LocalDate diaPreguntat = ParserAlarma.diaConsulta(frases.get(0), ara);
        if (diaPreguntat != null) {
            String frase = frases.get(0);
            ambPermisCalendari(() -> mostraDia(diaPreguntat, frase), frase);
            return;
        }
        String terme = ParserAlarma.termeCerca(frases.get(0));
        if (terme != null) {
            String frase = frases.get(0);
            ambPermisCalendari(() -> mostraCerca(terme, frase), frase);
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
                .putExtra(AlarmClock.EXTRA_MESSAGE, PREFIX_ALARMA + r.titol)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                // Tasca pròpia: si no, en tancar la nostra app es perdia l'encàrrec abans que el Rellotge el desés
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(a);
        } catch (ActivityNotFoundException e) {
            mostra(getString(R.string.sense_rellotge), getString(R.string.sense_rellotge_detall), frase, true);
            return;
        }
        acaba("⏰ " + r.horaText() + " · " + r.titol + "\n" + falten(r.minutsFins), frase);
    }

    // ---------------- Temporitzador ----------------

    /**
     * Temporitzador → alarma al Rellotge a l'hora que toca (ara + durada), amb el títol «⏱ …».
     * El temporitzador del Rellotge d'aquest mòbil s'obre però no accepta el temps, en canvi les alarmes sí.
     */
    private void posaTemporitzador(ParserAlarma.Resultat r, String frase) {
        LocalDateTime ara = LocalDateTime.now();
        LocalDateTime quan = ara.plusSeconds(r.segons).plusSeconds(30).truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        LocalDateTime minim = ara.truncatedTo(java.time.temporal.ChronoUnit.MINUTES).plusMinutes(1);
        if (quan.isBefore(minim)) quan = minim; // el Rellotge treballa en minuts: com a mínim, el minut següent
        Intent a = new Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, quan.getHour())
                .putExtra(AlarmClock.EXTRA_MINUTES, quan.getMinute())
                .putExtra(AlarmClock.EXTRA_MESSAGE, PREFIX_ALARMA + "⏱ " + r.titol)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                // Tasca pròpia: si no, en tancar la nostra app es perdia l'encàrrec abans que el Rellotge el desés
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(a);
        } catch (ActivityNotFoundException e) {
            mostra(getString(R.string.sense_rellotge), getString(R.string.sense_rellotge_detall), frase, true);
            return;
        }
        acaba(String.format(Locale.ROOT, "⏱ %02d:%02d · %s\n(d'aquí %s)", quan.getHour(), quan.getMinute(), r.titol, r.duradaText()), frase);
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
        // Ja està apuntat exactament igual? (sol passar quan es torna a dir perquè encara no surt a Google)
        String mateix = ParserAlarma.normalitza(r.horaText() + " · " + r.titol);
        for (String c : coses) {
            if (ParserAlarma.normalitza(c).equals(mateix)) {
                mostra(getString(R.string.ja_apuntat), "El " + ParserAlarma.nomDia(r.quan.toLocalDate())
                        + " a les " + r.horaText() + " ja hi ha «" + r.titol + "».\n\n"
                        + "Si no el veus a Google Calendar, és que encara s'està pujant: no cal tornar-ho a apuntar.",
                        frase, true);
                filaDubte.setVisibility(View.VISIBLE);
                findViewById(R.id.boto_si).setOnClickListener(v -> desaEsdeveniment(r, frase));
                findViewById(R.id.boto_no).setOnClickListener(v -> acaba("✕ Ja estava apuntat: " + r.titol, frase));
                return;
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("El ").append(ParserAlarma.nomDia(r.quan.toLocalDate())).append(" ja tens:\n");
        for (String c : coses) sb.append("• ").append(c).append("\n");
        sb.append("\nVols apuntar «").append(r.titol).append("» a les ").append(r.horaText()).append(" igualment?");
        mostra(getString(R.string.compte), sb.toString(), frase, true);
        filaDubte.setVisibility(View.VISIBLE);
        findViewById(R.id.boto_si).setOnClickListener(v -> desaEsdeveniment(r, frase));
        findViewById(R.id.boto_no).setOnClickListener(v -> acaba("✕ No s'ha apuntat: " + r.titol, frase));
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
        Historial.afegeix(this, frase, "🔎 " + nom + ": " + (coses.isEmpty() ? "res apuntat" : coses.size() + " coses"));
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

    /** Resposta a "quin dia tinc metge?": les properes coses de l'agenda que hi coincideixen (12 mesos). */
    private void mostraCerca(String terme, String frase) {
        List<String> trobats = new ArrayList<>();
        try {
            ZoneId zona = ZoneId.systemDefault();
            LocalDate avui = LocalDate.now();
            long ara = System.currentTimeMillis();
            long inici = avui.atStartOfDay(zona).toInstant().toEpochMilli();
            long fi = avui.plusYears(1).atStartOfDay(zona).toInstant().toEpochMilli();
            Uri.Builder b = CalendarContract.Instances.CONTENT_URI.buildUpon();
            ContentUris.appendId(b, inici);
            ContentUris.appendId(b, fi);
            String[] camps = {
                    CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END,
                    CalendarContract.Instances.ALL_DAY,
                    CalendarContract.Instances.EVENT_LOCATION,
            };
            Cursor c;
            try {
                c = getContentResolver().query(b.build(), camps, CalendarContract.Instances.VISIBLE + " = 1",
                        null, CalendarContract.Instances.BEGIN + " ASC");
            } catch (RuntimeException e) {
                c = getContentResolver().query(b.build(), camps, null, null, CalendarContract.Instances.BEGIN + " ASC");
            }
            if (c != null) {
                try {
                    while (c.moveToNext() && trobats.size() < 8) {
                        String titol = c.getString(0) == null ? "" : c.getString(0);
                        String lloc = c.getString(4) == null ? "" : c.getString(4);
                        if (!ParserAlarma.coincideix(titol + " " + lloc, terme)) continue;
                        long ini = c.getLong(1), end = c.getLong(2);
                        if (c.getInt(3) == 1) {
                            LocalDate d = Instant.ofEpochMilli(ini).atZone(ZoneOffset.UTC).toLocalDate();
                            if (d.isBefore(avui)) continue;
                            trobats.add(ParserAlarma.nomDia(d) + " · tot el dia · " + titol);
                        } else {
                            if (end < ara) continue; // ja ha passat
                            LocalDateTime h = Instant.ofEpochMilli(ini).atZone(zona).toLocalDateTime();
                            trobats.add(ParserAlarma.nomDia(h.toLocalDate())
                                    + String.format(Locale.ROOT, " · %02d:%02d · ", h.getHour(), h.getMinute()) + titol);
                        }
                    }
                } finally {
                    c.close();
                }
            }
        } catch (RuntimeException e) {
            mostra(getString(R.string.sense_calendari), "Error: " + e.getMessage(), frase, true);
            return;
        }
        String nom = terme.substring(0, 1).toUpperCase(Locale.ROOT) + terme.substring(1);
        if (trobats.isEmpty()) {
            Historial.afegeix(this, frase, "🔎 " + nom + ": no trobat");
            mostra(nom, getString(R.string.cerca_buida, terme), frase, false);
            return;
        }
        Historial.afegeix(this, frase, "🔎 " + nom + ": " + trobats.get(0));
        StringBuilder sb = new StringBuilder();
        for (String t : trobats) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(t);
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

            String avisSincro = sincronitza(cal);
            String fet = "📅 " + r.titol + "\n" + r.dataText()
                    + "\nAvisos: " + ParserAlarma.descriuAvisos(avisos, r.quan)
                    + "\nAgenda: " + cal.curt();
            if (avisSincro.isEmpty()) {
                acaba(fet, frase);
            } else {
                // Apuntat, però amb un problema de sincronització: l'app es queda oberta perquè es pugui llegir
                Historial.afegeix(this, frase, fet);
                mostra(getString(R.string.apuntat_amb_avis), fet + "\n\n⚠ " + avisSincro, frase, false);
            }
        } catch (RuntimeException e) {
            mostra(getString(R.string.sense_calendari), "Error: " + e.getMessage(), frase, true);
        }
    }

    /**
     * Perquè l'esdeveniment pugi de seguida al compte de Google (i es vegi a Google Calendar,
     * al web i als altres dispositius) i no es quedi només al telèfon:
     * - si el calendari tenia la sincronització d'esdeveniments desactivada, l'activa;
     * - demana una sincronització immediata d'aquell compte.
     * Retorna un avís per a la confirmació si el calendari és només del telèfon.
     */
    private String sincronitza(Calendari cal) {
        if (esLocal(cal)) return "Aquest calendari és només del telèfon: no es veurà a Google Calendar. "
                + "Tria el de Gmail amb el botó «Agenda» de sota.";
        String avis = "";
        try {
            Account compte = new Account(cal.compte, cal.tipus);
            if (!ContentResolver.getMasterSyncAutomatically()) {
                avis = "La sincronització automàtica del mòbil està desactivada, i per això Google tarda a rebre-ho. "
                        + "Activa-la a Configuració → Comptes i còpia de seguretat → Gestiona els comptes → "
                        + "«Sincronitza dades automàticament».";
            } else if (!ContentResolver.getSyncAutomatically(compte, CalendarContract.AUTHORITY)) {
                avis = "El compte " + cal.compte + " té la sincronització del Calendari desactivada. "
                        + "Activa-la a Configuració → Comptes → " + cal.compte + " → Sincronitza el compte → Calendari.";
            }
            if (!cal.sincronitza) {
                ContentValues v = new ContentValues();
                v.put(CalendarContract.Calendars.SYNC_EVENTS, 1);
                getContentResolver().update(
                        ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, cal.id), v, null, null);
            }
            demanaSincronitzacio(compte);
        } catch (RuntimeException ignorat) {
            // si no es pot forçar, ja es sincronitzarà quan el mòbil ho faci
        }
        return avis;
    }

    /** Calendaris que només són al telèfon (no es pugen a cap compte en línia). */
    private static boolean esLocal(Calendari cal) {
        String t = cal.tipus == null ? "local" : cal.tipus.toLowerCase(Locale.ROOT);
        if (t.equals("com.osp.app.signin")) return false; // compte Samsung (en línia)
        return t.isEmpty() || t.contains("local") || t.contains("samsung") || t.contains("honor")
                || t.contains("huawei") || t.contains("xiaomi") || t.contains("miui") || t.contains("oppo")
                || t.contains("coloros") || t.contains("vivo") || t.contains("oneplus") || t.contains("phone")
                || t.contains("courseschedule") || t.contains("birthday");
    }

    /** Sincronització manual (passa per sobre de la sincronització automàtica desactivada). */
    private static void demanaSincronitzacio(Account compte) {
        Bundle extres = new Bundle();
        extres.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
        extres.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
        ContentResolver.requestSync(compte, CalendarContract.AUTHORITY, extres);
    }

    /**
     * Cada cop que s'obre l'app, demana pujar el calendari triat. Així, si alguna cosa s'havia quedat
     * pendent de pujar a Google, s'acaba enviant.
     */
    private void sincronitzaCalendariTriat() {
        if (checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return;
        try {
            long desat = getSharedPreferences(PREFS, MODE_PRIVATE).getLong(CLAU_CALENDARI, -1);
            for (Calendari c : calendaris()) {
                if (c.id == desat && !esLocal(c)) demanaSincronitzacio(new Account(c.compte, c.tipus));
            }
        } catch (RuntimeException ignorat) {
            // no és imprescindible
        }
    }

    // ---------------- Triar el calendari ----------------

    private static final String PREFS = "config";
    private static final String CLAU_CALENDARI = "calendari_v2"; // v2: torna a preguntar un cop amb la llista nova

    private static final class Calendari {
        long id;
        String nom;
        String compte;
        String tipus;
        int color;
        boolean visible;
        boolean sincronitza;
        boolean principal;

        /** Quina app / servei és: «Google Calendar», «Calendari del telèfon»... */
        String app() {
            String t = tipus == null ? "" : tipus.toLowerCase(java.util.Locale.ROOT);
            if (t.equals("com.google")) return "Google Calendar";
            if (t.equals("com.osp.app.signin")) return "Compte Samsung";
            if (t.contains("exchange") || t.contains("outlook") || t.contains("microsoft") || t.contains("office"))
                return "Outlook";
            if (t.contains("caldav") || t.contains("dav")) return "CalDAV";
            if (t.contains("courseschedule")) return "Horari de classes del telèfon";
            return "Calendari del telèfon";
        }

        /** El calendari principal d'un compte de Google es diu com el correu: millor dir-ne «Principal». */
        String nomCurt() {
            return nom.equalsIgnoreCase(compte) ? "Principal" : nom;
        }

        /** Text curt per a la confirmació i el botó: «Familia (Google Calendar · compte)». */
        String curt() {
            String c = !compte.isEmpty() && !compte.equalsIgnoreCase(nom) ? " · " + compte : "";
            return nomCurt() + " (" + app() + c + ")";
        }

        /** Per agrupar: mateix compte i mateixa app. */
        String clauGrup() {
            return (tipus == null ? "" : tipus) + "|" + compte;
        }
    }

    private interface DespresDeTriar {
        void fes(Calendari cal);
    }

    /**
     * Tots els calendaris on es pot escriure, ordenats: comptes de Google primer,
     * agrupats per compte, i dins de cada compte el principal primer.
     */
    private List<Calendari> calendaris() {
        String[] camps = {
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.ACCOUNT_TYPE,
                CalendarContract.Calendars.OWNER_ACCOUNT,
                CalendarContract.Calendars.VISIBLE,
                CalendarContract.Calendars.SYNC_EVENTS,
                CalendarContract.Calendars.CALENDAR_COLOR,
        };
        String filtre = CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL + " >= "
                + CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR;
        List<Calendari> llista = new ArrayList<>();
        try (Cursor c = getContentResolver().query(CalendarContract.Calendars.CONTENT_URI, camps, filtre, null, null)) {
            if (c == null) return llista;
            while (c.moveToNext()) {
                Calendari cal = new Calendari();
                cal.id = c.getLong(0);
                cal.nom = c.getString(1) != null ? c.getString(1) : "(sense nom)";
                cal.compte = c.getString(2) != null ? c.getString(2) : "";
                cal.tipus = c.getString(3);
                String propietari = c.getString(4);
                cal.visible = c.getInt(5) == 1;
                cal.sincronitza = c.getInt(6) == 1;
                cal.color = c.getInt(7);
                cal.principal = propietari != null && propietari.equalsIgnoreCase(cal.compte);
                llista.add(cal);
            }
        }
        llista.sort((x, y) -> {
            boolean gx = "com.google".equals(x.tipus), gy = "com.google".equals(y.tipus);
            if (gx != gy) return gx ? -1 : 1;                                   // Google primer
            boolean lx = esLocal(x), ly = esLocal(y);
            if (lx != ly) return lx ? 1 : -1;                                   // els del telèfon al final
            int pc = x.clauGrup().compareToIgnoreCase(y.clauGrup());            // agrupats per compte
            if (pc != 0) return pc;
            if (x.principal != y.principal) return x.principal ? -1 : 1;        // el principal primer
            return x.nom.compareToIgnoreCase(y.nom);
        });
        return llista;
    }

    /** Els que val la pena oferir: en línia i sincronitzats. La resta queden a «Altres calendaris». */
    private static boolean recomanat(Calendari c) {
        return !esLocal(c) && c.sincronitza;
    }

    /**
     * Fa servir el calendari triat. Si encara no se n'ha triat cap (o ja no existeix), sempre pregunta,
     * encara que només n'hi hagi un, perquè quedi clar on s'apunten les coses.
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
        }
        mostraSelector(llista, desat, frase, prefs, despres);
    }

    // ---------------- Finestra per triar el calendari ----------------

    /**
     * Llista ordenada per comptes: a cada compte, la icona de la seva app i el correu,
     * i a sota els seus calendaris amb el seu color. Els calendaris que només són al telèfon
     * (aniversaris, horaris...) queden plegats a «Altres calendaris».
     */
    private void mostraSelector(List<Calendari> llista, long desat, String frase,
                                SharedPreferences prefs, DespresDeTriar despres) {
        List<Calendari> bons = new ArrayList<>();
        List<Calendari> altres = new ArrayList<>();
        for (Calendari c : llista) (recomanat(c) ? bons : altres).add(c);
        if (bons.isEmpty()) { // si no n'hi ha cap de recomanat, els ensenyem tots
            bons.addAll(altres);
            altres.clear();
        }
        boolean triatEsAltre = false;
        for (Calendari c : altres) if (c.id == desat) triatEsAltre = true;

        ScrollView scroll = new ScrollView(this);
        LinearLayout cos = new LinearLayout(this);
        cos.setOrientation(LinearLayout.VERTICAL);
        cos.setPadding(dp(12), dp(4), dp(12), dp(12));
        scroll.addView(cos);

        AlertDialog dialeg = new AlertDialog.Builder(this)
                .setTitle(R.string.on_apunto)
                .setView(scroll)
                .setNegativeButton(R.string.cancela, null)
                .setOnCancelListener(d -> mostra(getString(R.string.toca_per_parlar), getString(R.string.exemple), null, false))
                .create();

        DespresDeTriar tria = c -> {
            dialeg.dismiss();
            prefs.edit().putLong(CLAU_CALENDARI, c.id).apply();
            actualitzaBotoCalendari();
            despres.fes(c);
        };

        afegeixGrups(cos, bons, desat, tria);

        if (!altres.isEmpty()) {
            LinearLayout plegats = new LinearLayout(this);
            plegats.setOrientation(LinearLayout.VERTICAL);
            afegeixGrups(plegats, altres, desat, tria);
            plegats.setVisibility(triatEsAltre ? View.VISIBLE : View.GONE);

            TextView mostraAltres = textSelector(getString(triatEsAltre ? R.string.amaga_altres : R.string.mostra_altres,
                    altres.size()), 15, R.color.accent);
            mostraAltres.setPadding(dp(8), dp(16), dp(8), dp(8));
            mostraAltres.setOnClickListener(v -> {
                boolean obrir = plegats.getVisibility() != View.VISIBLE;
                plegats.setVisibility(obrir ? View.VISIBLE : View.GONE);
                mostraAltres.setText(getString(obrir ? R.string.amaga_altres : R.string.mostra_altres, altres.size()));
            });
            cos.addView(mostraAltres);
            TextView nota = textSelector(getString(R.string.altres_nota), 13, R.color.text_suau);
            nota.setPadding(dp(8), 0, dp(8), dp(4));
            plegats.addView(nota, 0);
            cos.addView(plegats);
        }

        TextView noHiEs = textSelector(getString(R.string.no_hi_es_el_compte), 15, R.color.text_suau);
        noHiEs.setPadding(dp(8), dp(16), dp(8), dp(8));
        noHiEs.setOnClickListener(v -> {
            dialeg.dismiss();
            mostra(getString(R.string.no_hi_es_el_compte), getString(R.string.no_hi_es_el_compte_detall), frase, true);
        });
        cos.addView(noHiEs);

        dialeg.show();
    }

    /** Afegeix els calendaris agrupats per compte: capçalera (icona + app + correu) i files. */
    private void afegeixGrups(LinearLayout on, List<Calendari> llista, long desat, DespresDeTriar tria) {
        String grupActual = null;
        for (Calendari c : llista) {
            if (!c.clauGrup().equals(grupActual)) {
                grupActual = c.clauGrup();
                on.addView(capcalera(c));
            }
            on.addView(fila(c, c.id == desat, tria));
        }
    }

    private View capcalera(Calendari c) {
        LinearLayout fila = new LinearLayout(this);
        fila.setOrientation(LinearLayout.HORIZONTAL);
        fila.setGravity(Gravity.CENTER_VERTICAL);
        fila.setPadding(dp(8), dp(18), dp(8), dp(6));

        View icona = iconaApp(c);
        LinearLayout.LayoutParams pi = new LinearLayout.LayoutParams(dp(32), dp(32));
        pi.setMarginEnd(dp(12));
        fila.addView(icona, pi);

        LinearLayout textos = new LinearLayout(this);
        textos.setOrientation(LinearLayout.VERTICAL);
        TextView app = textSelector(c.app(), 16, R.color.text);
        app.setTypeface(Typeface.DEFAULT_BOLD);
        textos.addView(app);
        if (!c.compte.isEmpty() && !esLocal(c)) textos.addView(textSelector(c.compte, 14, R.color.text_suau));
        fila.addView(textos, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return fila;
    }

    private View fila(Calendari c, boolean triat, DespresDeTriar tria) {
        LinearLayout fila = new LinearLayout(this);
        fila.setOrientation(LinearLayout.HORIZONTAL);
        fila.setGravity(Gravity.CENTER_VERTICAL);
        fila.setMinimumHeight(dp(52));
        fila.setPadding(dp(52), dp(6), dp(12), dp(6)); // sagnat sota la icona del compte
        GradientDrawable fons = new GradientDrawable();
        fons.setCornerRadius(dp(12));
        fons.setColor(triat ? 0x22E0563B : 0x00000000);
        fila.setBackground(fons);

        View punt = new View(this);
        GradientDrawable rodona = new GradientDrawable();
        rodona.setShape(GradientDrawable.OVAL);
        rodona.setColor(c.color != 0 ? (0xFF000000 | c.color) : getColor(R.color.text_suau));
        punt.setBackground(rodona);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(14), dp(14));
        pp.setMarginEnd(dp(14));
        fila.addView(punt, pp);

        TextView nom = textSelector(c.nomCurt() + (c.principal && !c.nomCurt().equals("Principal") ? "  ★" : ""),
                17, R.color.text);
        if (triat) nom.setTypeface(Typeface.DEFAULT_BOLD);
        fila.addView(nom, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        if (!c.sincronitza) fila.addView(textSelector("no se sincronitza", 12, R.color.text_suau));
        if (triat) {
            TextView marca = textSelector("✓", 20, R.color.accent);
            marca.setTypeface(Typeface.DEFAULT_BOLD);
            marca.setPadding(dp(8), 0, 0, 0);
            fila.addView(marca);
        }
        fila.setOnClickListener(v -> tria.fes(c));
        return fila;
    }

    /** Icona de l'app del calendari; si no se'n troba, un cercle amb la inicial. */
    private View iconaApp(Calendari c) {
        Drawable d = null;
        PackageManager pm = getPackageManager();
        String t = c.tipus == null ? "" : c.tipus;
        if (t.equals("com.google")) {
            try {
                d = pm.getApplicationIcon("com.google.android.calendar");
            } catch (PackageManager.NameNotFoundException | RuntimeException ignorat) {
                // Google Calendar no instal·lat: provem la icona del compte
            }
        }
        if (d == null && !esLocal(c)) {
            try {
                for (AuthenticatorDescription a : AccountManager.get(this).getAuthenticatorTypes()) {
                    if (a.type.equals(t) && a.iconId != 0) {
                        d = pm.getDrawable(a.packageName, a.iconId, null);
                        break;
                    }
                }
            } catch (RuntimeException ignorat) {
                // sense icona
            }
        }
        if (d == null) {
            try { // calendaris del telèfon: la icona de l'app de calendari del mòbil
                ResolveInfo ri = pm.resolveActivity(
                        Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR), 0);
                if (ri != null) d = ri.loadIcon(pm);
            } catch (RuntimeException ignorat) {
                // sense icona
            }
        }
        if (d != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(d);
            return iv;
        }
        TextView lletra = textSelector(c.app().substring(0, 1), 16, R.color.blanc);
        lletra.setGravity(Gravity.CENTER);
        lletra.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable rodona = new GradientDrawable();
        rodona.setShape(GradientDrawable.OVAL);
        rodona.setColor(getColor(R.color.accent));
        lletra.setBackground(rodona);
        return lletra;
    }

    private TextView textSelector(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(getColor(color));
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void actualitzaBotoCalendari() {
        TextView boto = findViewById(R.id.boto_calendari);
        String text = null;
        if (checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
            long desat = getSharedPreferences(PREFS, MODE_PRIVATE).getLong(CLAU_CALENDARI, -1);
            try {
                for (Calendari c : calendaris()) if (c.id == desat) text = c.curt();
            } catch (RuntimeException ignorat) {
                // si no es pot llegir, ensenyem el text genèric
            }
        }
        boto.setText(text != null ? getString(R.string.agenda_actual, text) : getString(R.string.tria_agenda));
    }

    // ---------------- Pantalla ----------------

    /** Missatge breu de confirmació i tancar l'app. */
    private void acaba(String missatge, String frase) {
        Historial.afegeix(this, frase, missatge);
        Toast.makeText(getApplicationContext(), missatge, Toast.LENGTH_LONG).show();
        // Esperem una mica abans de tancar perquè el Rellotge tingui temps de desar l'alarma.
        estat.postDelayed(this::finish, 1500);
    }

    private static String falten(long minuts) {
        long h = minuts / 60, m = minuts % 60;
        if (h == 0) return "Sonarà d'aquí a " + m + " min";
        if (m == 0) return "Sonarà d'aquí a " + h + " h";
        return "Sonarà d'aquí a " + h + " h " + m + " min";
    }

    /** Obre l'app d'agenda del mòbil al dia d'avui. */
    private void obreAgenda() {
        Intent i = new Intent(Intent.ACTION_VIEW)
                .setData(Uri.parse("content://com.android.calendar/time/" + System.currentTimeMillis()))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            try {
                startActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException e2) {
                mostra(getString(R.string.sense_app_agenda), "", null, true);
            }
        }
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
        if (error && frase != null && !titol.equals(getString(R.string.compte)) && !titol.equals(getString(R.string.ja_apuntat))) {
            Historial.afegeix(this, frase, "⚠ " + titol + (text != null ? ": " + text : ""));
        }
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
