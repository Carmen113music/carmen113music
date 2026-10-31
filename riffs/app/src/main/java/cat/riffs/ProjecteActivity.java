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
    private static final int DRUMPAD = 31;
    private static final int PIANO_ROLL = 32;
    private static final int TEREMIN = 33;
    private Motor motor;
    private boolean carregat = false;

    private RegleView regle;
    private PistesView pistes;
    private LinearLayout capcaleres;
    private FrameLayout botoPlay;
    private TextView textPosicio, textInfo, textTempo, textSnap;
    private LinearLayout botoSnap;
    private FrameLayout botoClic;
    private FrameLayout pestanyaPistes, pestanyaTaula;
    private TextView nomTitol;
    private LinearLayout vistaPistes, filaTaula;
    private android.widget.HorizontalScrollView vistaTaula;
    private final List<FaderView> faders = new ArrayList<>();
    private final List<TextView> textosPic = new ArrayList<>();
    private boolean taulaVisible = false;
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
    // gravar el sinte en directe (teclat virtual)
    private boolean gravantSinte = false;
    private Projecte.Pista pistaSinte;
    private GravacioNotes notesGravades;
    private Gest.Gravacio gestGravat;      // teremin
    private SinteViu sinteViu;
    private MidiViu midiViu;                // pista MIDI (polifònic)
    private boolean assaig = false;         // el teclat obert per tocar sense gravar
    private View panellTeclat;
    private TextView textTeclat;
    // desfer
    private final Desfes desfes = new Desfes();
    private boolean desfent = false;
    private FrameLayout botoDesfes;
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
                    textInfo.setText(!gravantSinte ? "● Gravant" : pistaSinte != null && pistaSinte.esTeremin() ? "● Gravant el teremin" : "● Gravant el sinte");
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
        BancActivity.prepara(this);
        boolean hiHaMidi = false;
        for (Projecte.Pista t : projecte.pistes) if (t.esMidi()) hiHaMidi = true;
        if (hiHaMidi) new Thread(Banc::actual, "banc").start();   // que estigui llest quan calgui
        desfes.inici(projecte.foto());
        construeix();
        pintaDesfes();
        carrega();
    }

    private void construeix() {
        LinearLayout arrel = Ui.pantalla(this);

        // ---- Barra de dalt ----
        LinearLayout dalt = Ui.fila(this);
        dalt.setPadding(Ui.dp(this, 2), Ui.dp(this, 1), Ui.dp(this, 10), Ui.dp(this, 1));
        FrameLayout enrere = Ui.botoIcona(this, Icona.ENRERE, Ui.TEXT, 0x00000000, 34, 20, "Enrere");
        // amb el teclat o el teremin oberts, la fletxa tanca el panell (i no surt del projecte)
        enrere.setOnClickListener(v -> {
            if (panellTeclat != null) tancaPanell();
            else finish();
        });
        dalt.addView(enrere);
        TextView nom = Ui.unaLinia(Ui.negreta(this, projecte.carpeta.getName(), 15, Ui.TEXT));
        nomTitol = nom;
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

        // Graella (snap): una icona; en tocar-la surt el desplegable Compàs / Temps / Lliure
        botoSnap = Ui.fila(this);
        botoSnap.setClickable(true);
        botoSnap.setContentDescription("Graella");
        botoSnap.setBackground(Ui.tocable(Ui.SUPERFICIE, Ui.dp(this, 14)));
        botoSnap.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 10), 0);
        botoSnap.setMinimumHeight(Ui.dp(this, 28));
        botoSnap.addView(Ui.imatge(this, Icona.GRAELLA, Ui.TEXT, 16), new LinearLayout.LayoutParams(Ui.dp(this, 16), Ui.dp(this, 16)));
        botoSnap.addView(Ui.espai(this, 5));
        textSnap = Ui.negreta(this, "", 12, Ui.TEXT);
        botoSnap.addView(textSnap);
        botoSnap.setOnClickListener(v -> desplegableSnap(v));
        dalt.addView(botoSnap);
        pintaSnap();
        dalt.addView(Ui.espai(this, 8));

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

        // El text d'informació ocupa només l'espai que sobra: mai no pot empènyer els botons fora de la pantalla
        textInfo = Ui.cartell(Ui.text(this, "", 13, Ui.GUITARRA));
        textInfo.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        dalt.addView(textInfo, dalt.indexOfChild(accions), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        dalt.addView(Ui.espai(this, 12));

        botoDesfes = Ui.botoIcona(this, Icona.DESFES, Ui.TEXT, Ui.SUPERFICIE, 32, 18, "Desfés l'últim canvi");
        botoDesfes.setOnClickListener(v -> desfes());
        dalt.addView(botoDesfes);
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

        // ---- A l'esquerra, la tira de pestanyes: Pistes (taulell) i Taula (de so) ----
        LinearLayout cosH = Ui.fila(this);
        cosH.setGravity(Gravity.TOP);
        LinearLayout tira = Ui.columna(this);
        tira.setBackgroundColor(Ui.SUPERFICIE);
        tira.setPadding(0, Ui.dp(this, 6), 0, 0);
        tira.setGravity(Gravity.CENTER_HORIZONTAL);
        pestanyaPistes = Ui.botoIcona(this, Icona.PISTES, Ui.TEXT, 0x00000000, 36, 20, "Pistes");
        pestanyaTaula = Ui.botoIcona(this, Icona.TAULA, Ui.SUAU, 0x00000000, 36, 20, "Taula de so");
        pestanyaPistes.setOnClickListener(v -> mostraTaula(false));
        pestanyaTaula.setOnClickListener(v -> mostraTaula(true));
        tira.addView(pestanyaPistes);
        tira.addView(Ui.espai(this, 6));
        tira.addView(pestanyaTaula);
        cosH.addView(tira, new LinearLayout.LayoutParams(Ui.dp(this, 44), LinearLayout.LayoutParams.MATCH_PARENT));
        View sepTira = new View(this);
        sepTira.setBackgroundColor(Ui.LINIA);
        cosH.addView(sepTira, new LinearLayout.LayoutParams(1, LinearLayout.LayoutParams.MATCH_PARENT));
        FrameLayout contingut = new FrameLayout(this);
        cosH.addView(contingut, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        vistaPistes = Ui.columna(this);
        contingut.addView(vistaPistes, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        // un dit mou els faders; dos dits desplacen la taula (com al taulell)
        vistaTaula = new android.widget.HorizontalScrollView(this) {
            private float xDits = Float.NaN;

            @Override
            public boolean onInterceptTouchEvent(android.view.MotionEvent ev) {
                return ev.getPointerCount() >= 2;
            }

            @Override
            public boolean onTouchEvent(android.view.MotionEvent ev) {
                switch (ev.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        xDits = Float.NaN;
                        return true;
                    case android.view.MotionEvent.ACTION_POINTER_DOWN:
                    case android.view.MotionEvent.ACTION_POINTER_UP:
                        xDits = Float.NaN;   // torna a agafar referència
                        return true;
                    case android.view.MotionEvent.ACTION_MOVE:
                        if (ev.getPointerCount() < 2) return true;
                        float x = (ev.getX(0) + ev.getX(1)) / 2f;
                        if (!Float.isNaN(xDits)) scrollBy(Math.round(xDits - x), 0);
                        xDits = x;
                        return true;
                    default:
                        xDits = Float.NaN;
                        return true;
                }
            }
        };
        vistaTaula.setHorizontalScrollBarEnabled(true);
        vistaTaula.setFillViewport(true);
        filaTaula = Ui.fila(this);
        filaTaula.setGravity(Gravity.TOP);
        vistaTaula.addView(filaTaula, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT));
        vistaTaula.setVisibility(View.GONE);
        contingut.addView(vistaTaula, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        arrel.addView(cosH, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // ---- Regla ----
        int ampleCap = Ui.dp(this, 170);
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
        vistaPistes.addView(filaRegle);

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

            @Override
            public void finestraBateria(int i, long ms) {
                posaFinestraBateria(i, ms);
            }

            @Override
            public void obreBateria(int i, Projecte.Clip clip) {
                if (Projecte.esNotes(clip.wav)) obrePianoRoll(clip);
                else if (Projecte.esTeremin(clip.wav)) obreTeremin(clip);
                else obreDrumpad(clip);
            }
        });
        fila.addView(pistes, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        scroll.addView(fila);
        vistaPistes.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        vista.escolta(this::refresca);
        setContentView(arrel);
        ompleCapcaleres();
        mostraTaula(Ui.prefs(this).getBoolean("estudi_taula", false));
        ajustaBarra(getResources().getConfiguration());
        mostraTempo();
        refresca();
    }

    // ---------- Capçaleres de pista ----------

    private void ompleCapcaleres() {
        capcaleres.removeAllViews();
        for (int i = 0; i < projecte.pistes.size(); i++) {
            capcaleres.addView(capcalera(i), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, altPista));
        }
        // espai buit amb un «+» sota l'última pista, per afegir-ne una de nova
        LinearLayout mes = Ui.fila(this);
        mes.setGravity(Gravity.CENTER_VERTICAL);
        mes.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 8), 0);
        LinearLayout boto = Ui.pastilla(this, Icona.MES, "Pista nova", Ui.SUPERFICIE2, Ui.SUAU);
        boto.setOnClickListener(v -> afegeixPista(null));
        mes.addView(boto, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(this, 40)));
        mes.setTag("mes");
        capcaleres.addView(mes, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, altPista));
        aplicaAlcada();
    }

    private View capcalera(int i) {
        Projecte.Pista t = projecte.pistes.get(i);
        int color = Ui.colorPista(t);
        LinearLayout fora = Ui.fila(this);
        fora.setGravity(Gravity.TOP);
        View franja = new View(this);
        franja.setBackgroundColor(color);
        fora.addView(franja, new LinearLayout.LayoutParams(Ui.dp(this, 4), LinearLayout.LayoutParams.MATCH_PARENT));

        LinearLayout c = Ui.columna(this);
        c.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 4));
        c.setClickable(true);
        c.setOnClickListener(v -> {
            if (t.esBateria() && t.clips.isEmpty()) {
                textInfo.setTextColor(Ui.BATERIA);
                textInfo.setText("Mantén premut a la fila de la bateria per posar-hi un ritme");
                return;
            }
            if (t.esTeremin() && t.clips.isEmpty()) {
                textInfo.setTextColor(Ui.TEREMIN);
                textInfo.setText("Encén el botó de gravar del teremin i mantén premut el play per tocar-lo");
                return;
            }
            if (t.esNotes() && t.clips.isEmpty()) {
                textInfo.setTextColor(Ui.colorPista(t));
                textInfo.setText("Mantén premut a la fila per posar-hi una frase, o grava-hi amb el teclat (botó de gravar + mantén premut el play)");
                return;
            }
            if (t.clips.isEmpty() && t.esBuida()) {
                textInfo.setTextColor(Ui.GUITARRA);
                textInfo.setText("Pista buida: encén el seu botó de gravar i mantén premut el play");
                return;
            }
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
        TextView num = Ui.negreta(this, String.valueOf(i + 1), 13, Ui.SUAU);
        num.setFontFeatureSettings("tnum");
        num.setMinWidth(Ui.dp(this, 16));
        dalt.addView(num);
        dalt.addView(Ui.espai(this, 4));
        dalt.addView(Ui.imatge(this, Ui.iconaPista(t), color, 14), new LinearLayout.LayoutParams(Ui.dp(this, 16), Ui.dp(this, 16)));
        dalt.addView(Ui.espai(this, 6));
        dalt.addView(Ui.unaLinia(Ui.negreta(this, t.nomVisible(), 13, Ui.TEXT)), Ui.pes(1));
        if (t.esInstrument()) {
            // obre el teclat (o la quadrícula del teremin) per tocar i assajar, sense gravar
            FrameLayout toca = new FrameLayout(this);
            toca.setClickable(true);
            toca.setContentDescription("Toca l'instrument (sense gravar)");
            toca.setBackground(Ui.tocable(Ui.alfa(color, 40), Ui.dp(this, 8)));
            toca.addView(Ui.imatge(this, t.esTeremin() ? Icona.TEREMIN : Icona.SINTE, color, 15),
                    new FrameLayout.LayoutParams(Ui.dp(this, 16), Ui.dp(this, 16), Gravity.CENTER));
            toca.setOnClickListener(v -> obreAssaig(t));
            LinearLayout.LayoutParams lt = new LinearLayout.LayoutParams(Ui.dp(this, 30), Ui.dp(this, 24));
            lt.leftMargin = Ui.dp(this, 4);
            dalt.addView(toca, lt);
        }
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
        });
        rec.setTag("rec");
        if (t.esBateria()) rec.setVisibility(View.INVISIBLE);
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
        if (t.esBateria()) {
            // el ritme de la finestra triada d'aquesta pista, o el de la primera
            Projecte.Clip u = vista.unic();
            Projecte.Clip k = u != null && t.clips.contains(u) ? u : t.clips.isEmpty() ? null : t.clips.get(0);
            if (k != null) ops.add(new Full.Accio(Icona.BATERIA, "Obre el drumpad", false, () -> obreDrumpad(k)));
        }
        if (t.esTeremin()) {
            Projecte.Clip u = vista.unic();
            Projecte.Clip k = u != null && t.clips.contains(u) ? u : t.clips.isEmpty() ? null : t.clips.get(0);
            if (k != null) ops.add(new Full.Accio(Icona.TEREMIN, "Obre el teremin", false, () -> obreTeremin(k)));
        }
        if (t.esNotes()) {
            Projecte.Clip u = vista.unic();
            Projecte.Clip k = u != null && t.clips.contains(u) ? u : t.clips.isEmpty() ? null : t.clips.get(0);
            if (k != null) ops.add(new Full.Accio(Ui.iconaPista(t), "Obre el piano roll", false, () -> obrePianoRoll(k)));
        }
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
        ops.add(new Full.Accio(Icona.PAPERERA, "Esborra la pista", true, () ->
                Full.confirma(this, "Esborrar la pista «" + t.nomVisible() + "»?",
                        "Es treu la pista i els seus clips del projecte. Les gravacions no s'esborren: continuen a la biblioteca.",
                        "Esborra", true, () -> {
                            synchronized (projecte) {
                                projecte.esborraPista(projecte.pistes.indexOf(t));
                            }
                            vista.tria(-1, null);
                            actualitzaAccions();
                            desa();
                            ompleCapcaleres();
                            posaEtiquetaPistes();
                            pistes.requestLayout();
                            vista.avisa();
                            refresca();
                        })));
        Full.accions(this, Ui.iconaPista(t), Ui.colorPista(t),
                (i + 1) + " · " + t.nomVisible(), "Per moure-la, mantén premut i arrossega", ops);
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
            // a la bateria, cada còpia té el seu propi ritme (per poder-la canviar sense tocar l'original)
            for (Projecte.Clip c : nous) {
                if (!Projecte.esGenerat(c.wav)) continue;
                try {
                    c.wav = projecte.copiaBateria(c.wav);
                } catch (IOException e) {
                    Ui.avis(this, Projecte.esSinte(c.wav) ? "No s'ha pogut copiar la frase: la còpia la comparteix"
                            : "No s'ha pogut copiar el ritme: la còpia el comparteix");
                }
            }
        }
        for (Projecte.Clip c : nous) if (Projecte.esGenerat(c.wav)) carregaSo(c.wav);
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

    /** «+»: afegeix una pista buida al final i la deixa a punt per gravar-hi. */
    /** «+ Pista nova»: pregunta de quin tipus ({@code despres} s'executa quan ja s'ha creat). */
    private void afegeixPista(Runnable despres) {
        if (gravant) return;
        List<Full.Accio> ops = new ArrayList<>();
        ops.add(new Full.Accio(Icona.MIC, "Àudio", false, () -> {
            afegeixPistaAudio();
            if (despres != null) despres.run();
        }));
        ops.add(new Full.Accio(Icona.BATERIA, "Bateria", false, () -> {
            creaBateria();
            if (despres != null) despres.run();
        }));
        ops.add(new Full.Accio(Icona.SINTE, "Sinte", false, () -> triaPresetNou(despres)));
        ops.add(new Full.Accio(Icona.POLI, "Poli", false, () -> triaPresetPoli(despres)));
        ops.add(new Full.Accio(Icona.MIDI, "MIDI", false, () -> triaInstrumentNou(despres)));
        ops.add(new Full.Accio(Icona.TEREMIN, "Teremin", false, () -> triaPresetTeremin(despres)));
        Full.llista(this, "Pista nova", "Àudio: per gravar-hi guitarra o veu. Bateria: un ritme fet amb el drumpad i els teus kits. "
                + "Sinte: baixos i melodies amb el piano roll. Poli: sinte polifònic per a pads, tecles i acords (50 sons). MIDI: 128 instruments (piano, cordes…), amb acords. "
                + "Teremin: es toca en directe lliscant el dit.", ops);
    }

    private void afegeixPistaAudio() {
        if (gravant) return;
        Projecte.Pista p;
        synchronized (projecte) {
            p = projecte.pistaBuida();
            projecte.armaRec(p);
        }
        desa();
        ompleCapcaleres();
        posaEtiquetaPistes();
        pistes.requestLayout();
        pistes.invalidate();
        textInfo.setTextColor(Ui.GUITARRA);
        textInfo.setText("«" + p.nomVisible() + "» a punt: mantén premut el play per gravar-hi");
    }

    // ---------- Bateria ----------

    /** Crea una pista de bateria buida (amb un primer ritme bàsic a punt per a la primera finestra). */
    private void creaBateria() {
        List<Kits.Kit> kits = Kits.llista(KitsActivity.arrel(this));
        if (kits.isEmpty()) {
            Full.avis(this, "Encara no tens kits de bateria",
                    "Primer cal baixar-los o importar-los: Gravar → botó d'ajustos → Bateria virtual.",
                    "Obre els kits", () -> startActivity(new android.content.Intent(this, KitsActivity.class)));
            return;
        }
        atura();
        try {
            Patro patro = Patro.nou(kits.get(0), projecte.compas);
            String wav = projecte.wavBateriaLliure();
            patro.desa(Patro.fitxerDe(projecte.carpeta, wav));
            long durada = patro.escriuWav(projecte.carpeta, wav, projecte.bpm, projecte.compas,
                    Patro.lectorSons(KitsActivity.arrel(this), new HashMap<>()));
            synchronized (projecte) {
                projecte.afegeixBateria(wav, durada);
            }
            carregaSo(wav);
        } catch (IOException e) {
            Ui.avis(this, "No s'ha pogut crear la pista de bateria");
            return;
        }
        desa();
        Ui.historial(this, "Pista de bateria nova a «" + projecte.carpeta.getName() + "»");
        ompleCapcaleres();
        posaEtiquetaPistes();
        pistes.requestLayout();
        pistes.invalidate();
        textInfo.setTextColor(Ui.BATERIA);
        textInfo.setText("Mantén premut a la fila de la bateria per posar-hi un ritme");
    }

    /**
     * Mantenir premut un espai buit de la bateria: finestra nova a l'inici d'aquell compàs.
     * Fa servir el primer ritme de la pista si encara no s'ha fet servir; si no, una còpia del ritme
     * de la finestra d'abans (per continuar-lo), que es pot editar sense tocar les altres.
     */
    private void posaFinestraBateria(int i, long ms) {
        if (gravant || i < 0 || i >= projecte.pistes.size()) return;
        Projecte.Pista t = projecte.pistes.get(i);
        if (t.esNotes()) {
            posaFinestraSinte(i, ms);
            return;
        }
        if (t.esTeremin()) {
            textInfo.setTextColor(Ui.TEREMIN);
            textInfo.setText("El teremin no es dibuixa: encén el seu botó de gravar i mantén premut el play per tocar-lo");
            return;
        }
        if (!t.esBateria()) return;
        atura();
        int compas = (int) Math.max(1, Math.floor(ms / projecte.msPerCompas()) + 1);
        String wav;
        long durada;
        try {
            if (!projecte.enUs(t.wav) && new File(projecte.carpeta, t.wav).isFile()) {
                wav = t.wav;
            } else {
                Projecte.Clip abans = Projecte.clipAbans(t, ms);
                Patro p = abans == null ? null : Patro.llegeix(Patro.fitxerDe(projecte.carpeta, abans.wav));
                if (p == null) {
                    List<Kits.Kit> kits = Kits.llista(KitsActivity.arrel(this));
                    p = Patro.nou(kits.isEmpty() ? null : kits.get(0), projecte.compas);
                }
                wav = projecte.wavBateriaLliure();
                p.desa(Patro.fitxerDe(projecte.carpeta, wav));
            }
            durada = Patro.genera(projecte.carpeta, wav, projecte.bpm, projecte.compas, KitsActivity.arrel(this));
            if (durada < 0) throw new IOException("sense ritme");
        } catch (IOException e) {
            Ui.avis(this, "No s'ha pogut crear la finestra de bateria");
            return;
        }
        Projecte.Clip c;
        synchronized (projecte) {
            c = projecte.afegeixFinestraBateria(t, wav, durada, compas);
        }
        carregaSo(wav);
        vista.tria(i, c);
        actualitzaAccions();
        desa();
        textInfo.setTextColor(Ui.BATERIA);
        textInfo.setText("Ritme al compàs " + compas + " · doble toc per editar-lo");
        vista.avisa();
        refresca();
    }

    /** Obre el drumpad amb el ritme d'una finestra de bateria. */
    private void obreDrumpad(Projecte.Clip clip) {
        if (gravant || clip == null) return;
        atura();
        long compas = Math.round(clip.iniciVisible() / projecte.msPerCompas()) + 1;
        int copies = 0;
        for (Projecte.Pista p : projecte.pistes) for (Projecte.Clip k : p.clips) if (k.wav.equals(clip.wav)) copies++;
        String nom = "Bateria · compàs " + compas + (copies > 1 ? " (i " + (copies - 1) + (copies == 2 ? " còpia més)" : " còpies més)") : "");
        desa();   // l'editor llegeix el projecte per fer sonar la resta de pistes
        android.content.Intent i = new android.content.Intent(this, DrumpadActivity.class);
        i.putExtra(DrumpadActivity.EXTRA_POS, clip.posMs);
        i.putExtra(DrumpadActivity.EXTRA_DIR, projecte.carpeta.getAbsolutePath());
        i.putExtra(DrumpadActivity.EXTRA_WAV, clip.wav);
        i.putExtra(DrumpadActivity.EXTRA_NOM, nom);
        i.putExtra(DrumpadActivity.EXTRA_BPM, projecte.bpm);
        i.putExtra(DrumpadActivity.EXTRA_COMPAS, projecte.compas);
        startActivityForResult(i, DRUMPAD);
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int req, int res, android.content.Intent data) {
        super.onActivityResult(req, res, data);
        if ((req != DRUMPAD && req != PIANO_ROLL && req != TEREMIN) || data == null) return;
        String wav = data.getStringExtra(DrumpadActivity.EXTRA_WAV);
        if (wav == null) return;
        refaBateria(wav, true, 1);
        if (req != DRUMPAD && data.getBooleanExtra(PianoRollActivity.EXTRA_SO_CANVIAT, false)) {
            sonsIgualsALaPista(wav);
            reanomenaPerSo(wav, presetEditor);
        }
        presetEditor = null;
        desa();
        ompleCapcaleres();
        if (taulaVisible) ompleTaula();
        vista.avisa();
        refresca();
    }

    /**
     * Torna a llegir (o, si {@code jaFet} és false, torna a fer) el so d'un ritme de bateria.
     * {@code factor}: quant s'ha allargat un compàs (1 si no ha canviat el tempo). Les finestres que el mostraven
     * sencer el continuen mostrant sencer amb la llargada nova.
     */
    private void refaBateria(String wav, boolean jaFet, double factor) {
        long durada;
        try {
            if (jaFet) durada = -1;
            else if (Projecte.esNotes(wav)) durada = Melodia.genera(projecte.carpeta, wav, projecte.bpm, projecte.compas);
            else if (Projecte.esTeremin(wav)) durada = Gest.genera(projecte.carpeta, wav, projecte.bpm, projecte.compas);
            else durada = Patro.genera(projecte.carpeta, wav, projecte.bpm, projecte.compas, KitsActivity.arrel(this));
        } catch (IOException e) {
            durada = -1;
        }
        File f = new File(projecte.carpeta, wav);
        if (durada < 0) {
            Wav.Info in = Wav.llegeixInfo(f);
            if (in == null) return;
            durada = in.duradaMs;
        }
        synchronized (projecte) {
            Projecte.Presa pr = projecte.preses.get(wav);
            long vella = pr == null ? durada : Math.round(pr.duradaMs * factor);
            projecte.canviaDurada(wav, vella, durada);
        }
        carregaSo(wav);
    }

    // ---------- Sinte ----------

    /** «Pista nova → Sinte»: quin so de fàbrica (després es pot canviar al piano roll). */
    private void triaPresetNou(Runnable despres) {
        List<Full.Accio> ops = new ArrayList<>();
        for (Sinte.Preset p : Sinte.PRESETS) {
            ops.add(new Full.Accio(Icona.SINTE, p.nom, false, () -> {
                creaSinte(p, false);
                if (despres != null) despres.run();
            }));
        }
        Full.llista(this, "So del sinte", "Els baixos s'obren a les notes greus i els leads a les agudes. "
                + "Al piano roll el pots canviar escoltant-los.", ops);
    }

    /** Crea una pista de sinte buida (amb una primera frase buida a punt per a la primera finestra). */
    private void creaSinte(Sinte.Preset preset, boolean poli) {
        atura();
        String wav;
        try {
            Melodia m = poli ? Melodia.novaPoli(preset, projecte.compas) : Melodia.nova(preset, projecte.compas);
            wav = poli ? projecte.wavPoliLliure() : projecte.wavSinteLliure();
            m.desa(Melodia.fitxerDe(projecte.carpeta, wav));
            long durada = m.escriuWav(projecte.carpeta, wav, projecte.bpm, projecte.compas);
            int n = 1;
            for (Projecte.Pista x : projecte.pistes) if (poli ? x.esPoli() : x.esSinte()) n++;
            synchronized (projecte) {
                projecte.afegeixSinte(wav, durada, n > 1 ? preset.nom + " " + n : preset.nom);
            }
            carregaSo(wav);
        } catch (IOException e) {
            Ui.avis(this, "No s'ha pogut crear la pista de sinte");
            return;
        }
        desa();
        Ui.historial(this, (poli ? "Pista de sinte polifònic nova («" : "Pista de sinte nova («") + preset.nom + "») a «" + projecte.carpeta.getName() + "»");
        ompleCapcaleres();
        posaEtiquetaPistes();
        pistes.requestLayout();
        pistes.invalidate();
        textInfo.setTextColor(poli ? Ui.POLI : Ui.SINTE);
        textInfo.setText("Mantén premut a la fila del sinte per posar-hi una frase");
    }

    /** «Pista nova → Poli»: primer la família (pads, tecles…) i després el so. */
    private void triaPresetPoli(Runnable despres) {
        List<Full.Accio> ops = new ArrayList<>();
        for (int f = 0; f < Poli.FAMILIES.length; f++) {
            int ff = f;
            ops.add(new Full.Accio(Icona.POLI, Poli.FAMILIES[f], false, () -> {
                List<Full.Accio> sons = new ArrayList<>();
                for (Sinte.Preset p : Poli.deFamilia(ff)) {
                    sons.add(new Full.Accio(Icona.POLI, p.nom, false, () -> {
                        creaSinte(p, true);
                        if (despres != null) despres.run();
                    }));
                }
                Full.llista(this, Poli.FAMILIES[ff], "Al piano roll el pots canviar escoltant-los.", sons,
                        () -> triaPresetPoli(despres));   // «<»: torna a les famílies
            }));
        }
        Full.llista(this, "Sinte polifònic", "Pots tocar acords. Tria una família.", ops);
    }

    /**
     * Mantenir premut un espai buit del sinte: finestra nova (buida, amb el so de la finestra d'abans)
     * a l'inici d'aquell compàs.
     */
    private void posaFinestraSinte(int i, long ms) {
        Projecte.Pista t = projecte.pistes.get(i);
        atura();
        int compas = (int) Math.max(1, Math.floor(ms / projecte.msPerCompas()) + 1);
        String wav;
        long durada;
        try {
            if (!projecte.enUs(t.wav) && new File(projecte.carpeta, t.wav).isFile()) {
                wav = t.wav;
            } else {
                Projecte.Clip abans = Projecte.clipAbans(t, ms);
                Melodia ma = abans == null ? null : Melodia.llegeix(Melodia.fitxerDe(projecte.carpeta, abans.wav));
                if (ma == null) ma = Melodia.llegeix(Melodia.fitxerDe(projecte.carpeta, t.wav));
                Melodia m = ma != null ? ma.buidaAmbElMateixSo()
                        : t.esMidi() ? Melodia.novaMidi(0, projecte.compas)
                        : t.esPoli() ? Melodia.novaPoli(Poli.PRESETS[0].preset, projecte.compas) : Melodia.nova(Sinte.PRESETS[0], projecte.compas);
                wav = projecte.wavNotesLliure(t.wav);
                m.desa(Melodia.fitxerDe(projecte.carpeta, wav));
            }
            durada = Melodia.genera(projecte.carpeta, wav, projecte.bpm, projecte.compas);
            if (durada < 0) throw new IOException("sense frase");
        } catch (IOException e) {
            Ui.avis(this, "No s'ha pogut crear la finestra del sinte");
            return;
        }
        Projecte.Clip c;
        synchronized (projecte) {
            c = projecte.afegeixFinestraBateria(t, wav, durada, compas);
        }
        carregaSo(wav);
        vista.tria(i, c);
        actualitzaAccions();
        desa();
        textInfo.setTextColor(Ui.colorPista(t));
        textInfo.setText("Frase al compàs " + compas + " · doble toc per escriure-hi notes");
        vista.avisa();
        refresca();
    }

    /** Obre el piano roll amb la frase d'una finestra de sinte. */
    private void obrePianoRoll(Projecte.Clip clip) {
        if (gravant || clip == null) return;
        atura();
        long compas = Math.round(clip.iniciVisible() / projecte.msPerCompas()) + 1;
        int copies = 0;
        String nomPista = "Sinte";
        for (Projecte.Pista p : projecte.pistes) {
            for (Projecte.Clip k : p.clips) {
                if (k.wav.equals(clip.wav)) copies++;
                if (k == clip) nomPista = p.nomVisible();
            }
        }
        String nom = nomPista + " · compàs " + compas + (copies > 1 ? " (i " + (copies - 1) + (copies == 2 ? " còpia més)" : " còpies més)") : "");
        desa();   // l'editor llegeix el projecte per fer sonar la resta de pistes
        android.content.Intent i = new android.content.Intent(this, PianoRollActivity.class);
        i.putExtra(DrumpadActivity.EXTRA_POS, clip.posMs);
        i.putExtra(DrumpadActivity.EXTRA_DIR, projecte.carpeta.getAbsolutePath());
        i.putExtra(DrumpadActivity.EXTRA_WAV, clip.wav);
        i.putExtra(DrumpadActivity.EXTRA_NOM, nom);
        i.putExtra(DrumpadActivity.EXTRA_BPM, projecte.bpm);
        i.putExtra(DrumpadActivity.EXTRA_COMPAS, projecte.compas);
        presetEditor = presetDe(clip.wav);
        startActivityForResult(i, PIANO_ROLL);
    }

    /**
     * S'ha canviat el so d'una finestra de sinte: totes les finestres de la mateixa pista agafen aquest so
     * (un sinte per pista, com en un estudi de debò). Les notes de cada finestra no es toquen.
     */
    /** El so que tenia la finestra en obrir-ne l'editor (per canviar el nom de la pista si en tornar n'és un altre). */
    private String presetEditor;

    private String presetDe(String wav) {
        if (Projecte.esTeremin(wav)) {
            Gest g = Gest.llegeix(Gest.fitxerDe(projecte.carpeta, wav));
            return g == null ? null : g.preset;
        }
        Melodia m = Melodia.llegeix(Melodia.fitxerDe(projecte.carpeta, wav));
        return m == null ? null : m.preset;
    }

    /** Les pistes que tenen la finestra {@code wav} i es deien com el so {@code vell} prenen el nom del so nou. */
    private void reanomenaPerSo(String wav, String vell) {
        String nou = presetDe(wav);
        if (vell == null || nou == null || vell.equals(nou)) return;
        for (Projecte.Pista p : projecte.pistes) {
            boolean hiEs = p.wav.equals(wav);
            for (Projecte.Clip k : p.clips) if (k.wav.equals(wav)) hiEs = true;
            if (!hiEs || p.nomPropi == null) continue;
            String n = Ui.nomAmbSo(p.nomPropi, vell, nou);
            if (!n.equals(p.nomPropi)) {
                p.nomPropi = n;
                Ui.historial(this, "So de la pista «" + n + "»: " + nou);
            }
        }
    }

    private void sonsIgualsALaPista(String wav) {
        boolean ter = Projecte.esTeremin(wav);
        String preset;
        Sinte.So so;
        int programa = -1, bancMidi = 0;
        if (ter) {
            Gest g = Gest.llegeix(Gest.fitxerDe(projecte.carpeta, wav));
            if (g == null) return;
            preset = g.preset;
            so = g.so;
        } else {
            Melodia m = Melodia.llegeix(Melodia.fitxerDe(projecte.carpeta, wav));
            if (m == null) return;
            preset = m.preset;
            so = m.so;
            programa = m.programa;
            bancMidi = m.bancMidi;
        }
        java.util.Set<String> germans = new java.util.LinkedHashSet<>();
        for (Projecte.Pista p : projecte.pistes) {
            boolean hiEs = false;
            for (Projecte.Clip k : p.clips) if (k.wav.equals(wav)) hiEs = true;
            if (!hiEs) continue;
            if (ter ? p.esTeremin() : p.esNotes()) germans.add(p.wav);
            for (Projecte.Clip k : p.clips) if (ter ? Projecte.esTeremin(k.wav) : Projecte.esNotes(k.wav)) germans.add(k.wav);
        }
        germans.remove(wav);
        for (String w : germans) {
            try {
                if (ter) {
                    File f = Gest.fitxerDe(projecte.carpeta, w);
                    Gest g = Gest.llegeix(f);
                    if (g == null) continue;
                    g.preset = preset;
                    g.so = so.copia();
                    g.desa(f);
                } else {
                    File f = Melodia.fitxerDe(projecte.carpeta, w);
                    Melodia m = Melodia.llegeix(f);
                    if (m == null) continue;
                    m.preset = preset;
                    m.so = so.copia();
                    m.programa = programa;
                    m.bancMidi = bancMidi;
                    m.desa(f);
                }
            } catch (IOException e) {
                continue;
            }
            refaBateria(w, false, 1);
        }
    }

    // ---------- Teremin ----------

    /** «Pista nova → Teremin»: quin so (després es pot canviar a la pantalla del teremin). */
    private void triaPresetTeremin(Runnable despres) {
        List<Full.Accio> ops = new ArrayList<>();
        for (Sinte.Preset p : Sinte.PRESETS_TEREMIN) {
            ops.add(new Full.Accio(Icona.TEREMIN, p.nom, false, () -> {
                creaTeremin(p);
                if (despres != null) despres.run();
            }));
        }
        Full.llista(this, "So del teremin", "Es toca en directe: encén el botó de gravar de la pista i mantén premut el play. "
                + "Després pots canviar el so i el que has tocat sonarà amb el nou.", ops);
    }

    /** Crea una pista de teremin buida, ja armada per gravar. */
    private void creaTeremin(Sinte.Preset preset) {
        atura();
        String wav;
        Projecte.Pista p;
        try {
            Gest g = Gest.nou(preset, projecte.compas);
            wav = projecte.wavTereminLliure();
            g.desa(Gest.fitxerDe(projecte.carpeta, wav));
            long durada = g.escriuWav(projecte.carpeta, wav, projecte.bpm, projecte.compas);
            int n = 1;
            for (Projecte.Pista x : projecte.pistes) if (x.esTeremin()) n++;
            synchronized (projecte) {
                p = projecte.afegeixSinte(wav, durada, n > 1 ? "Teremin " + n : "Teremin");
                projecte.armaRec(p);
            }
            carregaSo(wav);
        } catch (IOException e) {
            Ui.avis(this, "No s'ha pogut crear la pista de teremin");
            return;
        }
        desa();
        Ui.historial(this, "Pista de teremin nova («" + preset.nom + "») a «" + projecte.carpeta.getName() + "»");
        ompleCapcaleres();
        posaEtiquetaPistes();
        pistes.requestLayout();
        pistes.invalidate();
        textInfo.setTextColor(Ui.TEREMIN);
        textInfo.setText("Teremin a punt: mantén premut el play per tocar-lo");
    }

    /** Obre la pantalla d'una finestra de teremin (veure el gest, escoltar-lo, canviar el so). */
    private void obreTeremin(Projecte.Clip clip) {
        if (gravant || clip == null) return;
        atura();
        long compas = Math.round(clip.iniciVisible() / projecte.msPerCompas()) + 1;
        String nomPista = "Teremin";
        for (Projecte.Pista p : projecte.pistes) for (Projecte.Clip k : p.clips) if (k == clip) nomPista = p.nomVisible();
        desa();
        android.content.Intent i = new android.content.Intent(this, TereminActivity.class);
        i.putExtra(DrumpadActivity.EXTRA_POS, clip.posMs);
        i.putExtra(DrumpadActivity.EXTRA_DIR, projecte.carpeta.getAbsolutePath());
        i.putExtra(DrumpadActivity.EXTRA_WAV, clip.wav);
        i.putExtra(DrumpadActivity.EXTRA_NOM, nomPista + " · compàs " + compas);
        i.putExtra(DrumpadActivity.EXTRA_BPM, projecte.bpm);
        i.putExtra(DrumpadActivity.EXTRA_COMPAS, projecte.compas);
        presetEditor = presetDe(clip.wav);
        startActivityForResult(i, TEREMIN);
    }

    /** El gest (per saber-ne el so) d'una pista de teremin. */
    private Gest gestDeLaPista(Projecte.Pista t) {
        Gest g = Gest.llegeix(Gest.fitxerDe(projecte.carpeta, t.wav));
        if (g == null) {
            for (Projecte.Clip k : t.clips) {
                if (!Projecte.esTeremin(k.wav)) continue;
                g = Gest.llegeix(Gest.fitxerDe(projecte.carpeta, k.wav));
                if (g != null) break;
            }
        }
        return g != null ? g : Gest.nou(Sinte.PRESETS_TEREMIN[0], projecte.compas);
    }

    /** Llegeix el so d'una presa i la seva ona perquè l'Estudi la faci sonar i la dibuixi. */
    private void carregaSo(String wav) {
        File f = new File(projecte.carpeta, wav);
        short[] so = Wav.llegeixMostres(f);
        float[] pics = Ona.pics(f);
        synchronized (projecte) {
            if (so != null) audio.put(wav, so);
        }
        if (pics != null) pistes.posaOna(wav, pics);
    }

    // ---------- Taula de so ----------

    private void mostraTaula(boolean taula) {
        taulaVisible = taula;
        Ui.colorIcona(pestanyaPistes, taula ? Ui.SUAU : Ui.TEXT);
        Ui.colorIcona(pestanyaTaula, taula ? Ui.TEXT : Ui.SUAU);
        pestanyaPistes.setBackground(Ui.tocable(taula ? 0x00000000 : Ui.SUPERFICIE2, Ui.dp(this, 18)));
        pestanyaTaula.setBackground(Ui.tocable(taula ? Ui.SUPERFICIE2 : 0x00000000, Ui.dp(this, 18)));
        if (taula) {
            ompleTaula();
            vistaPistes.setVisibility(View.GONE);
            vistaTaula.setVisibility(View.VISIBLE);
            filaTaula.removeCallbacks(tic);
            filaTaula.postOnAnimation(tic);
        } else {
            filaTaula.removeCallbacks(tic);
            vistaTaula.setVisibility(View.GONE);
            vistaPistes.setVisibility(View.VISIBLE);
            ompleCapcaleres();   // els faders petits, al dia
            refresca();
        }
        Ui.prefs(this).edit().putBoolean("estudi_taula", taula).apply();
        // la taula es pot girar (en vertical els faders són més llargs); el taulell, sempre horitzontal
        setRequestedOrientation(taula ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_USER
                : android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    }

    /** Vúmetres de la taula: es refresquen a cada fotograma mentre es veu. */
    private final Runnable tic = new Runnable() {
        @Override
        public void run() {
            if (!taulaVisible) return;
            for (int i = 0; i < faders.size(); i++) {
                FaderView f = faders.get(i);
                f.setNivell(motor != null ? motor.nivell(i) : 0f);
                float p = f.picDb();
                textosPic.get(i).setText(p <= -60 ? "—" : String.format(Locale.ROOT, "%.1f", p).replace('.', ','));
                textosPic.get(i).setTextColor(p > 0 ? Ui.ACCENT : Ui.SUAU);
            }
            filaTaula.postOnAnimation(this);
        }
    };

    private void ompleTaula() {
        filaTaula.removeAllViews();
        faders.clear();
        textosPic.clear();
        for (int i = 0; i < projecte.pistes.size(); i++) filaTaula.addView(canal(i));
        // «+» també a la taula
        LinearLayout mes = Ui.columna(this);
        mes.setGravity(Gravity.CENTER);
        FrameLayout b = Ui.botoIcona(this, Icona.MES, Ui.SUAU, Ui.SUPERFICIE2, 44, 22, "Pista nova");
        b.setOnClickListener(v -> afegeixPista(this::ompleTaula));
        mes.addView(b);
        filaTaula.addView(mes, new LinearLayout.LayoutParams(Ui.dp(this, 64), LinearLayout.LayoutParams.MATCH_PARENT));
    }

    /**
     * Una tira de canal: a dalt, número i nom (una línia); al mig, el fader gros amb el vúmetre i,
     * al costat, una columna fina amb M, S i gravar; a baix, el dB del fader i el pic.
     */
    private View canal(int i) {
        Projecte.Pista t = projecte.pistes.get(i);
        int color = Ui.colorPista(t);
        LinearLayout c = Ui.columna(this);
        c.setPadding(Ui.dp(this, 3), 0, Ui.dp(this, 3), Ui.dp(this, 4));
        c.setBackgroundColor(i % 2 == 0 ? Ui.SUPERFICIE : 0xFF141418);

        View franja = new View(this);
        franja.setBackgroundColor(color);
        c.addView(franja, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 2)));
        LinearLayout cap = Ui.fila(this);
        cap.setPadding(Ui.dp(this, 2), Ui.dp(this, 3), Ui.dp(this, 2), 0);
        TextView num = Ui.negreta(this, String.valueOf(i + 1), 10, Ui.SUAU);
        cap.addView(num);
        cap.addView(Ui.espai(this, 4));
        cap.addView(Ui.unaLinia(Ui.text(this, t.nomVisible(), 10, Ui.TEXT)), Ui.pes(1));
        c.addView(cap);

        LinearLayout mig = Ui.fila(this);
        mig.setGravity(Gravity.TOP);
        FaderView f = new FaderView(this, t.volumDb, color);
        mig.addView(f, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        faders.add(f);

        // columna fina: M, S i gravar
        LinearLayout col = Ui.columna(this);
        col.setPadding(0, Ui.dp(this, 10), 0, 0);
        TextView m = botoMS("M", t.mut, Ui.ACCENT);
        TextView so = botoMS("S", t.solo, Ui.GUITARRA);
        m.setOnClickListener(v -> {
            synchronized (projecte) {
                t.mut = !t.mut;
            }
            pintaMS(m, t.mut, Ui.ACCENT);
            desa();
        });
        so.setOnClickListener(v -> {
            synchronized (projecte) {
                t.solo = !t.solo;
            }
            pintaMS(so, t.solo, Ui.GUITARRA);
            desa();
        });
        col.addView(m);
        col.addView(Ui.espai(this, 6));
        col.addView(so);
        col.addView(Ui.espai(this, 6));
        FrameLayout rec = new FrameLayout(this);
        rec.setClickable(true);
        rec.setContentDescription("Pista on es grava");
        rec.addView(Ui.imatge(this, Icona.REC, Ui.SUAU, 14),
                new FrameLayout.LayoutParams(Ui.dp(this, 16), Ui.dp(this, 16), Gravity.CENTER));
        col.addView(rec, new LinearLayout.LayoutParams(Ui.dp(this, 28), Ui.dp(this, 24)));
        if (t.esBateria()) rec.setVisibility(View.INVISIBLE);
        pintaRec(rec, t.rec);
        rec.setOnClickListener(v -> {
            synchronized (projecte) {
                projecte.armaRec(t.rec ? null : t);
            }
            desa();
            filaTaula.post(this::ompleTaula);
        });
        mig.addView(col, new LinearLayout.LayoutParams(Ui.dp(this, 28), LinearLayout.LayoutParams.WRAP_CONTENT));
        c.addView(mig, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // a baix: dB del fader i pic
        LinearLayout peu = Ui.fila(this);
        peu.setGravity(Gravity.CENTER);
        TextView db = Ui.negreta(this, Projecte.textDb(t.volumDb), 11, Ui.TEXT);
        db.setFontFeatureSettings("tnum");
        TextView pic = Ui.text(this, "—", 10, Ui.SUAU);
        pic.setFontFeatureSettings("tnum");
        peu.addView(db);
        peu.addView(Ui.espai(this, 6));
        peu.addView(pic);
        textosPic.add(pic);
        c.addView(peu);
        f.setOient((valor, acabat) -> {
            synchronized (projecte) {
                t.volumDb = valor;
            }
            db.setText(Projecte.textDb(valor));
            if (acabat) desa();
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 96), LinearLayout.LayoutParams.MATCH_PARENT);
        lp.rightMargin = 1;
        c.setLayoutParams(lp);
        return c;
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration nova) {
        super.onConfigurationChanged(nova);
        ajustaBarra(nova);
    }

    /** En vertical la barra de dalt no hi cap sencera: s'amaguen el nom del projecte i la posició. */
    private void ajustaBarra(android.content.res.Configuration conf) {
        boolean vertical = conf.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT;
        if (nomTitol != null) nomTitol.setVisibility(vertical || panellTeclat != null ? View.GONE : View.VISIBLE);
        if (textPosicio != null) textPosicio.setVisibility(vertical ? View.GONE : View.VISIBLE);
    }

    // ---------- Graella ----------

    private static final String[] NOMS_SNAP = {"Compàs", "Temps", "Lliure"};

    private void pintaSnap() {
        textSnap.setText(NOMS_SNAP[Math.max(0, Math.min(2, vista.snap))]);
    }

    private void desplegableSnap(View ancora) {
        LinearLayout llista = Ui.columna(this);
        llista.setBackground(Ui.rodoVora(Ui.SUPERFICIE2, Ui.LINIA_FORTA, Ui.dp(this, 12), Ui.dp(this, 1)));
        llista.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
        android.widget.PopupWindow pw = new android.widget.PopupWindow(llista,
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, true);
        for (int i = 0; i < NOMS_SNAP.length; i++) {
            int idx = i;
            boolean actual = i == vista.snap;
            TextView t = Ui.negreta(this, NOMS_SNAP[i], 14, actual ? Ui.SOBRE_COLOR : Ui.TEXT);
            t.setGravity(Gravity.CENTER_VERTICAL);
            t.setMinWidth(Ui.dp(this, 120));
            t.setMinHeight(Ui.dp(this, 40));
            t.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);
            t.setClickable(true);
            t.setBackground(Ui.tocable(actual ? Ui.TEXT : 0x00000000, Ui.dp(this, 9)));
            t.setOnClickListener(v -> {
                vista.snap = idx;
                Ui.prefs(this).edit().putInt("snap", idx).apply();
                pintaSnap();
                pw.dismiss();
            });
            llista.addView(t);
        }
        pw.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0x00000000));
        pw.setOutsideTouchable(true);
        pw.setElevation(Ui.dp(this, 8));
        pw.showAsDropDown(ancora, 0, Ui.dp(this, 4));
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
        // gravant àudio, el clic només amb auriculars (es gravaria); gravant el sinte no hi ha micròfon
        motor.posaClic(clicActiu && (!gravant || gravantSinte || Metronom.auriculars(this)));
    }

    // ---------- Gravar des de l'Estudi ----------

    /** Mantenir premut el play: tria on es grava (una pista o una de nova). */
    private void triaPistaGravar() {
        if (!carregat || motor == null) return;
        Projecte.Pista armada = projecte.pistaRec();
        if (armada != null && armada.esInstrument()) {
            // el sinte i el teremin es graven tocant en directe (no cal micròfon)
            comencaGravacioSinte(armada);
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, PERMIS_MIC);
            return;
        }
        atura();
        if (armada != null && armada.esGenerat()) armada = null;
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
            if (t.esBateria()) continue;
            if (t.esInstrument()) {
                ops.add(new Full.Accio(Ui.iconaPista(t), t.nomVisible() + (t.esTeremin() ? " · teremin" : " · teclat"), false, () -> {
                    synchronized (projecte) {
                        projecte.armaRec(t);
                    }
                    desa();
                    ompleCapcaleres();
                    comencaGravacioSinte(t);
                }));
                continue;
            }
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
        tancaAssaig();
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
            capcaleres.addView(nova, projecte.pistes.size(), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, altPista));
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
        if (gravantSinte) {
            aturaGravacioSinte(motiu);
            return;
        }
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
        if (taulaVisible) ompleTaula();
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

    // ---------- Gravar el sinte en directe ----------

    /** El so (i el preset) d'una pista de sinte: el de la seva frase principal, o el de qualsevol finestra seva. */
    private Melodia soDeLaPista(Projecte.Pista t) {
        Melodia m = Melodia.llegeix(Melodia.fitxerDe(projecte.carpeta, t.wav));
        if (m == null) {
            for (Projecte.Clip k : t.clips) {
                if (!Projecte.esNotes(k.wav)) continue;
                m = Melodia.llegeix(Melodia.fitxerDe(projecte.carpeta, k.wav));
                if (m != null) break;
            }
        }
        if (m != null) return m;
        return t.esPoli() ? Melodia.novaPoli(Poli.PRESETS[0].preset, projecte.compas) : Melodia.nova(Sinte.PRESETS[0], projecte.compas);
    }

    /** Moment de la línia de temps (ms) que se sentia a {@code nanos} (System.nanoTime). */
    private double msDeNano(long nanos) {
        if (motor == null) return entradaMs;
        return entradaMs + (nanos - motor.nanoDeLinia(entradaMs)) / 1e6;
    }

    /**
     * Gravar una pista de sinte: sona el projecte des d'un compàs abans (amb el clic, si està encès) i surt el
     * teclat virtual als dos terços de baix. El que toques sona en directe i, en aturar, es converteix en notes.
     */
    private void comencaGravacioSinte(Projecte.Pista t) {
        if (gravant || motor == null) return;
        tancaAssaig();
        atura();
        boolean ter = t.esTeremin();
        Melodia base = ter ? null : soDeLaPista(t);
        Sinte.So so = ter ? gestDeLaPista(t).so : base.so;
        entradaMs = projecte.entradaGravacio(vista.capcalMs);
        long iniciMs = Math.max(0, entradaMs - Math.round(projecte.msPerCompas()));
        notesGravades = new GravacioNotes();
        gestGravat = new Gest.Gravacio();
        pistaSinte = t;
        if (!ter && base.esMidi()) {
            // MIDI: polifònic, amb l'instrument del banc
            Banc b = Banc.actual();
            if (b == null) {
                Full.avis(this, "No hi ha banc de sons", "Per tocar les pistes MIDI cal el banc de sons: Gravar → botó d'ajustos → Instruments MIDI.",
                        "Obre", () -> startActivity(new android.content.Intent(this, BancActivity.class)));
                return;
            }
            midiViu = new MidiViu(b, base.bancMidi, base.programa, Mesclador.lineal(t.volumDb));
            if (!midiViu.comenca()) {
                midiViu = null;
                Ui.avis(this, "No s'ha pogut obrir la sortida de so per a l'instrument.");
                return;
            }
        } else {
            sinteViu = new SinteViu(so, Mesclador.lineal(t.volumDb), projecte.bpm, base != null && base.poliSinte);
            if (!sinteViu.comenca()) {
                sinteViu = null;
                Ui.avis(this, "No s'ha pogut obrir la sortida de so per al sinte.");
                return;
            }
        }
        gravant = true;
        gravantSinte = true;
        aplicaClic();
        motor.posaSilenci(false);
        if (!motor.comenca(iniciMs)) {
            gravant = false;
            gravantSinte = false;
            if (sinteViu != null) sinteViu.atura();
            sinteViu = null;
            if (midiViu != null) midiViu.atura();
            midiViu = null;
            motor.posaClic(false);
            Ui.avis(this, "No s'ha pogut obrir la sortida de so.");
            return;
        }
        if (ter) mostraTeremin(t);
        else mostraTeclat(t, base.esGreu() ? 36 : 60);
        vista.tria(-1, null);
        actualitzaAccions();
        vista.capcalMs = iniciMs;
        Ui.canviaIcona(botoPlay, Icona.STOP, Ui.ACCENT, 18);
        pistes.removeCallbacks(segueix);
        pistes.postOnAnimation(segueix);
    }

    // ---------- Assajar (tocar sense gravar) ----------

    /** En assaig, un botó per tancar el panell (gravant, s'atura amb el quadrat de dalt). */
    private void afegeixTanca(LinearLayout barra) {
        if (!assaig) return;
        barra.addView(Ui.espai(this, 8));
        LinearLayout tanca = Ui.pastilla(this, 0, "Tanca", Ui.SUPERFICIE2, Ui.TEXT);
        tanca.setMinimumHeight(Ui.dp(this, 36));
        tanca.setOnClickListener(v -> tancaAssaig());
        barra.addView(tanca, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(this, 36)));
    }

    /**
     * La barra del panell amb «Tanca» a part, a la dreta: si els botons de l'esquerra no hi caben, es retallen ells,
     * però «Tanca» sempre es veu sencer.
     */
    private LinearLayout ambTanca(LinearLayout barra) {
        LinearLayout fora = Ui.fila(this);
        fora.setGravity(Gravity.CENTER_VERTICAL);
        fora.addView(barra, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        afegeixTanca(fora);
        if (assaig) fora.addView(Ui.espai(this, 12));
        return fora;
    }

    /**
     * Obre el teclat (o la quadrícula del teremin) d'una pista per tocar-la sense gravar: sona en directe, amb el
     * so de la pista. Es pot fer sonar el projecte amb el play mentre tant. Tornar a tocar el botó el tanca.
     */
    private void obreAssaig(Projecte.Pista t) {
        if (gravant) return;
        boolean jaEra = assaig && pistaSinte == t;
        tancaAssaig();
        if (jaEra) return;
        boolean ter = t.esTeremin();
        Melodia base = ter ? null : soDeLaPista(t);
        if (!ter && base.esMidi()) {
            Banc b = Banc.actual();
            if (b == null) {
                Full.avis(this, "No hi ha banc de sons", "Per tocar les pistes MIDI cal el banc de sons: Gravar → botó d'ajustos → Instruments MIDI.",
                        "Obre", () -> startActivity(new android.content.Intent(this, BancActivity.class)));
                return;
            }
            midiViu = new MidiViu(b, base.bancMidi, base.programa, Mesclador.lineal(t.volumDb));
            if (!midiViu.comenca()) {
                midiViu = null;
                Ui.avis(this, "No s'ha pogut obrir la sortida de so per a l'instrument.");
                return;
            }
        } else {
            sinteViu = new SinteViu(ter ? gestDeLaPista(t).so : base.so, Mesclador.lineal(t.volumDb), projecte.bpm,
                    base != null && base.poliSinte);
            if (!sinteViu.comenca()) {
                sinteViu = null;
                Ui.avis(this, "No s'ha pogut obrir la sortida de so per al sinte.");
                return;
            }
        }
        assaig = true;
        pistaSinte = t;
        if (ter) mostraTeremin(t);
        else mostraTeclat(t, base.esGreu() ? 36 : 60);
        textInfo.setTextColor(Ui.colorPista(t));
        textInfo.setText("Assaig: toca «" + t.nomVisible() + "» (no es grava res)");
    }

    /** Tanca el teclat d'assaig (si n'hi ha) i atura el so en directe. */
    private void tancaAssaig() {
        if (!assaig) return;
        assaig = false;
        if (sinteViu != null) sinteViu.atura();
        sinteViu = null;
        if (midiViu != null) midiViu.atura();
        midiViu = null;
        amagaTeclat();
        pistaSinte = null;
    }

    // ---------- Pads d'acords (2.29) ----------

    /** Els acords dels 12 pads (es recorden; si no n'hi ha, es converteixen els de les tecles d'abans). */
    private Acords acords() {
        android.content.SharedPreferences pr = Ui.prefs(this);
        return Acords.deText(pr.getString("acords_pads", pr.getString("acords_mapa", "")));
    }

    private void desaAcords(Acords a) {
        Ui.prefs(this).edit().putString("acords_pads", a.text()).apply();
    }

    /** Llista dels 50 presets: el triat posa els seus acords, en ordre, als pads 1, 2, 3… */
    private void triaPresetAcords(PadsView pads, Runnable despres) {
        List<Full.Accio> ops = new ArrayList<>();
        String ara = pads.acords().preset;
        for (int i = 0; i < AcordsPresets.quants(); i++) {
            int k = i;
            boolean es = AcordsPresets.titol(i).equals(ara);
            ops.add(new Full.Accio(Icona.MIDI, AcordsPresets.titol(i) + " · " + AcordsPresets.grup(i) + (es ? "  (ara)" : ""), false, () -> {
                Acords a = Acords.dePreset(k);
                desaAcords(a);
                pads.posaAcords(a);
                if (despres != null) despres.run();
            }));
        }
        Full.llista(this, "Presets d'acords", "Posa els 8 acords de la cançó, en ordre, als pads 1 a 8 "
                + "(d'esquerra a dreta, com en un piano: C, C#, D…). Amb el llapis en pots canviar qualsevol.", ops);
    }

    /** Fa sonar un acord una estona (per escoltar-lo a l'editor). */
    private void provaAcord(int[] notes, View on) {
        if ((midiViu == null && sinteViu == null) || notes.length == 0) return;
        for (int n : notes) encenViu(n);
        on.postDelayed(() -> {
            for (int n : notes) apagaViu(n);
        }, 900);
    }

    /** Polifònic: encén una nota del que sona en directe (MIDI o sinte polifònic). */
    private void encenViu(int nota) {
        if (midiViu != null) midiViu.encen(nota, MidiSo.velocitat(2));
        else if (sinteViu != null && sinteViu.poli()) sinteViu.encen(nota);
    }

    private void apagaViu(int nota) {
        if (midiViu != null) midiViu.apaga(nota);
        else if (sinteViu != null && sinteViu.poli()) sinteViu.apaga(nota);
    }

    // ---------- Canviar el so des del panell (2.29) ----------

    /** La capçalera del panell: icona i nom de la pista; tocar-la obre la llista de sons. */
    private LinearLayout capcaleraSo(Projecte.Pista t) {
        int color = Ui.colorPista(t);
        LinearLayout cap = Ui.fila(this);
        cap.setClickable(true);
        cap.setContentDescription("Canvia el so");
        cap.setBackground(Ui.tocable(Ui.SUPERFICIE2, Ui.dp(this, 18)));
        cap.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 8), 0);
        cap.setMinimumHeight(Ui.dp(this, 36));
        cap.setGravity(Gravity.CENTER_VERTICAL);
        cap.addView(Ui.imatge(this, Ui.iconaPista(t), color, 18), new LinearLayout.LayoutParams(Ui.dp(this, 20), Ui.dp(this, 20)));
        cap.addView(Ui.espai(this, 8));
        TextView nom = Ui.unaLinia(Ui.negreta(this, t.nomVisible(), 14, Ui.TEXT));
        nom.setMaxWidth(Ui.dp(this, 140));
        cap.addView(nom);
        cap.addView(Ui.espai(this, 4));
        View xevro = Ui.imatge(this, Icona.XEVRO, Ui.SUAU, 14);
        xevro.setRotation(90);   // cap avall: obre una llista
        cap.addView(xevro, new LinearLayout.LayoutParams(Ui.dp(this, 14), Ui.dp(this, 14)));
        cap.setOnClickListener(v -> triaSoPanell(t, nom));
        return cap;
    }

    private void triaSoPanell(Projecte.Pista t, TextView nom) {
        if (!projecte.pistes.contains(t)) return;
        boolean ter = t.esTeremin();
        Melodia base = ter ? null : soDeLaPista(t);
        if (base != null && base.esMidi()) {
            triaFamiliaPanell(t, nom, base);
            return;
        }
        if (base != null && base.poliSinte) {
            triaFamiliaPoliPanell(t, nom);
            return;
        }
        Sinte.Preset[] ps = ter ? Sinte.PRESETS_TEREMIN : Sinte.PRESETS;
        String ara = ter ? gestDeLaPista(t).preset : base.preset;
        String[] noms = new String[ps.length];
        int triat = -1;
        for (int i = 0; i < ps.length; i++) {
            noms[i] = ps[i].nom;
            if (ps[i].nom.equals(ara)) triat = i;
        }
        Full.triaAmbProva(this, ter ? "So del teremin" : "So del sinte", "Toca un so per escoltar-lo; queda triat. «Fet» per tancar. "
                        + "Totes les finestres d'aquesta pista agafaran aquest so (també el que ja has gravat).",
                noms, triat, Ui.colorPista(t),
                i -> {
                    canviaSoPista(t, ps[i], -1, nom);
                    demoSo(t, ps[i].greu);
                },
                i -> canviaSoPista(t, ps[i], -1, nom),   // (si ja el té, no fa res; la fletxa «<» el fa servir per tornar enrere)
                null, null);
    }

    /** Sinte polifònic: família i després el so (sona en tocar-lo; s'aplica a tota la pista). */
    private void triaFamiliaPoliPanell(Projecte.Pista t, TextView nom) {
        List<Full.Accio> ops = new ArrayList<>();
        Melodia base = soDeLaPista(t);
        int actual = Poli.familiaDe(base.preset);
        for (int f = 0; f < Poli.FAMILIES.length; f++) {
            int ff = f;
            ops.add(new Full.Accio(Icona.POLI, Poli.FAMILIES[f] + (f == actual ? "  (ara)" : ""), false, () -> {
                List<Sinte.Preset> ps = Poli.deFamilia(ff);
                String[] noms = new String[ps.size()];
                Melodia ara = soDeLaPista(t);
                int triat = -1;
                for (int i = 0; i < noms.length; i++) {
                    noms[i] = ps.get(i).nom;
                    if (noms[i].equals(ara.preset)) triat = i;
                }
                Sinte.Preset original = Poli.preset(ara.preset);
                Full.triaAmbProva(this, Poli.FAMILIES[ff], "Toca un so per escoltar-lo; queda triat. «Fet» per tancar. "
                                + "Totes les finestres d'aquesta pista l'agafaran.",
                        noms, triat, Ui.POLI,
                        i -> {
                            canviaSoPista(t, ps.get(i), -1, nom);
                            demoSo(t, false);
                        },
                        i -> canviaSoPista(t, ps.get(i), -1, nom),
                        () -> {
                            if (original != null) canviaSoPista(t, original, -1, nom);   // «<»: torna al so d'abans
                        },
                        () -> triaFamiliaPoliPanell(t, nom));
            }));
        }
        Full.llista(this, "So del sinte polifònic", "Ara: " + base.preset + ". Tria una família.", ops);
    }

    private void triaFamiliaPanell(Projecte.Pista t, TextView nom, Melodia base) {
        if (Banc.actual() == null) {
            Full.avis(this, "No hi ha banc de sons", "Cal baixar el banc MIDI: Gravar → botó d'ajustos → Instruments MIDI.",
                    "Obre", () -> startActivity(new android.content.Intent(this, BancActivity.class)));
            return;
        }
        List<Full.Accio> ops = new ArrayList<>();
        int actual = base.programa / 8;
        for (int f = 0; f < Gm.FAMILIES.length; f++) {
            int ff = f;
            ops.add(new Full.Accio(Icona.MIDI, Gm.FAMILIES[f] + (f == actual ? "  (ara)" : ""), false, () -> {
                String[] noms = new String[8];
                for (int i = 0; i < 8; i++) noms[i] = Gm.nom(ff * 8 + i);
                Melodia ara = soDeLaPista(t);
                int triat = ara.programa / 8 == ff ? ara.programa % 8 : -1;
                int original = ara.programa;
                Full.triaAmbProva(this, Gm.FAMILIES[ff], "Toca un instrument per escoltar-lo; queda triat. «Fet» per tancar. "
                                + "Totes les finestres d'aquesta pista l'agafaran.",
                        noms, triat, Ui.MIDI,
                        i -> {
                            canviaSoPista(t, null, ff * 8 + i, nom);
                            demoSo(t, Gm.esGreu(ff * 8 + i));
                        },
                        i -> canviaSoPista(t, null, ff * 8 + i, nom),
                        () -> canviaSoPista(t, null, original, nom),          // «<»: torna a l'instrument d'abans
                        () -> triaFamiliaPanell(t, nom, soDeLaPista(t)));      // i a la llista de famílies
            }));
        }
        Full.llista(this, "Instrument", "Ara: " + Gm.nom(base.programa) + ". Tria una família.", ops);
    }

    /**
     * Canvia el so de tota la pista (la frase o el gest principal i totes les finestres): es desa, es torna a fer el
     * so de les finestres i el que sona en directe agafa el so nou. Si la pista es deia com el so d'abans, pren el nom
     * del nou.
     */
    private void canviaSoPista(Projecte.Pista t, Sinte.Preset preset, int programa, TextView nom) {
        boolean ter = t.esTeremin();
        java.util.Set<String> wavs = new java.util.LinkedHashSet<>();
        wavs.add(t.wav);
        for (Projecte.Clip k : t.clips) if (ter ? Projecte.esTeremin(k.wav) : Projecte.esNotes(k.wav)) wavs.add(k.wav);
        String vell = null, nou = null;
        Sinte.So so = null;
        int bancMidi = 0;
        for (String w : wavs) {
            try {
                if (ter) {
                    File f = Gest.fitxerDe(projecte.carpeta, w);
                    Gest g = Gest.llegeix(f);
                    if (g == null) continue;
                    if (preset.nom.equals(g.preset)) continue;
                    if (vell == null) vell = g.preset;
                    g.posaPreset(preset);
                    nou = g.preset;
                    so = g.so;
                    g.desa(f);
                } else {
                    File f = Melodia.fitxerDe(projecte.carpeta, w);
                    Melodia m = Melodia.llegeix(f);
                    if (m == null) continue;
                    if (programa >= 0 ? m.programa == programa : preset.nom.equals(m.preset)) continue;
                    if (vell == null) vell = m.preset;
                    if (programa >= 0) m.posaInstrument(programa);
                    else m.posaPreset(preset);
                    nou = m.preset;
                    so = m.so;
                    bancMidi = m.bancMidi;
                    m.desa(f);
                }
            } catch (IOException e) {
                continue;
            }
            if (new File(projecte.carpeta, w).isFile() || projecte.enUs(w)) refaBateria(w, false, 1);
        }
        if (nou == null) return;   // ja tenia aquest so
        if (t.nomPropi != null) t.nomPropi = Ui.nomAmbSo(t.nomPropi, vell, nou);
        // el so en directe
        double guany = Mesclador.lineal(t.volumDb);
        if (sinteViu != null && so != null) {
            SinteViu sv = new SinteViu(so, guany, projecte.bpm, sinteViu.poli());
            if (sv.comenca()) {
                SinteViu vellViu = sinteViu;
                sinteViu = sv;
                vellViu.atura();
            }
        }
        if (midiViu != null && programa >= 0) {
            MidiViu mv = new MidiViu(Banc.actual(), bancMidi, programa, guany);
            if (mv.comenca()) {
                MidiViu vellViu = midiViu;
                midiViu = mv;
                vellViu.atura();
            }
        }
        desa();
        Ui.historial(this, "So de la pista «" + t.nomVisible() + "»: " + nou);
        if (nom != null) nom.setText(t.nomVisible());
        ompleCapcaleres();
        if (taulaVisible) ompleTaula();
        vista.avisa();
        refresca();
    }

    /** Un trosset per sentir el so nou (pel so en directe; no es grava). */
    private void demoSo(Projecte.Pista t, boolean greu) {
        View on = panellTeclat != null ? panellTeclat : pistes;
        if (t.esTeremin()) {
            SinteViu sv = sinteViu;
            if (sv == null) return;
            int c = Ui.prefs(this).getInt("teremin_centre", 72);
            sv.engega(c, 0.8);
            on.postDelayed(() -> sv.posa(c + 7, 0.85), 350);
            on.postDelayed(sv::deixa, 800);
            return;
        }
        if (midiViu != null) {
            MidiViu m = midiViu;
            int b = greu ? 36 : 60;
            int[] ns = {0, 4, 7, 12};
            for (int i = 0; i < ns.length; i++) {
                int n = b + ns[i], k = i;
                on.postDelayed(() -> m.encen(n, MidiSo.velocitat(2)), k * 180L);
                on.postDelayed(() -> m.apaga(n), k * 180L + 170);
            }
            on.postDelayed(() -> { for (int n : new int[]{b, b + 4, b + 7}) m.encen(n, MidiSo.velocitat(2)); }, 720);
            on.postDelayed(() -> { for (int n : new int[]{b, b + 4, b + 7}) m.apaga(n); }, 1400);
            return;
        }
        SinteViu sv = sinteViu;
        if (sv == null) return;
        if (sv.poli()) {
            int[] ns = {60, 64, 67, 72};
            for (int i = 0; i < ns.length; i++) {
                int n = ns[i], k = i;
                on.postDelayed(() -> sv.encen(n), k * 180L);
                on.postDelayed(() -> sv.apaga(n), k * 180L + 170);
            }
            on.postDelayed(() -> { for (int n : new int[]{48, 60, 64, 67}) sv.encen(n); }, 720);
            on.postDelayed(sv::apagaTot, 1500);
            return;
        }
        int[] ns = greu ? new int[]{36, 36, 43, 48} : new int[]{60, 63, 67, 72};
        for (int i = 0; i < ns.length; i++) {
            int n = ns[i];
            on.postDelayed(() -> sv.nota(n, false), i * 180L);
        }
        on.postDelayed(sv::deixa, ns.length * 180L);
    }

    // ---------- MIDI ----------

    /** «Pista nova → MIDI»: quin instrument (família i instrument). */
    private void triaInstrumentNou(Runnable despres) {
        if (!Banc.hiEs()) {
            Full.avis(this, "Cal el banc de sons", "Les pistes MIDI fan servir un banc amb els 128 instruments (uns 31 MB). "
                            + "Baixa'l una vegada i torna aquí.",
                    "Baixa'l", () -> {
                        android.content.Intent i = new android.content.Intent(this, BancActivity.class);
                        i.putExtra(BancActivity.EXTRA_BAIXA, true);
                        startActivity(i);
                    });
            return;
        }
        List<Full.Accio> ops = new ArrayList<>();
        for (int f = 0; f < Gm.FAMILIES.length; f++) {
            int ff = f;
            ops.add(new Full.Accio(Icona.MIDI, Gm.FAMILIES[f], false, () -> {
                List<Full.Accio> ins = new ArrayList<>();
                for (int i = 0; i < 8; i++) {
                    int prog = ff * 8 + i;
                    ins.add(new Full.Accio(Icona.MIDI, Gm.nom(prog), false, () -> {
                        creaMidi(prog);
                        if (despres != null) despres.run();
                    }));
                }
                Full.llista(this, Gm.FAMILIES[ff], "Al piano roll el pots canviar escoltant-los.", ins,
                        () -> triaInstrumentNou(despres));   // «<»: torna a les famílies
            }));
        }
        Full.llista(this, "Instrument MIDI", "Polifònic: pots fer acords. Tria una família.", ops);
    }

    /** Crea una pista MIDI buida amb aquest instrument. */
    private void creaMidi(int programa) {
        atura();
        String wav;
        try {
            Melodia m = Melodia.novaMidi(programa, projecte.compas);
            wav = projecte.wavMidiLliure();
            m.desa(Melodia.fitxerDe(projecte.carpeta, wav));
            long durada = m.escriuWav(projecte.carpeta, wav, projecte.bpm, projecte.compas);
            int n = 1;
            for (Projecte.Pista x : projecte.pistes) if (x.esMidi()) n++;
            synchronized (projecte) {
                projecte.afegeixSinte(wav, durada, Gm.nom(programa) + (n > 1 ? " " + n : ""));
            }
            carregaSo(wav);
        } catch (IOException e) {
            Ui.avis(this, "No s'ha pogut crear la pista MIDI");
            return;
        }
        new Thread(Banc::actual, "banc").start();
        desa();
        Ui.historial(this, "Pista MIDI nova («" + Gm.nom(programa) + "») a «" + projecte.carpeta.getName() + "»");
        ompleCapcaleres();
        posaEtiquetaPistes();
        pistes.requestLayout();
        pistes.invalidate();
        textInfo.setTextColor(Ui.MIDI);
        textInfo.setText("Mantén premut a la fila per posar-hi una frase, o grava-hi amb el teclat");
    }

    /**
     * El teclat virtual, als dos terços de baix de la pantalla (per sobre de les pistes). A les pistes MIDI, un
     * interruptor Notes / Acords canvia el teclat pels 12 pads d'acords.
     */
    private void mostraTeclat(Projecte.Pista t, int doEsquerra) {
        amagaTeclat();
        LinearLayout panell = Ui.columna(this);
        panell.setBackgroundColor(Ui.FONS);
        panell.setClickable(true);   // que els tocs no passin a les pistes de sota
        View vora = new View(this);
        int color = Ui.colorPista(t);
        boolean poli = midiViu != null || (sinteViu != null && sinteViu.poli());
        vora.setBackgroundColor(color);
        panell.addView(vora, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 2)));

        TeclatView teclat = new TeclatView(this);
        teclat.posaBase(doEsquerra);
        teclat.posaColor(color);
        PadsView pads = poli ? new PadsView(this) : null;
        boolean[] modeAcords = {poli && Ui.prefs(this).getBoolean("acords_encesos", false)};
        if (pads != null) {
            pads.posaAcords(acords());
            pads.posaBase(Ui.prefs(this).getInt("acords_octava", 48));
        }

        LinearLayout barra = Ui.fila(this);
        barra.setPadding(Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 4));
        barra.setGravity(Gravity.CENTER_VERTICAL);
        barra.addView(capcaleraSo(t));
        barra.addView(Ui.espai(this, 12));
        FrameLayout menys = Ui.botoIcona(this, Icona.MENYS, Ui.TEXT, Ui.SUPERFICIE2, 36, 18, "Una octava avall");
        TextView rang = Ui.unaLinia(Ui.negreta(this, "", 13, Ui.SUAU));
        FrameLayout mes = Ui.botoIcona(this, Icona.MES, Ui.TEXT, Ui.SUPERFICIE2, 36, 18, "Una octava amunt");
        Runnable posaRang = () -> rang.setText(modeAcords[0] ? "Octava " + Melodia.nomNota(pads.base())
                : Melodia.nomNota(teclat.base()) + " – " + Melodia.nomNota(teclat.base() + 24));
        menys.setOnClickListener(v -> {
            if (modeAcords[0]) {
                pads.posaBase(pads.base() - 12);
                Ui.prefs(this).edit().putInt("acords_octava", pads.base()).apply();
            } else {
                teclat.posaBase(teclat.base() - 12);
            }
            posaRang.run();
        });
        mes.setOnClickListener(v -> {
            if (modeAcords[0]) {
                pads.posaBase(pads.base() + 12);
                Ui.prefs(this).edit().putInt("acords_octava", pads.base()).apply();
            } else {
                teclat.posaBase(teclat.base() + 12);
            }
            posaRang.run();
        });
        // −/+ de l'octava: van després dels botons dels acords (2.31.2), perquè el llapis no quedi mai tallat
        LinearLayout octava = Ui.fila(this);
        octava.setGravity(Gravity.CENTER_VERTICAL);
        octava.addView(menys);
        octava.addView(Ui.espai(this, 6));
        octava.addView(rang);
        octava.addView(Ui.espai(this, 6));
        octava.addView(mes);
        boolean[] editant = {false};

        textTeclat = Ui.cartell(Ui.text(this, assaig ? "Assaig: no es grava res" : "Gravant · el quadrat de dalt atura i desa", 12, Ui.APAGAT));
        textTeclat.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        Runnable textBase = () -> {
            if (textTeclat == null) return;
            textTeclat.setTextColor(Ui.APAGAT);
            if (pads != null && modeAcords[0] && editant[0]) {
                textTeclat.setTextColor(color);
                textTeclat.setText("Toca un pad petit i tria'n l'acord · torna a tocar el llapis per acabar");
            } else {
                textTeclat.setText(assaig ? "Assaig: no es grava res" : "Gravant · el quadrat de dalt atura i desa");
            }
        };

        FrameLayout zona = new FrameLayout(this);
        LinearLayout editor = null;
        if (poli) {
            // interruptor Notes / Acords, llapis i preset (aquests dos, només amb els acords)
            LinearLayout preset = Ui.pastilla(this, Ui.iconaPista(t), "", Ui.SUPERFICIE2, Ui.TEXT);
            preset.setMinimumHeight(Ui.dp(this, 36));
            TextView tp = (TextView) preset.getChildAt(preset.getChildCount() - 1);
            tp.setMaxWidth(Ui.dp(this, 120));
            Ui.unaLinia(tp);
            FrameLayout llapis = Ui.botoIcona(this, Icona.LLAPIS, Ui.TEXT, Ui.SUPERFICIE2, 36, 18, "Edita els acords dels pads");

            // editor (2.31.2): pads petits a l'esquerra i les quatre columnes a la dreta
            editor = Ui.fila(this);
            editor.setGravity(Gravity.TOP);
            LinearLayout esq = Ui.columna(this);
            PadsView mini = new PadsView(this);
            mini.posaColor(color);
            mini.posaEdita(true);
            mini.posaAcords(pads.acords());
            esq.addView(mini, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 110)));
            TextView nomPad = Ui.unaLinia(Ui.negreta(this, "", 16, Ui.TEXT));
            esq.addView(nomPad, Ui.ample(this, 8));
            LinearLayout botonsPad = Ui.fila(this);
            LinearLayout escolta = Ui.pastilla(this, Icona.PLAY, "Escolta", Ui.SUPERFICIE2, Ui.TEXT);
            LinearLayout buida = Ui.pastilla(this, Icona.PAPERERA, "Buida", Ui.SUPERFICIE2, Ui.TEXT);
            botonsPad.addView(escolta, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(this, 36)));
            botonsPad.addView(Ui.espai(this, 8));
            botonsPad.addView(buida, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(this, 36)));
            esq.addView(botonsPad, Ui.ample(this, 8));
            int ampleEsq = Math.min(Ui.dp(this, 250), getResources().getDisplayMetrics().widthPixels * 36 / 100);
            editor.addView(esq, new LinearLayout.LayoutParams(ampleEsq, LinearLayout.LayoutParams.MATCH_PARENT));
            editor.addView(Ui.espai(this, 14));
            int[] triat = {0};
            Runnable posaPreset = () -> {
                String pr = pads.acords().preset;
                tp.setText(pr == null || pr.isEmpty() ? "Presets" : pr);
            };
            Runnable posaNomPad = () -> {
                Acords.Acord ac = pads.acords().pads[triat[0]];
                nomPad.setText("Pad " + (triat[0] + 1) + " · " + (ac.buit() ? "buit" : ac.nom()));
            };
            Full.EditorAcord ed = Full.editorAcord(this, color, ac -> {
                Acords a = pads.acords();
                a.pads[triat[0]] = ac;
                if (a.preset != null && !a.preset.isEmpty() && !a.preset.endsWith(" (modificat)")) {
                    a.preset = a.preset + " (modificat)";
                }
                desaAcords(a);
                pads.invalidate();
                mini.invalidate();
                posaPreset.run();
                posaNomPad.run();
                provaAcord(ac.notes(pads.base()), mini);
            });
            editor.addView(ed.vista, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
            Runnable triaPad = () -> {
                mini.posaTriat(triat[0]);
                ed.posa(pads.acords().pads[triat[0]]);
                posaNomPad.run();
            };
            mini.posaOient(pad -> {
                triat[0] = pad;
                triaPad.run();
                Acords.Acord ac = pads.acords().pads[pad];
                if (!ac.buit()) provaAcord(ac.notes(pads.base()), mini);
            });
            escolta.setOnClickListener(v -> {
                Acords.Acord ac = pads.acords().pads[triat[0]];
                if (!ac.buit()) provaAcord(ac.notes(pads.base()), mini);
            });
            buida.setOnClickListener(v -> {
                Acords a = pads.acords();
                a.pads[triat[0]] = new Acords.Acord();
                if (a.preset != null && !a.preset.isEmpty() && !a.preset.endsWith(" (modificat)")) {
                    a.preset = a.preset + " (modificat)";
                }
                desaAcords(a);
                pads.invalidate();
                posaPreset.run();
                triaPad.run();
            });
            Runnable trasPreset = () -> {   // s'han posat els acords d'un preset
                mini.posaAcords(pads.acords());
                posaPreset.run();
                triaPad.run();
            };

            LinearLayout editorF = editor;
            Runnable pintaLlapis = () -> {
                boolean on = editant[0];
                llapis.setBackground(Ui.tocable(on ? color : Ui.SUPERFICIE2, Ui.dp(this, 18)));
                Ui.colorIcona(llapis, on ? Ui.SOBRE_COLOR : Ui.TEXT);
            };
            Runnable aplicaMode = () -> {
                teclat.deixaTot(android.os.SystemClock.uptimeMillis() * 1_000_000L);
                pads.deixaTot(android.os.SystemClock.uptimeMillis() * 1_000_000L);
                if (!modeAcords[0]) editant[0] = false;
                teclat.setVisibility(modeAcords[0] ? View.GONE : View.VISIBLE);
                pads.setVisibility(modeAcords[0] && !editant[0] ? View.VISIBLE : View.GONE);
                editorF.setVisibility(modeAcords[0] && editant[0] ? View.VISIBLE : View.GONE);
                preset.setVisibility(modeAcords[0] ? View.VISIBLE : View.GONE);
                llapis.setVisibility(modeAcords[0] ? View.VISIBLE : View.GONE);
                if (editant[0]) {
                    mini.posaBase(pads.base());
                    triaPad.run();
                }
                posaRang.run();
                pintaLlapis.run();
                textBase.run();
            };
            LinearLayout interruptor = Ui.segmentat(this, new String[]{"Notes", "Acords"}, modeAcords[0] ? 1 : 0, color, i -> {
                modeAcords[0] = i == 1;
                Ui.prefs(this).edit().putBoolean("acords_encesos", modeAcords[0]).apply();
                aplicaMode.run();
            });
            barra.addView(interruptor, new LinearLayout.LayoutParams(Ui.dp(this, 140), Ui.dp(this, 40)));
            barra.addView(Ui.espai(this, 8));
            llapis.setOnClickListener(v -> {
                editant[0] = !editant[0];
                aplicaMode.run();
            });
            barra.addView(llapis);
            barra.addView(Ui.espai(this, 8));
            preset.setOnClickListener(v -> triaPresetAcords(pads, trasPreset));
            barra.addView(preset, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(this, 36)));
            pads.posaColor(color);
            posaPreset.run();
            zona.addView(pads, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            FrameLayout.LayoutParams le = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
            le.setMargins(Ui.dp(this, 6), Ui.dp(this, 4), Ui.dp(this, 6), 0);
            zona.addView(editor, le);
            // l'interruptor ja s'ha creat: posem cada cosa al seu lloc
            barra.post(aplicaMode);
            teclat.setVisibility(modeAcords[0] ? View.GONE : View.VISIBLE);
            pads.setVisibility(modeAcords[0] ? View.VISIBLE : View.GONE);
            editor.setVisibility(View.GONE);
            preset.setVisibility(modeAcords[0] ? View.VISIBLE : View.GONE);
            llapis.setVisibility(modeAcords[0] ? View.VISIBLE : View.GONE);
            barra.addView(Ui.espai(this, 12));
        }
        barra.addView(octava);
        posaRang.run();
        // el text d'informació només ocupa el lloc que sobra (i corre si no hi cap); «Tanca» no es mou mai
        LinearLayout.LayoutParams lt = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lt.leftMargin = Ui.dp(this, 12);
        barra.addView(textTeclat, lt);
        panell.addView(ambTanca(barra));

        teclat.setPadding(0, 0, 0, 0);
        if (poli) {
            TeclatView.OientPoli op = new TeclatView.OientPoli() {
                @Override
                public void encen(int nota, long nanos) {
                    if (!(gravantSinte || assaig) || (midiViu == null && sinteViu == null)) return;
                    encenViu(nota);
                    if (gravantSinte) notesGravades.encen(nota, 2, msDeNano(nanos));
                    if (textTeclat == null) return;
                    textTeclat.setTextColor(color);
                    textTeclat.setText(Melodia.nomNota(nota));
                }

                @Override
                public void apaga(int nota, long nanos) {
                    if (!(gravantSinte || assaig) || (midiViu == null && sinteViu == null)) return;
                    apagaViu(nota);
                    if (!gravantSinte || textTeclat == null) return;
                    notesGravades.apaga(nota, msDeNano(nanos));
                    textTeclat.setTextColor(Ui.APAGAT);
                    textTeclat.setText(notesGravades.compte() == 1 ? "1 nota" : notesGravades.compte() + " notes");
                }
            };
            teclat.posaOientPoli(op);
            pads.posaOientPoli(op);
        }
        teclat.posaOient((nota, lligada, nanos) -> {
            if (!(gravantSinte || assaig) || sinteViu == null) return;
            if (nota >= 0) sinteViu.nota(nota, lligada);
            else sinteViu.deixa();
            if (textTeclat == null) return;
            if (!gravantSinte) {
                textTeclat.setTextColor(nota >= 0 ? Ui.SINTE : Ui.APAGAT);
                textTeclat.setText(nota >= 0 ? Melodia.nomNota(nota) : "Assaig: no es grava res");
                return;
            }
            notesGravades.canvi(nota, 2, msDeNano(nanos));
            textTeclat.setTextColor(nota >= 0 ? Ui.SINTE : Ui.APAGAT);
            textTeclat.setText(nota >= 0 ? Melodia.nomNota(nota) : notesGravades.compte() == 1 ? "1 nota" : notesGravades.compte() + " notes");
        });
        zona.addView(teclat, 0, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams lk = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        lk.setMargins(Ui.dp(this, 6), 0, Ui.dp(this, 6), Ui.dp(this, 6));
        panell.addView(zona, lk);

        obrePanell(panell);
    }

    /** La quadrícula del teremin, als dos terços de baix. */
    private void mostraTeremin(Projecte.Pista t) {
        amagaTeclat();
        LinearLayout panell = Ui.columna(this);
        panell.setBackgroundColor(Ui.FONS);
        panell.setClickable(true);
        View vora = new View(this);
        vora.setBackgroundColor(Ui.TEREMIN);
        panell.addView(vora, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 2)));

        TereminView pad = new TereminView(this);
        pad.posaCentre(Ui.prefs(this).getInt("teremin_centre", 72));
        pad.posaJustes(Ui.prefs(this).getBoolean("teremin_justes", false));
        LinearLayout barra = Ui.fila(this);
        barra.setPadding(Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 4));
        barra.setGravity(Gravity.CENTER_VERTICAL);
        barra.addView(capcaleraSo(t));
        barra.addView(Ui.espai(this, 12));
        FrameLayout menys = Ui.botoIcona(this, Icona.MENYS, Ui.TEXT, Ui.SUPERFICIE2, 36, 18, "Una octava avall");
        TextView rang = Ui.negreta(this, "", 13, Ui.SUAU);
        FrameLayout mes = Ui.botoIcona(this, Icona.MES, Ui.TEXT, Ui.SUPERFICIE2, 36, 18, "Una octava amunt");
        Runnable posaRang = () -> rang.setText("Mig: " + Melodia.nomNota(pad.centre()));
        menys.setOnClickListener(v -> {
            pad.posaCentre(pad.centre() - 12);
            Ui.prefs(this).edit().putInt("teremin_centre", pad.centre()).apply();
            posaRang.run();
        });
        mes.setOnClickListener(v -> {
            pad.posaCentre(pad.centre() + 12);
            Ui.prefs(this).edit().putInt("teremin_centre", pad.centre()).apply();
            posaRang.run();
        });
        posaRang.run();
        barra.addView(menys);
        barra.addView(Ui.espai(this, 8));
        barra.addView(rang);
        barra.addView(Ui.espai(this, 8));
        barra.addView(mes);
        barra.addView(Ui.espai(this, 12));
        LinearLayout justes = Ui.pastilla(this, 0, "Notes justes", Ui.SUPERFICIE2, Ui.TEXT);
        justes.setMinimumHeight(Ui.dp(this, 36));
        TextView tj = (TextView) justes.getChildAt(justes.getChildCount() - 1);
        Runnable pintaJustes = () -> {
            justes.setBackground(Ui.tocable(pad.justes() ? Ui.TEREMIN : Ui.SUPERFICIE2, Ui.dp(this, 24)));
            tj.setTextColor(pad.justes() ? Ui.SOBRE_COLOR : Ui.TEXT);
        };
        justes.setOnClickListener(v -> {
            pad.posaJustes(!pad.justes());
            Ui.prefs(this).edit().putBoolean("teremin_justes", pad.justes()).apply();
            pintaJustes.run();
            Ui.avis(this, pad.justes() ? "Notes justes: el to salta a la nota més propera" : "Lliure: com un teremin de debò");
        });
        pintaJustes.run();
        barra.addView(justes, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(this, 36)));
        // text curt (corre com un cartell si no hi cap); «Tanca» queda sempre fixat a la dreta
        textTeclat = Ui.cartell(Ui.text(this, assaig ? "Assaig: no es grava res" : "Gravant · el quadrat de dalt atura i desa", 12, Ui.APAGAT));
        textTeclat.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lt = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lt.leftMargin = Ui.dp(this, 12);
        barra.addView(textTeclat, lt);
        panell.addView(ambTanca(barra));

        pad.posaOient(new TereminView.Oient() {
            @Override
            public void toca(double nota, double vol, boolean posat, long nanos) {
                if (!(gravantSinte || assaig) || sinteViu == null) return;
                if (posat) sinteViu.engega(nota, vol);
                else sinteViu.posa(nota, vol);
                if (gravantSinte) gestGravat.posa(msDeNano(nanos), nota, vol);
            }

            @Override
            public void aixeca(long nanos) {
                if (!(gravantSinte || assaig) || sinteViu == null) return;
                sinteViu.deixa();
                if (gravantSinte) gestGravat.aixeca(msDeNano(nanos));
            }
        });
        LinearLayout.LayoutParams lk = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        lk.setMargins(Ui.dp(this, 8), 0, Ui.dp(this, 8), Ui.dp(this, 8));
        panell.addView(pad, lk);

        obrePanell(panell);
    }

    /** Posa el gest gravat al teremin en finestres noves (a partir de l'entrada). */
    private void desaGestGravat(Projecte.Pista t, String motiu) {
        textInfo.setTextColor(Ui.TEREMIN);
        List<Object[]> gestos = gestGravat.gestos(entradaMs, projecte.msPerCompas(), Melodia.MAX_COMPASSOS);
        if (!projecte.pistes.contains(t) || gestos.isEmpty()) {
            textInfo.setText(motiu != null ? motiu : "No has tocat res després del compte: no s'ha desat res");
            refresca();
            return;
        }
        Gest base = gestDeLaPista(t);
        int compasEntrada = (int) Math.round(entradaMs / projecte.msPerCompas()) + 1;
        Projecte.Clip primer = null;
        int finestres = 0;
        for (Object[] o : gestos) {
            int des = (Integer) o[0];
            Gest g = (Gest) o[1];
            g.compas = projecte.compas;
            g.preset = base.preset;
            g.so = base.so.copia();
            try {
                String wav = primer == null && !projecte.enUs(t.wav) && new File(projecte.carpeta, t.wav).isFile()
                        ? t.wav : projecte.wavTereminLliure();
                g.desa(Gest.fitxerDe(projecte.carpeta, wav));
                long durada = g.escriuWav(projecte.carpeta, wav, projecte.bpm, projecte.compas);
                Projecte.Clip c;
                synchronized (projecte) {
                    c = projecte.afegeixFinestraBateria(t, wav, durada, compasEntrada + des);
                }
                carregaSo(wav);
                finestres++;
                if (primer == null) primer = c;
            } catch (IOException e) {
                Ui.avis(this, "No s'ha pogut desar una part de la gravació");
            }
        }
        desa();
        Ui.historial(this, "Gravat el teremin «" + t.nomVisible() + "», entra al compàs " + compasEntrada
                + (finestres > 1 ? ", " + finestres + " finestres" : ""));
        int ip = projecte.pistes.indexOf(t);
        if (primer != null) vista.tria(ip, primer);
        actualitzaAccions();
        if (taulaVisible) ompleTaula();
        textInfo.setText(motiu != null ? motiu : "Teremin gravat · doble toc per escoltar-lo i canviar-ne el so");
        vista.avisa();
        refresca();
    }

    private void amagaTeclat() {
        if (panellTeclat != null && panellTeclat.getParent() instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) panellTeclat.getParent()).removeView(panellTeclat);
        }
        panellTeclat = null;
        textTeclat = null;
        ajustaBarra(getResources().getConfiguration());
    }

    /** Amb el panell obert, a dalt només es veu la fletxa (el nom del projecte s'amaga). */
    private void obrePanell(View panell) {
        int alt = getWindow().getDecorView().getHeight();
        if (alt <= 0) alt = getResources().getDisplayMetrics().heightPixels;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, alt * 2 / 3, Gravity.BOTTOM);
        addContentView(panell, lp);
        panellTeclat = panell;
        if (nomTitol != null) nomTitol.setVisibility(View.GONE);
    }

    /** Atura la gravació del sinte i posa el que s'ha tocat en finestres noves de la pista (a partir de l'entrada). */
    private void aturaGravacioSinte(String motiu) {
        Projecte.Pista t0 = pistaSinte;
        gravant = false;
        gravantSinte = false;
        double araMs = msDeNano(System.nanoTime());
        notesGravades.tanca(araMs);
        gestGravat.aixeca(araMs);
        motor.posaClic(false);
        motor.atura();
        if (sinteViu != null) sinteViu.atura();
        sinteViu = null;
        if (midiViu != null) midiViu.atura();
        midiViu = null;
        amagaTeclat();
        pistes.removeCallbacks(segueix);
        Ui.canviaIcona(botoPlay, Icona.PLAY, Ui.SOBRE_COLOR, 18);
        textInfo.setTextColor(t0 != null ? Ui.colorPista(t0) : Ui.SINTE);
        vista.capcalMs = entradaMs;
        Projecte.Pista t = pistaSinte;
        if (t != null && t.esTeremin()) {
            desaGestGravat(t, motiu);
            return;
        }
        int ppc = Patro.passosDe(projecte.compas);
        List<GravacioNotes.Frase> frases = notesGravades.frases(entradaMs, projecte.msPerCompas(), ppc, Melodia.MAX_COMPASSOS,
                t != null && (t.esMidi() || t.esPoli()));
        if (t == null || !projecte.pistes.contains(t) || frases.isEmpty()) {
            textInfo.setText(motiu != null ? motiu : "No has tocat cap nota després del compte: no s'ha desat res");
            refresca();
            return;
        }
        Melodia base = soDeLaPista(t);
        int compasEntrada = (int) Math.round(entradaMs / projecte.msPerCompas()) + 1;
        Projecte.Clip primer = null;
        int notes = 0, finestres = 0;
        for (GravacioNotes.Frase f : frases) {
            Melodia m = base.buidaAmbElMateixSo();
            m.compas = projecte.compas;
            m.passosPerCompas = ppc;
            m.compassos = f.compassos;
            for (Melodia.Nota n : f.notes) m.posa(new Melodia.Nota(n.pas, n.llarg, n.nota, n.forca));
            notes += m.notes.size();
            try {
                String wav = primer == null && !projecte.enUs(t.wav) && new File(projecte.carpeta, t.wav).isFile()
                        ? t.wav : projecte.wavNotesLliure(t.wav);
                m.desa(Melodia.fitxerDe(projecte.carpeta, wav));
                long durada = m.escriuWav(projecte.carpeta, wav, projecte.bpm, projecte.compas);
                Projecte.Clip c;
                synchronized (projecte) {
                    c = projecte.afegeixFinestraBateria(t, wav, durada, compasEntrada + f.compasDes);
                }
                carregaSo(wav);
                finestres++;
                if (primer == null) primer = c;
            } catch (IOException e) {
                Ui.avis(this, "No s'ha pogut desar una part de la gravació");
            }
        }
        desa();
        Ui.historial(this, "Gravat amb el teclat a «" + t.nomVisible() + "»: " + notes + (notes == 1 ? " nota" : " notes")
                + ", entra al compàs " + compasEntrada + (finestres > 1 ? ", " + finestres + " finestres" : ""));
        int ip = projecte.pistes.indexOf(t);
        if (primer != null) vista.tria(ip, primer);
        actualitzaAccions();
        if (taulaVisible) ompleTaula();
        textInfo.setText(motiu != null ? motiu : notes + (notes == 1 ? " nota gravada" : " notes gravades") + " · doble toc per veure-les al piano roll");
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
            atura();
            double vell = projecte.msPerCompas();
            synchronized (projecte) {
                projecte.bpm = bpm;
                projecte.compas = compas;
                // la bateria segueix els compassos: es mou i es torna a fer al tempo nou
                projecte.escalaBateria(projecte.msPerCompas() / vell);
            }
            double factor = projecte.msPerCompas() / vell;
            for (String w : projecte.wavsBateria()) refaBateria(w, false, factor);
            for (String w : projecte.wavsSinte()) refaBateria(w, false, factor);
            for (String w : projecte.wavsTeremin()) refaBateria(w, false, factor);
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
        String foto;
        try {
            synchronized (projecte) {
                projecte.desa();
                foto = desfent ? null : projecte.foto();
            }
        } catch (IOException e) {
            Ui.avis(this, "No s'ha pogut desar el projecte.");
            return;
        }
        if (foto != null && desfes.registra(foto)) pintaDesfes();
    }

    private void pintaDesfes() {
        if (botoDesfes == null) return;
        boolean pot = desfes.pot();
        botoDesfes.setAlpha(pot ? 1f : 0.35f);
    }

    /** Desfà l'últim canvi del projecte (moure, retallar, esborrar, pistes, volums, tempo, gravacions…). */
    private void desfes() {
        if (gravant) return;
        String foto = desfes.desfes();
        if (foto == null) {
            Ui.avis(this, "No hi ha res per desfer");
            return;
        }
        atura();
        double bpm0 = projecte.bpm;
        int compas0 = projecte.compas;
        synchronized (projecte) {
            projecte.tornaA(foto);
        }
        if (bpm0 != projecte.bpm || compas0 != projecte.compas) {
            // el so de la bateria, el sinte i el teremin es torna a fer al tempo d'abans
            for (String w : projecte.wavsBateria()) refaBateria(w, false, 1);
            for (String w : projecte.wavsSinte()) refaBateria(w, false, 1);
            for (String w : projecte.wavsTeremin()) refaBateria(w, false, 1);
            mostraTempo();
        }
        // sons que potser encara no s'havien carregat
        java.util.Set<String> falten = new java.util.LinkedHashSet<>();
        synchronized (projecte) {
            for (String w : projecte.presesEnUs()) if (!audio.containsKey(w)) falten.add(w);
        }
        for (String w : falten) carregaSo(w);
        desfent = true;
        desa();
        desfent = false;
        vista.tria(-1, null);
        actualitzaAccions();
        ompleCapcaleres();
        posaEtiquetaPistes();
        if (taulaVisible) ompleTaula();
        pistes.requestLayout();
        vista.avisa();
        refresca();
        pintaDesfes();
        textInfo.setTextColor(Ui.SUAU);
        textInfo.setText(desfes.pot() ? "Desfet (queden " + desfes.passos() + " passos)" : "Desfet");
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (panellTeclat != null) tancaPanell();
        else super.onBackPressed();
    }

    /** Tanca el panell del teclat o del teremin: si s'està gravant, primer atura i desa la gravació. */
    private void tancaPanell() {
        if (gravantSinte) aturaGravacio(null);
        else tancaAssaig();
        amagaTeclat();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (gravant) aturaGravacio("S'ha aturat la gravació (l'app ha passat a segon pla)");
        tancaAssaig();
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
