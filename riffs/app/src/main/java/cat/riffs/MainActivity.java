package cat.riffs;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;

/** Pantalla principal: el botó gran per gravar. */
public class MainActivity extends Activity {

    private static final int PERMIS_MIC = 1;

    private final Gravadora gravadora = new Gravadora();
    private final Handler h = new Handler(Looper.getMainLooper());

    private BotoGravar boto;
    private TextView rellotge, estat, carpeta;
    private Button botoGuitarra, botoVeu, botoCarpeta;
    private LinearLayout targetaUltima;
    private TextView textUltima;

    private String mode = Fitxa.GUITARRA;
    private File wavActual;
    private Fitxa fitxaActual;
    private File ultima;

    private final Runnable rellotgeTic = new Runnable() {
        @Override
        public void run() {
            if (!gravadora.grava()) return;
            rellotge.setText(Biblioteca.durada(gravadora.duradaMs()));
            h.postDelayed(this, 200);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mode = Ui.prefs(this).getString("mode", Fitxa.GUITARRA);
        construeix();

        // Si l'app es va tancar de cop mentre gravava, arregla aquell fitxer
        new Thread(() -> {
            int n = Ui.biblioteca(this).reparaTot(Ui.biblioteca(this).arrel);
            if (n > 0) Ui.historial(this, "🔧 Recuperades " + n + " gravacions que s'havien quedat a mitges");
        }).start();
    }

    private void construeix() {
        LinearLayout arrel = Ui.columna(this);
        arrel.setBackgroundColor(Ui.FONS);
        arrel.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 16));

        // Barra de dalt: Biblioteca · Historial · Diagnòstic
        LinearLayout dalt = Ui.fila(this);
        Button biblio = Ui.boto(this, "📁 Biblioteca", Ui.TARGETA, Ui.TEXT);
        biblio.setOnClickListener(v -> obreBiblioteca());
        dalt.addView(biblio);
        dalt.addView(new View(this), Ui.pes(1));
        Button hist = Ui.botoText(this, "🕘", Ui.TEXT);
        hist.setTextSize(22);
        hist.setContentDescription("Historial");
        hist.setOnClickListener(v -> { if (!gravadora.grava()) startActivity(new Intent(this, HistorialActivity.class)); });
        dalt.addView(hist);
        Button diag = Ui.botoText(this, "🔧", Ui.TEXT);
        diag.setTextSize(22);
        diag.setContentDescription("Diagnòstic");
        diag.setOnClickListener(v -> { if (!gravadora.grava()) startActivity(new Intent(this, DiagnosticActivity.class)); });
        dalt.addView(diag);
        arrel.addView(dalt);

        TextView titol = Ui.text(this, "Llibreta de riffs", 28, Ui.TEXT);
        titol.setTypeface(Typeface.DEFAULT_BOLD);
        arrel.addView(titol, Ui.ample(this, 12));

        // On es desa
        botoCarpeta = Ui.boto(this, "", Ui.TARGETA, Ui.TEXT);
        botoCarpeta.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        botoCarpeta.setOnClickListener(v -> {
            if (gravadora.grava()) return;
            Ui.triaCarpeta(this, "On es desen les gravacions?", Ui.carpetaActual(this), c -> {
                Ui.posaCarpetaActual(this, c);
                mostraCarpeta();
            });
        });
        carpeta = Ui.text(this, "Es desa a:", 13, Ui.SUAU);
        arrel.addView(carpeta, Ui.ample(this, 16));
        arrel.addView(botoCarpeta, Ui.ample(this, 4));

        // Guitarra o veu
        TextView quin = Ui.text(this, "Què graves?", 13, Ui.SUAU);
        arrel.addView(quin, Ui.ample(this, 16));
        LinearLayout modes = Ui.fila(this);
        botoGuitarra = Ui.boto(this, "🎸  Guitarra", Ui.TARGETA, Ui.TEXT);
        botoVeu = Ui.boto(this, "🎤  Veu", Ui.TARGETA, Ui.TEXT);
        botoGuitarra.setOnClickListener(v -> triaMode(Fitxa.GUITARRA));
        botoVeu.setOnClickListener(v -> triaMode(Fitxa.VEU));
        modes.addView(botoGuitarra, Ui.pes(1));
        modes.addView(Ui.espai(this, 10));
        modes.addView(botoVeu, Ui.pes(1));
        arrel.addView(modes, Ui.ample(this, 4));

