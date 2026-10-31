package cat.riffs;

import android.app.Activity;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;

/**
 * Fitxa d'una gravació: escoltar-la sobre la seva ona, posar-hi títol i escriure'n la lletra i notes.
 * El reproductor queda sempre a dalt, també quan surt el teclat.
 */
public class FitxaActivity extends Activity {

    public static final String EXTRA_WAV = "wav";

    private final Handler h = new Handler(Looper.getMainLooper());
    private Biblioteca.Gravacio g;

    private EditText campTitol, campLletra, campNotes;
    private TextView info, tempsAra, tempsTotal, textMode;
    private LinearLayout xipMode;
    private FrameLayout botoPlay, botoBucle;
    private OnaView ona;
    private Switch pausaEscrivint, retrocedeix;

    private MediaPlayer mp;
    private boolean preparat = false;
    private boolean bucle = false;
    private boolean saltat = false;
    private LinearLayout xipTempo, xipPrimer, xipTreu;
    private TextView textTempo, textPrimer;         // l'usuari ha triat on reprendre: no cal retrocedir
    private boolean omplint = false;        // canvis de text fets per l'app, no per l'usuari
    private boolean pendent = false;        // hi ha canvis per desar
    private boolean lletraApuntada = false; // ja s'ha apuntat a l'historial en aquesta visita

    // Anàlisi: notes, acords i tonalitat (2.33)
    private AnalisiView vistaAnalisi;
    private LinearLayout zonaAnalisi;
    private Analisi.Resultat analisi;
    private boolean analitzant = false;
    private int percentAnalisi = 0;
    private volatile boolean tancant = false;
    private boolean teclatObert = false;

    private final Runnable desa = this::desaAra;

    private final Runnable tic = new Runnable() {
        @Override
        public void run() {
            if (mp != null && preparat) {
                int pos = mp.getCurrentPosition();
                posaProgres(pos / (float) Math.max(1, mp.getDuration()));
                tempsAra.setText(Biblioteca.durada(pos));
            }
            if (mp != null && mp.isPlaying()) h.postDelayed(this, 50);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        String p = getIntent().getStringExtra(EXTRA_WAV);
        File wav = p == null ? null : new File(p);
        if (wav == null || !wav.exists()) {
            Ui.avis(this, "Aquesta gravació ja no hi és.");
            finish();
            return;
        }
        g = Ui.biblioteca(this).gravacio(wav);
        construeix();
        preparaSo();
        File wavAra = g.wav;
        new Thread(() -> {
            float[] pics = Ona.pics(wavAra);
            Analisi.Resultat an = Analisi.llegeix(wavAra);
            runOnUiThread(() -> {
                if (tancant) return;
                ona.setPics(pics);
                if (an != null && !analitzant) {
                    analisi = an;
                    mostraAnalisi();
                }
            });
        }).start();
    }

    private void construeix() {
        LinearLayout arrel = Ui.pantalla(this);

        // ---- Barra de dalt: enrere · tipus · opcions ----
        xipMode = Ui.fila(this);
        xipMode.setClickable(true);
        xipMode.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 14), 0);
        xipMode.setMinimumHeight(Ui.dp(this, 36));
        xipMode.addView(Ui.imatge(this, Icona.GUITARRA, Ui.SOBRE_COLOR, 18), new LinearLayout.LayoutParams(Ui.dp(this, 20), Ui.dp(this, 20)));
        xipMode.addView(Ui.espai(this, 6));
        textMode = Ui.negreta(this, "", 14, Ui.SOBRE_COLOR);
        xipMode.addView(textMode);
        xipMode.setOnClickListener(v -> canviaMode());
        FrameLayout opcions = Ui.botoIcona(this, Icona.PUNTS, Ui.TEXT, 0x00000000, 42, 20, "Opcions");
        opcions.setOnClickListener(v -> menu());
        arrel.addView(Ui.barraEnrere(this, "", xipMode, Ui.espai(this, 4), opcions));

