package cat.riffs;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
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
        LinearLayout arrel = Ui.pantalla(this);

        FrameLayout esborra = Ui.botoIcona(this, Icona.PAPERERA, Ui.SUAU, 0x00000000, 44, 22, "Esborra l'historial");
        esborra.setOnClickListener(v -> Ui.confirma(this, "Vols esborrar tot l'historial?",
                "Només s'esborra aquesta llista. Les gravacions no es toquen.", "Esborra", () -> {
                    Historial.esborra(Ui.fitxerHistorial(this));
                    omple();
                }));
        arrel.addView(Ui.barraEnrere(this, "Historial", esborra));

        ScrollView scroll = new ScrollView(this);
        llista = Ui.columna(this);
        llista.setPadding(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 24));
        scroll.addView(llista);
        arrel.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(arrel);
        omple();
    }

    private void omple() {
        llista.removeAllViews();
        List<Historial.Entrada> entrades = Historial.llegeix(Ui.fitxerHistorial(this));
        if (entrades.isEmpty()) {
            TextView buit = Ui.text(this, "Encara no hi ha res.", 15, Ui.SUAU);
            buit.setGravity(Gravity.CENTER);
            llista.addView(buit, Ui.ample(this, 48));
            return;
        }
        LinearLayout grup = Ui.columna(this);
        grup.setBackground(Ui.rodo(Ui.SUPERFICIE, Ui.dp(this, 16)));
        for (int k = 0; k < entrades.size(); k++) {
            Historial.Entrada e = entrades.get(k);
            LinearLayout t = Ui.columna(this);
            t.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
            TextView text = Ui.text(this, e.text, 15, Ui.TEXT);
            text.setLineSpacing(0, 1.15f);
            t.addView(text);
            t.addView(Ui.text(this, Biblioteca.data(e.quan), 12, Ui.SUAU), Ui.ample(this, 4));
            grup.addView(t);
            if (k < entrades.size() - 1) grup.addView(Ui.separador(this));
        }
        llista.addView(grup, Ui.ample(this, 4));
    }
}