        // Botó gran
        arrel.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));
        boto = new BotoGravar(this);
        boto.setOnClickListener(v -> premBoto());
        LinearLayout.LayoutParams pb = new LinearLayout.LayoutParams(Ui.dp(this, 210), Ui.dp(this, 210));
        pb.gravity = Gravity.CENTER_HORIZONTAL;
        arrel.addView(boto, pb);

        rellotge = Ui.text(this, "0:00", 44, Ui.TEXT);
        rellotge.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        rellotge.setGravity(Gravity.CENTER);
        arrel.addView(rellotge, Ui.ample(this, 4));

        estat = Ui.text(this, "Toca el botó per gravar", 16, Ui.SUAU);
        estat.setGravity(Gravity.CENTER);
        arrel.addView(estat, Ui.ample(this, 2));
        arrel.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));

        // Última gravació
        targetaUltima = Ui.targeta(this);
        TextView etiqueta = Ui.text(this, "Última gravació", 12, Ui.SUAU);
        targetaUltima.addView(etiqueta);
        LinearLayout fu = Ui.fila(this);
        textUltima = Ui.text(this, "", 16, Ui.TEXT);
        textUltima.setSingleLine(true);
        textUltima.setEllipsize(android.text.TextUtils.TruncateAt.END);
        fu.addView(textUltima, Ui.pes(1));
        fu.addView(Ui.text(this, "  Obre ›", 15, Ui.ACCENT));
        targetaUltima.addView(fu);
        targetaUltima.setOnClickListener(v -> {
            if (ultima != null && ultima.exists() && !gravadora.grava()) obreFitxa(ultima);
        });
        arrel.addView(targetaUltima, Ui.ample(this, 12));

        setContentView(arrel);
        mostraMode();
        mostraCarpeta();
        mostraUltima();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mostraCarpeta();
        String u = Ui.prefs(this).getString("ultima", null);
        ultima = u == null ? null : new File(u);
        mostraUltima();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Si l'app passa a segon pla mentre grava, s'atura i es desa el que hi ha
        if (gravadora.grava()) atura("S'ha aturat perquè l'app ha passat a segon pla.");
    }

    // ---------- Mode i carpeta ----------

    private void triaMode(String m) {
        if (gravadora.grava()) return;
        mode = m;
        Ui.prefs(this).edit().putString("mode", m).apply();
        mostraMode();
    }

    private void mostraMode() {
        boolean veu = Fitxa.VEU.equals(mode);
        botoGuitarra.setBackground(Ui.rodo(veu ? Ui.TARGETA : Ui.GUITARRA, Ui.dp(this, 22)));
        botoGuitarra.setTextColor(veu ? Ui.TEXT : 0xFF1A1714);
        botoVeu.setBackground(Ui.rodo(veu ? Ui.VEU : Ui.TARGETA, Ui.dp(this, 22)));
        botoVeu.setTextColor(veu ? 0xFF1A1714 : Ui.TEXT);
    }

    private void mostraCarpeta() {
        botoCarpeta.setText(Ui.nomCarpeta(this, Ui.carpetaActual(this)) + "   ▾");
    }

    private void mostraUltima() {
        if (ultima == null || !ultima.exists()) {
            targetaUltima.setVisibility(View.GONE);
            return;
        }
        Biblioteca.Gravacio g = Ui.biblioteca(this).gravacio(ultima);
        textUltima.setText(g.fitxa.emoji() + "  " + g.fitxa.titol + "  ·  " + Biblioteca.durada(g.fitxa.duradaMs));
        targetaUltima.setVisibility(View.VISIBLE);
    }

    // ---------- Gravar ----------

    private void premBoto() {
        if (gravadora.grava()) {
            atura(null);
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, PERMIS_MIC);
            return;
        }
        comenca();
    }

    @Override
    public void onRequestPermissionsResult(int codi, String[] permisos, int[] resultats) {
        super.onRequestPermissionsResult(codi, permisos, resultats);
        if (codi != PERMIS_MIC) return;
        if (resultats.length > 0 && resultats[0] == PackageManager.PERMISSION_GRANTED) {
            comenca();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("Cal permís de micròfon")
                    .setMessage("Sense el micròfon l'app no pot gravar. Dona-li permís a Configuració → Aplicacions → Riffs → Permisos → Micròfon.")
                    .setPositiveButton("Obre la configuració", (d, w) -> {
                        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", getPackageName(), null));
                        startActivity(i);
                    })
                    .setNegativeButton("Ara no", null)
                    .show();
        }
    }

    private void comenca() {
        Biblioteca b = Ui.biblioteca(this);
        File dir = Ui.carpetaActual(this);
        long ara = System.currentTimeMillis();
        wavActual = b.nouWav(dir, ara);
        fitxaActual = new Fitxa();
        fitxaActual.mode = mode;
        fitxaActual.data = ara;
        fitxaActual.titol = Biblioteca.titolPerDefecte(ara);
        try {
            fitxaActual.desa(Biblioteca.fitxaDe(wavActual));
        } catch (IOException e) {
            estat.setText("No puc desar al mòbil. Potser està ple?");
            return;
        }

        String font = Ui.prefs(this).getString("font", Gravadora.AUTO);
        String error = gravadora.comenca(this, wavActual, font, new Gravadora.Oient() {
            @Override
            public void nivell(float pic) {
                runOnUiThread(() -> boto.setNivell(pic));
            }

            @Override
            public void error(String missatge) {
                runOnUiThread(() -> atura(missatge));
            }
        });
        if (error != null) {
            b.esborra(wavActual);
            wavActual = null;
            estat.setText(error);
            estat.setTextColor(Ui.ACCENT);
            return;
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        boto.setGravant(true);
        estat.setText("Gravant… toca per aturar");
        estat.setTextColor(Ui.ACCENT);
        rellotge.setText("0:00");
        h.post(rellotgeTic);
        botoCarpeta.setEnabled(false);
        botoGuitarra.setEnabled(false);
        botoVeu.setEnabled(false);
    }

    private void atura(String motiu) {
        if (wavActual == null) return;
        long durada = gravadora.atura();
        h.removeCallbacks(rellotgeTic);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        boto.setGravant(false);
        botoCarpeta.setEnabled(true);
        botoGuitarra.setEnabled(true);
        botoVeu.setEnabled(true);

        Biblioteca b = Ui.biblioteca(this);
        File wav = wavActual;
        wavActual = null;
        if (durada < 700) {
            b.esborra(wav);
            rellotge.setText("0:00");
            estat.setText(motiu != null ? motiu : "Massa curta: no l'he desada.");
            estat.setTextColor(Ui.SUAU);
            return;
        }
        fitxaActual.duradaMs = durada;
        try {
            fitxaActual.desa(Biblioteca.fitxaDe(wav));
        } catch (IOException ignored) {
            // el so ja és desat; la fitxa es pot refer
        }
        ultima = wav;
        Ui.prefs(this).edit().putString("ultima", wav.getAbsolutePath()).apply();
        Ui.historial(this, fitxaActual.emoji() + " Gravat «" + fitxaActual.titol + "» (" + Biblioteca.durada(durada)
                + ") a " + Ui.nomCarpeta(this, wav.getParentFile()));
        rellotge.setText(Biblioteca.durada(durada));
        estat.setText(motiu != null ? motiu : "Desada ✓  Toca per gravar una altra");
        estat.setTextColor(motiu != null ? Ui.ACCENT : Ui.SUAU);
        mostraUltima();
    }

    // ---------- Navegació ----------

    private void obreBiblioteca() {
        if (gravadora.grava()) return;
        startActivity(new Intent(this, BibliotecaActivity.class));
    }

    private void obreFitxa(File wav) {
        Intent i = new Intent(this, FitxaActivity.class);
        i.putExtra(FitxaActivity.EXTRA_WAV, wav.getAbsolutePath());
        startActivity(i);
    }
}
