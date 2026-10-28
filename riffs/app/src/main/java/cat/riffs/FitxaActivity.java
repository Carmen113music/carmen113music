package cat.riffs;

import android.app.Activity;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;

/**
 * Fitxa d'una gravació: escoltar-la, posar-hi títol i escriure'n la lletra i notes.
 * El reproductor queda sempre a dalt, també quan surt el teclat.
 */
public class FitxaActivity extends Activity {

    public static final String EXTRA_WAV = "wav";

    private final Handler h = new Handler(Looper.getMainLooper());
    private Biblioteca.Gravacio g;

    private EditText campTitol, campLletra, campNotes;
    private TextView info, temps;
    private Button botoPlay, botoMode, botoBucle;
    private SeekBar barra;
    private CheckBox pausaEscrivint, retrocedeix;

    private MediaPlayer mp;
    private boolean preparat = false;
    private boolean movent = false;   // l'usuari arrossega la barra
    private boolean omplint = false;  // canvis de text fets per l'app, no per l'usuari
    private boolean pendent = false;  // hi ha canvis per desar
    private boolean lletraApuntada = false; // ja s'ha apuntat a l'historial en aquesta visita

    private final Runnable desa = this::desaAra;

    private final Runnable tic = new Runnable() {
        @Override
        public void run() {
            if (mp != null && preparat && !movent) {
                int pos = mp.getCurrentPosition();
                barra.setProgress(pos);
                mostraTemps(pos);
            }
            if (mp != null && mp.isPlaying()) h.postDelayed(this, 100);
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
    }

    private void construeix() {
        LinearLayout arrel = Ui.columna(this);
        arrel.setBackgroundColor(Ui.FONS);
        arrel.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), 0);

        botoMode = Ui.boto(this, "", Ui.TARGETA, Ui.TEXT);
        botoMode.setOnClickListener(v -> canviaMode());
        arrel.addView(Ui.capcalera(this, "", botoMode));

