package cat.riffs;

import android.app.Activity;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Piano roll d'una finestra de sinte: notes sobre una graella (una fila per nota, una columna per casella).
 * La frase es desa sola; en sortir, es torna a fer el so de la finestra perquè l'Estudi el faci sonar.
 * Si s'ha canviat el so del sinte, l'Estudi el posa a totes les finestres de la pista.
 */
public class PianoRollActivity extends Activity {

    public static final String EXTRA_SO_CANVIAT = "so_canviat";
    /** Quantes notes (files) caben a la pantalla: una octava i mitja. */
    private static final float FILES_VISIBLES = 18f;
    /** Alçada de les files triada amb dos dits (dp; 0 = la de per defecte). */
    private static final String PREF_FILA = "piano_fila_dp";

    private File carpeta;
    private String wav;
    private double bpm;
    private int compas;
    private Melodia melodia;
    private boolean canviat = false;
    private boolean soCanviat = false;

    private PianoRollView roll;
    private ScrollView scroll;
    private TextView subtitol;
    private TextView textSo, textCompassos, textDivisio;
    private FrameLayout botoPlay;
    private FrameLayout botoDesfes;
    private final Desfes desfes = new Desfes();
    private boolean desfent = false;
    private BotoSolo botoSolo;
    private final android.media.AudioTimestamp marca = new android.media.AudioTimestamp();

    // so
    private AudioTrack bucle;
    private int llargBucle;
    private int iniciBucle;
    private AudioTrack prova;

    private final Runnable segueix = new Runnable() {
        @Override
        public void run() {
            if (bucle == null || llargBucle <= 0) return;
            long pos = posicioQueSona();
            if (pos < 0) {
                roll.posaPasSonant(-1);
            } else {
                pos %= llargBucle;
                roll.posaPasSonant((int) (pos * melodia.passos() / llargBucle));
            }
            roll.postOnAnimation(this);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setNavigationBarColor(Ui.FONS);
        Intent in = getIntent();
        String d = in.getStringExtra(DrumpadActivity.EXTRA_DIR);
        wav = in.getStringExtra(DrumpadActivity.EXTRA_WAV);
        bpm = in.getDoubleExtra(DrumpadActivity.EXTRA_BPM, 120);
        compas = in.getIntExtra(DrumpadActivity.EXTRA_COMPAS, 4);
        carpeta = d == null ? null : new File(d);
        if (carpeta == null || wav == null) {
            finish();
            return;
        }
        BancActivity.prepara(this);
        melodia = Melodia.llegeix(Melodia.fitxerDe(carpeta, wav));
        if (melodia == null) {
            melodia = Melodia.nova(Sinte.PRESETS[0], compas);
            canviat = true;
        } else if (melodia.assegura(compas)) {
            canviat = true;
        }

        desfes.inici(melodia.text());
        // el banc MIDI es llegeix d'entrada (el primer cop triga una mica)
        if (melodia.esMidi()) new Thread(Banc::actual, "banc").start();
        LinearLayout arrel = Ui.pantalla(this);

        // barra de dalt
        LinearLayout dalt = Ui.fila(this);
        dalt.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4));
        FrameLayout enrere = Ui.botoIcona(this, Icona.ENRERE, Ui.TEXT, 0x00000000, 44, 24, "Enrere");
        enrere.setOnClickListener(v -> surt());
        dalt.addView(enrere);
        LinearLayout tit = Ui.columna(this);
        String nom = in.getStringExtra(DrumpadActivity.EXTRA_NOM);
        nomInicial = nom == null ? "Sinte" : nom;
        presetInicial = melodia.preset;
        titol = Ui.unaLinia(Ui.negreta(this, nomInicial, 16, Ui.TEXT));
        tit.addView(titol);
        subtitol = Ui.unaLinia(Ui.text(this, Math.round(bpm) + " BPM · " + Ritme.nomCompas(compas), 12, Ui.SUAU));
        tit.addView(subtitol);
        LinearLayout.LayoutParams lt = Ui.pes(1);
        lt.leftMargin = Ui.dp(this, 4);
        dalt.addView(tit, lt);

