package cat.riffs;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * L'Estudi multipista d'una carpeta (sempre en horitzontal).
 * A l'esquerra, la capçalera de cada pista (nom, M, S i volum); a la dreta, la regla i els clips.
 */
public class ProjecteActivity extends Activity {

    public static final String EXTRA_DIR = "dir";

    private Projecte projecte;
    private final Vista vista = new Vista();
    private final Map<String, short[]> audio = new HashMap<>();
    private Motor motor;
    private boolean carregat = false;

    private RegleView regle;
    private PistesView pistes;
    private LinearLayout capcaleres;
    private FrameLayout botoPlay;
    private TextView textPosicio, textInfo, textTempo;
    private int altPista;

    private final Runnable segueix = new Runnable() {
        @Override
        public void run() {
            if (motor == null || !motor.sona()) return;
            long ms = motor.posicioMs();
            vista.capcalMs = ms;
            // la vista segueix el capçal
            float w = pistes.getWidth();
            float x = vista.x(ms);
            if (w > 0 && (x > w * 0.85f || x < 0)) {
                vista.iniciMs = ms - (w * 0.15f) / vista.pxPerMs;
            }
            if (ms > projecte.finalMs() + 800) {
                atura();
                return;
            }
            refresca();
            pistes.postOnAnimation(this);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setNavigationBarColor(Ui.FONS);
        String d = getIntent().getStringExtra(EXTRA_DIR);
        Biblioteca b = Ui.biblioteca(this);
        File dir = d == null ? null : new File(d);
        if (dir == null || !dir.isDirectory() || !b.esDins(dir) || dir.equals(b.arrel)) {
            Ui.avis(this, "Aquesta carpeta ja no hi és.");
            finish();
            return;
        }
        projecte = Projecte.obre(dir);
        if (projecte.sincronitza(b)) desa();
        vista.snap = Ui.prefs(this).getInt("snap", Projecte.SNAP_TEMPS);
        altPista = Ui.dp(this, 78);
        construeix();
        carrega();
    }

    private void construeix() {
        LinearLayout arrel = Ui.pantalla(this);

        // ---- Barra de dalt ----
        LinearLayout dalt = Ui.fila(this);
        dalt.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 4));
        FrameLayout enrere = Ui.botoIcona(this, Icona.ENRERE, Ui.TEXT, 0x00000000, 44, 24, "Enrere");
        enrere.setOnClickListener(v -> finish());
        dalt.addView(enrere);
        TextView nom = Ui.unaLinia(Ui.negreta(this, projecte.carpeta.getName(), 17, Ui.TEXT));
        nom.setMaxWidth(Ui.dp(this, 150));
        dalt.addView(nom);
        dalt.addView(Ui.espai(this, 12));

