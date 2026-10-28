package cat.alarmaveu;

import android.Manifest;
import android.accounts.Account;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SyncAdapterType;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * Pantalla de diagnòstic: mostra l'estat de la sincronització del calendari triat i permet provar
 * les diferents maneres de demanar un temporitzador al Rellotge, per quedar-se amb la que funciona.
 */
public class DiagnosticActivity extends Activity {

    private static final int PETICIO_COMPTE = 1;
    private static final int SEGONS_PROVA = 10;

    private LinearLayout cos;
    private TextView informe;

    private String calNom, calCompte, calTipus;
    private long calId = -1;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(getColor(R.color.fons));
        cos = new LinearLayout(this);
        cos.setOrientation(LinearLayout.VERTICAL);
        cos.setPadding(dp(20), dp(20), dp(20), dp(32));
        scroll.addView(cos);
        setContentView(scroll);
        omple();
    }

    @Override
    protected void onResume() {
        super.onResume();
        omple();
    }

    private void omple() {
        cos.removeAllViews();
        llegeixCalendariTriat();

        TextView titol = text("🔧 Diagnòstic", 24, R.color.text);
        titol.setTypeface(Typeface.DEFAULT_BOLD);
        cos.addView(titol);

        // ---------- Sincronització ----------
        cos.addView(seccio("Sincronització amb Google"));
        StringBuilder sb = new StringBuilder();
        sb.append("App: Alarma per veu ").append(versio()).append("\n");
        sb.append("Mòbil: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL)
                .append(" · Android ").append(Build.VERSION.RELEASE).append("\n\n");

        boolean acces = false;
        if (calId < 0) {
            sb.append("Calendari triat: cap\n");
        } else {
            sb.append("Calendari triat: ").append(calNom).append("\n");
            sb.append("Compte: ").append(calCompte).append(" (").append(calTipus).append(")\n");
            acces = Comptes.teAcces(this, calCompte, calTipus);
            sb.append("Accés de l'app al compte: ").append(acces ? "✅ sí" : "❌ no").append("\n");
            sb.append("Sincronització general del mòbil: ")
                    .append(siNo(ContentResolver.getMasterSyncAutomatically())).append("\n");
            if (acces) {
                Account a = new Account(calCompte, calTipus);
                sb.append("Sincronització del calendari del compte: ")
                        .append(siNo(ContentResolver.getSyncAutomatically(a, CalendarContract.AUTHORITY))).append("\n");
                sb.append("Sincronitzant ara: ").append(ContentResolver.isSyncActive(a, CalendarContract.AUTHORITY) ? "sí" : "no")
                        .append(" · Pendent: ").append(ContentResolver.isSyncPending(a, CalendarContract.AUTHORITY) ? "sí" : "no")
                        .append("\n");
            } else {
                sb.append("Sincronització del calendari del compte: (no es pot saber sense accés)\n");
            }
            sb.append("Esdeveniments pendents de pujar: ").append(pendents()).append("\n");
        }
        sb.append("Qui sincronitza el calendari: ").append(sincronitzadors()).append("\n");
        sb.append("Google Calendar instal·lat: ").append(Temporitzadors.installat(this, "com.google.android.calendar") ? "sí" : "no").append("\n");

        // ---------- Rellotge ----------
        sb.append("\nApps per a temporitzadors: ").append(llista(Temporitzadors.appsPer(this, "android.intent.action.SET_TIMER"))).append("\n");
        sb.append("Apps per a alarmes: ").append(llista(Temporitzadors.appsPer(this, "android.intent.action.SET_ALARM"))).append("\n");
        sb.append("Manera de fer temporitzadors: ").append(Temporitzadors.nom(Temporitzadors.modeTriat(this))).append("\n");

        informe = text(sb.toString(), 15, R.color.text);
        informe.setTextIsSelectable(true);
        informe.setLineSpacing(dp(2), 1f);
        cos.addView(informe);

        if (calId >= 0 && !acces) {
            cos.addView(boto("Dona accés al compte " + calCompte, true, v -> demanaAcces()));
        }
        if (calId >= 0 && acces) {
            cos.addView(boto("Puja el calendari ara", false, v -> {
                Comptes.pujaAra(calCompte, calTipus);
                Toast.makeText(this, "Demanat. Mira Google Calendar d'aquí uns segons.", Toast.LENGTH_LONG).show();
                cos.postDelayed(this::omple, 3000);
            }));
        }

        // ---------- Proves de temporitzador ----------
        cos.addView(seccio("Prova del temporitzador"));
        cos.addView(text("Toca cada prova. Hauria de començar un temporitzador de " + SEGONS_PROVA
                + " segons al Rellotge. Quan una funcioni (que surti el compte enrere i soni), toca «Fes servir aquesta».", 15, R.color.text_suau));
        int triat = Temporitzadors.modeTriat(this);
        for (int mode = 1; mode <= Temporitzadors.MODES; mode++) {
            final int m = mode;
            boolean possible = Temporitzadors.crea(this, m, SEGONS_PROVA, "Prova " + m) != null;
            LinearLayout fila = new LinearLayout(this);
            fila.setOrientation(LinearLayout.HORIZONTAL);
            fila.setGravity(Gravity.CENTER_VERTICAL);
            fila.setPadding(0, dp(10), 0, 0);
            TextView nom = text(Temporitzadors.nom(m) + (m == triat ? "  ✓" : "") + (possible ? "" : "\n(no disponible en aquest mòbil)"),
                    15, m == triat ? R.color.accent : R.color.text);
            fila.addView(nom, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            if (possible) {
                fila.addView(boto("Prova", false, v -> prova(m)));
                fila.addView(boto("Fes servir aquesta", false, v -> {
                    Temporitzadors.triaMode(this, m);
                    Toast.makeText(this, "Ara els temporitzadors es faran així", Toast.LENGTH_SHORT).show();
                    omple();
                }));
            }
            cos.addView(fila);
        }
        cos.addView(boto("Cap funciona: fes servir alarmes", false, v -> {
            Temporitzadors.triaMode(this, 0);
            omple();
        }));

        cos.addView(seccio(""));
        cos.addView(boto("Copia el diagnòstic", true, v -> {
            ClipboardManager cm = getSystemService(ClipboardManager.class);
            cm.setPrimaryClip(ClipData.newPlainText("Diagnòstic", informe.getText()));
            Toast.makeText(this, "Copiat. Ja el pots enganxar on vulguis.", Toast.LENGTH_SHORT).show();
        }));
        cos.addView(boto("← Enrere", false, v -> finish()));
    }

    private void prova(int mode) {
        Intent t = Temporitzadors.crea(this, mode, SEGONS_PROVA, "Prova " + mode);
        if (t == null) return;
        try {
            startActivity(t);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, "Aquesta manera no funciona en aquest mòbil", Toast.LENGTH_LONG).show();
        }
    }

    private void demanaAcces() {
        try {
            startActivityForResult(Comptes.intentAcces(calCompte, calTipus,
                    "Tria " + calCompte + " perquè Alarma per veu pugui pujar-hi el que apuntes"), PETICIO_COMPTE);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No s'ha pogut obrir la finestra de comptes", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PETICIO_COMPTE) omple();
    }

    // ---------- Dades ----------

    private void llegeixCalendariTriat() {
        calId = getSharedPreferences("config", MODE_PRIVATE).getLong(MainActivity.CLAU_CALENDARI, -1);
        if (calId < 0 || checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            calId = -1;
            return;
        }
        String[] camps = {CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.ACCOUNT_TYPE};
        try (Cursor c = getContentResolver().query(CalendarContract.Calendars.CONTENT_URI, camps,
                CalendarContract.Calendars._ID + " = " + calId, null, null)) {
            if (c != null && c.moveToFirst()) {
                calNom = c.getString(0);
                calCompte = c.getString(1);
                calTipus = c.getString(2);
            } else {
                calId = -1;
            }
        } catch (RuntimeException e) {
            calId = -1;
        }
    }

    /** Quants esdeveniments d'aquest calendari encara no s'han pujat (marcats com a «bruts»). */
    private String pendents() {
        try (Cursor c = getContentResolver().query(CalendarContract.Events.CONTENT_URI,
                new String[]{CalendarContract.Events._ID},
                CalendarContract.Events.CALENDAR_ID + " = " + calId + " AND " + CalendarContract.Events.DIRTY + " = 1",
                null, null)) {
            return c == null ? "?" : String.valueOf(c.getCount());
        } catch (RuntimeException e) {
            return "?";
        }
    }

    private String sincronitzadors() {
        StringBuilder sb = new StringBuilder();
        for (SyncAdapterType t : ContentResolver.getSyncAdapterTypes()) {
            if (!CalendarContract.AUTHORITY.equals(t.authority)) continue;
            if (calTipus != null && !calTipus.equals(t.accountType)) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(t.accountType);
        }
        return sb.length() == 0 ? "cap" : sb.toString();
    }

    private String versio() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "?";
        }
    }

    private static String siNo(boolean b) {
        return b ? "✅ activada" : "❌ desactivada";
    }

    private static String llista(List<String> l) {
        return l.isEmpty() ? "cap" : String.join(", ", l);
    }

    // ---------- Vistes ----------

    private TextView seccio(String s) {
        TextView t = text(s, 18, R.color.accent);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(24), 0, dp(8));
        return t;
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(getColor(color));
        return t;
    }

    private Button boto(String s, boolean principal, android.view.View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        if (principal) {
            b.setBackgroundResource(R.drawable.boto_pastilla);
            b.setTextColor(getColor(R.color.blanc));
        } else {
            b.setBackgroundResource(R.drawable.boto_contorn);
            b.setTextColor(getColor(R.color.accent));
        }
        b.setPadding(dp(16), 0, dp(16), 0);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(48));
        p.setMargins(dp(4), dp(8), 0, 0);
        b.setLayoutParams(p);
        return b;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
