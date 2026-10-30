package cat.riffs;

import android.app.Activity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;

/**
 * Botó «Solo» del drumpad i del piano roll. Encès: només sona el que s'edita. Apagat: sona amb la resta de pistes
 * de l'Estudi que no estiguin mutejades (amb els seus faders). La tria es recorda i és la mateixa per als dos editors.
 * També llegeix la resta de pistes (en un altre fil, perquè no s'encalli la pantalla).
 */
final class BotoSolo {

    static final String PREF = "editor_solo";

    final LinearLayout vista;
    private final TextView text;
    private final Activity a;
    private final int color;
    private boolean solo;
    private volatile Acompanyament acomp;

    BotoSolo(Activity a, int color, Runnable canvi) {
        this.a = a;
        this.color = color;
        solo = Ui.prefs(a).getBoolean(PREF, false);
        vista = Ui.pastilla(a, 0, "Solo", Ui.SUPERFICIE2, Ui.TEXT);
        vista.setContentDescription("Solo: escoltar només el que edites");
        text = (TextView) vista.getChildAt(vista.getChildCount() - 1);
        vista.setOnClickListener(v -> {
            solo = !solo;
            Ui.prefs(a).edit().putBoolean(PREF, solo).apply();
            pinta();
            Acompanyament ac = acomp;
            if (!solo && ac != null && !ac.hiHaAltres()) {
                Ui.avis(a, "Aquí no sona cap altra pista (o estan mutejades)");
            } else {
                Ui.avis(a, solo ? "Solo: només sona el que edites" : "Sona amb la resta de pistes");
            }
            canvi.run();
        });
        pinta();
    }

    private void pinta() {
        vista.setBackground(Ui.tocable(solo ? color : Ui.SUPERFICIE2, Ui.dp(a, 24)));
        text.setTextColor(solo ? Ui.SOBRE_COLOR : Ui.TEXT);
    }

    /** Llegeix la resta de pistes; quan ho té, executa {@code llest} a la pantalla. */
    void carrega(File carpeta, String wav, long posMs, long finsMs, Runnable llest) {
        if (posMs < 0) return;   // no ve de l'Estudi
        new Thread(() -> {
            Acompanyament ac = null;
            try {
                ac = Acompanyament.obre(carpeta, wav, posMs, finsMs);
            } catch (RuntimeException | OutOfMemoryError ignorat) {
            }
            Acompanyament fet = ac;
            a.runOnUiThread(() -> {
                acomp = fet;
                if (fet != null && fet.hiHaAltres() && !solo) llest.run();
            });
        }).start();
    }

    /** El so que ha de sonar en bucle: el propi sol, o barrejat amb la resta. */
    short[] so(short[] propi) {
        Acompanyament ac = acomp;
        return ac == null ? propi : ac.barreja(propi, solo);
    }
}
