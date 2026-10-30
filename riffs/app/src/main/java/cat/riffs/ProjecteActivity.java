package cat.riffs;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
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
import java.util.ArrayList;
import java.util.List;
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
    private FrameLayout botoClic;
    private boolean clicActiu = true;
    private float volumClic = 1f;
    private LinearLayout accions;
    private TextView textTriats, etPistes;

    // gravar des de l'Estudi
    private static final int PERMIS_MIC = 7;
    private final Gravadora gravadora = new Gravadora();
    private boolean gravant = false;
    private File wavGravant;
    private Fitxa fitxaGravant;
    private Projecte.Pista pistaGravant;     // null = pista nova
    private long entradaMs;
    private double retardViu = Double.NaN;   // retard de la sortida actual (per dibuixar l'ona en directe)
    private AudioManager gestorSo;
    private AudioDeviceCallback vigilant;
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
            if (gravant) {
                // compte enrere d'un compàs i després «Gravant»
                if (ms < entradaMs) {
                    long falten = (long) Math.ceil((entradaMs - ms) / projecte.msPerTemps());
                    textInfo.setTextColor(Ui.GUITARRA);
                    textInfo.setText("Compte: " + Math.max(1, falten));
                } else {
                    textInfo.setTextColor(Ui.ACCENT);
                    textInfo.setText("● Gravant");
                }
            } else if (ms > projecte.finalMs() + 800) {
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
        altPista = Ui.dp(this, Ui.prefs(this).getInt("alt_pista_dp", 78));
        clicActiu = Ui.prefs(this).getBoolean("estudi_clic", true);
        volumClic = Ui.prefs(this).getFloat("estudi_clic_vol", 1f);
        construeix();
        carrega();
    }

    private void construeix() {
        LinearLayout arrel = Ui.pantalla(this);

        // ---- Barra de dalt ----
        LinearLayout dalt = Ui.fila(this);
        dalt.setPadding(Ui.dp(this, 2), Ui.dp(this, 1), Ui.dp(this, 10), Ui.dp(this, 1));
        FrameLayout enrere = Ui.botoIcona(this, Icona.ENRERE, Ui.TEXT, 0x00000000, 34, 20, "Enrere");
        enrere.setOnClickListener(v -> finish());
        dalt.addView(enrere);
        TextView nom = Ui.unaLinia(Ui.negreta(this, projecte.carpeta.getName(), 15, Ui.TEXT));
        nom.setMaxWidth(Ui.dp(this, 150));
        dalt.addView(nom);
        dalt.addView(Ui.espai(this, 12));

        // Pastilla del tempo: la icona encén o apaga el clic (mantenir premut = volum); el text canvia el tempo
        LinearLayout xipTempo = Ui.fila(this);
        xipTempo.setBackground(Ui.rodo(Ui.SUPERFICIE, Ui.dp(this, 16)));
        xipTempo.setMinimumHeight(Ui.dp(this, 28));
        botoClic = new FrameLayout(this);
        botoClic.setClickable(true);
        botoClic.setContentDescription("Clic del metrònom (mantén premut per al volum)");
        botoClic.addView(Ui.imatge(this, Icona.METRONOM, Ui.SUAU, 15),
                new FrameLayout.LayoutParams(Ui.dp(this, 16), Ui.dp(this, 16), Gravity.CENTER));
        botoClic.setOnClickListener(v -> {
            clicActiu = !clicActiu;
            Ui.prefs(this).edit().putBoolean("estudi_clic", clicActiu).apply();
            pintaClic();
            aplicaClic();
            textInfo.setTextColor(Ui.GUITARRA);
            textInfo.setText(clicActiu ? "Clic activat" : "Clic apagat");
        });
        botoClic.setOnLongClickListener(v -> {
            Full.lliscador(this, "Volum del clic", Math.round(volumClic * 100), 150, val -> {
                volumClic = val / 100f;
                if (motor != null) motor.posaVolumClic(volumClic);
                Ui.prefs(this).edit().putFloat("estudi_clic_vol", volumClic).apply();
            }, val -> val + " %");
            return true;
        });
        xipTempo.addView(botoClic, new LinearLayout.LayoutParams(Ui.dp(this, 34), Ui.dp(this, 28)));
        textTempo = Ui.negreta(this, "", 13, Ui.TEXT);
        textTempo.setGravity(Gravity.CENTER_VERTICAL);
        textTempo.setPadding(Ui.dp(this, 2), 0, Ui.dp(this, 12), 0);
        textTempo.setMinHeight(Ui.dp(this, 28));
        textTempo.setClickable(true);
        textTempo.setBackground(Ui.tocable(0x00000000, Ui.dp(this, 14)));
        textTempo.setOnClickListener(v -> editaTempo());
        xipTempo.addView(textTempo);
        dalt.addView(xipTempo);
        pintaClic();
        dalt.addView(Ui.espai(this, 10));

        LinearLayout snap = Ui.segmentat(this, new String[]{"Compàs", "Temps", "Lliure"}, vista.snap, Ui.TEXT, i -> {
            vista.snap = i;
            Ui.prefs(this).edit().putInt("snap", i).apply();
        });
        snap.setPadding(Ui.dp(this, 2), Ui.dp(this, 2), Ui.dp(this, 2), Ui.dp(this, 2));
        for (int k = 0; k < snap.getChildCount(); k++) ((TextView) snap.getChildAt(k)).setMinHeight(Ui.dp(this, 26));
        dalt.addView(snap, new LinearLayout.LayoutParams(Ui.dp(this, 200), LinearLayout.LayoutParams.WRAP_CONTENT));

        dalt.addView(Ui.mollaH(this));

        // accions dels clips triats (només es veuen si n'hi ha)
        accions = Ui.fila(this);
        textTriats = Ui.text(this, "", 12, Ui.SUAU);
        accions.addView(textTriats);
        accions.addView(Ui.espai(this, 6));
        FrameLayout duplica = Ui.botoIcona(this, Icona.DUPLICA, Ui.TEXT, Ui.SUPERFICIE, 32, 18, "Duplica");
        duplica.setOnClickListener(v -> duplica());
        accions.addView(duplica);
        accions.addView(Ui.espai(this, 6));
        FrameLayout esborra = Ui.botoIcona(this, Icona.PAPERERA, Ui.ACCENT, Ui.SUPERFICIE, 32, 18, "Treu del projecte");
        esborra.setOnClickListener(v -> esborra());
        accions.addView(esborra);
        accions.addView(Ui.espai(this, 12));
        accions.setVisibility(View.GONE);
        dalt.addView(accions);

        textInfo = Ui.unaLinia(Ui.text(this, "", 13, Ui.GUITARRA));
        dalt.addView(textInfo);
        dalt.addView(Ui.espai(this, 12));

        textPosicio = Ui.text(this, "1.1.1", 14, Ui.TEXT);
        textPosicio.setTypeface(Ui.MITJA);
        textPosicio.setFontFeatureSettings("tnum");
        textPosicio.setMinWidth(Ui.dp(this, 64));
        textPosicio.setGravity(Gravity.END);
        dalt.addView(textPosicio);
        dalt.addView(Ui.espai(this, 12));

        FrameLayout inici = Ui.botoIcona(this, Icona.ENRERE5, Ui.TEXT, Ui.SUPERFICIE, 32, 18, "Torna a l'inici");
        Ui.canviaIcona(inici, Icona.ENRERE, Ui.TEXT, 18);
        inici.setOnClickListener(v -> {
            if (gravant) return;
            boolean sonava = motor != null && motor.sona();
            atura();
            vista.capcalMs = 0;
            vista.iniciMs = -200;
            refresca();
            if (sonava) play();
        });
        dalt.addView(inici);
        dalt.addView(Ui.espai(this, 8));
        botoPlay = Ui.botoIcona(this, Icona.PLAY, Ui.SOBRE_COLOR, Ui.TEXT, 36, 18, "Reprodueix (mantén premut per gravar)");
        botoPlay.setOnClickListener(v -> {
            if (gravant) aturaGravacio(null);
            else if (motor != null && motor.sona()) atura();
            else play();
        });
        // mantenir premut el play: gravar
        botoPlay.setOnLongClickListener(v -> {
            if (!gravant) triaPistaGravar();
            return true;
        });
        dalt.addView(botoPlay);
        arrel.addView(dalt);
        arrel.addView(Ui.separador(this));

        // ---- Regla ----
        int ampleCap = Ui.dp(this, 176);
        LinearLayout filaRegle = Ui.fila(this);
        etPistes = Ui.etiqueta(this, "");
        posaEtiquetaPistes();
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

            @Override
            public void bandera(String wav) {
                desaBandera(wav);
            }

            @Override
            public void seleccio() {
                actualitzaAccions();
            }

            @Override
            public void alcada(int px) {
                altPista = px;
                aplicaAlcada();
                Ui.prefs(ProjecteActivity.this).edit()
                        .putInt("alt_pista_dp", Math.round(px / getResources().getDisplayMetrics().density)).apply();
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
        aplicaAlcada();
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
            if (t.clips.isEmpty()) {
                // pista buida: es pot tornar a posar la gravació sencera
                Full.confirma(this, "Tornar a posar «" + t.nomVisible() + "»?",
                        "La pista no té cap clip. Es tornarà a posar la gravació sencera.", "Posa", false, () -> {
                            synchronized (projecte) {
                                t.clips.add(projecte.clipSencer(t.wav));
                            }
                            vista.tria(i, t.clips.get(0));
                            desa();
                            actualitzaAccions();
                            vista.avisa();
                            refresca();
                        });
                return;
            }
            // tocar la capçalera tria tots els clips de la pista
            vista.tria(-1, null);
            if (!t.clips.isEmpty()) {
                vista.triada = i;
                vista.triats.addAll(t.clips);
            }
            actualitzaAccions();
            vista.avisa();
        });

        arrossegable(c, i);

        LinearLayout dalt = Ui.fila(this);
        dalt.addView(Ui.imatge(this, t.veu ? Icona.MIC : Icona.GUITARRA, color, 14), new LinearLayout.LayoutParams(Ui.dp(this, 16), Ui.dp(this, 16)));
        dalt.addView(Ui.espai(this, 6));
        dalt.addView(Ui.unaLinia(Ui.negreta(this, t.nomVisible(), 13, Ui.TEXT)), Ui.pes(1));
        c.addView(dalt);

        LinearLayout baix = Ui.fila(this);
        TextView m = botoMS("M", t.mut, Ui.ACCENT);
        TextView s = botoMS("S", t.solo, Ui.GUITARRA);
        FrameLayout rec = new FrameLayout(this);
        rec.setClickable(true);
        rec.setContentDescription("Pista on es grava");
        rec.addView(Ui.imatge(this, Icona.REC, Ui.SUAU, 14),
                new FrameLayout.LayoutParams(Ui.dp(this, 16), Ui.dp(this, 16), Gravity.CENTER));
        rec.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, 28), Ui.dp(this, 24)));
        pintaRec(rec, t.rec);
        rec.setOnClickListener(v -> {
            synchronized (projecte) {
                projecte.armaRec(t.rec ? null : t);
            }
            desa();
            // repinta les R de totes les pistes (només n'hi pot haver una)
            for (int k = 0; k < capcaleres.getChildCount(); k++) {
                View r = capcaleres.getChildAt(k).findViewWithTag("rec");
                if (r instanceof FrameLayout && k < projecte.pistes.size()) {
                    pintaRec((FrameLayout) r, projecte.pistes.get(k).rec);
                }
            }
            textInfo.setText(t.rec ? "Es gravarà a «" + t.nomVisible() + "»" : "");
        });
        rec.setTag("rec");
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
        baix.addView(Ui.espai(this, 6));
        baix.addView(rec);
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
        db.setTag("db");
        c.addView(db);

        fora.addView(c, Ui.pes(1));
        LinearLayout amb = Ui.columna(this);
        amb.addView(fora, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        amb.addView(Ui.separador(this));
        return amb;
    }

    /** Posa l'alçada de cada capçalera igual que la de les pistes (zoom vertical). */
    private void aplicaAlcada() {
        boolean petita = altPista < Ui.dp(this, 74);
        for (int k = 0; k < capcaleres.getChildCount(); k++) {
            View v = capcaleres.getChildAt(k);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) v.getLayoutParams();
            lp.height = altPista;
            v.setLayoutParams(lp);
            View db = v.findViewWithTag("db");
            if (db != null) db.setVisibility(petita ? View.GONE : View.VISIBLE);
        }
    }

    // ---------- Ordenar i canviar el nom de les pistes ----------

    private int pistaArrossegada = -1;
    private float yArrossega0;
    private int destiPista;

    /**
     * Capçalera: mantenir premut 0,4 s (vibra) i arrossegar amunt o avall mou la pista.
     * Si es deixa anar sense moure, surten les opcions (canviar el nom).
     */
    private void arrossegable(View c, int i) {
        final boolean[] actiu = {false};
        final float[] y0 = {0};
        final int marge = Ui.dp(this, 8);
        Runnable premut = () -> {
            actiu[0] = true;
            pistaArrossegada = i;
            destiPista = i;
            c.setPressed(false);
            c.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            View cap = capcaleres.getChildAt(i);
            cap.setElevation(Ui.dp(this, 8));
            cap.setAlpha(0.92f);
            cap.setBackgroundColor(Ui.SUPERFICIE2);
            textInfo.setText("Arrossega per moure la pista");
        };
        c.setOnTouchListener((v, ev) -> {
            switch (ev.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    actiu[0] = false;
                    y0[0] = ev.getRawY();
                    v.postDelayed(premut, 400);
                    return false;
                case android.view.MotionEvent.ACTION_MOVE:
                    if (!actiu[0]) {
                        if (Math.abs(ev.getRawY() - y0[0]) > marge) v.removeCallbacks(premut);
                        return false;
                    }
                    float dy = ev.getRawY() - y0[0];
                    View cap = capcaleres.getChildAt(i);
                    cap.setTranslationY(dy);
                    int desti = Math.max(0, Math.min(projecte.pistes.size() - 1, Math.round(i + dy / altPista)));
                    if (desti != destiPista) {
                        destiPista = desti;
                        textInfo.setText(desti == i ? "Arrossega per moure la pista" : "Posició " + (desti + 1));
                        // les altres capçaleres s'aparten per fer-li lloc
                        for (int k = 0; k < projecte.pistes.size(); k++) {
                            if (k == i) continue;
                            float t = 0;
                            if (i < desti && k > i && k <= desti) t = -altPista;
                            if (desti < i && k >= desti && k < i) t = altPista;
                            capcaleres.getChildAt(k).animate().translationY(t).setDuration(120).start();
                        }
                    }
                    pistes.setArrossegant(i, dy, destiPista);
                    return true;
                case android.view.MotionEvent.ACTION_UP:
                    v.removeCallbacks(premut);
                    if (!actiu[0]) return false;
                    acabaArrossegarPista(i, Math.abs(ev.getRawY() - y0[0]) > marge);
                    return true;
                case android.view.MotionEvent.ACTION_CANCEL:
                    v.removeCallbacks(premut);
                    if (actiu[0]) acabaArrossegarPista(i, false);
                    return false;
                default:
                    return actiu[0];
            }
        });
    }

    private void acabaArrossegarPista(int i, boolean mogut) {
        pistaArrossegada = -1;
        textInfo.setText("");
        pistes.setArrossegant(-1, 0, -1);
        // tot torna a la seva posició visual (si s'ha mogut, es reconstrueix tot seguit)
        for (int k = 0; k < capcaleres.getChildCount(); k++) {
            View cap = capcaleres.getChildAt(k);
            cap.animate().cancel();
            cap.setTranslationY(0);
            cap.setElevation(0);
            cap.setAlpha(1f);
            cap.setBackgroundColor(0x00000000);
        }
        final int desti = destiPista;
        // No es poden treure capçaleres mentre Android encara reparteix el toc del dit
        // (l'app es tancava): es reconstrueix just després.
        capcaleres.post(() -> {
            if (mogut && desti != i) {
                mouPistaA(i, desti);
            } else if (!mogut) {
                opcionsPista(i);
            }
        });
    }

    private void opcionsPista(int i) {
        if (i < 0 || i >= projecte.pistes.size()) return;
        Projecte.Pista t = projecte.pistes.get(i);
        List<Full.Accio> ops = new ArrayList<>();
        ops.add(new Full.Accio(Icona.LLAPIS, "Canvia el nom", false, () ->
                Full.demanaText(this, "Nom de la pista", t.nomVisible(), "Desa", text -> {
                    String n = text == null ? "" : text.trim();
                    synchronized (projecte) {
                        // si és igual que el de la gravació, no cal nom propi
                        t.nomPropi = n.equals(t.nom) ? "" : n;
                    }
                    desa();
                    ompleCapcaleres();
                    pistes.invalidate();
                })));
        if (t.nomPropi != null && !t.nomPropi.trim().isEmpty()) {
            ops.add(new Full.Accio(Icona.HISTORIAL, "Nom de la gravació", false, () -> {
                synchronized (projecte) {
                    t.nomPropi = "";
                }
                desa();
                ompleCapcaleres();
                pistes.invalidate();
            }));
        }
        if (i > 0) {
            ops.add(new Full.Accio(Icona.PUJA, "Puja", false, () -> mouPistaA(i, i - 1)));
        }
        if (i < projecte.pistes.size() - 1) {
            ops.add(new Full.Accio(Icona.BAIXA, "Baixa", false, () -> mouPistaA(i, i + 1)));
        }
        Full.accions(this, t.veu ? Icona.MIC : Icona.GUITARRA, t.veu ? Ui.VEU : Ui.GUITARRA, t.nomVisible(),
                "Mantén premut i arrossega per moure-la", ops);
    }

    private void mouPistaA(int de, int a) {
        synchronized (projecte) {
            projecte.mouPista(de, a);
        }
        vista.tria(-1, null);
        actualitzaAccions();
        desa();
        ompleCapcaleres();
        vista.avisa();
        refresca();
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

    /** Botó de gravar de la pista: cercle gris, o vermell quan és la pista on es grava. */
    private void pintaRec(FrameLayout b, boolean actiu) {
        b.setBackground(Ui.tocable(actiu ? Ui.alfa(Ui.ACCENT, 70) : Ui.SUPERFICIE2, Ui.dp(this, 6)));
        Ui.colorIcona(b, actiu ? Ui.ACCENT : Ui.SUAU);
    }

    private void pintaMS(TextView t, boolean actiu, int color) {
        t.setBackground(Ui.tocable(actiu ? color : Ui.SUPERFICIE2, Ui.dp(this, 6)));
        t.setTextColor(actiu ? Ui.SOBRE_COLOR : Ui.SUAU);
    }

    // ---------- Clips triats ----------

    private void actualitzaAccions() {
        int n = vista.triats.size();
        accions.setVisibility(n > 0 ? View.VISIBLE : View.GONE);
        textTriats.setText(n == 1 ? "1 clip" : n + " clips");
    }

    /** Duplica els clips triats just a continuació, i queden triats els nous (tornant a tocar, el loop creix). */
    private void duplica() {
        int i = vista.triada;
        if (i < 0 || i >= projecte.pistes.size() || vista.triats.isEmpty()) return;
        Projecte.Pista t = projecte.pistes.get(i);
        java.util.List<Projecte.Clip> nous;
        synchronized (projecte) {
            nous = Projecte.duplica(t, new java.util.ArrayList<>(vista.triats));
        }
        vista.triats.clear();
        vista.triats.addAll(nous);
        desa();
        actualitzaAccions();
        textInfo.setText(nous.size() == 1 ? "Clip duplicat" : nous.size() + " clips duplicats");
        // que es vegin els nous
        long fi = 0;
        for (Projecte.Clip k : nous) fi = Math.max(fi, k.fiVisible());
        float w = pistes.getWidth();
        if (w > 0 && vista.x(fi) > w) vista.iniciMs = fi - (w * 0.85f) / vista.pxPerMs;
        vista.avisa();
        refresca();
    }

    /** Treu els clips triats del projecte (la gravació no es toca). */
    private void esborra() {
        int i = vista.triada;
        if (i < 0 || i >= projecte.pistes.size() || vista.triats.isEmpty()) return;
        Projecte.Pista t = projecte.pistes.get(i);
        int n = vista.triats.size();
        Runnable fes = () -> {
            synchronized (projecte) {
                Projecte.esborra(t, new java.util.ArrayList<>(vista.triats));
            }
            vista.tria(-1, null);
            desa();
            actualitzaAccions();
            textInfo.setText(n == 1 ? "Clip tret" : n + " clips trets");
            vista.avisa();
            refresca();
        };
        if (n == 1) {
            fes.run();
        } else {
            Full.confirma(this, "Treure " + n + " clips?",
                    "Només es treuen del projecte. La gravació no s'esborra i continua a la biblioteca.",
                    "Treu", true, fes);
        }
    }

    /** Desa la bandera nova a la fitxa de la gravació (és d'ella, no només del projecte). */
    private void desaBandera(String wav) {
        Projecte.Presa pr = projecte.preses.get(wav);
        if (pr == null) return;
        File fx = Biblioteca.fitxaDe(new File(projecte.carpeta, wav));
        Fitxa f = Fitxa.llegeix(fx);
        long abans = f.primerTempsMs;
        f.primerTempsMs = pr.primerMs;
        try {
            f.desa(fx);
            Ui.historial(this, "Bandera de «" + pr.nom + "» moguda a l'Estudi ("
                    + String.format(Locale.ROOT, "%+d ms", pr.primerMs - abans) + ")");
        } catch (IOException e) {
            Ui.avis(this, "No s'ha pogut desar la bandera.");
        }
    }

    // ---------- Clic del metrònom ----------

    private void pintaClic() {
        if (botoClic == null) return;
        Ui.colorIcona(botoClic, clicActiu ? Ui.GUITARRA : Ui.APAGAT);
        botoClic.setBackground(Ui.tocable(clicActiu ? Ui.alfa(Ui.GUITARRA, 40) : 0x00000000, Ui.dp(this, 14)));
    }

    /**
     * Fa sonar el clic segons l'interruptor. Mentre es grava, només amb auriculars
     * (per l'altaveu quedaria gravat).
     */
    private void aplicaClic() {
        if (motor == null) return;
        motor.posaVolumClic(volumClic);
        motor.posaClic(clicActiu && (!gravant || Metronom.auriculars(this)));
    }

    // ---------- Gravar des de l'Estudi ----------

    /** Mantenir premut el play: tria on es grava (una pista o una de nova). */
    private void triaPistaGravar() {
        if (!carregat || motor == null) return;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, PERMIS_MIC);
            return;
        }
        atura();
        Projecte.Pista armada = projecte.pistaRec();
        if (armada != null) {
            comencaGravacio(armada, armada.veu);
            return;
        }
        long entrada = projecte.entradaGravacio(vista.capcalMs);
        long compas = Math.round(entrada / projecte.msPerCompas()) + 1;
        String sub = "Entra al compàs " + compas + ", amb un compàs de compte abans. La pista que triïs queda marcada "
                + "amb el botó de gravar vermell: la propera vegada no et preguntarà (toca'l per canviar-la).";
        if (!Metronom.auriculars(this)) {
            sub += " Sense auriculars no sonarà res (per l'altaveu es gravaria): el compte serà visual.";
        }
        List<Full.Accio> ops = new ArrayList<>();
        for (Projecte.Pista t : projecte.pistes) {
            ops.add(new Full.Accio(t.veu ? Icona.MIC : Icona.GUITARRA, t.nomVisible(), false, () -> {
                synchronized (projecte) {
                    projecte.armaRec(t);
                }
                desa();
                ompleCapcaleres();
                comencaGravacio(t, t.veu);
            }));
        }
        ops.add(new Full.Accio(Icona.MES, "Pista nova · guitarra", false, () -> comencaGravacio(null, false)));
        ops.add(new Full.Accio(Icona.MES, "Pista nova · veu", false, () -> comencaGravacio(null, true)));
        Full.llista(this, "Gravar a…", sub, ops);
    }

    private void comencaGravacio(Projecte.Pista pista, boolean veu) {
        if (gravant || motor == null) return;
        atura();
        entradaMs = projecte.entradaGravacio(vista.capcalMs);
        long iniciMs = Math.max(0, entradaMs - Math.round(projecte.msPerCompas()));
        Biblioteca b = Ui.biblioteca(this);
        long ara = System.currentTimeMillis();
        wavGravant = b.nouWav(projecte.carpeta, ara);
        fitxaGravant = new Fitxa();
        fitxaGravant.mode = veu ? Fitxa.VEU : Fitxa.GUITARRA;
        fitxaGravant.data = ara;
        fitxaGravant.titol = Biblioteca.titolPerDefecte(ara);
        fitxaGravant.bpm = projecte.bpm;
        fitxaGravant.compas = projecte.compas;
        fitxaGravant.claqueta = true;
        try {
            fitxaGravant.desa(Biblioteca.fitxaDe(wavGravant));
        } catch (IOException e) {
            Ui.avis(this, "No puc desar al mòbil. Potser està ple?");
            return;
        }
        pistaGravant = pista;
        String font = Ui.prefs(this).getString("font", Gravadora.AUTO);
        // Igual que a Gravar: primer el micròfon i just després el so
        String error = gravadora.comenca(this, wavGravant, font, new Gravadora.Oient() {
            @Override
            public void nivell(float pic) {
                // l'ona es dibuixa en directe on sona ara (el que entra pel micròfon és el que se sent)
                runOnUiThread(() -> {
                    if (!gravant || motor == null || !motor.sona()) return;
                    if (Double.isNaN(retardViu)) {
                        String t = motor.tipusSortida();
                        if (t == null) return;   // encara no se sap per on sona
                        double r = DiagnosticActivity.retardDesat(ProjecteActivity.this, t, motor.nomSortida());
                        retardViu = Double.isNaN(r) ? 0 : r;
                    }
                    // el que entra ara pel micròfon és el que ha sonat fa «retard» ms
                    pistes.afegeixViva(Math.round(motor.posicioMs() - retardViu), pic);
                });
            }

            @Override
            public void error(String missatge) {
                runOnUiThread(() -> aturaGravacio(missatge));
            }
        });
        if (error != null) {
            b.esborra(wavGravant);
            Ui.avis(this, error);
            return;
        }
        // Mentre es grava, res no pot sonar per l'altaveu (es gravaria): sense auriculars, silenci total
        gravant = true;
        aplicaClic();
        gravant = false;
        motor.posaSilenci(!Metronom.auriculars(this));
        if (!Metronom.auriculars(this)) Ui.avis(this, "Sense auriculars: no sonarà res, el compte és visual");
        if (!motor.comenca(iniciMs)) {
            gravadora.atura();
            b.esborra(wavGravant);
            motor.posaClic(false);
            Ui.avis(this, "No s'ha pogut obrir la sortida de so.");
            return;
        }
        vigilaAuriculars();
        gravant = true;
        // ona en directe: a la pista triada o en una fila nova a sota (amb la seva capçalera provisional)
        int fila = pista != null ? projecte.pistes.indexOf(pista) : projecte.pistes.size();
        if (pista == null) {
            TextView nova = Ui.negreta(this, "Pista nova · gravant", 13, Ui.ACCENT);
            nova.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 8), 0);
            nova.setTag("viva");
            capcaleres.addView(nova, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, altPista));
        }
        retardViu = Double.NaN;
        pistes.comencaViva(fila);
        vista.tria(-1, null);
        actualitzaAccions();
        vista.capcalMs = iniciMs;
        Ui.canviaIcona(botoPlay, Icona.STOP, Ui.ACCENT, 18);
        pistes.removeCallbacks(segueix);
        pistes.postOnAnimation(segueix);
    }

    /** Si es desconnecten els auriculars mentre es grava, el clic s'apaga perquè no passi a l'altaveu. */
    private void vigilaAuriculars() {
        gestorSo = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (gestorSo == null) return;
        vigilant = new AudioDeviceCallback() {
            @Override
            public void onAudioDevicesRemoved(AudioDeviceInfo[] trets) {
                if (gravant && motor != null && !Metronom.auriculars(ProjecteActivity.this)) {
                    motor.posaClic(false);
                    motor.posaSilenci(true);
                    Ui.avis(ProjecteActivity.this, "Auriculars desconnectats: s'ha silenciat el so perquè no es gravi");
                }
            }
        };
        gestorSo.registerAudioDeviceCallback(vigilant, new android.os.Handler(android.os.Looper.getMainLooper()));
    }

    private void aturaGravacio(String motiu) {
        if (!gravant) return;
        gravant = false;
        // on ha quedat el primer temps (l'entrada) dins la gravació, abans d'aturar res
        long nano = motor.nanoDeLinia(entradaMs);
        String sortida = motor.tipusSortida();
        String sortidaNom = motor.nomSortida();
        long primer = gravadora.msDelMoment(nano);
        long correccio = 0;
        double r = DiagnosticActivity.retardDesat(this, sortida, sortidaNom);
        if (!Double.isNaN(r)) {
            correccio = Math.round(r);
            primer += correccio;
        }
        long durada = gravadora.atura();
        motor.posaClic(false);
        motor.atura();
        motor.posaSilenci(false);
        pistes.aturaViva();
        View provisional = capcaleres.findViewWithTag("viva");
        if (provisional != null) capcaleres.removeView(provisional);
        if (gestorSo != null && vigilant != null) {
            gestorSo.unregisterAudioDeviceCallback(vigilant);
            vigilant = null;
        }
        pistes.removeCallbacks(segueix);
        Ui.canviaIcona(botoPlay, Icona.PLAY, Ui.SOBRE_COLOR, 18);
        textInfo.setTextColor(Ui.GUITARRA);
        File wav = wavGravant;
        wavGravant = null;
        Biblioteca b = Ui.biblioteca(this);
        if (durada < primer + 300) {
            b.esborra(wav);
            textInfo.setText(motiu != null ? motiu : "Aturat abans d'entrar: no s'ha desat");
            vista.capcalMs = entradaMs;
            refresca();
            return;
        }
        fitxaGravant.duradaMs = durada;
        fitxaGravant.primerTempsMs = primer;
        try {
            fitxaGravant.desa(Biblioteca.fitxaDe(wav));
        } catch (IOException ignored) {
            // el so ja és desat
        }
        boolean nova = pistaGravant == null;
        Projecte.Clip clip;
        synchronized (projecte) {
            clip = projecte.afegeixPresa(pistaGravant, wav.getName(), fitxaGravant.titol, fitxaGravant.esVeu(),
                    durada, primer, entradaMs);
        }
        if (nova) {
            synchronized (projecte) {
                projecte.armaRec(projecte.pistes.get(projecte.pistes.size() - 1));
            }
        }
        desa();
        int ip = projecte.pistaDe(clip);
        String nomPista = ip >= 0 ? projecte.pistes.get(ip).nomVisible() : "";
        long compas = Math.round(entradaMs / projecte.msPerCompas()) + 1;
        Ui.historial(this, "Gravat a l'Estudi «" + fitxaGravant.titol + "» (" + Biblioteca.durada(durada) + ") a "
                + (nova ? "una pista nova" : "la pista «" + nomPista + "»") + ", entra al compàs " + compas
                + (correccio != 0 ? " · retard corregit " + correccio + " ms (" + Retards.nomVisible(sortida, sortidaNom) + ")" : ""));
        if (nova) {
            ompleCapcaleres();
            posaEtiquetaPistes();
            pistes.requestLayout();
        }
        vista.tria(ip, clip);
        actualitzaAccions();
        vista.capcalMs = entradaMs;
        textInfo.setText(motiu != null ? motiu : "Desada");
        // carrega el so nou per poder-lo escoltar
        new Thread(() -> {
            short[] so = Wav.llegeixMostres(wav);
            float[] pics = Ona.pics(wav);
            runOnUiThread(() -> {
                if (so != null) {
                    synchronized (projecte) {
                        audio.put(wav.getName(), so);
                    }
                }
                if (pics != null) pistes.posaOna(wav.getName(), pics);
            });
        }).start();
        vista.avisa();
        refresca();
    }

    private void posaEtiquetaPistes() {
        int n = projecte.pistes.size();
        etPistes.setText(n == 1 ? "1 pista" : n + " pistes");
    }

    @Override
    public void onRequestPermissionsResult(int codi, String[] permisos, int[] resultats) {
        super.onRequestPermissionsResult(codi, permisos, resultats);
        if (codi == PERMIS_MIC && resultats.length > 0 && resultats[0] == PackageManager.PERMISSION_GRANTED) {
            triaPistaGravar();
        } else if (codi == PERMIS_MIC) {
            Ui.avis(this, "Cal permís de micròfon per gravar.");
        }
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
            java.util.Set<String> wavs;
            synchronized (projecte) {
                wavs = new java.util.LinkedHashSet<>(projecte.presesEnUs());
                for (Projecte.Pista t : projecte.pistes) wavs.add(t.wav);
            }
            for (String w : wavs) {
                File f = new File(projecte.carpeta, w);
                short[] so = Wav.llegeixMostres(f);
                if (so != null) llegit.put(w, so);
                float[] pics = Ona.pics(f);
                if (pics != null) runOnUiThread(() -> pistes.posaOna(w, pics));
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
        if (!carregat || motor == null || gravant) return;
        motor.posaSilenci(false);
        aplicaClic();
        if (!motor.comenca(vista.capcalMs)) {
            Ui.avis(this, "No s'ha pogut obrir la sortida de so.");
            return;
        }
        Ui.canviaIcona(botoPlay, Icona.PAUSA, Ui.SOBRE_COLOR, 18);
        pistes.removeCallbacks(segueix);
        pistes.postOnAnimation(segueix);
    }

    private void atura() {
        if (motor != null && motor.sona()) {
            vista.capcalMs = motor.posicioMs();
            motor.atura();
        }
        pistes.removeCallbacks(segueix);
        Ui.canviaIcona(botoPlay, Icona.PLAY, Ui.SOBRE_COLOR, 18);
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
        if (gravant) aturaGravacio("S'ha aturat la gravació (l'app ha passat a segon pla)");
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