        // ---- Reproductor (fix a dalt) ----
        LinearLayout rep = Ui.targeta(this);
        barra = new SeekBar(this);
        barra.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int pos, boolean usuari) {
                if (usuari) mostraTemps(pos);
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
                movent = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                movent = false;
                if (mp != null && preparat) mp.seekTo(s.getProgress());
            }
        });
        rep.addView(barra);
        temps = Ui.text(this, "0:00 / 0:00", 13, Ui.SUAU);
        temps.setGravity(Gravity.CENTER);
        rep.addView(temps);

        LinearLayout controls = Ui.fila(this);
        controls.setGravity(Gravity.CENTER);
        Button enrere5 = Ui.boto(this, "⏪ 5 s", Ui.LINIA, Ui.TEXT);
        enrere5.setOnClickListener(v -> salta(-5000));
        controls.addView(enrere5);
        controls.addView(Ui.espai(this, 12));
        botoPlay = Ui.boto(this, "▶", Ui.ACCENT, 0xFFFFFFFF);
        botoPlay.setTextSize(24);
        botoPlay.setOnClickListener(v -> alternaPlay());
        controls.addView(botoPlay, new LinearLayout.LayoutParams(Ui.dp(this, 84), Ui.dp(this, 56)));
        controls.addView(Ui.espai(this, 12));
        botoBucle = Ui.boto(this, "🔁", Ui.LINIA, Ui.TEXT);
        botoBucle.setContentDescription("Repeteix");
        botoBucle.setOnClickListener(v -> alternaBucle());
        controls.addView(botoBucle);
        rep.addView(controls, Ui.ample(this, 6));
        arrel.addView(rep, Ui.ample(this, 6));

        // ---- La resta, amb desplaçament ----
        ScrollView scroll = new ScrollView(this);
        LinearLayout cos = Ui.columna(this);
        cos.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 24));

        campTitol = Ui.camp(this, "Títol", false);
        campTitol.setTextSize(20);
        campTitol.setTypeface(Typeface.DEFAULT_BOLD);
        cos.addView(campTitol, Ui.ample(this, 10));

        info = Ui.text(this, "", 13, Ui.SUAU);
        info.setPadding(Ui.dp(this, 4), 0, 0, 0);
        cos.addView(info, Ui.ample(this, 6));

        pausaEscrivint = new CheckBox(this);
        pausaEscrivint.setText("Atura el so quan escric");
        pausaEscrivint.setTextColor(Ui.TEXT);
        pausaEscrivint.setChecked(Ui.prefs(this).getBoolean("pausa_escrivint", true));
        pausaEscrivint.setOnCheckedChangeListener((b, v) -> Ui.prefs(this).edit().putBoolean("pausa_escrivint", v).apply());
        cos.addView(pausaEscrivint, Ui.ample(this, 8));

        retrocedeix = new CheckBox(this);
        retrocedeix.setText("En tornar a ▶, retrocedeix 2 s");
        retrocedeix.setTextColor(Ui.TEXT);
        retrocedeix.setChecked(Ui.prefs(this).getBoolean("retrocedeix", true));
        retrocedeix.setOnCheckedChangeListener((b, v) -> Ui.prefs(this).edit().putBoolean("retrocedeix", v).apply());
        cos.addView(retrocedeix);

        cos.addView(Ui.text(this, "Lletra", 13, Ui.SUAU), Ui.ample(this, 10));
        campLletra = Ui.camp(this, "Escolta i escriu aquí la lletra…", true);
        campLletra.setMinLines(7);
        campLletra.setLineSpacing(0, 1.15f);
        cos.addView(campLletra, Ui.ample(this, 4));

        cos.addView(Ui.text(this, "Notes (acords, to, idees…)", 13, Ui.SUAU), Ui.ample(this, 14));
        campNotes = Ui.camp(this, "Per exemple: Re menor, capo 2. Pont per a «La del tren».", true);
        campNotes.setMinLines(3);
        cos.addView(campNotes, Ui.ample(this, 4));

        LinearLayout accions = Ui.fila(this);
        Button mou = Ui.boto(this, "📁 Mou", Ui.TARGETA, Ui.TEXT);
        mou.setOnClickListener(v -> {
            desaAra();
            Accions.mou(this, g.wav, nou -> {
                g = Ui.biblioteca(this).gravacio(nou);
                mostraInfo();
            });
        });
        Button comp = Ui.boto(this, "↗ Comparteix", Ui.TARGETA, Ui.TEXT);
        comp.setOnClickListener(v -> {
            desaAra();
            Accions.comparteix(this, g.wav);
        });
        Button esb = Ui.boto(this, "🗑", Ui.TARGETA, Ui.ACCENT);
        esb.setContentDescription("Esborra");
        esb.setOnClickListener(v -> {
            aturaSo();
            Accions.esborra(this, g.wav, () -> {
                g = null;
                finish();
            });
        });
        accions.addView(mou, Ui.pes(1));
        accions.addView(Ui.espai(this, 8));
        accions.addView(comp, Ui.pes(1.4f));
        accions.addView(Ui.espai(this, 8));
        accions.addView(esb);
        cos.addView(accions, Ui.ample(this, 18));

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

    private void mostraInfo() {
        boolean veu = g.fitxa.esVeu();
        botoMode.setText(veu ? "🎤 Veu" : "🎸 Guitarra");
        botoMode.setBackground(Ui.rodo(veu ? Ui.VEU : Ui.GUITARRA, Ui.dp(this, 22)));
        botoMode.setTextColor(0xFF1A1714);
        info.setText(Biblioteca.data(g.fitxa.data) + "  ·  " + Biblioteca.durada(g.fitxa.duradaMs)
                + "  ·  " + Ui.nomCarpeta(this, g.wav.getParentFile()));
    }

    private void canviaMode() {
        g.fitxa.mode = g.fitxa.esVeu() ? Fitxa.GUITARRA : Fitxa.VEU;
        pendent = true;
        desaAra();
        mostraInfo();
    }

    // ---------- So ----------

    private void preparaSo() {
        try {
            mp = new MediaPlayer();
            mp.setDataSource(g.wav.getAbsolutePath());
            mp.setOnCompletionListener(m -> {
                if (m.isLooping()) return;
                botoPlay.setText("▶");
                barra.setProgress(barra.getMax());
                mostraTemps(barra.getMax());
            });
            mp.prepare();
            preparat = true;
            barra.setMax(Math.max(1, mp.getDuration()));
            mostraTemps(0);
            // si la fitxa no tenia la durada (gravació recuperada), l'apunta
            if (g.fitxa.duradaMs <= 0 && mp.getDuration() > 0) {
                g.fitxa.duradaMs = mp.getDuration();
                pendent = true;
                desaAra();
                mostraInfo();
            }
        } catch (IOException | RuntimeException e) {
            preparat = false;
            temps.setText("No puc reproduir aquesta gravació");
            botoPlay.setEnabled(false);
        }
    }

    private void alternaPlay() {
        if (mp == null || !preparat) return;
        if (mp.isPlaying()) {
            pausa();
        } else {
            int pos = mp.getCurrentPosition();
            if (pos >= mp.getDuration() - 50) {
                mp.seekTo(0);
            } else if (retrocedeix.isChecked() && pos > 0) {
                mp.seekTo(Math.max(0, pos - 2000));
            }
            mp.start();
            botoPlay.setText("⏸");
            h.removeCallbacks(tic);
            h.post(tic);
        }
    }

    private void pausa() {
        if (mp == null || !preparat) return;
        if (mp.isPlaying()) mp.pause();
        botoPlay.setText("▶");
        h.removeCallbacks(tic);
        mostraTemps(mp.getCurrentPosition());
        barra.setProgress(mp.getCurrentPosition());
    }

    private void salta(int ms) {
        if (mp == null || !preparat) return;
        int pos = Math.max(0, Math.min(mp.getDuration(), mp.getCurrentPosition() + ms));
        mp.seekTo(pos);
        barra.setProgress(pos);
        mostraTemps(pos);
    }

    private void alternaBucle() {
        if (mp == null || !preparat) return;
        boolean b = !mp.isLooping();
        mp.setLooping(b);
        botoBucle.setBackground(Ui.rodo(b ? Ui.ACCENT : Ui.LINIA, Ui.dp(this, 22)));
        Ui.avis(this, b ? "Repetició activada" : "Repetició desactivada");
    }

    private void mostraTemps(int pos) {
        int total = (mp != null && preparat) ? mp.getDuration() : 0;
        temps.setText(Biblioteca.durada(pos) + " / " + Biblioteca.durada(total));
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
            if (!vell.equals(g.fitxa.titol)) Ui.historial(this, "✎ «" + vell + "» ara es diu «" + g.fitxa.titol + "»");
            if (lletraCanviada && !lletraApuntada) {
                lletraApuntada = true;
                Ui.historial(this, "✎ Lletra de «" + g.fitxa.titol + "» actualitzada");
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