        // ---- Reproductor (fix a dalt) ----
        LinearLayout rep = Ui.columna(this);
        rep.setPadding(Ui.dp(this, 20), Ui.dp(this, 4), Ui.dp(this, 20), Ui.dp(this, 14));
        ona = new OnaView(this);
        ona.setBarres(Ui.dp(this, 3), Ui.dp(this, 2));
        OnaView.OientSalt salt = (f, acabat) -> {
            if (mp == null || !preparat) return;
            int pos = Math.round(f * mp.getDuration());
            tempsAra.setText(Biblioteca.durada(pos));
            posaProgres(f);
            if (acabat) {
                mp.seekTo(pos);
                saltat = true;
            }
        };
        ona.setOientSalt(salt);
        rep.addView(ona, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 88)));

        // Franja de l'anàlisi (notes o acords), alineada amb l'ona
        vistaAnalisi = new AnalisiView(this, ona);
        vistaAnalisi.setOientSalt(salt);
        vistaAnalisi.setVisibility(View.GONE);
        LinearLayout.LayoutParams lpAn = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 48));
        lpAn.topMargin = Ui.dp(this, 6);
        rep.addView(vistaAnalisi, lpAn);

        LinearLayout temps = Ui.fila(this);
        tempsAra = Ui.text(this, "0:00", 13, Ui.TEXT);
        tempsAra.setFontFeatureSettings("tnum");
        tempsTotal = Ui.text(this, "0:00", 13, Ui.SUAU);
        tempsTotal.setFontFeatureSettings("tnum");
        temps.addView(tempsAra);
        temps.addView(Ui.mollaH(this));
        temps.addView(tempsTotal);
        rep.addView(temps, Ui.ample(this, 8));

        LinearLayout controls = Ui.fila(this);
        controls.setGravity(Gravity.CENTER);
        FrameLayout enrere5 = Ui.botoIcona(this, Icona.ENRERE5, Ui.TEXT, Ui.SUPERFICIE, 52, 26, "Enrere 5 segons");
        enrere5.setOnClickListener(v -> salta(-5000));
        controls.addView(enrere5);
        controls.addView(Ui.espai(this, 28));
        botoPlay = Ui.botoIcona(this, Icona.PLAY, Ui.SOBRE_COLOR, Ui.TEXT, 68, 28, "Reprodueix");
        botoPlay.setOnClickListener(v -> alternaPlay());
        controls.addView(botoPlay);
        controls.addView(Ui.espai(this, 28));
        botoBucle = Ui.botoIcona(this, Icona.BUCLE, Ui.TEXT, Ui.SUPERFICIE, 52, 24, "Repeteix");
        botoBucle.setOnClickListener(v -> alternaBucle());
        controls.addView(botoBucle);
        rep.addView(controls, Ui.ample(this, 4));

        // Tempo i primer temps
        LinearLayout ritme = Ui.fila(this);
        ritme.setGravity(Gravity.CENTER);
        xipTempo = xip(Icona.METRONOM);
        textTempo = (TextView) xipTempo.getChildAt(2);
        xipTempo.setOnClickListener(v -> editaTempo());
        xipPrimer = xip(Icona.BANDERA);
        textPrimer = (TextView) xipPrimer.getChildAt(2);
        xipPrimer.setOnClickListener(v -> alternaEditaPrimer());
        xipTreu = xip(Icona.PAPERERA);
        ((TextView) xipTreu.getChildAt(2)).setText("Treu");
        xipTreu.setOnClickListener(v -> treuPrimer());
        ritme.addView(xipTempo);
        ritme.addView(Ui.espai(this, 8));
        ritme.addView(xipPrimer);
        ritme.addView(Ui.espai(this, 8));
        ritme.addView(xipTreu);
        rep.addView(ritme, Ui.ample(this, 12));

        // Analitza / tonalitat i Notes·Acords
        zonaAnalisi = Ui.fila(this);
        zonaAnalisi.setGravity(Gravity.CENTER_VERTICAL);
        rep.addView(zonaAnalisi, Ui.ample(this, 10));
        arrel.addView(rep);
        arrel.addView(Ui.separador(this));

        // ---- La resta, amb desplaçament ----
        ScrollView scroll = new ScrollView(this);
        LinearLayout cos = Ui.columna(this);
        cos.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), Ui.dp(this, 32));

        campTitol = new EditText(this);
        campTitol.setTextColor(Ui.TEXT);
        campTitol.setHint("Títol");
        campTitol.setHintTextColor(Ui.APAGAT);
        campTitol.setTextSize(22);
        campTitol.setTypeface(Ui.MITJA);
        campTitol.setSingleLine(true);
        campTitol.setBackground(null);
        campTitol.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 4));
        campTitol.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        cos.addView(campTitol, Ui.ample(this, 4));

        info = Ui.text(this, "", 13, Ui.SUAU);
        cos.addView(info, Ui.ample(this, 4));

        // Lletra
        LinearLayout capLletra = Ui.fila(this);
        capLletra.addView(Ui.imatge(this, Icona.LLETRA, Ui.SUAU, 14), new LinearLayout.LayoutParams(Ui.dp(this, 16), Ui.dp(this, 16)));
        capLletra.addView(Ui.espai(this, 6));
        capLletra.addView(Ui.etiqueta(this, "Lletra"));
        cos.addView(capLletra, Ui.ample(this, 24));
        campLletra = Ui.camp(this, "Escolta i escriu aquí la lletra…", true);
        campLletra.setMinLines(7);
        campLletra.setLineSpacing(0, 1.25f);
        cos.addView(campLletra, Ui.ample(this, 8));

        LinearLayout opcionsLletra = Ui.columna(this);
        opcionsLletra.setBackground(Ui.rodo(Ui.SUPERFICIE, Ui.dp(this, 14)));
        opcionsLletra.setPadding(Ui.dp(this, 14), Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4));
        pausaEscrivint = interruptor("Atura el so quan escric", "pausa_escrivint");
        retrocedeix = interruptor("En tornar a reproduir, retrocedeix 2 s", "retrocedeix");
        opcionsLletra.addView(pausaEscrivint);
        opcionsLletra.addView(Ui.separador(this));
        opcionsLletra.addView(retrocedeix);
        cos.addView(opcionsLletra, Ui.ample(this, 8));

        // Notes
        LinearLayout capNotes = Ui.fila(this);
        capNotes.addView(Ui.imatge(this, Icona.NOTA, Ui.SUAU, 14), new LinearLayout.LayoutParams(Ui.dp(this, 16), Ui.dp(this, 16)));
        capNotes.addView(Ui.espai(this, 6));
        capNotes.addView(Ui.etiqueta(this, "Notes · acords, to, idees"));
        cos.addView(capNotes, Ui.ample(this, 24));
        campNotes = Ui.camp(this, "Per exemple: Re menor, capo 2. Pont per a «La del tren».", true);
        campNotes.setMinLines(3);
        cos.addView(campNotes, Ui.ample(this, 8));

        // Accions
        LinearLayout accions = Ui.fila(this);
        LinearLayout mou = Ui.pastilla(this, Icona.MOURE, "Mou", Ui.SUPERFICIE, Ui.TEXT);
        mou.setOnClickListener(v -> mou());
        LinearLayout comp = Ui.pastilla(this, Icona.COMPARTIR, "Comparteix", Ui.SUPERFICIE, Ui.TEXT);
        comp.setOnClickListener(v -> {
            desaAra();
            Accions.comparteix(this, g.wav);
        });
        FrameLayout esb = Ui.botoIcona(this, Icona.PAPERERA, Ui.ACCENT, Ui.SUPERFICIE, 48, 22, "Esborra");
        esb.setOnClickListener(v -> esborra());
        accions.addView(mou, Ui.pes(1));
        accions.addView(Ui.espai(this, 8));
        accions.addView(comp, Ui.pes(1.3f));
        accions.addView(Ui.espai(this, 8));
        accions.addView(esb);
        cos.addView(accions, Ui.ample(this, 28));

        scroll.addView(cos);
        arrel.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(arrel);

        // Amb el teclat obert, la fila de la tonalitat s'amaga (queda més lloc per a la lletra)
        arrel.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            android.graphics.Rect vis = new android.graphics.Rect();
            arrel.getWindowVisibleDisplayFrame(vis);
            int total = arrel.getRootView().getHeight();
            boolean obert = total - vis.bottom > Ui.dp(this, 150);
            if (obert != teclatObert) {
                teclatObert = obert;
                zonaAnalisi.setVisibility(obert && analisi != null ? View.GONE : View.VISIBLE);
            }
        });

        omplint = true;
        campTitol.setText(g.fitxa.titol);
        campLletra.setText(g.fitxa.lletra);
        campNotes.setText(g.fitxa.notes);
        omplint = false;
        mostraInfo();
        mostraAnalisi();

        TextWatcher vigila = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (omplint) return;
                pendent = true;
                h.removeCallbacks(desa);
                h.postDelayed(desa, 1200);
            }
        };
        campTitol.addTextChangedListener(vigila);
        campNotes.addTextChangedListener(vigila);
        campLletra.addTextChangedListener(vigila);
        campLletra.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                if (!omplint && pausaEscrivint.isChecked() && mp != null && mp.isPlaying()) pausa();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private Switch interruptor(String text, String clau) {
        Switch s = new Switch(this);
        s.setText(text);
        s.setTextColor(Ui.TEXT);
        s.setTextSize(14);
        s.setMinHeight(Ui.dp(this, 48));
        s.setChecked(Ui.prefs(this).getBoolean(clau, true));
        s.setOnCheckedChangeListener((b, v) -> Ui.prefs(this).edit().putBoolean(clau, v).apply());
        return s;
    }

    private LinearLayout xip(int icona) {
        LinearLayout x = Ui.fila(this);
        x.setClickable(true);
        x.setFocusable(true);
        x.setBackground(Ui.tocable(Ui.SUPERFICIE, Ui.dp(this, 16)));
        x.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 12), 0);
        x.setMinimumHeight(Ui.dp(this, 34));
        x.addView(Ui.imatge(this, icona, Ui.SUAU, 15), new LinearLayout.LayoutParams(Ui.dp(this, 16), Ui.dp(this, 16)));
        x.addView(Ui.espai(this, 6));
        x.addView(Ui.unaLinia(Ui.negreta(this, "", 13, Ui.TEXT)));
        return x;
    }

    private static String msAmbDecimes(long ms) {
        long d = Math.max(0, ms) / 100;
        return String.format(java.util.Locale.ROOT, "%d:%02d.%d", d / 600, (d / 10) % 60, d % 10);
    }

    private void mostraRitme() {
        Fitxa f = g.fitxa;
        textTempo.setText(f.teTempo() ? f.textTempo() : "Posa el tempo");
        textTempo.setTextColor(f.teTempo() ? Ui.TEXT : Ui.SUAU);
        boolean edita = ona.editaMarca();
        if (edita) {
            textPrimer.setText(f.tePrimerTemps() ? "Fet · " + msAmbDecimes(f.primerTempsMs) : "Fet");
            xipPrimer.setBackground(Ui.tocable(Ui.ACCENT, Ui.dp(this, 16)));
            textPrimer.setTextColor(0xFFFFFFFF);
            Ui.colorIcona(xipPrimer, 0xFFFFFFFF);
        } else {
            textPrimer.setText(f.tePrimerTemps() ? "1r temps " + msAmbDecimes(f.primerTempsMs) : "Marca el 1r temps");
            xipPrimer.setBackground(Ui.tocable(Ui.SUPERFICIE, Ui.dp(this, 16)));
            textPrimer.setTextColor(f.tePrimerTemps() ? Ui.TEXT : Ui.SUAU);
            Ui.colorIcona(xipPrimer, f.tePrimerTemps() ? Ui.ACCENT : Ui.SUAU);
        }
        xipTreu.setVisibility(edita && f.tePrimerTemps() ? View.VISIBLE : View.GONE);
        ona.setGraella(f.bpm, f.compas, f.primerTempsMs, durada());
    }

    private long durada() {
        if (mp != null && preparat && mp.getDuration() > 0) return mp.getDuration();
        return g.fitxa.duradaMs;
    }

    private void editaTempo() {
        Full.tempo(this, g.fitxa, (bpm, compas) -> {
            String abans = g.fitxa.teTempo() ? g.fitxa.textTempo() : "sense tempo";
            g.fitxa.bpm = bpm;
            g.fitxa.compas = compas;
            pendent = true;
            desaAra();
            Ui.historial(this, "Tempo de «" + g.fitxa.titol + "»: " + abans + " → "
                    + (g.fitxa.teTempo() ? g.fitxa.textTempo() : "sense tempo"));
            mostraRitme();
        });
    }

    private void alternaEditaPrimer() {
        if (ona.editaMarca()) {
            ona.setEditaMarca(false, null);
            pendent = true;
            desaAra();
            mostraRitme();
            return;
        }
        pausa();
        if (!g.fitxa.tePrimerTemps()) {
            // comença on és el capçal (o al principi)
            g.fitxa.primerTempsMs = (mp != null && preparat) ? mp.getCurrentPosition() : 0;
        }
        ona.setEditaMarca(true, (ms, acabat) -> {
            g.fitxa.primerTempsMs = ms;
            textPrimer.setText("Fet · " + msAmbDecimes(ms));
            if (acabat && mp != null && preparat) {
                // deixa el capçal a la bandera per poder-ho escoltar
                mp.seekTo((int) ms);
                saltat = true;
                tempsAra.setText(Biblioteca.durada(ms));
            }
        });
        mostraRitme();
        Ui.avis(this, "Arrossega la bandera vermella fins al primer temps");
    }

    private void treuPrimer() {
        g.fitxa.primerTempsMs = -1;
        ona.setEditaMarca(false, null);
        pendent = true;
        desaAra();
        mostraRitme();
    }

    private void mostraInfo() {
        int color = Ui.colorMode(g.fitxa);
        xipMode.setBackground(Ui.tocable(color, Ui.dp(this, 18)));
        android.widget.ImageView i = (android.widget.ImageView) xipMode.getChildAt(0);
        i.setImageDrawable(new Icona(Ui.iconaMode(g.fitxa), Ui.SOBRE_COLOR, Ui.dp(this, 18)));
        textMode.setText(g.fitxa.esVeu() ? "Veu" : "Guitarra");
        ona.setColors(color, Ui.LINIA_FORTA);
        if (vistaAnalisi != null) vistaAnalisi.setColor(color);
        info.setText(Biblioteca.data(g.fitxa.data) + "  ·  " + Ui.nomCarpeta(this, g.wav.getParentFile()));
        tempsTotal.setText(Biblioteca.durada(g.fitxa.duradaMs));
        mostraRitme();
    }

    private void canviaMode() {
        g.fitxa.mode = g.fitxa.esVeu() ? Fitxa.GUITARRA : Fitxa.VEU;
        pendent = true;
        desaAra();
        mostraInfo();
    }

    private void menu() {
        desaAra();
        String info = (g.fitxa.esVeu() ? "Veu" : "Guitarra") + "  ·  " + Biblioteca.durada(g.fitxa.duradaMs)
                + "  ·  " + Biblioteca.data(g.fitxa.data);
        Full.accions(this, Ui.iconaMode(g.fitxa), Ui.colorMode(g.fitxa), g.fitxa.titol, info, java.util.Arrays.asList(
                new Full.Accio(Icona.LLAPIS, "Canvia el nom", false, () -> Accions.canviaNom(this, g.wav, () -> {
                    g = Ui.biblioteca(this).gravacio(g.wav);
                    omplint = true;
                    campTitol.setText(g.fitxa.titol);
                    omplint = false;
                })),
                new Full.Accio(Icona.MOURE, "Mou", false, this::mou),
                new Full.Accio(Icona.COMPARTIR, "Comparteix", false, () -> Accions.comparteix(this, g.wav)),
                new Full.Accio(Icona.NOTA, analisi != null ? "Torna a analitzar" : "Analitza notes i acords", false, this::analitza),
                new Full.Accio(Icona.PAPERERA, "Esborra", true, this::esborra)));
    }

    private void mou() {
        desaAra();
        Accions.mou(this, g.wav, nou -> {
            boolean sonava = mp != null && mp.isPlaying();
            int pos = (mp != null && preparat) ? mp.getCurrentPosition() : 0;
            aturaSo();
            g = Ui.biblioteca(this).gravacio(nou);
            preparaSo();
            if (mp != null && preparat) mp.seekTo(pos);
            if (sonava) alternaPlay();
            mostraInfo();
        });
    }

    private void esborra() {
        pausa();
        Accions.esborra(this, g.wav, () -> {
            aturaSo();
            g = null;
            finish();
        });
    }

    // ---------- Anàlisi: notes, acords i tonalitat ----------

    private void posaProgres(float f) {
        ona.setProgres(f);
        if (vistaAnalisi != null) vistaAnalisi.setProgres(f);
    }

    /** Omple la fila de sota: el botó «Analitza», el progrés, o la tonalitat amb Notes / Acords. */
    private void mostraAnalisi() {
        if (zonaAnalisi == null) return;
        zonaAnalisi.removeAllViews();
        if (analitzant) {
            vistaAnalisi.setVisibility(View.GONE);
            TextView t = Ui.negreta(this, "Analitzant notes i acords… " + percentAnalisi + " %", 14, Ui.SUAU);
            t.setGravity(Gravity.CENTER);
            t.setMinHeight(Ui.dp(this, 40));
            zonaAnalisi.addView(t, Ui.pes(1));
            return;
        }
        if (analisi == null) {
            vistaAnalisi.setVisibility(View.GONE);
            LinearLayout b = Ui.pastilla(this, Icona.NOTA, "Analitza notes, acords i tonalitat", Ui.SUPERFICIE, Ui.TEXT);
            b.setOnClickListener(v -> analitza());
            zonaAnalisi.addView(b, Ui.pes(1));
            return;
        }
        vistaAnalisi.setVisibility(View.VISIBLE);
        vistaAnalisi.setColor(Ui.colorMode(g.fitxa));
        vistaAnalisi.posa(analisi, analisi.duradaMs > 0 ? analisi.duradaMs : durada());
        vistaAnalisi.setAcords(analisi.vistaAcords);

        LinearLayout col = Ui.columna(this);
        col.addView(Ui.text(this, "Tonalitat", 11, Ui.SUAU));
        String to = analisi.textTo() + (analisi.dubtosa() ? "  ·  o " + analisi.nomTo2() : "");
        col.addView(Ui.cartell(Ui.negreta(this, to, 15, Ui.TEXT)));
        zonaAnalisi.addView(col, Ui.pes(1));
        zonaAnalisi.addView(Ui.espai(this, 8));
        LinearLayout seg = Ui.segmentat(this, new String[]{"Notes", "Acords"}, analisi.vistaAcords ? 1 : 0,
                Ui.colorMode(g.fitxa), idx -> {
                    analisi.vistaAcords = idx == 1;
                    vistaAnalisi.setAcords(analisi.vistaAcords);
                    desaAnalisi();
                });
        zonaAnalisi.addView(seg, new LinearLayout.LayoutParams(Ui.dp(this, 156), LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private void analitza() {
        if (analitzant || g == null) return;
        if (g.fitxa.duradaMs > 10 * 60 * 1000L) {
            Ui.avis(this, "Massa llarga per analitzar (màxim 10 minuts)");
            return;
        }
        analitzant = true;
        percentAnalisi = 0;
        mostraAnalisi();
        File wav = g.wav;
        boolean veu = g.fitxa.esVeu();
        double bpm = g.fitxa.bpm;
        long primer = g.fitxa.primerTempsMs;
        String titol = g.fitxa.titol;
        new Thread(() -> {
            Analisi.Resultat r = null;
            String error = null;
            try {
                Wav.Info info = Wav.llegeixInfo(wav);
                short[] m = info == null ? null : Wav.llegeixMostres(wav);
                if (m == null) error = "No s'ha pogut llegir la gravació";
                else r = Analisi.analitza(m, info.mostreig > 0 ? info.mostreig : Sinte.MOSTREIG, veu, bpm, primer, f -> {
                    int p = (int) Math.round(f * 100);
                    if (p != percentAnalisi) runOnUiThread(() -> {
                        percentAnalisi = p;
                        if (analitzant && !tancant) mostraAnalisi();
                    });
                    return !tancant;
                });
                if (r != null) Analisi.desa(wav, r);
            } catch (IOException e) {
                // l'anàlisi es mostra igualment, encara que no s'hagi pogut desar
            } catch (OutOfMemoryError e) {
                r = null;
                error = "No hi ha prou memòria per analitzar-la";
            }
            Analisi.Resultat fet = r;
            String err = error;
            runOnUiThread(() -> {
                if (tancant) return;
                analitzant = false;
                if (fet != null) {
                    analisi = fet;
                    Ui.historial(this, "Analitzada «" + titol + "»: " + fet.nomTo() + ", "
                            + fet.acords.size() + " acords, " + fet.notes.size() + " notes");
                } else if (err != null) {
                    Ui.avis(this, err);
                }
                mostraAnalisi();
            });
        }).start();
    }

    private void desaAnalisi() {
        if (analisi == null || g == null) return;
        try {
            Analisi.desa(g.wav, analisi);
        } catch (IOException ignored) {
        }
    }

    // ---------- So ----------

    private void preparaSo() {
        try {
            mp = new MediaPlayer();
            mp.setDataSource(g.wav.getAbsolutePath());
            mp.setOnCompletionListener(m -> {
                if (bucle) return;
                Ui.canviaIcona(botoPlay, Icona.PLAY, Ui.SOBRE_COLOR, 28);
                posaProgres(1f);
                tempsAra.setText(Biblioteca.durada(m.getDuration()));
            });
            mp.prepare();
            mp.setLooping(bucle);
            preparat = true;
            if (textTempo != null) mostraRitme();
            posaProgres(0);
            tempsTotal.setText(Biblioteca.durada(mp.getDuration()));
            // si la fitxa no tenia la durada (gravació recuperada), l'apunta
            if (g.fitxa.duradaMs <= 0 && mp.getDuration() > 0) {
                g.fitxa.duradaMs = mp.getDuration();
                pendent = true;
                desaAra();
            }
        } catch (IOException | RuntimeException e) {
            preparat = false;
            tempsAra.setText("No puc reproduir aquesta gravació");
            botoPlay.setEnabled(false);
            botoPlay.setAlpha(0.4f);
        }
    }

    private void alternaPlay() {
        if (mp == null || !preparat) return;
        if (mp.isPlaying()) {
            pausa();
            return;
        }
        int pos = mp.getCurrentPosition();
        if (pos >= mp.getDuration() - 80) {
            mp.seekTo(0);
        } else if (retrocedeix.isChecked() && pos > 0 && !saltat) {
            mp.seekTo(Math.max(0, pos - 2000));
        }
        saltat = false;
        mp.start();
        Ui.canviaIcona(botoPlay, Icona.PAUSA, Ui.SOBRE_COLOR, 28);
        h.removeCallbacks(tic);
        h.post(tic);
    }

    private void pausa() {
        if (mp == null || !preparat) return;
        if (mp.isPlaying()) mp.pause();
        Ui.canviaIcona(botoPlay, Icona.PLAY, Ui.SOBRE_COLOR, 28);
        h.removeCallbacks(tic);
        int pos = mp.getCurrentPosition();
        posaProgres(pos / (float) Math.max(1, mp.getDuration()));
        tempsAra.setText(Biblioteca.durada(pos));
    }

    private void salta(int ms) {
        if (mp == null || !preparat) return;
        int pos = Math.max(0, Math.min(mp.getDuration(), mp.getCurrentPosition() + ms));
        mp.seekTo(pos);
        saltat = true;
        posaProgres(pos / (float) Math.max(1, mp.getDuration()));
        tempsAra.setText(Biblioteca.durada(pos));
    }

    private void alternaBucle() {
        bucle = !bucle;
        if (mp != null && preparat) mp.setLooping(bucle);
        botoBucle.setBackground(Ui.tocable(bucle ? Ui.ACCENT : Ui.SUPERFICIE, Ui.dp(this, 26)));
        Ui.colorIcona(botoBucle, bucle ? 0xFFFFFFFF : Ui.TEXT);
    }

    private void aturaSo() {
        h.removeCallbacks(tic);
        if (mp != null) {
            try {
                if (mp.isPlaying()) mp.stop();
            } catch (IllegalStateException ignored) {
            }
            mp.release();
            mp = null;
            preparat = false;
        }
    }

    // ---------- Desar ----------

    private void desaAra() {
        h.removeCallbacks(desa);
        if (g == null || !pendent || !g.wav.exists()) return;
        String t = campTitol.getText().toString().trim();
        String vell = g.fitxa.titol;
        boolean lletraCanviada = !g.fitxa.lletra.equals(campLletra.getText().toString());
        g.fitxa.titol = t.isEmpty() ? Biblioteca.titolPerDefecte(g.fitxa.data) : t;
        g.fitxa.lletra = campLletra.getText().toString();
        g.fitxa.notes = campNotes.getText().toString();
        try {
            g.desa();
            pendent = false;
            if (!vell.equals(g.fitxa.titol)) Ui.historial(this, "«" + vell + "» ara es diu «" + g.fitxa.titol + "»");
            if (lletraCanviada && !lletraApuntada) {
                lletraApuntada = true;
                Ui.historial(this, "Lletra de «" + g.fitxa.titol + "» actualitzada");
            }
        } catch (IOException e) {
            Ui.avis(this, "No s'ha pogut desar.");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mp != null && mp.isPlaying()) pausa();
        desaAra();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        tancant = true;
        aturaSo();
        h.removeCallbacksAndMessages(null);
    }
}
