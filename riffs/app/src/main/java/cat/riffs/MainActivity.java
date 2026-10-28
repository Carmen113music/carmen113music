package cat.riffs;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

/** Pantalla principal: gravar. */
public class MainActivity extends Activity {

    private static final int PERMIS_MIC = 1;

    private final Gravadora gravadora = new Gravadora();
    private final Handler h = new Handler(Looper.getMainLooper());

    private BotoGravar boto;
    private OnaView ona;
    private TextView rellotge, estat, textCarpeta, textMetronom;
    private LinearLayout rajolaMet;
    private PuntsTemps punts;
    private Metronom metronom;
    private View pampalluga;
    private long darrerTemps = Long.MIN_VALUE;
    private LinearLayout segGuitarra, segVeu, filaCarpeta;
    private LinearLayout targetaUltima;
    private TextView titolUltima, infoUltima;
    private FrameLayout rajolaUltima, playUltima;

    private String mode = Fitxa.GUITARRA;
    private File wavActual;
    private Fitxa fitxaActual;
    private File ultima;
    private MediaPlayer mp;

    private final Runnable rellotgeTic = new Runnable() {
        @Override
        public void run() {
            if (!gravadora.grava()) return;
            rellotge.setText(tempsAmbDecimes(gravadora.duradaMs()));
            if (metronom != null) mostraTemps();
            h.postDelayed(this, metronom != null ? 25 : 100);
        }
    };

    /** Pampalluga de tota la pantalla: més forta al primer temps del compàs. */
    private void pampallugueja(boolean primer, boolean compteEnrere) {
        pampalluga.animate().cancel();
        pampalluga.setBackgroundColor(compteEnrere ? Ui.GUITARRA : Ui.ACCENT);
        pampalluga.setAlpha(primer ? 0.32f : 0.14f);
        pampalluga.animate().alpha(0f).setDuration(primer ? 220 : 150).start();
    }

    /** Punts del compàs i compte enrere mentre grava amb metrònom. */
    private void mostraTemps() {
        long t = metronom.tempsAra();
        int compte = metronom.tempsCompte();
        if (t < 0) {
            punts.setActiu(-1, Ui.GUITARRA);
            mostraEstat("Preparat…", Ui.GUITARRA);
            return;
        }
        int dins = (int) (t % metronom.compas());
        if (t != darrerTemps) {
            darrerTemps = t;
            if (!metronom.ambSo()) pampallugueja(dins == 0, t < compte);
        }
        if (t < compte) {
            punts.setActiu(dins, Ui.GUITARRA);
            mostraEstat("Compte enrere · " + (compte - t), Ui.GUITARRA);
        } else {
            punts.setActiu(dins, Ui.ACCENT);
            long compasN = (t - compte) / metronom.compas() + 1;
            mostraEstat("● Gravant · compàs " + compasN, Ui.ACCENT);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setNavigationBarColor(Ui.SUPERFICIE);
        mode = Ui.prefs(this).getString("mode", Fitxa.GUITARRA);
        construeix();

        // Si l'app es va tancar de cop mentre gravava, arregla aquell fitxer
        new Thread(() -> {
            Biblioteca b = Ui.biblioteca(this);
            int n = b.reparaTot(b.arrel);
            if (n > 0) Ui.historial(this, "Recuperades " + n + " gravacions que s'havien quedat a mitges");
        }).start();
    }

    private void construeix() {
        LinearLayout arrel = Ui.pantalla(this);
        LinearLayout cos = Ui.columna(this);
        cos.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), Ui.dp(this, 12));

        // ---- Barra de dalt ----
        LinearLayout dalt = Ui.fila(this);
        TextView nom = Ui.negreta(this, "Riffs", 26, Ui.TEXT);
        dalt.addView(nom);
        dalt.addView(Ui.mollaH(this));
        FrameLayout hist = Ui.botoIcona(this, Icona.HISTORIAL, Ui.TEXT, Ui.SUPERFICIE, 42, 21, "Historial");
        hist.setOnClickListener(v -> { if (!gravadora.grava()) startActivity(new Intent(this, HistorialActivity.class)); });
        dalt.addView(hist);
        dalt.addView(Ui.espai(this, 8));
        FrameLayout diag = Ui.botoIcona(this, Icona.AJUSTOS, Ui.TEXT, Ui.SUPERFICIE, 42, 21, "Diagnòstic i ajustos");
        diag.setOnClickListener(v -> { if (!gravadora.grava()) startActivity(new Intent(this, DiagnosticActivity.class)); });
        dalt.addView(diag);
        cos.addView(dalt, Ui.ample(this, 4));

