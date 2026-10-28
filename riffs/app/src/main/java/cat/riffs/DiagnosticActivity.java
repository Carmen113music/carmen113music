package cat.riffs;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.Locale;

/**
 * 🔧 Diagnòstic: estat real del micròfon i de l'emmagatzematge, i proves per triar
 * la font de micròfon que sona millor en aquest mòbil.
 */
public class DiagnosticActivity extends Activity {

    private static final int PERMIS_MIC = 2;

    private final Handler h = new Handler(Looper.getMainLooper());
    private final Gravadora gravadora = new Gravadora();
    private TextView estat, resultat;
    private LinearLayout prova, escolta;
    private TextView textProva;
    private MediaPlayer mp;
    private String resultatProva = "(cap prova feta)";
    private File fitxerProva;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        fitxerProva = new File(getCacheDir(), "prova.wav");

        LinearLayout arrel = Ui.pantalla(this);
        arrel.addView(Ui.barraEnrere(this, "Diagnòstic i micròfon"));

        ScrollView scroll = new ScrollView(this);
        LinearLayout cos = Ui.columna(this);
        cos.setPadding(Ui.dp(this, 20), 0, Ui.dp(this, 20), Ui.dp(this, 32));

        cos.addView(Ui.etiqueta(this, "Font del micròfon"), Ui.ample(this, 8));
        TextView ajuda = Ui.text(this, "Si les gravacions sonen massa fluixes, saturades o «aspirades», prova una altra font i queda't la que soni millor.", 14, Ui.SUAU);
        ajuda.setLineSpacing(0, 1.2f);
        cos.addView(ajuda, Ui.ample(this, 8));

        RadioGroup grup = new RadioGroup(this);
        grup.setBackground(Ui.rodo(Ui.SUPERFICIE, Ui.dp(this, 16)));
        grup.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
        String actual = Ui.prefs(this).getString("font", Gravadora.AUTO);
        for (int i = 0; i < Gravadora.FONTS.length; i++) {
            RadioButton r = new RadioButton(this);
            r.setId(100 + i);
            r.setText(Gravadora.NOMS_FONTS[i]);
            r.setTextColor(Ui.TEXT);
            r.setTextSize(15);
            r.setMinHeight(Ui.dp(this, 46));
            grup.addView(r);
            if (Gravadora.FONTS[i].equals(actual)) r.setChecked(true);
        }
        grup.setOnCheckedChangeListener((gr, id) -> {
            int i = id - 100;
            if (i >= 0 && i < Gravadora.FONTS.length) {
                Ui.prefs(this).edit().putString("font", Gravadora.FONTS[i]).apply();
                omple();
            }
        });
        cos.addView(grup, Ui.ample(this, 12));

        LinearLayout fila = Ui.fila(this);
        prova = Ui.pastilla(this, Icona.MIC, "Prova 3 segons", Ui.ACCENT, 0xFFFFFFFF);
        textProva = (TextView) prova.getChildAt(2);
        prova.setOnClickListener(v -> provaMic());
        fila.addView(prova, Ui.pes(1));
        fila.addView(Ui.espai(this, 8));
        escolta = Ui.pastilla(this, Icona.PLAY, "Escolta-la", Ui.SUPERFICIE, Ui.TEXT);
        escolta.setEnabled(false);
        escolta.setAlpha(0.4f);
        escolta.setOnClickListener(v -> escoltaProva());
        fila.addView(escolta, Ui.pes(1));
        cos.addView(fila, Ui.ample(this, 12));

        resultat = Ui.text(this, "Toca «Prova» i toca la guitarra o canta fort, com quan graves.", 14, Ui.SUAU);
        resultat.setLineSpacing(0, 1.2f);
        cos.addView(resultat, Ui.ample(this, 12));