        botoDesfes = Ui.botoIcona(this, Icona.DESFES, Ui.TEXT, Ui.SUPERFICIE2, 40, 20, "Desfés l'últim canvi");
        botoDesfes.setOnClickListener(v -> desfes());
        dalt.addView(botoDesfes);
        dalt.addView(Ui.espai(this, 8));
        LinearLayout so = Ui.pastilla(this, melodia.esMidi() ? Icona.MIDI : melodia.poliSinte ? Icona.POLI : Icona.SINTE, "", Ui.SUPERFICIE2, Ui.TEXT);
        textSo = (TextView) so.getChildAt(so.getChildCount() - 1);
        so.setOnClickListener(v -> triaSo());
        dalt.addView(so, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(this, 40)));
        dalt.addView(Ui.espai(this, 8));
        LinearLayout comp = Ui.pastilla(this, 0, "", Ui.SUPERFICIE2, Ui.TEXT);
        textCompassos = (TextView) comp.getChildAt(comp.getChildCount() - 1);
        comp.setOnClickListener(v -> triaCompassos());
        dalt.addView(comp, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(this, 40)));
        dalt.addView(Ui.espai(this, 8));
        LinearLayout div = Ui.pastilla(this, Icona.GRAELLA, "", Ui.SUPERFICIE2, Ui.TEXT);
        textDivisio = (TextView) div.getChildAt(div.getChildCount() - 1);
        div.setOnClickListener(v -> triaDivisio());
        dalt.addView(div, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(this, 40)));
        dalt.addView(Ui.espai(this, 8));
        botoSolo = new BotoSolo(this, color(), () -> {
            if (bucle != null) reprenBucle();
        });
        dalt.addView(botoSolo.vista, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(this, 40)));
        dalt.addView(Ui.espai(this, 8));
        botoPlay = Ui.botoIcona(this, Icona.PLAY, Ui.SOBRE_COLOR, color(), 44, 20, "Escolta la frase");
        botoPlay.setOnClickListener(v -> {
            if (bucle != null) aturaBucle();
            else sonaBucle(0);
        });
        dalt.addView(botoPlay);
        arrel.addView(dalt);

        // ajuda (2.31.2: sense pestanyes de compàs; el compàs que es veu surt al subtítol de dalt)
        LinearLayout segona = Ui.fila(this);
        segona.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), Ui.dp(this, 4));
        TextView ajuda = Ui.text(this, "Toca per posar una nota i toca-la per treure-la · arrossega-la per moure-la · "
                + "arrossega la punta dreta per allargar-la · dos dits: obre'ls per fer zoom (de costat, el temps; amunt i avall, les files) i arrossega per moure't", 11, Ui.APAGAT);
        ajuda.setMaxLines(2);
        LinearLayout.LayoutParams la = Ui.pes(1);
        segona.addView(ajuda, la);
        arrel.addView(segona);

        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(true);
        roll = new PianoRollView(this);
        roll.posaColor(color());
        roll.setPadding(0, 0, Ui.dp(this, 6), 0);
        scroll.addView(roll, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        arrel.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(arrel);
        // la resta de pistes de l'Estudi, per sentir-les amb el bucle (si el Solo no està encès)
        botoSolo.carrega(carpeta, wav, in.getLongExtra(DrumpadActivity.EXTRA_POS, -1),
                Math.round(Melodia.MAX_COMPASSOS * Patro.msCompas(bpm, compas)), () -> {
                    if (bucle != null) reprenBucle();
                });

        roll.posa(melodia, new PianoRollView.Oients() {
            @Override
            public void canvi(boolean acabat) {
                canviat = true;
                if (acabat) {
                    desaMelodia();
                    if (bucle != null) reprenBucle();
                }
            }

            @Override
            public void prova(int nota) {
                if (bucle == null) provaNota(nota);
            }

            @Override
            public void compas(int c) {
                subtitol.post(() -> actualitzaCapcalera());
            }

            @Override
            public void alcada(float dp) {
                Ui.prefs(PianoRollActivity.this).edit().putFloat(PREF_FILA, dp).apply();
            }
        });
        actualitzaCapcalera();
        pintaDesfes();
        // una octava i mitja a la pantalla, centrada on hi ha les notes (o on toca segons el so)
        scroll.post(() -> {
            int h = scroll.getHeight();
            if (h <= 0) return;
            float dp = Ui.prefs(this).getFloat(PREF_FILA, 0);
            roll.posaAltFila(dp > 0 ? dp * getResources().getDisplayMetrics().density : h / FILES_VISIBLES);
            scroll.post(() -> centra(h));
        });
    }

    /** Desplaça el piano roll perquè es vegin les notes de la frase (o la zona del baix o del lead si és buida). */
    private void centra(int h) {
        int centre;
        if (!melodia.notes.isEmpty()) {
            int min = 999, max = -1;
            for (Melodia.Nota n : melodia.notes) {
                min = Math.min(min, n.nota);
                max = Math.max(max, n.nota);
            }
            centre = (min + max) / 2;
        } else {
            centre = melodia.esGreu() ? 42 : 66;   // Fa#1 / Fa#3
        }
        scroll.scrollTo(0, Math.max(0, Math.round(roll.yDe(centre) - h / 2f)));
    }

    private TextView titol;
    private String nomInicial, presetInicial;

    private void actualitzaCapcalera() {
        textSo.setText(melodia.preset == null || melodia.preset.isEmpty() ? "So" : melodia.preset);
        if (titol != null) titol.setText(Ui.nomAmbSo(nomInicial, presetInicial, melodia.preset));
        textCompassos.setText(melodia.compassos == 1 ? "1 compàs" : melodia.compassos + " compassos");
        textDivisio.setText(melodia.divisio() + "/temps");
        subtitol.setText(Math.round(bpm) + " BPM · " + Ritme.nomCompas(compas)
                + (melodia.compassos > 1 ? " · compàs " + (roll.compasVisible() + 1) + " de " + melodia.compassos : ""));
        roll.invalidate();
    }

    // ---------- Opcions ----------

    private int color() {
        return melodia == null ? Ui.SINTE : melodia.esMidi() ? Ui.MIDI : melodia.poliSinte ? Ui.POLI : Ui.SINTE;
    }

    /** Pista MIDI: primer la família i després l'instrument (cada un sona en tocar-lo). */
    private void triaInstrument() {
        if (Banc.actual() == null) {
            Full.avis(this, "No hi ha banc de sons", "Cal baixar el banc MIDI: Gravar → botó d'ajustos → Instruments MIDI.",
                    "Obre", () -> startActivity(new Intent(this, BancActivity.class)));
            return;
        }
        List<Full.Accio> ops = new ArrayList<>();
        int actual = melodia.programa / 8;
        for (int f = 0; f < Gm.FAMILIES.length; f++) {
            int ff = f;
            ops.add(new Full.Accio(Icona.MIDI, Gm.FAMILIES[f] + (f == actual ? "  (ara)" : ""), false, () -> triaDeFamilia(ff)));
        }
        Full.llista(this, "Instrument", "Ara: " + Gm.nom(melodia.programa) + ". Tria una família.", ops);
    }

    private void triaDeFamilia(int f) {
        String[] noms = new String[8];
        for (int i = 0; i < 8; i++) noms[i] = Gm.nom(f * 8 + i);
        int triat = melodia.programa / 8 == f ? melodia.programa % 8 : -1;
        int original = melodia.programa;
        Full.triaAmbProva(this, Gm.FAMILIES[f], "Toca un instrument per escoltar-lo; queda triat. «Fet» per tancar. "
                        + "Totes les finestres d'aquesta pista agafaran aquest instrument.",
                noms, triat, Ui.MIDI,
                i -> provaInstrument(f * 8 + i),
                i -> posaPrograma(f * 8 + i),
                () -> posaPrograma(original),   // «<»: torna a l'instrument d'abans
                this::triaInstrument);           // i a la llista de famílies
    }

    private void posaPrograma(int prog) {
        if (prog == melodia.programa) return;
        melodia.posaInstrument(prog);
        canviat = true;
        soCanviat = true;
        desaMelodia();
        actualitzaCapcalera();
        if (bucle != null) reprenBucle();
    }

    /** Un arpegi i un acord curts amb l'instrument, per sentir com sona. */
    private void provaInstrument(int prog) {
        boolean greu = Gm.esGreu(prog);
        int base = greu ? 36 : 60;
        int pas = (int) Math.round(60000.0 / bpm / 2 * Sinte.MOSTREIG / 1000.0);
        List<Sinte.Toc> t = new ArrayList<>();
        int[] ns = {0, 4, 7, 12};
        for (int i = 0; i < ns.length; i++) t.add(new Sinte.Toc(i * pas, i * pas + pas * 9 / 10, base + ns[i], 2));
        for (int n : new int[]{0, 4, 7}) t.add(new Sinte.Toc(4 * pas, 8 * pas, base + n, 2));
        float[] x = MidiSo.toca(Banc.actual(), 0, prog, t, 8 * pas, false);
        int fi = x.length;
        while (fi > 8 * pas && Math.abs(x[fi - 1]) < 1e-4f) fi--;
        sona(Sinte.a16(java.util.Arrays.copyOf(x, fi), 0));
    }

    private void triaSo() {
        if (melodia.esMidi()) {
            triaInstrument();
            return;
        }
        if (melodia.poliSinte) {
            triaFamiliaPoli();
            return;
        }
        String[] noms = new String[Sinte.PRESETS.length];
        int triat = -1;
        for (int i = 0; i < noms.length; i++) {
            noms[i] = Sinte.PRESETS[i].nom;
            if (Sinte.PRESETS[i].nom.equals(melodia.preset)) triat = i;
        }
        Full.triaAmbProva(this, "So del sinte", "Toca un so per escoltar-lo; queda triat. «Fet» per tancar. "
                        + "Totes les finestres d'aquesta pista agafaran aquest so.",
                noms, triat, Ui.SINTE,
                i -> provaPreset(Sinte.PRESETS[i]),
                i -> {
                    if (Sinte.PRESETS[i].nom.equals(melodia.preset)) return;
                    melodia.posaPreset(Sinte.PRESETS[i]);
                    canviat = true;
                    soCanviat = true;
                    desaMelodia();
                    actualitzaCapcalera();
                    if (bucle != null) reprenBucle();
                });
    }

    /** Sinte polifònic: primer la família (pads, tecles…) i després el so (cada un sona en tocar-lo). */
    private void triaFamiliaPoli() {
        List<Full.Accio> ops = new ArrayList<>();
        int actual = Poli.familiaDe(melodia.preset);
        for (int f = 0; f < Poli.FAMILIES.length; f++) {
            int ff = f;
            ops.add(new Full.Accio(Icona.POLI, Poli.FAMILIES[f] + (f == actual ? "  (ara)" : ""), false, () -> triaPoliDeFamilia(ff)));
        }
        Full.llista(this, "So del sinte polifònic", "Ara: " + melodia.preset + ". Tria una família.", ops);
    }

    private void triaPoliDeFamilia(int f) {
        List<Sinte.Preset> ps = Poli.deFamilia(f);
        String[] noms = new String[ps.size()];
        int triat = -1;
        for (int i = 0; i < noms.length; i++) {
            noms[i] = ps.get(i).nom;
            if (noms[i].equals(melodia.preset)) triat = i;
        }
        Sinte.Preset original = Poli.preset(melodia.preset);
        Full.triaAmbProva(this, Poli.FAMILIES[f], "Toca un so per escoltar-lo; queda triat. «Fet» per tancar. "
                        + "Totes les finestres d'aquesta pista agafaran aquest so.",
                noms, triat, Ui.POLI,
                i -> provaPoli(ps.get(i)),
                i -> posaPresetPoli(ps.get(i)),
                () -> {
                    if (original != null) posaPresetPoli(original);   // «<»: torna al so d'abans
                },
                this::triaFamiliaPoli);
    }

    private void posaPresetPoli(Sinte.Preset p) {
        if (p.nom.equals(melodia.preset)) return;
        melodia.posaPreset(p);
        canviat = true;
        soCanviat = true;
        desaMelodia();
        actualitzaCapcalera();
        if (bucle != null) reprenBucle();
    }

    /** Un arpegi i un acord amb un so del sinte polifònic. */
    private void provaPoli(Sinte.Preset p) {
        int pas = (int) Math.round(60000.0 / bpm / 2 * Sinte.MOSTREIG / 1000.0);
        sona(Sinte.a16(Poli.toca(p.so, Poli.tocsDeProva(pas), 8 * pas, false, bpm), p.so.volumDb));
    }

    private void triaCompassos() {
        List<Full.Accio> ops = new ArrayList<>();
        for (int n = 1; n <= Melodia.MAX_COMPASSOS; n++) {
            int nn = n;
            String t = (n == 1 ? "1 compàs" : n + " compassos") + (n == melodia.compassos ? "  (ara)" : "");
            ops.add(new Full.Accio(Icona.GRAELLA, t, false, () -> {
                if (nn == melodia.compassos) return;
                melodia.posaCompassos(nn);
                roll.limita();
                canviat = true;
                desaMelodia();
                actualitzaCapcalera();
                if (bucle != null) reprenBucle();
            }));
        }
        Full.llista(this, "Llargada de la frase", "Els compassos nous surten en blanc; si l'escurces, es perden les notes del final. "
                + "A l'Estudi, la finestra s'allarga o s'escurça (mira que no trepitgi la del costat).", ops);
    }

    private void triaDivisio() {
        List<Full.Accio> ops = new ArrayList<>();
        for (int dv : Patro.divisions(compas)) {
            boolean ara = dv == melodia.divisio();
            ops.add(new Full.Accio(Icona.GRAELLA, Patro.nomDivisio(compas, dv) + (ara ? "  (ara)" : ""), false, () -> {
                if (dv == melodia.divisio()) return;
                melodia.posaDivisio(dv);
                canviat = true;
                desaMelodia();
                actualitzaCapcalera();
                if (bucle != null) reprenBucle();
            }));
        }
        Full.llista(this, "Graella", "En quantes caselles es parteix cada temps. Les notes que ja hi ha es queden al seu lloc.", ops);
    }

    // ---------- So ----------

    private AudioTrack nouTrack(short[] so) {
        AudioTrack t = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setSampleRate(Sinte.MOSTREIG)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                .setBufferSizeInBytes(Math.max(so.length, 2) * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build();
        t.write(so, 0, so.length);
        return t;
    }

    private void sona(short[] s) {
        aturaProva();
        if (s == null || s.length == 0) return;
        try {
            prova = nouTrack(s);
            prova.play();
        } catch (RuntimeException ignorat) {
        }
    }

    private void provaNota(int nota) {
        sona(melodia.prova(nota, 380));
    }

    /** Un trosset de frase amb el so de fàbrica, per sentir com sona abans de triar-lo. */
    private void provaPreset(Sinte.Preset p) {
        int[] ns = p.greu ? new int[]{36, 36, 43, 48} : new int[]{60, 63, 67, 72};
        int pas = (int) Math.round(60000.0 / bpm / 2 * Sinte.MOSTREIG / 1000.0);   // corxeres
        List<Sinte.Toc> t = new ArrayList<>();
        for (int i = 0; i < ns.length; i++) t.add(new Sinte.Toc(i * pas, i * pas + pas * 9 / 10, ns[i], 2));
        sona(Sinte.a16(Sinte.toca(p.so, t, pas * ns.length, false), p.so.volumDb));
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

    private long posicioQueSona() {
        try {
            if (bucle.getTimestamp(marca) && marca.nanoTime > 0) {
                double s = (System.nanoTime() - marca.nanoTime) / 1e9;
                return marca.framePosition + Math.round(s * Sinte.MOSTREIG) + iniciBucle;
            }
            return -1;
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private void sonaBucle(int pos) {
        aturaProva();
        short[] so = botoSolo.so(melodia.renderitza(bpm, compas));
        int inici = pos > 0 && pos < so.length ? pos : 0;
        if (inici > 0) {
            short[] r = new short[so.length];
            System.arraycopy(so, inici, r, 0, so.length - inici);
            System.arraycopy(so, 0, r, so.length - inici, inici);
            so = r;
        }
        try {
            AudioTrack t = nouTrack(so);
            t.setLoopPoints(0, so.length, -1);
            t.play();
            bucle = t;
            llargBucle = so.length;
            iniciBucle = inici;
        } catch (RuntimeException e) {
            Ui.avis(this, "No s'ha pogut fer sonar la frase");
            return;
        }
        Ui.canviaIcona(botoPlay, Icona.STOP, Ui.SOBRE_COLOR, 18);
        roll.removeCallbacks(segueix);
        roll.postOnAnimation(segueix);
    }

    private void reprenBucle() {
        int pos = 0;
        long vell = llargBucle;
        if (bucle != null) {
            long p = posicioQueSona();
            if (p < 0) {
                try {
                    p = bucle.getPlaybackHeadPosition() + iniciBucle;
                } catch (RuntimeException ignorat) {
                    p = 0;
                }
            }
            pos = (int) (p % Math.max(1, llargBucle));
        }
        alliberaBucle();
        long nou = melodia.mostres(bpm, compas);
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
        roll.removeCallbacks(segueix);
        roll.posaPasSonant(-1);
        Ui.canviaIcona(botoPlay, Icona.PLAY, Ui.SOBRE_COLOR, 20);
    }

    // ---------- Desar i sortir ----------

    private void desaMelodia() {
        try {
            melodia.desa(Melodia.fitxerDe(carpeta, wav));
        } catch (IOException e) {
            Ui.avis(this, "No s'ha pogut desar la frase");
        }
        if (!desfent && desfes.registra(melodia.text())) pintaDesfes();
    }

    private void actualitzaPantalla() {
        actualitzaCapcalera();
    }

    private void pintaDesfes() {
        if (botoDesfes != null) botoDesfes.setAlpha(desfes.pot() ? 1f : 0.35f);
    }

    /** Desfà l'últim canvi (notes, llargada, graella, so…). */
    private void desfes() {
        String foto = desfes.desfes();
        Melodia anterior = foto == null ? null : Melodia.deText(foto);
        if (anterior == null) {
            Ui.avis(this, "No hi ha res per desfer");
            pintaDesfes();
            return;
        }
        if (!java.util.Objects.equals(anterior.preset, melodia.preset)) soCanviat = true;
        melodia = anterior;
        roll.canviaMelodia(anterior);
        canviat = true;
        desfent = true;
        desaMelodia();
        desfent = false;
        pintaDesfes();
        actualitzaPantalla();
        if (bucle != null) reprenBucle();
    }


    private void generaSo() {
        if (!canviat && new File(carpeta, wav).isFile()) return;
        desaMelodia();
        try {
            melodia.escriuWav(carpeta, wav, bpm, compas);
            canviat = false;
        } catch (IOException e) {
            Ui.avis(this, "No s'ha pogut fer el so del sinte");
        }
    }

    private void surt() {
        aturaBucle();
        aturaProva();
        generaSo();
        Intent r = new Intent();
        r.putExtra(DrumpadActivity.EXTRA_WAV, wav);
        r.putExtra(EXTRA_SO_CANVIAT, soCanviat);
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
        if (roll != null) aturaBucle();
        aturaProva();
        if (melodia != null) generaSo();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        alliberaBucle();
        aturaProva();
    }
}