        // ---- Guitarra / Veu (control segmentat) ----
        LinearLayout seg = Ui.fila(this);
        seg.setBackground(Ui.rodo(Ui.SUPERFICIE, Ui.dp(this, 26)));
        seg.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
        segGuitarra = Ui.pastilla(this, Icona.GUITARRA, "Guitarra", 0x00000000, Ui.TEXT);
        segVeu = Ui.pastilla(this, Icona.MIC, "Veu", 0x00000000, Ui.TEXT);
        segGuitarra.setOnClickListener(v -> triaMode(Fitxa.GUITARRA));
        segVeu.setOnClickListener(v -> triaMode(Fitxa.VEU));
        seg.addView(segGuitarra, Ui.pes(1));
        seg.addView(segVeu, Ui.pes(1));
        cos.addView(seg, Ui.ample(this, 16));

        // ---- On es desa · Metrònom ----
        LinearLayout duesRajoles = Ui.fila(this);
        filaCarpeta = rajolaAjust(Icona.CARPETA, "Es desa a");
        textCarpeta = (TextView) ((LinearLayout) filaCarpeta.getChildAt(1)).getChildAt(2);
        filaCarpeta.setOnClickListener(v -> {
            if (gravadora.grava()) return;
            Ui.triaCarpeta(this, "On es desen les gravacions?", Ui.carpetaActual(this), c -> {
                Ui.posaCarpetaActual(this, c);
                mostraCarpeta();
            });
        });
        rajolaMet = rajolaAjust(Icona.METRONOM, "Metrònom");
        textMetronom = (TextView) ((LinearLayout) rajolaMet.getChildAt(1)).getChildAt(2);
        rajolaMet.setOnClickListener(v -> {
            if (gravadora.grava()) return;
            Full.metronom(this, this::mostraMetronom);
        });
        duesRajoles.addView(filaCarpeta, Ui.pes(1));
        duesRajoles.addView(Ui.espai(this, 10));
        duesRajoles.addView(rajolaMet, Ui.pes(1));
        cos.addView(duesRajoles, Ui.ample(this, 12));

