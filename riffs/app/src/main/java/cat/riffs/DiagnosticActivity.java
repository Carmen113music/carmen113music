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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 🔧 Diagnòstic: estat real del micròfon i de l'emmagatzematge, i proves per triar
 * la font de micròfon que sona millor en aquest mòbil.
 */
public class DiagnosticActivity extends Activity {

    private static final int PERMIS_MIC = 2;
    private static final int PERMIS_RETARD = 3;

    private final Handler h = new Handler(Looper.getMainLooper());
    private final Gravadora gravadora = new Gravadora();
    private TextView estat, resultat;
    private LinearLayout prova, escolta;
    private TextView textProva;
    private MediaPlayer mp;
    private String resultatProva = "(cap prova feta)";
    private File fitxerProva;

    // Prova de retard (latència)
    private static final int RETARD_BPM = 80;          // un clic cada 0,75 s
    private static final int RETARD_CLICS = 12;        // els 2 primers no compten (arrencada)
    private static final int RETARD_DESCARTA = 2;
    private final Gravadora gravadoraRetard = new Gravadora();
    private Metronom metRetard;
    private File fitxerRetard;
    private LinearLayout botoRetard;
    private TextView textBotoRetard, resultatRetard, ajudaRetard;
    private String sortidaRetard = "", nomSortidaRetard = "";
    private boolean provantRetard = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        fitxerProva = new File(getCacheDir(), "prova.wav");
        fitxerRetard = new File(getCacheDir(), "retard.wav");

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

