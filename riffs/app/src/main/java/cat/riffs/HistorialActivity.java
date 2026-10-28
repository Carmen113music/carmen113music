package cat.riffs;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Tot el que s'ha fet amb l'app, el més recent a dalt. */
public class HistorialActivity extends Activity {

    private LinearLayout llista;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout arrel = Ui.columna(this);
        arrel.setBackgroundColor(Ui.FONS);
        arrel.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), 0);

        Button esborra = Ui.botoText(this, "Esborra", Ui.SUAU);
        esborra.setOnClickListener(v -> Ui.confirma(this, "Vols esborrar tot l'historial?",
                "Només s'esborra aquesta llista. Les gravacions no es toquen.", "Esborra", () -> {
                    Historial.esborra(Ui.fitxerHistorial(this));
                    omple();
                }));
        arrel.addView(Ui.capcalera(this, "Historial", esborra));

        ScrollView scroll = new ScrollView(this);
        llista = Ui.columna(this);
        llista.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 24));
        scroll.addView(llista);
        arrel.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(arrel);
        omple();
    }

    private void omple() {
        llista.removeAllViews();
        List<Historial.Entrada> entrades = Historial.llegeix(Ui.fitxerHistorial(this));
        if (entrades.isEmpty()) {
            TextView buit = Ui.text(this, "Encara no hi ha res.", 16, Ui.SUAU);
            buit.setGravity(Gravity.CENTER);
            buit.setPadding(0, Ui.dp(this, 48), 0, 0);
            llista.addView(buit);
            return;
        }
        for (Historial.Entrada e : entrades) {
            LinearLayout t = Ui.columna(this);
            t.setPadding(Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 10));
            t.addView(Ui.text(this, Biblioteca.data(e.quan), 12, Ui.SUAU));
            t.addView(Ui.text(this, e.text, 16, Ui.TEXT));
            llista.addView(t);
            android.view.View linia = new android.view.View(this);
            linia.setBackgroundColor(Ui.LINIA);
            llista.addView(linia, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
        }
    }
}
