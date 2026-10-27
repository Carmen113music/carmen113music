package cat.alarmaveu;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Pantalla amb l'historial de tot el que s'ha demanat (el més recent a dalt). */
public class HistorialActivity extends Activity {

    private LinearLayout llista;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout arrel = new LinearLayout(this);
        arrel.setOrientation(LinearLayout.VERTICAL);
        arrel.setBackgroundColor(getColor(R.color.fons));
        arrel.setPadding(dp(16), dp(16), dp(16), dp(8));

        // Capçalera: Enrere · Historial · Esborra
        LinearLayout cap = new LinearLayout(this);
        cap.setOrientation(LinearLayout.HORIZONTAL);
        cap.setGravity(Gravity.CENTER_VERTICAL);

        Button enrere = botoText("← Enrere", R.color.accent);
        enrere.setOnClickListener(v -> finish());
        cap.addView(enrere);

        TextView titol = new TextView(this);
        titol.setText(R.string.historial);
        titol.setTextColor(getColor(R.color.text));
        titol.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        titol.setTypeface(Typeface.DEFAULT_BOLD);
        titol.setGravity(Gravity.CENTER);
        cap.addView(titol, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button esborra = botoText("Esborra", R.color.text_suau);
        esborra.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(R.string.esborrar_historial)
                .setPositiveButton(R.string.esborra, (d, w) -> {
                    Historial.esborra(this);
                    omple();
                })
                .setNegativeButton(R.string.cancela, null)
                .show());
        cap.addView(esborra);
        arrel.addView(cap);

        ScrollView scroll = new ScrollView(this);
        llista = new LinearLayout(this);
        llista.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(llista);
        arrel.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(arrel);
        omple();
    }

    private void omple() {
        llista.removeAllViews();
        List<Historial.Entrada> entrades = Historial.llegeix(this);
        if (entrades.isEmpty()) {
            TextView buit = text(getString(R.string.historial_buit), 17, R.color.text_suau);
            buit.setGravity(Gravity.CENTER);
            buit.setPadding(0, dp(48), 0, 0);
            llista.addView(buit);
            return;
        }
        for (Historial.Entrada e : entrades) {
            LinearLayout fila = new LinearLayout(this);
            fila.setOrientation(LinearLayout.VERTICAL);
            fila.setPadding(dp(4), dp(14), dp(4), dp(14));

            fila.addView(text(e.dataText(), 13, R.color.text_suau));
            TextView r = text(e.resultat, 17, R.color.text);
            r.setPadding(0, dp(2), 0, dp(2));
            fila.addView(r);
            if (!e.frase.isEmpty()) {
                TextView f = text("«" + e.frase + "»", 14, R.color.text_suau);
                f.setTypeface(Typeface.defaultFromStyle(Typeface.ITALIC));
                fila.addView(f);
            }
            llista.addView(fila);

            View linia = new View(this);
            linia.setBackgroundColor(getColor(R.color.linia));
            llista.addView(linia, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        }
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(getColor(color));
        return t;
    }

    private Button botoText(String s, int color) {
        Button b = new Button(this, null, android.R.attr.borderlessButtonStyle);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(getColor(color));
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        return b;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