        LinearLayout xipTempo = Ui.fila(this);
        xipTempo.setClickable(true);
        xipTempo.setBackground(Ui.tocable(Ui.SUPERFICIE, Ui.dp(this, 16)));
        xipTempo.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 12), 0);
        xipTempo.setMinimumHeight(Ui.dp(this, 34));
        xipTempo.addView(Ui.imatge(this, Icona.METRONOM, Ui.SUAU, 15), new LinearLayout.LayoutParams(Ui.dp(this, 16), Ui.dp(this, 16)));
        xipTempo.addView(Ui.espai(this, 6));
        textTempo = Ui.negreta(this, "", 13, Ui.TEXT);
        xipTempo.addView(textTempo);
        xipTempo.setOnClickListener(v -> editaTempo());
        dalt.addView(xipTempo);
        dalt.addView(Ui.espai(this, 10));

        LinearLayout snap = Ui.segmentat(this, new String[]{"Compàs", "Temps", "Lliure"}, vista.snap, Ui.TEXT, i -> {
            vista.snap = i;
            Ui.prefs(this).edit().putInt("snap", i).apply();
        });
        dalt.addView(snap, new LinearLayout.LayoutParams(Ui.dp(this, 210), LinearLayout.LayoutParams.WRAP_CONTENT));

        dalt.addView(Ui.mollaH(this));
        textInfo = Ui.unaLinia(Ui.text(this, "", 13, Ui.GUITARRA));
        dalt.addView(textInfo);
        dalt.addView(Ui.espai(this, 12));

        textPosicio = Ui.text(this, "1.1.1", 15, Ui.TEXT);
        textPosicio.setTypeface(Ui.MITJA);
        textPosicio.setFontFeatureSettings("tnum");
        textPosicio.setMinWidth(Ui.dp(this, 64));
        textPosicio.setGravity(Gravity.END);
        dalt.addView(textPosicio);
        dalt.addView(Ui.espai(this, 12));

        FrameLayout inici = Ui.botoIcona(this, Icona.ENRERE5, Ui.TEXT, Ui.SUPERFICIE, 40, 20, "Torna a l'inici");
        Ui.canviaIcona(inici, Icona.ENRERE, Ui.TEXT, 20);
        inici.setOnClickListener(v -> {
            boolean sonava = motor != null && motor.sona();
            atura();
            vista.capcalMs = 0;
            vista.iniciMs = -200;
            refresca();
            if (sonava) play();
        });
        dalt.addView(inici);
        dalt.addView(Ui.espai(this, 8));
        botoPlay = Ui.botoIcona(this, Icona.PLAY, Ui.SOBRE_COLOR, Ui.TEXT, 44, 20, "Reprodueix");
        botoPlay.setOnClickListener(v -> {
            if (motor != null && motor.sona()) atura();
            else play();
        });
        dalt.addView(botoPlay);
        arrel.addView(dalt);
        arrel.addView(Ui.separador(this));

        // ---- Regla ----
        int ampleCap = Ui.dp(this, 176);
        LinearLayout filaRegle = Ui.fila(this);
        TextView etPistes = Ui.etiqueta(this, projecte.pistes.size() == 1 ? "1 pista" : projecte.pistes.size() + " pistes");
        etPistes.setPadding(Ui.dp(this, 14), 0, 0, 0);
        etPistes.setGravity(Gravity.CENTER_VERTICAL);
        etPistes.setBackgroundColor(Ui.SUPERFICIE);
        filaRegle.addView(etPistes, new LinearLayout.LayoutParams(ampleCap, Ui.dp(this, 30)));
        regle = new RegleView(this, projecte, vista);
        regle.setOientCapcal((ms, acabat) -> {
            vista.capcalMs = ms;
            refresca();
            if (acabat && motor != null && motor.sona()) play();
        });
        filaRegle.addView(regle, new LinearLayout.LayoutParams(0, Ui.dp(this, 30), 1f));
        arrel.addView(filaRegle);

        // ---- Capçaleres + pistes (amb desplaçament vertical si n'hi ha moltes) ----
        // El desplaçament vertical només es fa amb dos dits (des de les pistes): un dit és per editar
        ScrollView scroll = new ScrollView(this) {
            @Override
            public boolean onInterceptTouchEvent(android.view.MotionEvent ev) {
                return false;
            }

            @Override
            public boolean onTouchEvent(android.view.MotionEvent ev) {
                return false;
            }
        };
        scroll.setVerticalScrollBarEnabled(true);
        scroll.setFillViewport(true);
        LinearLayout fila = Ui.fila(this);
        fila.setGravity(Gravity.TOP);
        capcaleres = Ui.columna(this);
        capcaleres.setBackgroundColor(Ui.SUPERFICIE);
        fila.addView(capcaleres, new LinearLayout.LayoutParams(ampleCap, LinearLayout.LayoutParams.WRAP_CONTENT));
        pistes = new PistesView(this, projecte, vista, altPista);
        pistes.setOient(new PistesView.Oient() {
            @Override
            public void editat(boolean acabat) {
                if (acabat) desa();
                regle.invalidate();
            }

            @Override
            public void capcal(long ms) {
                vista.capcalMs = ms;
                if (motor != null && motor.sona()) play();
                refresca();
            }

            @Override
            public void info(String text) {
                textInfo.setText(text == null ? "" : text);
            }

            @Override
            public void desplacaVertical(int dy) {
                scroll.scrollBy(0, dy);
            }
        });
        fila.addView(pistes, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        scroll.addView(fila);
        arrel.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        vista.escolta(this::refresca);
        setContentView(arrel);
        ompleCapcaleres();
        mostraTempo();
        refresca();
    }

    // ---------- Capçaleres de pista ----------

    private void ompleCapcaleres() {
        capcaleres.removeAllViews();
        for (int i = 0; i < projecte.pistes.size(); i++) {
            capcaleres.addView(capcalera(i), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, altPista));
        }
        if (projecte.pistes.isEmpty()) {
            TextView t = Ui.text(this, "Aquesta carpeta no té gravacions.", 13, Ui.SUAU);
            t.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 8), 0);
            capcaleres.addView(t, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, altPista));
        }
    }

    private View capcalera(int i) {
        Projecte.Pista t = projecte.pistes.get(i);
        int color = t.veu ? Ui.VEU : Ui.GUITARRA;
        LinearLayout fora = Ui.fila(this);
        fora.setGravity(Gravity.TOP);
        View franja = new View(this);
        franja.setBackgroundColor(color);
        fora.addView(franja, new LinearLayout.LayoutParams(Ui.dp(this, 4), LinearLayout.LayoutParams.MATCH_PARENT));

        LinearLayout c = Ui.columna(this);
        c.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 4));
        c.setClickable(true);
        c.setOnClickListener(v -> {
            vista.triada = i;
            vista.avisa();
        });

        LinearLayout dalt = Ui.fila(this);
        dalt.addView(Ui.imatge(this, t.veu ? Icona.MIC : Icona.GUITARRA, color, 14), new LinearLayout.LayoutParams(Ui.dp(this, 16), Ui.dp(this, 16)));
        dalt.addView(Ui.espai(this, 6));
        dalt.addView(Ui.unaLinia(Ui.negreta(this, t.nom, 13, Ui.TEXT)), Ui.pes(1));
        c.addView(dalt);

        LinearLayout baix = Ui.fila(this);
        TextView m = botoMS("M", t.mut, Ui.ACCENT);
        TextView s = botoMS("S", t.solo, Ui.GUITARRA);
        m.setOnClickListener(v -> {
            synchronized (projecte) {
                t.mut = !t.mut;
            }
            pintaMS(m, t.mut, Ui.ACCENT);
            desa();
            pistes.invalidate();
        });
        s.setOnClickListener(v -> {
            synchronized (projecte) {
                t.solo = !t.solo;
            }
            pintaMS(s, t.solo, Ui.GUITARRA);
            desa();
            pistes.invalidate();
        });
        baix.addView(m);
        baix.addView(Ui.espai(this, 6));
        baix.addView(s);
        baix.addView(Ui.espai(this, 4));

        // fader: de -∞ (-60) a +6 dB, amb 0 dB al 90%
        SeekBar fader = new SeekBar(this);
        fader.setMax(660);
        fader.setProgress((int) Math.round((t.volumDb + 60) * 10));
        TextView db = Ui.text(this, Projecte.textDb(t.volumDb), 10, Ui.SUAU);
        fader.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar b, int p, boolean usuari) {
                if (!usuari) return;
                double v = p / 10.0 - 60;
                if (Math.abs(v) < 0.8) v = 0;   // s'enganxa a 0 dB
                synchronized (projecte) {
                    t.volumDb = v;
                }
                db.setText(Projecte.textDb(v));
            }

            @Override
            public void onStartTrackingTouch(SeekBar b) {}

            @Override
            public void onStopTrackingTouch(SeekBar b) {
                desa();
            }
        });
        baix.addView(fader, Ui.pes(1));
        c.addView(baix, Ui.ample(this, 6));
        db.setPadding(Ui.dp(this, 64), 0, 0, 0);
        c.addView(db);

        fora.addView(c, Ui.pes(1));
        LinearLayout amb = Ui.columna(this);
        amb.addView(fora, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        amb.addView(Ui.separador(this));
        return amb;
    }

    private TextView botoMS(String s, boolean actiu, int color) {
        TextView t = Ui.negreta(this, s, 12, Ui.TEXT);
        t.setGravity(Gravity.CENTER);
        t.setClickable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 28), Ui.dp(this, 24));
        t.setLayoutParams(lp);
        pintaMS(t, actiu, color);
        return t;
    }

    private void pintaMS(TextView t, boolean actiu, int color) {
        t.setBackground(Ui.tocable(actiu ? color : Ui.SUPERFICIE2, Ui.dp(this, 6)));
        t.setTextColor(actiu ? Ui.SOBRE_COLOR : Ui.SUAU);
    }

    // ---------- Tempo ----------

    private void mostraTempo() {
        textTempo.setText(Math.round(projecte.bpm) + " BPM · " + Ritme.nomCompas(projecte.compas));
    }

    private void editaTempo() {
        Fitxa f = new Fitxa();
        f.bpm = projecte.bpm;
        f.compas = projecte.compas;
        Full.tempo(this, f, (bpm, compas) -> {
            if (bpm <= 0) return;   // un projecte sempre té tempo
            synchronized (projecte) {
                projecte.bpm = bpm;
                projecte.compas = compas;
            }
            desa();
            mostraTempo();
            refresca();
        });
    }

    // ---------- So ----------

    private void carrega() {
        textInfo.setText("Carregant les pistes…");
        new Thread(() -> {
            Map<String, short[]> llegit = new HashMap<>();
            for (Projecte.Pista t : projecte.pistes) {
                File f = new File(projecte.carpeta, t.wav);
                short[] so = Wav.llegeixMostres(f);
                if (so != null) llegit.put(t.wav, so);
                float[] pics = Ona.pics(f);
                if (pics != null) runOnUiThread(() -> pistes.posaOna(t.wav, pics));
            }
            runOnUiThread(() -> {
                audio.putAll(llegit);
                carregat = true;
                textInfo.setText("");
                motor = new Motor(projecte, audio);
            });
        }).start();
    }

    private void play() {
        if (!carregat || motor == null) return;
        if (!motor.comenca(vista.capcalMs)) {
            Ui.avis(this, "No s'ha pogut obrir la sortida de so.");
            return;
        }
        Ui.canviaIcona(botoPlay, Icona.PAUSA, Ui.SOBRE_COLOR, 20);
        pistes.removeCallbacks(segueix);
        pistes.postOnAnimation(segueix);
    }

    private void atura() {
        if (motor != null && motor.sona()) {
            vista.capcalMs = motor.posicioMs();
            motor.atura();
        }
        pistes.removeCallbacks(segueix);
        Ui.canviaIcona(botoPlay, Icona.PLAY, Ui.SOBRE_COLOR, 20);
        refresca();
    }

    // ---------- Comú ----------

    private void refresca() {
        if (regle == null) return;
        regle.invalidate();
        pistes.invalidate();
        textPosicio.setText(textPosicio(vista.capcalMs));
    }

    /** «3.2.1» (compàs.temps.semicorxera) a la manera dels DAW. */
    private String textPosicio(long ms) {
        double mpt = projecte.msPerTemps();
        double temps = ms / mpt;
        long t = (long) Math.floor(temps + 1e-9);
        long comp = t / projecte.compas + 1;
        long dins = t % projecte.compas + 1;
        long sub = (long) Math.floor((temps - t) * 4) + 1;
        return String.format(Locale.ROOT, "%d.%d.%d", comp, dins, Math.min(4, sub));
    }

    private void desa() {
        try {
            synchronized (projecte) {
                projecte.desa();
            }
        } catch (IOException e) {
            Ui.avis(this, "No s'ha pogut desar el projecte.");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (projecte != null) {
            atura();
            desa();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (motor != null) motor.atura();
    }
}
