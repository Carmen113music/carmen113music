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
 * Pestanya «Estudi»: la llista de projectes. Cada carpeta amb gravacions es pot obrir
 * com a projecte multipista (en horitzontal).
 */
public class EstudiActivity extends Activity {

    private LinearLayout llista;

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
        TextView sub = Ui.text(this, "Obre una carpeta com a projecte multipista", 13, Ui.SUAU);
        sub.setPadding(Ui.dp(this, 20), Ui.dp(this, 2), Ui.dp(this, 20), 0);
        arrel.addView(sub);

        ScrollView scroll = new ScrollView(this);
        llista = Ui.columna(this);
        llista.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 24));
        scroll.addView(llista);
        arrel.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        arrel.addView(Nav.barra(this, Nav.ESTUDI));
        setContentView(arrel);
    }

    @Override
    protected void onResume() {
        super.onResume();
        omple();
    }

    private void omple() {
        llista.removeAllViews();
        Biblioteca b = Ui.biblioteca(this);
        llista.addView(Ui.etiqueta(this, "Projectes"), Ui.ample(this, 12));
        int n = 0;
        for (Biblioteca.Carpeta c : b.arbre()) {
            if (c.nivell == 0) continue;
            int grav = b.gravacions(c.dir).size();
            if (grav < 1) continue;
            n++;
            llista.addView(filaProjecte(c, grav), Ui.ample(this, 8));
        }
        if (n == 0) {
            LinearLayout buit = Ui.columna(this);
            buit.setGravity(Gravity.CENTER_HORIZONTAL);
            buit.addView(Ui.rajola(this, Icona.ESTUDI, Ui.SUAU, Ui.SUPERFICIE, 72));
            TextView t = Ui.negreta(this, "Encara no hi ha cap projecte", 17, Ui.TEXT);
            t.setGravity(Gravity.CENTER);
            buit.addView(t, Ui.ample(this, 16));
            TextView s = Ui.text(this, "Crea una carpeta per a una cançó a la Biblioteca i grava-hi les idees. Aquí podràs muntar-les una a sobre de l'altra.", 14, Ui.SUAU);
            s.setGravity(Gravity.CENTER);
            s.setLineSpacing(0, 1.2f);
            buit.addView(s, Ui.ample(this, 8));
            llista.addView(buit, Ui.ample(this, 32));
        }

        // Com funciona
        LinearLayout ajuda = Ui.columna(this);
        ajuda.setBackground(Ui.rodoVora(Ui.SUPERFICIE, Ui.LINIA, Ui.dp(this, 18), 1));
        ajuda.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 16));
        ajuda.addView(Ui.etiqueta(this, "Com funciona"));
        ajuda.addView(punt(Icona.ESTUDI, "Cada gravació de la carpeta és una pista. Les que tenen bandera s'encaixen soles al compàs."), Ui.ample(this, 12));
        ajuda.addView(punt(Icona.MOURE, "Arrossega un clip per moure'l (amb snap a compàs, temps o lliure). Arrossega l'espai buit per desplaçar-te i fes zoom amb dos dits."), Ui.ample(this, 10));
        ajuda.addView(punt(Icona.LLAPIS, "Toca un clip per triar-lo: cantonades de baix per retallar, de dalt per als fades, i el punt del mig per al volum del clip."), Ui.ample(this, 10));
        ajuda.addView(punt(Icona.BUCLE, "Properament: marcadors i bucle, exportar la mescla i canviar el to en semitons."), Ui.ample(this, 10));
        llista.addView(ajuda, Ui.ample(this, 24));
    }

    private LinearLayout filaProjecte(Biblioteca.Carpeta c, int grav) {
        LinearLayout r = Ui.targeta(this);
        r.addView(Ui.rajola(this, Icona.ESTUDI, Ui.ACCENT, Ui.alfa(Ui.ACCENT, 36), 44));
        LinearLayout tx = Ui.columna(this);
        tx.addView(Ui.unaLinia(Ui.negreta(this, c.ruta, 16, Ui.TEXT)));
        String info = grav == 1 ? "1 pista" : grav + " pistes";
        File fp = new File(c.dir, Projecte.FITXER);
        if (fp.exists()) {
            Projecte p = Projecte.obre(c.dir);
            info += "  ·  " + Math.round(p.bpm) + " BPM · " + Ritme.nomCompas(p.compas);
        } else {
            info += "  ·  sense obrir";
        }
        tx.addView(Ui.text(this, info, 13, Ui.SUAU), Ui.ample(this, 4));
        LinearLayout.LayoutParams lp = Ui.pes(1);
        lp.leftMargin = Ui.dp(this, 12);
        r.addView(tx, lp);
        r.addView(Ui.imatge(this, Icona.XEVRO, Ui.APAGAT, 20), new LinearLayout.LayoutParams(Ui.dp(this, 20), Ui.dp(this, 24)));
        File dir = c.dir;
        r.setOnClickListener(v -> obre(this, dir));
        return r;
    }

    public static void obre(Activity a, File dir) {
        Intent i = new Intent(a, ProjecteActivity.class);
        i.putExtra(ProjecteActivity.EXTRA_DIR, dir.getAbsolutePath());
        a.startActivity(i);
    }

    private LinearLayout punt(int icona, String s) {
        LinearLayout f = Ui.fila(this);
        f.setGravity(Gravity.TOP);
        FrameLayout r = Ui.rajola(this, icona, Ui.TEXT, Ui.SUPERFICIE2, 30);
        f.addView(r);
        TextView t = Ui.text(this, s, 13, Ui.TEXT);
        t.setLineSpacing(0, 1.2f);
        LinearLayout.LayoutParams lp = Ui.pes(1);
        lp.leftMargin = Ui.dp(this, 12);
        f.addView(t, lp);
        return f;
    }
}
