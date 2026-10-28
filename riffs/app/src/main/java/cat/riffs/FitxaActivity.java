package cat.riffs;

import android.app.Activity;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
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
    private boolean saltat = false;         // l'usuari ha triat on reprendre: no cal retrocedir
    private boolean omplint = false;        // canvis de text fets per l'app, no per l'usuari
    private boolean pendent = false;        // hi ha canvis per desar
    private boolean lletraApuntada = false; // ja s'ha apuntat a l'historial en aquesta visita

    private final Runnable desa = this::desaAra;

    private final Runnable tic = new Runnable() {
        @Override
        public void run() {
            if (mp != null && preparat) {
                int pos = mp.getCurrentPosition();
                ona.setProgres(pos / (float) Math.max(1, mp.getDuration()));
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
        new Thread(() -> {
            float[] pics = Ona.pics(g.wav);
            runOnUiThread(() -> ona.setPics(pics));
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
        ona.setOientSalt((f, acabat) -> {
            if (mp == null || !preparat) return;
            int pos = Math.round(f * mp.getDuration());
            tempsAra.setText(Biblioteca.durada(pos));
            if (acabat) {
                mp.seekTo(pos);
                saltat = true;
            }
        });
        rep.addView(ona, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 88)));

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

        omplint = true;
        campTitol.setText(g.fitxa.titol);
        campLletra.setText(g.fitxa.lletra);
        campNotes.setText(g.fitxa.notes);
        omplint = false;
        mostraInfo();

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

    private void mostraInfo() {
        int color = Ui.colorMode(g.fitxa);
        xipMode.setBackground(Ui.tocable(color, Ui.dp(this, 18)));
        android.widget.ImageView i = (android.widget.ImageView) xipMode.getChildAt(0);
        i.setImageDrawable(new Icona(Ui.iconaMode(g.fitxa), Ui.SOBRE_COLOR, Ui.dp(this, 18)));
        textMode.setText(g.fitxa.esVeu() ? "Veu" : "Guitarra");
        ona.setColors(color, Ui.LINIA_FORTA);
        info.setText(Biblioteca.data(g.fitxa.data) + "  ·  " + Ui.nomCarpeta(this, g.wav.getParentFile()));
        tempsTotal.setText(Biblioteca.durada(g.fitxa.duradaMs));
    }

    private void canviaMode() {
        g.fitxa.mode = g.fitxa.esVeu() ? Fitxa.GUITARRA : Fitxa.VEU;
        pendent = true;
        desaAra();
        mostraInfo();
    }

    private void menu() {
        Ui.dialeg(this)
                .setItems(new String[]{"Mou a una carpeta", "Comparteix", "Esborra"}, (d, i) -> {
                    if (i == 0) mou();
                    else if (i == 1) {
                        desaAra();
                        Accions.comparteix(this, g.wav);
                    } else esborra();
                })
                .show();
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

    // ---------- So ----------

    private void preparaSo() {
        try {
            mp = new MediaPlayer();
            mp.setDataSource(g.wav.getAbsolutePath());
            mp.setOnCompletionListener(m -> {
                if (bucle) return;
                Ui.canviaIcona(botoPlay, Icona.PLAY, Ui.SOBRE_COLOR, 28);
                ona.setProgres(1f);
                tempsAra.setText(Biblioteca.durada(m.getDuration()));
            });
            mp.prepare();
            mp.setLooping(bucle);
            preparat = true;
            ona.setProgres(0);
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
        ona.setProgres(pos / (float) Math.max(1, mp.getDuration()));
        tempsAra.setText(Biblioteca.durada(pos));
    }

    private void salta(int ms) {
        if (mp == null || !preparat) return;
        int pos = Math.max(0, Math.min(mp.getDuration(), mp.getCurrentPosition() + ms));
        mp.seekTo(pos);
        saltat = true;
        ona.setProgres(pos / (float) Math.max(1, mp.getDuration()));
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
        aturaSo();
        h.removeCallbacksAndMessages(null);
    }
}
