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
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Una finestra de teremin: es veu el gest gravat i es pot escoltar en bucle i canviar-ne el so (preset).
 * El nou so s'aplica a totes les finestres de la pista (l'Estudi ho fa en tornar).
 */
public class TereminActivity extends Activity {

    private File carpeta;
    private String wav;
    private double bpm;
    private int compas;
    private Gest gest;
    private boolean canviat = false, soCanviat = false;

    private TereminCorbaView corba;
    private TextView textSo;
    private FrameLayout botoPlay;
    private FrameLayout botoDesfes;
    private final Desfes desfes = new Desfes();
    private boolean desfent = false;
    private BotoSolo botoSolo;
    private final android.media.AudioTimestamp marca = new android.media.AudioTimestamp();
    private AudioTrack bucle, prova;
    private int llargBucle, iniciBucle;

    private final Runnable segueix = new Runnable() {
        @Override
        public void run() {
            if (bucle == null || llargBucle <= 0) return;
            long pos = posicioQueSona();
            corba.posaSonant(pos < 0 ? -1 : (pos % llargBucle) * (double) gest.compassos / llargBucle);
            corba.postOnAnimation(this);
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
        gest = Gest.llegeix(Gest.fitxerDe(carpeta, wav));
        if (gest == null) {
            gest = Gest.nou(Sinte.PRESETS_TEREMIN[0], compas);
            canviat = true;
        } else if (gest.assegura(compas)) {
            canviat = true;
        }

        desfes.inici(gest.text());
        LinearLayout arrel = Ui.pantalla(this);
        LinearLayout dalt = Ui.fila(this);
        dalt.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4));
        FrameLayout enrere = Ui.botoIcona(this, Icona.ENRERE, Ui.TEXT, 0x00000000, 44, 24, "Enrere");
        enrere.setOnClickListener(v -> surt());
        dalt.addView(enrere);
        LinearLayout tit = Ui.columna(this);
        String nom = in.getStringExtra(DrumpadActivity.EXTRA_NOM);
        nomInicial = nom == null ? "Teremin" : nom;
        presetInicial = gest.preset;
        titol = Ui.unaLinia(Ui.negreta(this, nomInicial, 16, Ui.TEXT));
        tit.addView(titol);
        tit.addView(Ui.unaLinia(Ui.text(this, Math.round(bpm) + " BPM · " + Ritme.nomCompas(compas)
                + " · " + (gest.compassos == 1 ? "1 compàs" : gest.compassos + " compassos"), 12, Ui.SUAU)));
        LinearLayout.LayoutParams lt = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lt.leftMargin = Ui.dp(this, 4);
        dalt.addView(tit, lt);
        Ui.limitaAmple(tit, 170);
        LinearLayout botons = Ui.desplacable(this, dalt);
        botoDesfes = Ui.botoIcona(this, Icona.DESFES, Ui.TEXT, Ui.SUPERFICIE2, 40, 20, "Desfés l'últim canvi");
        botoDesfes.setOnClickListener(v -> desfes());
        botons.addView(botoDesfes);
        botons.addView(Ui.espai(this, 8));
        LinearLayout so = Ui.pastilla(this, Icona.TEREMIN, "", Ui.SUPERFICIE2, Ui.TEXT);
        textSo = (TextView) so.getChildAt(so.getChildCount() - 1);
        so.setOnClickListener(v -> triaSo());
        botons.addView(so, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(this, 40)));
        botons.addView(Ui.espai(this, 8));
        botoSolo = new BotoSolo(this, Ui.TEREMIN, () -> {
            if (bucle != null) reprenBucle();
        });
        botons.addView(botoSolo.vista, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(this, 40)));
        botons.addView(Ui.espai(this, 8));
        botoPlay = Ui.botoIcona(this, Icona.PLAY, Ui.SOBRE_COLOR, Ui.TEREMIN, 44, 20, "Escolta-ho");
        botoPlay.setOnClickListener(v -> {
            if (bucle != null) aturaBucle();
            else sonaBucle(0);
        });
        botons.addView(botoPlay);
        arrel.addView(dalt);

        TextView ajuda = Ui.text(this, "El gest no es pot editar aquí: si no t'agrada, torna'l a gravar. "
                + "El so sí: toca el botó del so per provar-ne d'altres (s'aplica a tota la pista).", 11, Ui.APAGAT);
        ajuda.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), Ui.dp(this, 4));
        arrel.addView(ajuda);

        corba = new TereminCorbaView(this);
        corba.posa(gest);
        LinearLayout.LayoutParams lc = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        lc.setMargins(0, 0, 0, Ui.dp(this, 8));
        arrel.addView(corba, lc);
        setContentView(arrel);
        actualitza();
        pintaDesfes();

        botoSolo.carrega(carpeta, wav, in.getLongExtra(DrumpadActivity.EXTRA_POS, -1),
                Math.round(Melodia.MAX_COMPASSOS * Patro.msCompas(bpm, compas)), () -> {
                    if (bucle != null) reprenBucle();
                });
    }

    private TextView titol;
    private String nomInicial, presetInicial;

    private void actualitza() {
        textSo.setText(gest.preset == null || gest.preset.isEmpty() ? "So" : gest.preset);
        if (titol != null) titol.setText(Ui.nomAmbSo(nomInicial, presetInicial, gest.preset));
        corba.invalidate();
    }

    private void triaSo() {
        Sinte.Preset[] ps = Sinte.PRESETS_TEREMIN;
        String[] noms = new String[ps.length];
        int triat = -1;
        for (int i = 0; i < ps.length; i++) {
            noms[i] = ps[i].nom;
            if (ps[i].nom.equals(gest.preset)) triat = i;
        }
        Full.triaAmbProva(this, "So del teremin", "Toca un so per escoltar-lo; queda triat. «Fet» per tancar. "
                        + "Totes les finestres d'aquesta pista agafaran aquest so.",
                noms, triat, Ui.TEREMIN,
                i -> provaPreset(ps[i]),
                i -> {
                    if (ps[i].nom.equals(gest.preset)) return;
                    gest.posaPreset(ps[i]);
                    canviat = true;
                    soCanviat = true;
                    desa();
                    actualitza();
                    if (bucle != null) reprenBucle();
                });
    }

    /** Un glissando curt (o el principi del gest, si n'hi ha) amb el so de fàbrica. */
    private void provaPreset(Sinte.Preset p) {
        List<List<Gest.Punt>> tr = new ArrayList<>();
        tr.add(Arrays.asList(new Gest.Punt(0, 64, 0.5), new Gest.Punt(0.4, 72, 0.9), new Gest.Punt(0.8, 69, 0.7),
                new Gest.Punt(1.2, 69, 0.6)));
        int mc = Sinte.MOSTREIG;   // un «compàs» d'un segon
        sona(Sinte.a16(Gest.toca(p.so, tr, mc, (int) (mc * 1.3), false), p.so.volumDb));
    }

    // ---------- So ----------

    private AudioTrack nouTrack(short[] s) {
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
                .setBufferSizeInBytes(Math.max(s.length, 2) * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build();
        t.write(s, 0, s.length);
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
        short[] s = botoSolo.so(gest.renderitza(bpm, compas));
        int inici = pos > 0 && pos < s.length ? pos : 0;
        if (inici > 0) {
            short[] r = new short[s.length];
            System.arraycopy(s, inici, r, 0, s.length - inici);
            System.arraycopy(s, 0, r, s.length - inici, inici);
            s = r;
        }
        try {
            AudioTrack t = nouTrack(s);
            t.setLoopPoints(0, s.length, -1);
            t.play();
            bucle = t;
            llargBucle = s.length;
            iniciBucle = inici;
        } catch (RuntimeException e) {
            Ui.avis(this, "No s'ha pogut fer sonar");
            return;
        }
        Ui.canviaIcona(botoPlay, Icona.STOP, Ui.SOBRE_COLOR, 18);
        corba.removeCallbacks(segueix);
        corba.postOnAnimation(segueix);
    }

    private void reprenBucle() {
        int pos = 0;
        if (bucle != null) {
            long p = posicioQueSona();
            if (p < 0) p = 0;
            pos = (int) (p % Math.max(1, llargBucle));
        }
        alliberaBucle();
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
        corba.removeCallbacks(segueix);
        corba.posaSonant(-1);
        Ui.canviaIcona(botoPlay, Icona.PLAY, Ui.SOBRE_COLOR, 20);
    }

    // ---------- Desar i sortir ----------

    private void desa() {
        try {
            gest.desa(Gest.fitxerDe(carpeta, wav));
        } catch (IOException e) {
            Ui.avis(this, "No s'ha pogut desar");
        }
        if (!desfent && desfes.registra(gest.text())) pintaDesfes();
    }

    private void actualitzaPantalla() {
        actualitza();
    }

    private void pintaDesfes() {
        if (botoDesfes != null) botoDesfes.setAlpha(desfes.pot() ? 1f : 0.35f);
    }

    /** Desfà l'últim canvi (notes, llargada, graella, so…). */
    private void desfes() {
        String foto = desfes.desfes();
        Gest anterior = foto == null ? null : Gest.deText(foto);
        if (anterior == null) {
            Ui.avis(this, "No hi ha res per desfer");
            pintaDesfes();
            return;
        }
        if (!java.util.Objects.equals(anterior.preset, gest.preset)) soCanviat = true;
        gest = anterior;
        corba.posa(anterior);
        canviat = true;
        desfent = true;
        desa();
        desfent = false;
        pintaDesfes();
        actualitzaPantalla();
        if (bucle != null) reprenBucle();
    }


    private void generaSo() {
        if (!canviat && new File(carpeta, wav).isFile()) return;
        desa();
        try {
            gest.escriuWav(carpeta, wav, bpm, compas);
            canviat = false;
        } catch (IOException e) {
            Ui.avis(this, "No s'ha pogut fer el so del teremin");
        }
    }

    private void surt() {
        aturaBucle();
        aturaProva();
        generaSo();
        Intent r = new Intent();
        r.putExtra(DrumpadActivity.EXTRA_WAV, wav);
        r.putExtra(PianoRollActivity.EXTRA_SO_CANVIAT, soCanviat);
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
        if (corba != null) aturaBucle();
        aturaProva();
        if (gest != null) generaSo();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        alliberaBucle();
        aturaProva();
    }
}
