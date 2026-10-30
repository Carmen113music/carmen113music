package cat.riffs;

import android.app.Activity;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Drumpad d'una pista de bateria: una graella amb una fila per tipus de so del kit i una casella per semicorxera.
 * El ritme es desa sol; en sortir, es torna a fer el so de la pista perquè l'Estudi el faci sonar.
 */
public class DrumpadActivity extends Activity {

    public static final String EXTRA_DIR = "dir";
    public static final String EXTRA_WAV = "wav";
    public static final String EXTRA_NOM = "nom";
    public static final String EXTRA_BPM = "bpm";
    public static final String EXTRA_COMPAS = "compas";

    private File carpeta;
    private String wav;
    private double bpm;
    private int compas;
    private Patro patro;
    private List<Kits.Kit> kits = new ArrayList<>();
    private final Map<String, String> nomsSons = new HashMap<>();
    private final Map<String, short[]> cau = new HashMap<>();
    private Function<String, short[]> sons;
    private boolean canviat = false;

    private GraellaView graella;
    private LinearLayout filaCompassos;
    private TextView textKit, textCompassos;
    private FrameLayout botoPlay;

    // so
    private AudioTrack bucle;
    private int llargBucle;
    private AudioTrack prova;

    private final Runnable segueix = new Runnable() {
        @Override
        public void run() {
            if (bucle == null || llargBucle <= 0) return;
            int pos;
            try {
                pos = bucle.getPlaybackHeadPosition() % llargBucle;
            } catch (RuntimeException e) {
                return;
            }
            graella.posaPasSonant((int) ((long) pos * patro.passos() / llargBucle));
            graella.postOnAnimation(this);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setNavigationBarColor(Ui.FONS);
        Intent in = getIntent();
        String d = in.getStringExtra(EXTRA_DIR);
        wav = in.getStringExtra(EXTRA_WAV);
        bpm = in.getDoubleExtra(EXTRA_BPM, 120);
        compas = in.getIntExtra(EXTRA_COMPAS, 4);
        carpeta = d == null ? null : new File(d);
        if (carpeta == null || wav == null) {
            finish();
            return;
        }
        File arrelKits = KitsActivity.arrel(this);
        kits = Kits.llista(arrelKits);
        for (Kits.Kit k : kits) for (Kits.So s : k.sons) nomsSons.put(Patro.ref(k, s), s.nom);
        sons = Patro.lectorSons(arrelKits, cau);
        patro = Patro.llegeix(Patro.fitxerDe(carpeta, wav));
        if (patro == null) {
            patro = Patro.nou(kits.isEmpty() ? null : kits.get(0), compas);
            canviat = true;
        } else if (patro.passosPerCompas != Patro.passosDe(compas)) {
            patro.ajustaCompas(compas);
            canviat = true;
        }

        LinearLayout arrel = Ui.pantalla(this);

        // barra de dalt
        LinearLayout dalt = Ui.fila(this);
        dalt.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4));
        FrameLayout enrere = Ui.botoIcona(this, Icona.ENRERE, Ui.TEXT, 0x00000000, 44, 24, "Enrere");
        enrere.setOnClickListener(v -> surt());
        dalt.addView(enrere);
        LinearLayout tit = Ui.columna(this);
        String nom = in.getStringExtra(EXTRA_NOM);
        tit.addView(Ui.unaLinia(Ui.negreta(this, nom == null ? "Bateria" : nom, 16, Ui.TEXT)));
        tit.addView(Ui.unaLinia(Ui.text(this, Math.round(bpm) + " BPM · " + Ritme.nomCompas(compas), 12, Ui.SUAU)));
        LinearLayout.LayoutParams lt = Ui.pes(1);
        lt.leftMargin = Ui.dp(this, 4);
        dalt.addView(tit, lt);

