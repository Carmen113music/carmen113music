package cat.riffs;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.List;

/** Les carpetes i gravacions d'una carpeta. */
public class BibliotecaActivity extends Activity {

    public static final String EXTRA_DIR = "dir";

    private Biblioteca biblio;
    private File dir;
    private LinearLayout llista;
    private TextView titol, ruta;

    private MediaPlayer reproductor;
    private File sonant;
    private Button botoSonant;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        biblio = Ui.biblioteca(this);
        String d = getIntent().getStringExtra(EXTRA_DIR);
        dir = d == null ? biblio.arrel : new File(d);
        if (!dir.isDirectory() || !biblio.esDins(dir)) dir = biblio.arrel;

        LinearLayout arrel = Ui.columna(this);
        arrel.setBackgroundColor(Ui.FONS);
        arrel.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), 0);

        Button nova = Ui.botoText(this, "＋ Carpeta", Ui.ACCENT);
        nova.setOnClickListener(v -> novaCarpeta());
        LinearLayout cap = Ui.capcalera(this, "", nova);
        titol = (TextView) cap.getChildAt(1);
        arrel.addView(cap);

        ruta = Ui.text(this, "", 13, Ui.SUAU);
        ruta.setGravity(Gravity.CENTER);
        arrel.addView(ruta);

        Button grava = Ui.boto(this, "🎙  Grava aquí", Ui.ACCENT, 0xFFFFFFFF);
        grava.setOnClickListener(v -> {
            Ui.posaCarpetaActual(this, dir);
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
        });
        arrel.addView(grava, Ui.ample(this, 10));

        ScrollView scroll = new ScrollView(this);
        llista = Ui.columna(this);
        llista.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 24));
        scroll.addView(llista);
        arrel.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(arrel);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!dir.isDirectory()) dir = biblio.arrel;
        omple();
    }

    @Override
    protected void onPause() {
        super.onPause();
        aturaSo();
    }

    private void omple() {
        aturaSo();
        boolean esArrel = dir.equals(biblio.arrel);
        titol.setText(esArrel ? "Biblioteca" : dir.getName());
        String r = biblio.ruta(dir);
        ruta.setText(esArrel ? "" : "Biblioteca / " + r);
        ruta.setVisibility(esArrel ? android.view.View.GONE : android.view.View.VISIBLE);

        llista.removeAllViews();
        List<File> carpetes = biblio.carpetes(dir);
        List<Biblioteca.Gravacio> gravacions = biblio.gravacions(dir);

        if (carpetes.isEmpty() && gravacions.isEmpty()) {
            TextView buit = Ui.text(this, esArrel
                    ? "Encara no hi ha cap gravació.\n\nCrea una carpeta per a cada cançó amb «＋ Carpeta», o toca «🎙 Grava aquí»."
                    : "Aquesta carpeta és buida.\n\nToca «🎙 Grava aquí» per afegir-hi idees, o mou-hi gravacions des d'una altra carpeta.",
                    16, Ui.SUAU);
            buit.setGravity(Gravity.CENTER);
            buit.setPadding(Ui.dp(this, 16), Ui.dp(this, 40), Ui.dp(this, 16), 0);
            llista.addView(buit);
            return;
        }

        for (File c : carpetes) llista.addView(filaCarpeta(c), Ui.ample(this, 8));
        if (!carpetes.isEmpty() && !gravacions.isEmpty()) {
            TextView sep = Ui.text(this, "Gravacions", 13, Ui.SUAU);
            sep.setPadding(Ui.dp(this, 4), Ui.dp(this, 14), 0, 0);
            llista.addView(sep);
        }
        for (Biblioteca.Gravacio g : gravacions) llista.addView(filaGravacio(g), Ui.ample(this, 8));
    }

    private LinearLayout filaCarpeta(File c) {
        LinearLayout t = Ui.targeta(this);
        LinearLayout f = Ui.fila(this);
        TextView nom = Ui.text(this, "📁  " + c.getName(), 17, Ui.TEXT);
        nom.setTypeface(Typeface.DEFAULT_BOLD);
        f.addView(nom, Ui.pes(1));
        int n = biblio.compta(c);
        f.addView(Ui.text(this, n == 1 ? "1 gravació  ›" : n + " gravacions  ›", 14, Ui.SUAU));
        t.addView(f);
        t.setOnClickListener(v -> {
            Intent i = new Intent(this, BibliotecaActivity.class);
            i.putExtra(EXTRA_DIR, c.getAbsolutePath());
            startActivity(i);
        });
        t.setOnLongClickListener(v -> {
            menuCarpeta(c);
            return true;
        });
        return t;
    }

    private LinearLayout filaGravacio(Biblioteca.Gravacio g) {
        LinearLayout t = Ui.targeta(this);
        LinearLayout f = Ui.fila(this);
        LinearLayout textos = Ui.columna(this);
        TextView nom = Ui.text(this, g.fitxa.emoji() + "  " + g.fitxa.titol, 17, Ui.TEXT);
        nom.setSingleLine(true);
        nom.setEllipsize(android.text.TextUtils.TruncateAt.END);
        textos.addView(nom);
        String info = Biblioteca.data(g.fitxa.data) + "  ·  " + Biblioteca.durada(g.fitxa.duradaMs);
        if (!g.fitxa.lletra.trim().isEmpty()) info += "  ·  ✎ lletra";
        textos.addView(Ui.text(this, info, 13, Ui.SUAU));
        f.addView(textos, Ui.pes(1));

        Button play = Ui.boto(this, "▶", Ui.LINIA, Ui.TEXT);
        play.setTextSize(18);
        play.setOnClickListener(v -> alternaSo(g.wav, play));
        f.addView(play, new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 44)));
        t.addView(f);

        t.setOnClickListener(v -> {
            Intent i = new Intent(this, FitxaActivity.class);
            i.putExtra(FitxaActivity.EXTRA_WAV, g.wav.getAbsolutePath());
            startActivity(i);
        });
        t.setOnLongClickListener(v -> {
            menuGravacio(g);
            return true;
        });
        return t;
    }

    // ---------- Escoltar ràpid ----------

    private void alternaSo(File wav, Button b) {
        if (wav.equals(sonant)) {
            aturaSo();
            return;
        }
        aturaSo();
        try {
            reproductor = new MediaPlayer();
            reproductor.setDataSource(wav.getAbsolutePath());
            reproductor.setOnCompletionListener(mp -> aturaSo());
            reproductor.prepare();
            reproductor.start();
            sonant = wav;
            botoSonant = b;
            b.setText("■");
        } catch (Exception e) {
            aturaSo();
            Ui.avis(this, "No puc reproduir aquesta gravació.");
        }
    }

    private void aturaSo() {
        if (reproductor != null) {
            try {
                reproductor.stop();
            } catch (IllegalStateException ignored) {
            }
            reproductor.release();
            reproductor = null;
        }
        if (botoSonant != null) botoSonant.setText("▶");
        botoSonant = null;
        sonant = null;
    }

    // ---------- Menús ----------

    private void novaCarpeta() {
        Ui.demanaText(this, "Carpeta nova", "", "Crea", nom -> {
            try {
                File c = biblio.creaCarpeta(dir, nom);
                Ui.historial(this, "📁 Carpeta nova: " + Ui.nomCarpeta(this, c));
                omple();
            } catch (IllegalArgumentException e) {
                Ui.avis(this, e.getMessage());
            }
        });
    }

    private void menuCarpeta(File c) {
        new AlertDialog.Builder(this)
                .setTitle("📁 " + c.getName())
                .setItems(new String[]{"Canvia el nom", "Esborra la carpeta"}, (d, i) -> {
                    if (i == 0) {
                        Ui.demanaText(this, "Nom de la carpeta", c.getName(), "Desa", nom -> {
                            try {
                                String vell = c.getName();
                                File nova = biblio.reanomenaCarpeta(c, nom);
                                Ui.reubica(this, c, nova);
                                Ui.historial(this, "✎ Carpeta «" + vell + "» ara es diu «" + nova.getName() + "»");
                                omple();
                            } catch (IllegalArgumentException e) {
                                Ui.avis(this, e.getMessage());
                            }
                        });
                    } else {
                        int n = biblio.compta(c);
                        String msg = n == 0 ? "La carpeta és buida."
                                : "S'esborraran també les " + n + " gravacions que té a dins (i les seves lletres). No es pot desfer.";
                        if (n == 1) msg = "S'esborrarà també la gravació que té a dins (i la seva lletra). No es pot desfer.";
                        Ui.confirma(this, "Vols esborrar «" + c.getName() + "»?", msg, "Esborra", () -> {
                            biblio.esborraCarpeta(c);
                            Ui.historial(this, "🗑 Esborrada la carpeta «" + c.getName() + "»" + (n > 0 ? " (" + n + " gravacions)" : ""));
                            omple();
                        });
                    }
                })
                .show();
    }

    private void menuGravacio(Biblioteca.Gravacio g) {
        new AlertDialog.Builder(this)
                .setTitle(g.fitxa.emoji() + " " + g.fitxa.titol)
                .setItems(new String[]{"Mou a una carpeta", "Comparteix", "Esborra"}, (d, i) -> {
                    aturaSo();
                    if (i == 0) Accions.mou(this, g.wav, nou -> omple());
                    else if (i == 1) Accions.comparteix(this, g.wav);
                    else Accions.esborra(this, g.wav, this::omple);
                })
                .show();
    }
}
