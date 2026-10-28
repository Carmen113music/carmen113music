package cat.riffs;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;

/**
 * Pestanya «Estudi»: aquí hi anirà el mini DAW multipista (fase 3).
 * De moment explica què farà i mostra quines carpetes es podran obrir com a projecte.
 */
public class EstudiActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setNavigationBarColor(Ui.SUPERFICIE);
        LinearLayout arrel = Ui.pantalla(this);

        LinearLayout dalt = Ui.fila(this);
        dalt.setPadding(Ui.dp(this, 20), Ui.dp(this, 12), Ui.dp(this, 20), 0);
        dalt.addView(Ui.negreta(this, "Estudi", 26, Ui.TEXT));
        dalt.setMinimumHeight(Ui.dp(this, 54));
        arrel.addView(dalt);

        ScrollView scroll = new ScrollView(this);
        LinearLayout cos = Ui.columna(this);
        cos.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 24));

        // Targeta principal
        LinearLayout heroi = Ui.columna(this);
        heroi.setBackground(Ui.rodoVora(Ui.SUPERFICIE, Ui.LINIA, Ui.dp(this, 20), 1));
        heroi.setPadding(Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20));
        LinearLayout fila = Ui.fila(this);
        fila.addView(Ui.rajola(this, Icona.ESTUDI, Ui.ACCENT, Ui.alfa(Ui.ACCENT, 36), 52));
        LinearLayout t = Ui.columna(this);
        t.addView(Ui.negreta(this, "Estudi multipista", 19, Ui.TEXT));
        TextView xip = Ui.negreta(this, "ARRIBA A LA VERSIÓ 3", 10, Ui.GUITARRA);
        xip.setLetterSpacing(0.1f);
        xip.setBackground(Ui.rodo(Ui.alfa(Ui.GUITARRA, 30), Ui.dp(this, 8)));
        xip.setPadding(Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4));
        LinearLayout.LayoutParams lx = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lx.topMargin = Ui.dp(this, 6);
        t.addView(xip, lx);
        LinearLayout.LayoutParams lt = Ui.pes(1);
        lt.leftMargin = Ui.dp(this, 14);
        fila.addView(t, lt);
        heroi.addView(fila);
        TextView desc = Ui.text(this, "Obre una carpeta com a projecte i munta-hi les idees: una guitarra, una altra a sobre i la veu, sonant alhora.", 14, Ui.SUAU);
        desc.setLineSpacing(0, 1.25f);
        heroi.addView(desc, Ui.ample(this, 16));

        heroi.addView(punt(Icona.ESTUDI, "Diverses pistes alhora, cada una amb el seu volum"), Ui.ample(this, 16));
        heroi.addView(punt(Icona.MOURE, "Desplaça cada pista per quadrar-la amb les altres"), Ui.ample(this, 12));
        heroi.addView(punt(Icona.BUCLE, "Reproducció lineal o en bucle entre dos marcadors"), Ui.ample(this, 12));
        heroi.addView(punt(Icona.COMPARTIR, "Exporta la mescla en M4A o WAV i envia-la a la banda"), Ui.ample(this, 12));
        cos.addView(heroi, Ui.ample(this, 4));

        // Projectes possibles
        Biblioteca b = Ui.biblioteca(this);
        cos.addView(Ui.etiqueta(this, "Futurs projectes"), Ui.ample(this, 24));
        TextView expl = Ui.text(this, "Cada carpeta amb dues o més gravacions es podrà obrir aquí com a projecte.", 13, Ui.SUAU);
        cos.addView(expl, Ui.ample(this, 6));
        int n = 0;
        for (Biblioteca.Carpeta c : b.arbre()) {
            if (c.nivell == 0) continue;
            int grav = b.gravacions(c.dir).size();
            if (grav < 2) continue;
            n++;
            LinearLayout r = Ui.targeta(this);
            r.addView(Ui.rajola(this, Icona.CARPETA, Ui.TEXT, Ui.SUPERFICIE2, 40));
            LinearLayout tx = Ui.columna(this);
            tx.addView(Ui.unaLinia(Ui.negreta(this, c.ruta, 15, Ui.TEXT)));
            tx.addView(Ui.text(this, grav + " pistes possibles", 13, Ui.SUAU), Ui.ample(this, 3));
            LinearLayout.LayoutParams lp = Ui.pes(1);
            lp.leftMargin = Ui.dp(this, 12);
            r.addView(tx, lp);
            r.addView(Ui.imatge(this, Icona.XEVRO, Ui.APAGAT, 20), new LinearLayout.LayoutParams(Ui.dp(this, 20), Ui.dp(this, 24)));
            File dir = c.dir;
            r.setOnClickListener(v -> {
                Intent i = new Intent(this, BibliotecaActivity.class);
                i.putExtra(BibliotecaActivity.EXTRA_DIR, dir.getAbsolutePath());
                startActivity(i);
            });
            cos.addView(r, Ui.ample(this, 8));
        }
        if (n == 0) {
            TextView cap = Ui.text(this, "Encara no n'hi ha cap.", 14, Ui.APAGAT);
            cap.setGravity(Gravity.CENTER);
            cos.addView(cap, Ui.ample(this, 20));
        }

        scroll.addView(cos);
        arrel.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        arrel.addView(Nav.barra(this, Nav.ESTUDI));
        setContentView(arrel);
    }

    private LinearLayout punt(int icona, String s) {
        LinearLayout f = Ui.fila(this);
        FrameLayout r = Ui.rajola(this, icona, Ui.TEXT, Ui.SUPERFICIE2, 32);
        f.addView(r);
        TextView t = Ui.text(this, s, 14, Ui.TEXT);
        t.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams lp = Ui.pes(1);
        lp.leftMargin = Ui.dp(this, 12);
        f.addView(t, lp);
        return f;
    }
}
