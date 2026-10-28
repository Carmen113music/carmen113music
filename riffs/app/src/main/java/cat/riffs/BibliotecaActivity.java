package cat.riffs;

import android.app.Activity;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Les carpetes i gravacions d'una carpeta. L'arrel és la pestanya «Biblioteca». */
public class BibliotecaActivity extends Activity {

    public static final String EXTRA_DIR = "dir";

    private final ExecutorService fil = Executors.newSingleThreadExecutor();
    private Biblioteca biblio;
    private File dir;
    private boolean esArrel;
    private LinearLayout llista;
    private TextView resum;

    private MediaPlayer reproductor;
    private File sonant;
    private FrameLayout botoSonant;
    private OnaView onaSonant;
    private final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());

    /** Il·lumina l'ona de la gravació que sona, com a la fitxa. */
    private final Runnable tic = new Runnable() {
        @Override
        public void run() {
            if (reproductor == null || onaSonant == null) return;
            try {
                onaSonant.setProgres(reproductor.getCurrentPosition() / (float) Math.max(1, reproductor.getDuration()));
            } catch (IllegalStateException e) {
                return;
            }
            h.postDelayed(this, 50);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setNavigationBarColor(Ui.SUPERFICIE);
        biblio = Ui.biblioteca(this);
        String d = getIntent().getStringExtra(EXTRA_DIR);
        dir = d == null ? biblio.arrel : new File(d);
        if (!dir.isDirectory() || !biblio.esDins(dir)) dir = biblio.arrel;
        esArrel = dir.equals(biblio.arrel);

        LinearLayout arrel = Ui.pantalla(this);

        FrameLayout nova = Ui.botoIcona(this, Icona.MES, Ui.TEXT, Ui.SUPERFICIE, 42, 22, "Carpeta nova");
        nova.setOnClickListener(v -> novaCarpeta());

        if (esArrel) {
            LinearLayout dalt = Ui.fila(this);
            dalt.setPadding(Ui.dp(this, 20), Ui.dp(this, 12), Ui.dp(this, 20), 0);
            dalt.addView(Ui.negreta(this, "Biblioteca", 26, Ui.TEXT));
            dalt.addView(Ui.mollaH(this));
            dalt.addView(nova);
            arrel.addView(dalt);
        } else {
            FrameLayout grava = Ui.botoIcona(this, Icona.MIC, Ui.SOBRE_COLOR, Ui.ACCENT, 42, 20, "Grava aquí");
            grava.setOnClickListener(v -> gravaAqui());
            LinearLayout barra = Ui.barraEnrere(this, dir.getName(), nova, Ui.espai(this, 8), grava);
            barra.setPadding(Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 4));
            arrel.addView(barra);
        }

        resum = Ui.text(this, "", 13, Ui.SUAU);
        resum.setPadding(Ui.dp(this, 20), Ui.dp(this, 6), Ui.dp(this, 20), 0);
        arrel.addView(resum);

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        llista = Ui.columna(this);
        llista.setPadding(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 24));
        scroll.addView(llista);
        arrel.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        if (esArrel) arrel.addView(Nav.barra(this, Nav.BIBLIOTECA));
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        fil.shutdownNow();
    }

    private void gravaAqui() {
        Ui.posaCarpetaActual(this, dir);
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
    }

    private void omple() {
        aturaSo();
        llista.removeAllViews();
        List<File> carpetes = biblio.carpetes(dir);
        List<Biblioteca.Gravacio> gravacions = biblio.gravacions(dir);

        String r = esArrel ? "" : "Biblioteca / " + biblio.ruta(dir) + "\n";
        int total = biblio.compta(dir);
        r += (total == 1 ? "1 gravació" : total + " gravacions");
        if (!carpetes.isEmpty()) r += "  ·  " + (carpetes.size() == 1 ? "1 carpeta" : carpetes.size() + " carpetes");
        resum.setText(r);

        if (carpetes.isEmpty() && gravacions.isEmpty()) {
            llista.addView(estatBuit(), Ui.ample(this, 48));
            return;
        }

        if (!carpetes.isEmpty()) {
            llista.addView(Ui.etiqueta(this, esArrel ? "Cançons i carpetes" : "Carpetes"), Ui.ample(this, 18));
            for (File c : carpetes) llista.addView(filaCarpeta(c), Ui.ample(this, 8));
        }
        if (!gravacions.isEmpty()) {
            llista.addView(Ui.etiqueta(this, "Gravacions"), Ui.ample(this, 22));
            for (Biblioteca.Gravacio g : gravacions) llista.addView(filaGravacio(g), Ui.ample(this, 8));
        }
    }

    private View estatBuit() {
        LinearLayout c = Ui.columna(this);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout icona = Ui.rajola(this, esArrel ? Icona.BIBLIOTECA : Icona.CARPETA, Ui.SUAU, Ui.SUPERFICIE, 72);
        c.addView(icona);
        TextView t = Ui.negreta(this, esArrel ? "Encara no hi ha res" : "Carpeta buida", 18, Ui.TEXT);
        t.setGravity(Gravity.CENTER);
        c.addView(t, Ui.ample(this, 16));
        TextView s = Ui.text(this, esArrel
                ? "Crea una carpeta per a cada cançó amb el botó +, o grava una idea des de la pestanya Gravar."
                : "Toca el micròfon de dalt per gravar idees directament aquí, o mou-hi gravacions des d'una altra carpeta.",
                14, Ui.SUAU);
        s.setGravity(Gravity.CENTER);
        s.setLineSpacing(0, 1.2f);
        c.addView(s, Ui.ample(this, 8));
        c.setPadding(Ui.dp(this, 24), 0, Ui.dp(this, 24), 0);
        return c;
    }

    private LinearLayout filaCarpeta(File c) {
        LinearLayout t = Ui.targeta(this);
        t.addView(Ui.rajola(this, Icona.CARPETA, Ui.TEXT, Ui.SUPERFICIE2, 44));
        LinearLayout textos = Ui.columna(this);
        textos.addView(Ui.unaLinia(Ui.negreta(this, c.getName(), 16, Ui.TEXT)));
        int n = biblio.compta(c);
        int sub = biblio.carpetes(c).size();
        String info = n == 1 ? "1 gravació" : n + " gravacions";
        if (sub > 0) info += "  ·  " + (sub == 1 ? "1 carpeta" : sub + " carpetes");
        textos.addView(Ui.text(this, info, 13, Ui.SUAU), Ui.ample(this, 4));
        LinearLayout.LayoutParams lp = Ui.pes(1);
        lp.leftMargin = Ui.dp(this, 12);
        t.addView(textos, lp);
        FrameLayout mes = Ui.botoIcona(this, Icona.PUNTS, Ui.SUAU, 0x00000000, 40, 20, "Opcions");
        mes.setOnClickListener(v -> menuCarpeta(c));
        t.addView(mes);
        t.addView(Ui.imatge(this, Icona.XEVRO, Ui.APAGAT, 20), new LinearLayout.LayoutParams(Ui.dp(this, 20), Ui.dp(this, 24)));
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
        int color = Ui.colorMode(g.fitxa);
        LinearLayout t = Ui.targeta(this);
        t.setGravity(Gravity.TOP);

        FrameLayout play = Ui.botoIcona(this, Icona.PLAY, color, Ui.alfa(color, 36), 44, 18, "Escolta");
        t.addView(play);

        LinearLayout textos = Ui.columna(this);
        textos.addView(Ui.unaLinia(Ui.negreta(this, g.fitxa.titol, 16, Ui.TEXT)));
        StringBuilder info = new StringBuilder();
        info.append(g.fitxa.esVeu() ? "Veu" : "Guitarra").append("  ·  ")
                .append(Biblioteca.durada(g.fitxa.duradaMs)).append("  ·  ")
                .append(Biblioteca.data(g.fitxa.data));
        textos.addView(Ui.unaLinia(Ui.text(this, info, 13, Ui.SUAU)), Ui.ample(this, 4));

        OnaView ona = new OnaView(this);
        ona.setBarres(Ui.dp(this, 2), Ui.dp(this, 1.5f));
        ona.setColors(color, Ui.LINIA_FORTA);
        ona.setProgres(1f); // sense reproduir, tota l'ona es veu amb el seu color
        ona.setPics(null);
        textos.addView(ona, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 26)));
        play.setOnClickListener(v -> alternaSo(g.wav, play, color, ona));
        LinearLayout.LayoutParams o = (LinearLayout.LayoutParams) ona.getLayoutParams();
        o.topMargin = Ui.dp(this, 8);
        fil.execute(() -> {
            float[] p = Ona.pics(g.wav);
            runOnUiThread(() -> ona.setPics(p));
        });

        if (!g.fitxa.lletra.trim().isEmpty() || !g.fitxa.notes.trim().isEmpty()) {
            LinearLayout marques = Ui.fila(this);
            if (!g.fitxa.lletra.trim().isEmpty()) marques.addView(xip(Icona.LLETRA, "Lletra"));
            if (!g.fitxa.notes.trim().isEmpty()) {
                if (marques.getChildCount() > 0) marques.addView(Ui.espai(this, 6));
                marques.addView(xip(Icona.NOTA, "Notes"));
            }
            textos.addView(marques, Ui.ample(this, 8));
        }

        LinearLayout.LayoutParams lp = Ui.pes(1);
        lp.leftMargin = Ui.dp(this, 12);
        t.addView(textos, lp);

        FrameLayout mes = Ui.botoIcona(this, Icona.PUNTS, Ui.SUAU, 0x00000000, 40, 20, "Opcions");
        mes.setOnClickListener(v -> menuGravacio(g));
        t.addView(mes);

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

    private LinearLayout xip(int icona, String s) {
        LinearLayout x = Ui.fila(this);
        x.setBackground(Ui.rodo(Ui.SUPERFICIE2, Ui.dp(this, 10)));
        x.setPadding(Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 4));
        x.addView(Ui.imatge(this, icona, Ui.SUAU, 13), new LinearLayout.LayoutParams(Ui.dp(this, 14), Ui.dp(this, 14)));
        x.addView(Ui.espai(this, 5));
        x.addView(Ui.text(this, s, 12, Ui.SUAU));
        return x;
    }

    // ---------- Escoltar ràpid ----------

    private void alternaSo(File wav, FrameLayout b, int color, OnaView ona) {
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
            onaSonant = ona;
            ona.setProgres(0f);
            h.post(tic);
            b.setTag(color);
            Ui.canviaIcona(b, Icona.STOP, color, 18);
        } catch (Exception e) {
            aturaSo();
            Ui.avis(this, "No puc reproduir aquesta gravació.");
        }
    }

    private void aturaSo() {
        h.removeCallbacks(tic);
        if (onaSonant != null) onaSonant.setProgres(1f);
        onaSonant = null;
        if (reproductor != null) {
            try {
                reproductor.stop();
            } catch (IllegalStateException ignored) {
            }
            reproductor.release();
            reproductor = null;
        }
        if (botoSonant != null) {
            Object c = botoSonant.getTag();
            Ui.canviaIcona(botoSonant, Icona.PLAY, c instanceof Integer ? (Integer) c : Ui.TEXT, 18);
        }
        botoSonant = null;
        sonant = null;
    }

    // ---------- Menús ----------

    private void novaCarpeta() {
        Ui.demanaText(this, esArrel ? "Carpeta nova" : "Carpeta nova dins «" + dir.getName() + "»", "", "Crea", nom -> {
            try {
                File c = biblio.creaCarpeta(dir, nom);
                Ui.historial(this, "Carpeta nova: " + Ui.nomCarpeta(this, c));
                omple();
            } catch (IllegalArgumentException e) {
                Ui.avis(this, e.getMessage());
            }
        });
    }

    private void menuCarpeta(File c) {
        int n = biblio.compta(c);
        int sub = biblio.carpetes(c).size();
        String info = n == 1 ? "1 gravació" : n + " gravacions";
        if (sub > 0) info += "  ·  " + (sub == 1 ? "1 carpeta" : sub + " carpetes");
        List<Full.Accio> acc = new java.util.ArrayList<>();
        acc.add(new Full.Accio(Icona.MIC, "Grava aquí", false, () -> {
            Ui.posaCarpetaActual(this, c);
            Intent in = new Intent(this, MainActivity.class);
            in.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(in);
        }));
        acc.add(new Full.Accio(Icona.LLAPIS, "Canvia el nom", false, () ->
                Ui.demanaText(this, "Nom de la carpeta", c.getName(), "Desa", nom -> {
                    try {
                        String vell = c.getName();
                        File nova = biblio.reanomenaCarpeta(c, nom);
                        Ui.reubica(this, c, nova);
                        Ui.historial(this, "La carpeta «" + vell + "» ara es diu «" + nova.getName() + "»");
                        omple();
                    } catch (IllegalArgumentException e) {
                        Ui.avis(this, e.getMessage());
                    }
                })));
        acc.add(new Full.Accio(Icona.PAPERERA, "Esborra", true, () -> {
            String msg = n == 0 ? "La carpeta és buida."
                    : n == 1 ? "S'esborrarà també la gravació que té a dins (i la seva lletra). No es pot desfer."
                    : "S'esborraran també les " + n + " gravacions que té a dins (i les seves lletres). No es pot desfer.";
            Ui.confirma(this, "Vols esborrar «" + c.getName() + "»?", msg, "Esborra", () -> {
                biblio.esborraCarpeta(c);
                Ui.historial(this, "Esborrada la carpeta «" + c.getName() + "»" + (n > 0 ? " (" + n + " gravacions)" : ""));
                omple();
            });
        }));
        Full.accions(this, Icona.CARPETA, Ui.TEXT, c.getName(), info, acc);
    }

    private void menuGravacio(Biblioteca.Gravacio g) {
        aturaSo();
        String info = (g.fitxa.esVeu() ? "Veu" : "Guitarra") + "  ·  " + Biblioteca.durada(g.fitxa.duradaMs)
                + "  ·  " + Biblioteca.data(g.fitxa.data);
        Full.accions(this, Ui.iconaMode(g.fitxa), Ui.colorMode(g.fitxa), g.fitxa.titol, info, java.util.Arrays.asList(
                new Full.Accio(Icona.LLAPIS, "Canvia el nom", false, () -> Accions.canviaNom(this, g.wav, this::omple)),
                new Full.Accio(Icona.MOURE, "Mou", false, () -> Accions.mou(this, g.wav, nou -> omple())),
                new Full.Accio(Icona.COMPARTIR, "Comparteix", false, () -> Accions.comparteix(this, g.wav)),
                new Full.Accio(Icona.PAPERERA, "Esborra", true, () -> Accions.esborra(this, g.wav, this::omple))));
    }
}