        cos.addView(Ui.etiqueta(this, "Estat"), Ui.ample(this, 28));
        estat = Ui.text(this, "", 13, Ui.TEXT);
        estat.setTextIsSelectable(true);
        estat.setLineSpacing(0, 1.35f);
        estat.setBackground(Ui.rodo(Ui.SUPERFICIE, Ui.dp(this, 16)));
        estat.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));
        cos.addView(estat, Ui.ample(this, 8));

        LinearLayout copia = Ui.pastilla(this, 0, "Copia el diagnòstic", Ui.SUPERFICIE, Ui.TEXT);
        copia.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("Diagnòstic Riffs", textDiagnostic()));
            Ui.avis(this, "Copiat. Enganxa'l al xat.");
        });
        cos.addView(copia, Ui.ample(this, 12));

        scroll.addView(cos);
        arrel.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(arrel);
        omple();
    }

    private void omple() {
        estat.setText(textDiagnostic());
    }

    private String textDiagnostic() {
        StringBuilder s = new StringBuilder();
        String versio = "?";
        try {
            versio = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        s.append("Riffs ").append(versio).append('\n');
        s.append("Mòbil: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(" · Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        boolean permis = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        s.append("Permís de micròfon: ").append(permis ? "sí ✓" : "NO ✗").append('\n');
        s.append("Admet «sense processar»: ").append(Gravadora.admetSenseProcessar(this) ? "sí" : "no").append('\n');
        String triada = Ui.prefs(this).getString("font", Gravadora.AUTO);
        s.append("Font triada: ").append(triada).append(" → ")
                .append(Gravadora.nomFont(Gravadora.font(this, triada))).append('\n');
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am != null) {
            s.append("Freqüència nativa: ").append(am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)).append(" Hz\n");
        }
        int minim = AudioRecord.getMinBufferSize(Gravadora.MOSTREIG, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        s.append("Memòria mínima per gravar: ").append(minim).append(" bytes\n");
        Biblioteca b = Ui.biblioteca(this);
        s.append("Gravacions: ").append(b.compta(b.arrel)).append(" · ").append(Biblioteca.mida(b.midaTotal(b.arrel))).append('\n');
        s.append("Espai lliure: ").append(Biblioteca.mida(getFilesDir().getUsableSpace())).append('\n');
        s.append("Es desa a: ").append(Ui.nomCarpeta(this, Ui.carpetaActual(this))).append('\n');
        s.append("Darrera prova: ").append(resultatProva);
        return s.toString();
    }

    private void provaMic() {
        if (gravadora.grava()) return;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, PERMIS_MIC);
            return;
        }
        aturaEscolta();
        String font = Ui.prefs(this).getString("font", Gravadora.AUTO);
        String error = gravadora.comenca(this, fitxerProva, font, null);
        if (error != null) {
            resultatProva = font + ": error — " + error;
            resultat.setText(error);
            resultat.setTextColor(Ui.ACCENT);
            omple();
            return;
        }
        prova.setEnabled(false);
        textProva.setText("Gravant…");
        resultat.setText("Toca o canta ara…");
        resultat.setTextColor(Ui.ACCENT);
        h.postDelayed(() -> {
            gravadora.atura();
            prova.setEnabled(true);
            textProva.setText("Prova 3 segons");
            double pic = gravadora.picDb();
            double mitja = gravadora.mitjaDb();
            String valoracio;
            if (pic > -0.5) valoracio = "Satura (massa fort): allunya el mòbil o prova una altra font.";
            else if (pic < -30) valoracio = "Molt fluix: potser aquesta font no capta bé, prova'n una altra.";
            else valoracio = "Nivell correcte ✓";
            resultatProva = String.format(Locale.ROOT, "%s (%s): pic %.1f dB, mitjà %.1f dB",
                    font, gravadora.fontUsada(), pic, mitja);
            resultat.setText(String.format(new Locale("ca"), "Pic: %.1f dB · Mitjà: %.1f dB\n%s", pic, mitja, valoracio));
            resultat.setTextColor(Ui.TEXT);
            escolta.setEnabled(true);
            escolta.setAlpha(1f);
            omple();
        }, 3000);
    }

    private void escoltaProva() {
        aturaEscolta();
        try {
            mp = new MediaPlayer();
            mp.setDataSource(fitxerProva.getAbsolutePath());
            mp.setOnCompletionListener(m -> aturaEscolta());
            mp.prepare();
            mp.start();
        } catch (Exception e) {
            aturaEscolta();
            Ui.avis(this, "No puc reproduir la prova.");
        }
    }

    private void aturaEscolta() {
        if (mp != null) {
            try {
                mp.stop();
            } catch (IllegalStateException ignored) {
            }
            mp.release();
            mp = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int codi, String[] permisos, int[] resultats) {
        super.onRequestPermissionsResult(codi, permisos, resultats);
        omple();
        if (codi == PERMIS_MIC && resultats.length > 0 && resultats[0] == PackageManager.PERMISSION_GRANTED) provaMic();
    }

    @Override
    protected void onPause() {
        super.onPause();
        h.removeCallbacksAndMessages(null);
        if (gravadora.grava()) {
            gravadora.atura();
            prova.setEnabled(true);
            textProva.setText("Prova 3 segons");
        }
        aturaEscolta();
    }
}