        // ---- Ona, rellotge i botó ----
        cos.addView(Ui.mollaV(this, 1));
        ona = new OnaView(this);
        ona.setBarres(Ui.dp(this, 3), Ui.dp(this, 2.5f));
        ona.setColors(Ui.ACCENT, Ui.LINIA_FORTA);
        ona.comencaDirecte();
        cos.addView(ona, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 96)));

        rellotge = Ui.text(this, "0:00.0", 48, Ui.TEXT);
        rellotge.setTypeface(Ui.FINA);
        rellotge.setFontFeatureSettings("tnum");
        rellotge.setGravity(Gravity.CENTER);
        cos.addView(rellotge, Ui.ample(this, 12));

        punts = new PuntsTemps(this);
        cos.addView(punts, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 20)));
        ((LinearLayout.LayoutParams) punts.getLayoutParams()).topMargin = Ui.dp(this, 10);

        estat = Ui.text(this, "Preparat per gravar", 14, Ui.SUAU);
        estat.setGravity(Gravity.CENTER);
        cos.addView(estat, Ui.ample(this, 8));

        boto = new BotoGravar(this);
        boto.setOnClickListener(v -> premBoto());
        LinearLayout.LayoutParams pb = new LinearLayout.LayoutParams(Ui.dp(this, 92), Ui.dp(this, 92));
        pb.gravity = Gravity.CENTER_HORIZONTAL;
        pb.topMargin = Ui.dp(this, 18);
        cos.addView(boto, pb);
        cos.addView(Ui.mollaV(this, 1));

        // ---- Última gravació ----
        targetaUltima = Ui.targeta(this);
        rajolaUltima = Ui.rajola(this, Icona.GUITARRA, Ui.GUITARRA, Ui.alfa(Ui.GUITARRA, 40), 42);
        targetaUltima.addView(rajolaUltima);
        LinearLayout textos = Ui.columna(this);
        textos.addView(Ui.etiqueta(this, "Última gravació"));
        titolUltima = Ui.unaLinia(Ui.negreta(this, "", 15, Ui.TEXT));
        textos.addView(titolUltima, Ui.ample(this, 4));
        infoUltima = Ui.unaLinia(Ui.text(this, "", 13, Ui.SUAU));
        textos.addView(infoUltima, Ui.ample(this, 3));
        LinearLayout.LayoutParams lpt = Ui.pes(1);
        lpt.leftMargin = Ui.dp(this, 12);
        targetaUltima.addView(textos, lpt);
        playUltima = Ui.botoIcona(this, Icona.PLAY, Ui.TEXT, Ui.SUPERFICIE2, 44, 18, "Escolta");
        playUltima.setOnClickListener(v -> alternaUltima());
        targetaUltima.addView(playUltima);
        targetaUltima.setOnClickListener(v -> {
            if (ultima != null && ultima.exists() && !gravadora.grava()) obreFitxa(ultima);
        });
        cos.addView(targetaUltima, Ui.ample(this, 0));

        arrel.addView(cos, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        arrel.addView(Nav.barra(this, Nav.GRAVAR));

        // Capa per a les pampallugues del metrònom visual (no captura tocs)
        FrameLayout marc = new FrameLayout(this);
        marc.addView(arrel);
        pampalluga = new View(this);
        pampalluga.setAlpha(0f);
        pampalluga.setClickable(false);
        pampalluga.setFocusable(false);
        marc.addView(pampalluga, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(marc);
        mostraMode();
        mostraCarpeta();
        mostraMetronom();
    }

    /** Rajola d'ajust (mitja amplada): icona + etiqueta, i a sota el valor. */
    private LinearLayout rajolaAjust(int icona, String etiqueta) {
        LinearLayout t = Ui.targeta(this);
        t.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
        t.addView(Ui.rajola(this, icona, Ui.TEXT, Ui.SUPERFICIE2, 34));
        LinearLayout col = Ui.columna(this);
        col.addView(Ui.etiqueta(this, etiqueta));
        col.addView(Ui.espai(this, 3));
        col.addView(Ui.unaLinia(Ui.negreta(this, "", 14, Ui.TEXT)));
        LinearLayout.LayoutParams lp = Ui.pes(1);
        lp.leftMargin = Ui.dp(this, 10);
        t.addView(col, lp);
        return t;
    }

    private void mostraMetronom() {
        Metronom.Config k = Metronom.Config.llegeix(this);
        if (k.actiu) {
            textMetronom.setText(Math.round(k.bpm) + " BPM · " + Ritme.nomCompas(k.compas));
            textMetronom.setTextColor(Ui.TEXT);
            Ui.colorIcona((FrameLayout) rajolaMet.getChildAt(0), Ui.ACCENT);
            punts.setCompas(k.compas);
            punts.setActiu(-1, Ui.ACCENT);
            punts.setVisibility(View.VISIBLE);
        } else {
            textMetronom.setText("Desactivat");
            textMetronom.setTextColor(Ui.SUAU);
            Ui.colorIcona((FrameLayout) rajolaMet.getChildAt(0), Ui.TEXT);
            punts.setVisibility(View.INVISIBLE);
        }
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
    protected void onPause() {
        super.onPause();
        aturaUltima();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Si l'app passa a segon pla mentre grava, s'atura i es desa el que hi ha
        if (gravadora.grava()) atura("S'ha aturat perquè l'app ha passat a segon pla");
    }

    private static String tempsAmbDecimes(long ms) {
        long d = ms / 100;
        return String.format(Locale.ROOT, "%d:%02d.%d", d / 600, (d / 10) % 60, d % 10);
    }

    // ---------- Mode i carpeta ----------

    private void triaMode(String m) {
        if (gravadora.grava()) return;
        mode = m;
        Ui.prefs(this).edit().putString("mode", m).apply();
        mostraMode();
    }

    private void pintaSegment(LinearLayout s, boolean actiu, int color) {
        s.setBackground(Ui.tocable(actiu ? color : 0x00000000, Ui.dp(this, 22)));
        int c = actiu ? Ui.SOBRE_COLOR : Ui.SUAU;
        android.widget.ImageView i = (android.widget.ImageView) s.getChildAt(0);
        ((Icona) i.getDrawable()).setColor(c);
        ((TextView) s.getChildAt(2)).setTextColor(c);
    }

    private void mostraMode() {
        boolean veu = Fitxa.VEU.equals(mode);
        pintaSegment(segGuitarra, !veu, Ui.GUITARRA);
        pintaSegment(segVeu, veu, Ui.VEU);
    }

    private void mostraCarpeta() {
        textCarpeta.setText(Ui.nomCarpeta(this, Ui.carpetaActual(this)));
    }

    private void mostraUltima() {
        if (ultima == null || !ultima.exists()) {
            targetaUltima.setVisibility(View.INVISIBLE);
            return;
        }
        Biblioteca.Gravacio g = Ui.biblioteca(this).gravacio(ultima);
        int color = Ui.colorMode(g.fitxa);
        rajolaUltima.setBackground(Ui.rodo(Ui.alfa(color, 40), Ui.dp(this, 12)));
        android.widget.ImageView i = (android.widget.ImageView) rajolaUltima.getChildAt(0);
        i.setImageDrawable(new Icona(Ui.iconaMode(g.fitxa), color, Ui.dp(this, 21)));
        titolUltima.setText(g.fitxa.titol);
        infoUltima.setText(Biblioteca.durada(g.fitxa.duradaMs) + "  ·  " + Ui.nomCarpeta(this, ultima.getParentFile()));
        targetaUltima.setVisibility(View.VISIBLE);
    }

    private void alternaUltima() {
        if (gravadora.grava() || ultima == null || !ultima.exists()) return;
        if (mp != null) {
            aturaUltima();
            return;
        }
        try {
            mp = new MediaPlayer();
            mp.setDataSource(ultima.getAbsolutePath());
            mp.setOnCompletionListener(m -> aturaUltima());
            mp.prepare();
            mp.start();
            Ui.canviaIcona(playUltima, Icona.STOP, Ui.TEXT, 18);
        } catch (IOException | RuntimeException e) {
            aturaUltima();
            Ui.avis(this, "No puc reproduir la gravació.");
        }
    }

    private void aturaUltima() {
        if (mp != null) {
            try {
                mp.stop();
            } catch (IllegalStateException ignored) {
            }
            mp.release();
            mp = null;
        }
        if (playUltima != null) Ui.canviaIcona(playUltima, Icona.PLAY, Ui.TEXT, 18);
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
            Full.avis(this, "Cal permís de micròfon",
                    "Sense el micròfon l'app no pot gravar. Dona-li permís a Configuració → Aplicacions → Riffs → Permisos → Micròfon.",
                    "Obre la configuració", () -> startActivity(new Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", getPackageName(), null))));
        }
    }

    private void comenca() {
        aturaUltima();
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
            wavActual = null;
            mostraEstat("No puc desar al mòbil. Potser està ple?", Ui.ACCENT);
            return;
        }

        String font = Ui.prefs(this).getString("font", Gravadora.AUTO);
        String error = gravadora.comenca(this, wavActual, font, new Gravadora.Oient() {
            @Override
            public void nivell(float pic) {
                runOnUiThread(() -> ona.afegeix(pic));
            }

            @Override
            public void error(String missatge) {
                runOnUiThread(() -> atura(missatge));
            }
        });
        if (error != null) {
            b.esborra(wavActual);
            wavActual = null;
            mostraEstat(error, Ui.ACCENT);
            return;
        }
        // Metrònom: comença just després del micròfon perquè no es perdi el primer clic
        Metronom.Config k = Metronom.Config.llegeix(this);
        metronom = null;
        if (k.actiu) {
            metronom = new Metronom(k.bpm, k.compas, k.compteEnrere, Metronom.ambSo(this, k));
            if (!metronom.comenca()) {
                metronom = null;
                Ui.avis(this, "No s'ha pogut engegar el metrònom: grava sense.");
            } else {
                fitxaActual.bpm = metronom.bpm();
                fitxaActual.compas = metronom.compas();
                fitxaActual.claqueta = true;
                punts.setCompas(metronom.compas());
                darrerTemps = Long.MIN_VALUE;
                if (metronom.ambSo()) {
                    metronom.vigilaAuriculars(this, () ->
                            Ui.avis(this, "Auriculars desconnectats: el clic passa a ser visual"));
                }
            }
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        boto.setGravant(true);
        ona.comencaDirecte();
        mostraEstat(metronom != null ? "Preparat…" : "● Gravant", metronom != null ? Ui.GUITARRA : Ui.ACCENT);
        rellotge.setText("0:00.0");
        h.post(rellotgeTic);
        bloqueja(true);
    }

    private void bloqueja(boolean b) {
        float a = b ? 0.4f : 1f;
        filaCarpeta.setEnabled(!b);
        filaCarpeta.setAlpha(a);
        rajolaMet.setEnabled(!b);
        rajolaMet.setAlpha(a);
        segGuitarra.setEnabled(!b);
        segVeu.setEnabled(!b);
        ((View) segGuitarra.getParent()).setAlpha(a);
        targetaUltima.setEnabled(!b);
        targetaUltima.setAlpha(a);
    }

    private void mostraEstat(String s, int color) {
        estat.setText(s);
        estat.setTextColor(color);
    }

    private void atura(String motiu) {
        if (wavActual == null) return;
        // On cau el primer temps dins la gravació (abans d'aturar res)
        long primerEsperat = -1;
        Metronom met = metronom;
        metronom = null;
        if (met != null) primerEsperat = gravadora.msDelMoment(met.nanoPrimerTemps());
        long durada = gravadora.atura();
        if (met != null) met.atura();
        punts.setActiu(-1, Ui.ACCENT);
        h.removeCallbacks(rellotgeTic);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        boto.setGravant(false);
        bloqueja(false);

        Biblioteca b = Ui.biblioteca(this);
        File wav = wavActual;
        wavActual = null;
        if (durada < 700) {
            b.esborra(wav);
            rellotge.setText("0:00.0");
            ona.comencaDirecte();
            mostraEstat(motiu != null ? motiu : "Massa curta: no s'ha desat", Ui.SUAU);
            return;
        }
        fitxaActual.duradaMs = durada;
        if (met != null) {
            if (primerEsperat >= 0 && primerEsperat < durada - 100) {
                // l'enganxa a l'atac (la nota) més proper, si n'hi ha un de clar
                fitxaActual.primerTempsMs = Ritme.afinaPrimerTemps(wav, primerEsperat);
            } else {
                fitxaActual.primerTempsMs = -1; // s'ha aturat durant el compte enrere
            }
        }
        try {
            fitxaActual.desa(Biblioteca.fitxaDe(wav));
        } catch (IOException ignored) {
            // el so ja és desat; la fitxa es pot refer
        }
        ultima = wav;
        Ui.prefs(this).edit().putString("ultima", wav.getAbsolutePath()).apply();
        Ui.historial(this, "Gravat «" + fitxaActual.titol + "» (" + (fitxaActual.esVeu() ? "veu" : "guitarra") + ", "
                + Biblioteca.durada(durada) + (fitxaActual.claqueta ? ", " + fitxaActual.textTempo() : "")
                + ") a " + Ui.nomCarpeta(this, wav.getParentFile()));
        rellotge.setText(tempsAmbDecimes(durada));
        mostraEstat(motiu != null ? motiu : "Desada ✓", motiu != null ? Ui.ACCENT : Ui.SUAU);
        mostraUltima();
        // prepara l'ona per a la biblioteca sense fer esperar
        new Thread(() -> Ona.pics(wav)).start();
    }

    private void obreFitxa(File wav) {
        Intent i = new Intent(this, FitxaActivity.class);
        i.putExtra(FitxaActivity.EXTRA_WAV, wav.getAbsolutePath());
        startActivity(i);
    }
}