        // ---------- Retard (latència) ----------
        cos.addView(Ui.etiqueta(this, "Retard del so (latència)"), Ui.ample(this, 28));
        ajudaRetard = Ui.text(this, "", 14, Ui.SUAU);
        ajudaRetard.setLineSpacing(0, 1.2f);
        cos.addView(ajudaRetard, Ui.ample(this, 8));
        botoRetard = Ui.pastilla(this, Icona.METRONOM, "Mesura el retard", Ui.ACCENT, 0xFFFFFFFF);
        textBotoRetard = (TextView) botoRetard.getChildAt(2);
        botoRetard.setOnClickListener(v -> provaRetard());
        cos.addView(botoRetard, Ui.ample(this, 12));
        resultatRetard = Ui.text(this, "", 14, Ui.TEXT);
        resultatRetard.setLineSpacing(0, 1.25f);
        cos.addView(resultatRetard, Ui.ample(this, 12));
        posaAjudaRetard();
        mostraMesures();

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
        Metronom.Config k = Metronom.Config.llegeix(this);
        s.append("Metrònom: ").append(k.actiu ? k.resum() + " · clic " + k.so : "desactivat")
                .append(" · auriculars: ").append(Metronom.auriculars(this) ? "sí" : "no").append('\n');
        s.append("Es desa a: ").append(Ui.nomCarpeta(this, Ui.carpetaActual(this))).append('\n');
        s.append("Darrera prova: ").append(resultatProva).append('\n');
        s.append("Mesures de retard (la més recent a dalt):\n").append(historialRetard().isEmpty() ? "(cap)" : historialRetard());
        return s.toString();
    }

    private void provaMic() {
        if (gravadora.grava() || provantRetard) return;
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
            resultatProva = String.format(Locale.ROOT, "%s (%s): pic %.1f dB, mitjà %.1f dB, marques de temps: %s",
                    font, gravadora.fontUsada(), pic, mitja, gravadora.teMarcaDeTemps() ? "sí" : "no");
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

    // ---------- Prova de retard ----------

    private void posaAjudaRetard() {
        if (ajudaRetard == null) return;
        String text = "L'app fa sonar 12 clics i els grava. Així sabem quant tarda el so a arribar a la gravació i si "
                + "aquest retard és sempre igual. Fes silenci durant la prova (uns 10 segons) i apuja el volum.";
        if (Metronom.auriculars(this)) {
            text += "\n\nAuriculars connectats: el clic sonarà pels auriculars. Posa un auricular tocant el micròfon "
                    + "del mòbil (a baix, al costat del connector).";
        } else {
            text += "\n\nSense auriculars: el clic sonarà per l'altaveu. Deixa el mòbil sobre la taula.";
        }
        text += "\n\nFes-la uns quants cops, amb cada font de micròfon i amb i sense auriculars.";
        ajudaRetard.setText(text);
    }

    private void provaRetard() {
        if (provantRetard || gravadora.grava()) return;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, PERMIS_RETARD);
            return;
        }
        aturaEscolta();
        String font = Ui.prefs(this).getString("font", Gravadora.AUTO);
        // Igual que quan es grava de debò: primer el micròfon i just després el metrònom
        String error = gravadoraRetard.comenca(this, fitxerRetard, font, null);
        if (error != null) {
            mostraResultatRetard(error, Ui.ACCENT);
            return;
        }
        metRetard = new Metronom(RETARD_BPM, 1, 0, true);
        if (!metRetard.comenca()) {
            metRetard = null;
            gravadoraRetard.atura();
            mostraResultatRetard("No s'ha pogut fer sonar el clic.", Ui.ACCENT);
            return;
        }
        provantRetard = true;
        sortidaRetard = "";
        nomSortidaRetard = "";
        botoRetard.setEnabled(false);
        botoRetard.setAlpha(0.6f);
        final long durada = Math.round(RETARD_CLICS * 60000.0 / RETARD_BPM) + 600;
        final long inici = System.currentTimeMillis();
        h.post(new Runnable() {
            @Override
            public void run() {
                if (!provantRetard) return;
                if (metRetard != null && sortidaRetard.isEmpty()) {
                    String t = metRetard.tipusSortida();
                    if (t != null) {
                        sortidaRetard = t;
                        nomSortidaRetard = metRetard.nomSortida();
                    }
                }
                long queda = durada - (System.currentTimeMillis() - inici);
                if (queda <= 0) {
                    acabaRetard();
                    return;
                }
                textBotoRetard.setText("Escoltant… " + (queda / 1000 + 1) + " s");
                resultatRetard.setText("Fes silenci. Sona per: " + nomSortida(sortidaRetard, nomSortidaRetard));
                resultatRetard.setTextColor(Ui.SUAU);
                h.postDelayed(this, 200);
            }
        });
    }

    private void acabaRetard() {
        Metronom met = metRetard;
        metRetard = null;
        // On l'app es pensa que ha sonat cada clic dins la gravació (abans d'aturar res, com quan es grava)
        long[] esperats = new long[RETARD_CLICS - RETARD_DESCARTA];
        for (int i = 0; i < esperats.length; i++) {
            esperats[i] = gravadoraRetard.msDelMoment(met.nanoDelTemps(i + RETARD_DESCARTA));
        }
        final boolean marcaSortida = met.teMarcaDeTemps();
        final boolean marcaEntrada = gravadoraRetard.teMarcaDeTemps();
        final String fontUsada = gravadoraRetard.fontUsada();
        met.atura();
        gravadoraRetard.atura();
        textBotoRetard.setText("Calculant…");
        final String sortida = sortidaRetard.isEmpty() ? "altre" : sortidaRetard;
        final String nom = nomSortida(sortida, nomSortidaRetard);
        new Thread(() -> {
            short[] mostres = Wav.llegeixMostres(fitxerRetard);
            Latencia.Resultat r = Latencia.mesura(mostres, Gravadora.MOSTREIG, esperats);
            h.post(() -> {
                provantRetard = false;
                botoRetard.setEnabled(true);
                botoRetard.setAlpha(1f);
                textBotoRetard.setText("Mesura el retard");
                String linia = new SimpleDateFormat("dd/MM HH:mm", Locale.ROOT).format(new Date())
                        + " · " + nom + " · " + fontUsada + " · " + r.resum()
                        + " · marques " + (marcaSortida ? "sí" : "no") + "/" + (marcaEntrada ? "sí" : "no")
                        + (r.errors.isEmpty() ? "" : " · [" + r.detall() + "]");
                desaMesura(linia);
                if (r.valida()) {
                    Ui.prefs(this).edit()
                            .putFloat("retard_" + sortida + "_" + Ui.prefs(this).getString("font", Gravadora.AUTO), (float) r.mediana)
                            .apply();
                }
                Ui.historial(this, "Prova de retard (" + nom + "): " + r.resum());
                String text;
                if (r.errors.isEmpty()) {
                    text = r.valoracio();
                } else {
                    text = String.format(new Locale("ca"),
                            "El so arriba %d ms %s del que l'app calcula.\nVaria entre %d i %d ms · %d de %d clics sentits.\n%s",
                            Math.abs(Math.round(r.mediana)), r.mediana >= 0 ? "més tard" : "abans",
                            r.minim, r.maxim, r.errors.size(), r.clics, r.valoracio());
                }
                mostraResultatRetard(text, r.valida() ? Ui.TEXT : Ui.ACCENT);
                mostraMesures();
                omple();
            });
        }, "prova-retard").start();
    }

    private static String nomSortida(String tipus, String nom) {
        String base;
        switch (tipus) {
            case "altaveu":
                base = "altaveu";
                break;
            case "bluetooth":
                base = "auriculars Bluetooth";
                break;
            case "cable":
                base = "auriculars de cable";
                break;
            case "":
                return "…";
            default:
                base = "una altra sortida";
                break;
        }
        return nom == null || nom.isEmpty() || "altaveu".equals(base) ? base : base + " (" + nom + ")";
    }

    private void mostraResultatRetard(String text, int color) {
        resultatRetard.setText(text);
        resultatRetard.setTextColor(color);
    }

    private String historialRetard() {
        return Ui.prefs(this).getString("retard_hist", "");
    }

    /** Desa una mesura a dalt de la llista (en guarda com a molt 20). */
    private void desaMesura(String linia) {
        String[] velles = historialRetard().isEmpty() ? new String[0] : historialRetard().split("\n");
        StringBuilder s = new StringBuilder(linia);
        for (int i = 0; i < velles.length && i < 19; i++) s.append('\n').append(velles[i]);
        Ui.prefs(this).edit().putString("retard_hist", s.toString()).apply();
    }

    private void mostraMesures() {
        if (provantRetard) return;
        String hist = historialRetard();
        if (hist.isEmpty()) {
            mostraResultatRetard("Encara no s'ha fet cap mesura.", Ui.SUAU);
        } else if (resultatRetard.getText().length() == 0 || resultatRetard.getText().toString().startsWith("Encara")) {
            mostraResultatRetard("Darrera mesura: " + hist.split("\n")[0], Ui.SUAU);
        }
    }

    private void cancellaRetard() {
        if (!provantRetard) return;
        provantRetard = false;
        if (metRetard != null) {
            metRetard.atura();
            metRetard = null;
        }
        if (gravadoraRetard.grava()) gravadoraRetard.atura();
        botoRetard.setEnabled(true);
        botoRetard.setAlpha(1f);
        textBotoRetard.setText("Mesura el retard");
        mostraResultatRetard("Prova interrompuda. Torna-la a fer amb la pantalla oberta.", Ui.ACCENT);
    }

    @Override
    protected void onResume() {
        super.onResume();
        posaAjudaRetard();
    }

    @Override
    public void onRequestPermissionsResult(int codi, String[] permisos, int[] resultats) {
        super.onRequestPermissionsResult(codi, permisos, resultats);
        omple();
        if (codi == PERMIS_MIC && resultats.length > 0 && resultats[0] == PackageManager.PERMISSION_GRANTED) provaMic();
        if (codi == PERMIS_RETARD && resultats.length > 0 && resultats[0] == PackageManager.PERMISSION_GRANTED) provaRetard();
    }

    @Override
    protected void onPause() {
        super.onPause();
        h.removeCallbacksAndMessages(null);
        cancellaRetard();
        if (gravadora.grava()) {
            gravadora.atura();
            prova.setEnabled(true);
            textProva.setText("Prova 3 segons");
        }
        aturaEscolta();
    }
}