        LinearLayout kit = Ui.pastilla(this, Icona.BATERIA, "", Ui.SUPERFICIE2, Ui.TEXT);
        kit.setMinimumHeight(Ui.dp(this, 40));
        textKit = (TextView) kit.getChildAt(kit.getChildCount() - 1);
        kit.setOnClickListener(v -> triaKit());
        dalt.addView(kit, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(this, 40)));
        dalt.addView(Ui.espai(this, 8));
        LinearLayout comp = Ui.pastilla(this, 0, "", Ui.SUPERFICIE2, Ui.TEXT);
        textCompassos = (TextView) comp.getChildAt(comp.getChildCount() - 1);
        comp.setOnClickListener(v -> triaCompassos());
        dalt.addView(comp, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(this, 40)));
        dalt.addView(Ui.espai(this, 8));
        botoPlay = Ui.botoIcona(this, Icona.PLAY, Ui.SOBRE_COLOR, Ui.BATERIA, 44, 20, "Escolta el ritme");
        botoPlay.setOnClickListener(v -> {
            if (bucle != null) aturaBucle();
            else sonaBucle(0);
        });
        dalt.addView(botoPlay);
        arrel.addView(dalt);

        // pestanyes dels compassos i ajuda
        LinearLayout segona = Ui.fila(this);
        segona.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), Ui.dp(this, 4));
        filaCompassos = Ui.fila(this);
        segona.addView(filaCompassos);
        TextView ajuda = Ui.text(this, "Toca per posar o treure un cop · mantén premut i arrossega amunt o avall per la força · toca el nom per triar el so", 11, Ui.APAGAT);
        ajuda.setMaxLines(2);
        LinearLayout.LayoutParams la = Ui.pes(1);
        la.leftMargin = Ui.dp(this, 12);
        segona.addView(ajuda, la);
        arrel.addView(segona);

        ScrollView sc = new ScrollView(this);
        sc.setFillViewport(true);
        graella = new GraellaView(this, i -> {
            Patro.Fila f = patro.files.get(i);
            String n = nomsSons.get(f.so);
            return n == null ? "(el so no hi és)" : n;
        });
        graella.setPadding(0, 0, Ui.dp(this, 8), 0);
        sc.addView(graella, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        arrel.addView(sc, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(arrel);

        graella.posa(patro, Patro.grupDe(compas), new GraellaView.Oients() {
            @Override
            public void canvi(boolean acabat) {
                canviat = true;
                if (acabat) {
                    desaPatro();
                    if (bucle != null) reprenBucle();
                }
            }

            @Override
            public void prova(int fila, int forca) {
                if (bucle == null) provaSo(patro.files.get(fila), forca);
            }

            @Override
            public void fila(int fila) {
                opcionsFila(fila);
            }
        });
        actualitzaCapcalera();

        if (kits.isEmpty()) {
            Full.avis(this, "Encara no tens kits de bateria",
                    "Ves a Diagnòstic i ajustos → Bateria virtual i baixa'ls de GitHub o importa'ls. Després torna a obrir aquesta pista.",
                    "D'acord", () -> {});
        }
    }

    private void actualitzaCapcalera() {
        String nk = "Sense kit";
        for (Kits.Kit k : kits) if (k.dir.getName().equals(patro.kit)) nk = k.nom;
        textKit.setText(nk);
        textCompassos.setText(patro.compassos == 1 ? "1 compàs" : patro.compassos + " compassos");
        filaCompassos.removeAllViews();
        if (patro.compassos > 1) {
            String[] ops = new String[patro.compassos];
            for (int i = 0; i < ops.length; i++) ops[i] = "Compàs " + (i + 1);
            filaCompassos.addView(Ui.segmentat(this, ops, graella.compasVisible(), Ui.BATERIA, i -> graella.posaCompas(i)));
        }
        graella.requestLayout();
        graella.invalidate();
    }

    // ---------- Opcions ----------

    private void triaKit() {
        if (kits.isEmpty()) {
            Ui.avis(this, "Encara no tens kits: ves a Diagnòstic i ajustos → Bateria virtual");
            return;
        }
        List<Full.Accio> ops = new ArrayList<>();
        for (Kits.Kit k : kits) {
            boolean ara = k.dir.getName().equals(patro.kit);
            ops.add(new Full.Accio(Icona.BATERIA, k.nom + (ara ? "  (ara)" : ""), false, () -> {
                patro.canviaKit(k);
                canviat = true;
                desaPatro();
                actualitzaCapcalera();
                if (bucle != null) reprenBucle();
            }));
        }
        Full.llista(this, "Kit de bateria", "Cada fila agafa el primer so del seu tipus del kit nou. Els cops no es toquen.", ops);
    }

    private void triaCompassos() {
        List<Full.Accio> ops = new ArrayList<>();
        for (int n = 1; n <= Patro.MAX_COMPASSOS; n++) {
            int nn = n;
            String t = (n == 1 ? "1 compàs" : n + " compassos") + (n == patro.compassos ? "  (ara)" : "");
            ops.add(new Full.Accio(Icona.GRAELLA, t, false, () -> {
                if (nn == patro.compassos) return;
                patro.posaCompassos(nn);
                graella.posaCompas(Math.min(graella.compasVisible(), nn - 1));
                canviat = true;
                desaPatro();
                actualitzaCapcalera();
                if (bucle != null) reprenBucle();
            }));
        }
        Full.llista(this, "Llargada del ritme", "Si l'allargues, es copien els compassos que ja tens. A l'Estudi, la finestra s'allarga o s'escurça (mira que no trepitgi la del costat).", ops);
    }

    private void opcionsFila(int i) {
        Patro.Fila f = patro.files.get(i);
        List<Full.Accio> ac = new ArrayList<>();
        ac.add(new Full.Accio(Icona.BATERIA, "Tria el so", false, () -> triaSo(f)));
        ac.add(new Full.Accio(Icona.TAULA, "Volum", false, () ->
                Full.lliscador(this, "Volum · " + Kits.nomTipus(f.tipus), (int) Math.round(f.volumDb - Patro.VOLUM_MIN_DB),
                        (int) (Patro.VOLUM_MAX_DB - Patro.VOLUM_MIN_DB), v -> {
                            f.volumDb = v + Patro.VOLUM_MIN_DB;
                            canviat = true;
                            desaPatro();
                            if (bucle != null) reprenBucle();
                        }, v -> Projecte.textDb(v + Patro.VOLUM_MIN_DB))));
        ac.add(new Full.Accio(f.mut ? Icona.PLAY : Icona.MENYS, f.mut ? "Activa" : "Silencia", false, () -> {
            f.mut = !f.mut;
            canviat = true;
            desaPatro();
            graella.invalidate();
            if (bucle != null) reprenBucle();
        }));
        ac.add(new Full.Accio(Icona.PAPERERA, "Treu els cops", true, () -> {
            java.util.Arrays.fill(f.cops, 0);
            canviat = true;
            desaPatro();
            graella.invalidate();
            if (bucle != null) reprenBucle();
        }));
        String so = nomsSons.get(f.so);
        Full.accions(this, Icona.BATERIA, Ui.BATERIA, Kits.nomTipus(f.tipus), so == null ? "" : so + "  ·  " + Projecte.textDb(f.volumDb), ac);
    }

    private void triaSo(Patro.Fila f) {
        List<Full.Accio> ops = new ArrayList<>();
        for (Kits.Kit k : kits) {
            for (Kits.So s : k.delTipus(f.tipus)) {
                String ref = Patro.ref(k, s);
                boolean ara = ref.equals(f.so);
                ops.add(new Full.Accio(ara ? Icona.BATERIA : Icona.PLAY, s.nom + " · " + k.nom + (ara ? "  (ara)" : ""), false, () -> {
                    f.so = ref;
                    canviat = true;
                    desaPatro();
                    graella.invalidate();
                    if (bucle != null) reprenBucle();
                    else provaSo(f, 2);
                }));
            }
        }
        if (ops.isEmpty()) {
            Ui.avis(this, "No hi ha cap so d'aquest tipus als kits");
            return;
        }
        Full.llista(this, Kits.nomTipus(f.tipus), "Sons d'aquest tipus de tots els teus kits. En triar-ne un, sona.", ops);
    }

    // ---------- So ----------

    private AudioTrack nouTrack(short[] so) {
        AudioTrack t = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setSampleRate(Kits.MOSTREIG)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                .setBufferSizeInBytes(Math.max(so.length, 2) * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build();
        t.write(so, 0, so.length);
        return t;
    }

    private void provaSo(Patro.Fila f, int forca) {
        short[] s = sons.apply(f.so);
        if (s == null || s.length == 0) return;
        float g = Patro.MARGE * Patro.guany(f.volumDb) * Patro.FORCA[Math.max(1, Math.min(3, forca))];
        short[] r = new short[s.length];
        for (int i = 0; i < s.length; i++) r[i] = (short) Math.max(-32768, Math.min(32767, Math.round(s[i] * g)));
        aturaProva();
        try {
            prova = nouTrack(r);
            prova.play();
        } catch (RuntimeException ignorat) {
        }
    }

    private void aturaProva() {
        if (prova != null) {
            try {
                prova.stop();
            } catch (RuntimeException ignorat) {
            }
            prova.release();
            prova = null;
        }
    }

    /** Fa sonar el ritme en bucle, des de la mostra {@code pos}. */
    private void sonaBucle(int pos) {
        aturaProva();
        short[] so = patro.renderitza(bpm, compas, sons);
        try {
            AudioTrack t = nouTrack(so);
            t.setLoopPoints(0, so.length, -1);
            if (pos > 0 && pos < so.length) t.setPlaybackHeadPosition(pos);
            t.play();
            bucle = t;
            llargBucle = so.length;
        } catch (RuntimeException e) {
            Ui.avis(this, "No s'ha pogut fer sonar el ritme");
            return;
        }
        Ui.canviaIcona(botoPlay, Icona.STOP, Ui.SOBRE_COLOR, 18);
        graella.removeCallbacks(segueix);
        graella.postOnAnimation(segueix);
    }

    /** El ritme ha canviat mentre sona: es torna a fer i continua des del mateix punt. */
    private void reprenBucle() {
        int pos = 0;
        long vell = llargBucle;
        if (bucle != null) {
            try {
                pos = bucle.getPlaybackHeadPosition() % Math.max(1, llargBucle);
            } catch (RuntimeException ignorat) {
            }
        }
        alliberaBucle();
        long nou = Math.round(patro.compassos * Patro.msCompas(bpm, compas) * Kits.MOSTREIG / 1000.0);
        if (vell > 0 && nou != vell) pos = (int) (pos % nou);
        sonaBucle(pos);
    }

    private void alliberaBucle() {
        if (bucle != null) {
            try {
                bucle.stop();
            } catch (RuntimeException ignorat) {
            }
            bucle.release();
            bucle = null;
        }
    }

    private void aturaBucle() {
        alliberaBucle();
        graella.removeCallbacks(segueix);
        graella.posaPasSonant(-1);
        Ui.canviaIcona(botoPlay, Icona.PLAY, Ui.SOBRE_COLOR, 20);
    }

    // ---------- Desar i sortir ----------

    private void desaPatro() {
        try {
            patro.desa(Patro.fitxerDe(carpeta, wav));
        } catch (IOException e) {
            Ui.avis(this, "No s'ha pogut desar el ritme");
        }
    }

    /** Desa el ritme i en fa el so per a l'Estudi. */
    private void generaSo() {
        if (!canviat && new File(carpeta, wav).isFile()) return;
        desaPatro();
        try {
            patro.escriuWav(carpeta, wav, bpm, compas, sons);
            canviat = false;
        } catch (IOException e) {
            Ui.avis(this, "No s'ha pogut fer el so de la bateria");
        }
    }

    private void surt() {
        aturaBucle();
        aturaProva();
        generaSo();
        Intent r = new Intent();
        r.putExtra(EXTRA_WAV, wav);
        setResult(RESULT_OK, r);
        finish();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        surt();
    }

    @Override
    protected void onPause() {
        super.onPause();
        aturaBucle();
        aturaProva();
        if (patro != null) generaSo();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        alliberaBucle();
        aturaProva();
    }

    /** Text curt per a l'historial: «Bateria: 12 cops, 1 compàs, kit Gimme». */
    static String resum(Patro p, List<Kits.Kit> kits) {
        String nk = "";
        for (Kits.Kit k : kits) if (k.dir.getName().equals(p.kit)) nk = k.nom;
        return String.format(Locale.ROOT, "%d cops, %s%s", p.compte(),
                p.compassos == 1 ? "1 compàs" : p.compassos + " compassos", nk.isEmpty() ? "" : ", kit " + nk);
    }
}
