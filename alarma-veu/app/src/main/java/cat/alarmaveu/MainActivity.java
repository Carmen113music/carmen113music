package cat.alarmaveu;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.provider.AlarmClock;
import android.speech.RecognizerIntent;
import android.view.View;
import android.widget.TextView;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Una sola pantalla: un botó de micròfon. En obrir l'app ja comença a escoltar.
 * Diu la frase → s'interpreta l'hora i el títol → es crea l'alarma al Rellotge.
 */
public class MainActivity extends Activity {

    private static final int PETICIO_VEU = 1;

    private TextView estat;
    private TextView detall;
    private TextView escoltat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        estat = findViewById(R.id.estat);
        detall = findViewById(R.id.detall);
        escoltat = findViewById(R.id.escoltat);

        findViewById(R.id.boto_parla).setOnClickListener(v -> escolta());
        findViewById(R.id.boto_alarmes).setOnClickListener(v -> obreAlarmes());

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
            mostra(getString(R.string.sense_reconeixement), getString(R.string.sense_reconeixement_detall), null, false);
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

        LocalTime ara = LocalTime.now();
        ParserAlarma.Resultat primer = null;
        String frasePrimer = frases.get(0);
        for (String f : frases) {
            ParserAlarma.Resultat r = ParserAlarma.analitza(f, ara);
            if (primer == null) primer = r;
            if (r.ok) {
                posaAlarma(r, f);
                return;
            }
        }
        mostra(getString(R.string.no_ho_he_entes), primer.error, frasePrimer, true);
    }

    private void posaAlarma(ParserAlarma.Resultat r, String frase) {
        Intent a = new Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, r.hora)
                .putExtra(AlarmClock.EXTRA_MINUTES, r.minut)
                .putExtra(AlarmClock.EXTRA_MESSAGE, r.titol)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true);
        try {
            startActivity(a);
        } catch (ActivityNotFoundException e) {
            mostra(getString(R.string.sense_rellotge), getString(R.string.sense_rellotge_detall), frase, true);
            return;
        }
        String quan = falten(r.minutsFins);
        mostra("✓ " + r.horaText(), r.titol + "\n" + quan, frase, false);
    }

    private static String falten(int minuts) {
        int h = minuts / 60, m = minuts % 60;
        if (h == 0) return String.format(Locale.ROOT, "Sonarà d'aquí a %d min", m);
        if (m == 0) return String.format(Locale.ROOT, "Sonarà d'aquí a %d h", h);
        return String.format(Locale.ROOT, "Sonarà d'aquí a %d h %d min", h, m);
    }

    private void obreAlarmes() {
        try {
            startActivity(new Intent(AlarmClock.ACTION_SHOW_ALARMS));
        } catch (ActivityNotFoundException e) {
            mostra(getString(R.string.sense_rellotge), getString(R.string.sense_rellotge_detall), null, true);
        }
    }

    private void mostra(String titol, String text, String frase, boolean error) {
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
