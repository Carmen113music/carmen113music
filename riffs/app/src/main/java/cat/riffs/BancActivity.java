package cat.riffs;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * El banc de sons MIDI (SoundFont .sf2) amb els 128 instruments: baixar-lo de GitHub o importar-ne un del mòbil.
 * Es desa a la memòria de l'app (files/midi/banc.sf2).
 */
public class BancActivity extends Activity {

    public static final String EXTRA_BAIXA = "baixa";
    /** Primer es prova la release «midi» del repositori del grup; si no hi és, el de l'autor del banc. */
    public static final String[] URLS = {
            "https://github.com/Carmen113music/carmen113music/releases/download/midi/GeneralUser-GS.sf2",
            "https://github.com/mrbumpy409/GeneralUser-GS/raw/main/GeneralUser-GS.sf2"};
    private static final int TRIA_SF2 = 41;

    private TextView textBanc, textEstat;
    private LinearLayout botoBaixa, botoImporta;
    private boolean ocupat = false;

    /** On es desa el banc de l'app. */
    public static File fitxer(Context c) {
        return new File(new File(c.getFilesDir(), "midi"), "banc.sf2");
    }

    /** Diu al banc on és (cal cridar-ho abans de fer servir instruments MIDI). */
    public static void prepara(Context c) {
        Banc.posaFitxer(fitxer(c));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prepara(this);
        LinearLayout arrel = Ui.pantalla(this);
        arrel.addView(Ui.barraEnrere(this, "Instruments MIDI"));
        ScrollView sc = new ScrollView(this);
        LinearLayout cos = Ui.columna(this);
        cos.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), Ui.dp(this, 24));

        TextView ajuda = Ui.text(this, "Les pistes MIDI fan servir un banc de sons amb els 128 instruments General MIDI "
                + "(pianos, guitarres, cordes, metalls, orgues…). Cal baixar-lo una vegada (uns 31 MB, millor amb wifi). "
                + "El banc és GeneralUser GS, de S. Christian Collins: lliure per fer-ne música, també comercial.", 14, Ui.SUAU);
        ajuda.setLineSpacing(0, 1.2f);
        cos.addView(ajuda, Ui.ample(this, 4));

        LinearLayout targeta = Ui.targeta(this);
        targeta.addView(Ui.rajola(this, Icona.MIDI, Ui.MIDI, Ui.alfa(Ui.MIDI, 36), 40));
        textBanc = Ui.text(this, "", 15, Ui.TEXT);
        LinearLayout.LayoutParams lt = Ui.pes(1);
        lt.leftMargin = Ui.dp(this, 12);
        targeta.addView(textBanc, lt);
        cos.addView(targeta, Ui.ample(this, 16));

        botoBaixa = Ui.pastilla(this, Icona.BAIXA, "Baixa el banc (GeneralUser GS)", Ui.MIDI, Ui.SOBRE_COLOR);
        botoBaixa.setOnClickListener(v -> comencaBaixada());
        cos.addView(botoBaixa, Ui.ample(this, 16));
        botoImporta = Ui.pastilla(this, Icona.CARPETA, "Importa un banc .sf2 del mòbil", Ui.SUPERFICIE2, Ui.TEXT);
        botoImporta.setOnClickListener(v -> triaFitxer());
        cos.addView(botoImporta, Ui.ample(this, 8));

        textEstat = Ui.text(this, "", 14, Ui.TEXT);
        textEstat.setVisibility(View.GONE);
        cos.addView(textEstat, Ui.ample(this, 16));
        sc.addView(cos);
        arrel.addView(sc, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(arrel);
        omple();
        if (savedInstanceState == null && getIntent().getBooleanExtra(EXTRA_BAIXA, false)) comencaBaixada();
    }

    private void omple() {
        File f = fitxer(this);
        if (!f.isFile()) {
            textBanc.setText("Encara no hi ha cap banc");
            return;
        }
        textBanc.setText("Llegint el banc…");
        new Thread(() -> {
            Banc b = Banc.actual();
            String t;
            if (b == null) {
                t = "Hi ha un fitxer, però no s'ha pogut llegir com a banc SF2";
            } else {
                int gm = 0;
                for (Banc.Programa p : b.programes()) if (p.banc == 0 && p.numero < 128) gm++;
                t = b.nom + "\n" + gm + " instruments · " + (f.length() >> 20) + " MB";
            }
            runOnUiThread(() -> textBanc.setText(t));
        }).start();
    }

    private void ocupa(boolean si, String estat) {
        ocupat = si;
        botoBaixa.setAlpha(si ? 0.4f : 1f);
        botoImporta.setAlpha(si ? 0.4f : 1f);
        textEstat.setText(estat == null ? "" : estat);
        textEstat.setVisibility(estat == null ? View.GONE : View.VISIBLE);
    }

    private void acaba(String titol, String missatge) {
        runOnUiThread(() -> {
            ocupa(false, null);
            if (isFinishing() || isDestroyed()) return;
            omple();
            Full.avis(this, titol, missatge, "D'acord", () -> {});
        });
    }

    // ---------- Baixar ----------

    private void comencaBaixada() {
        if (ocupat) return;
        ocupa(true, "Connectant amb GitHub…");
        new Thread(this::baixa, "baixa-banc").start();
    }

    private void baixa() {
        File desti = fitxer(this);
        desti.getParentFile().mkdirs();
        File tmp = new File(desti.getPath() + ".tmp");
        String error = null;
        for (String u : URLS) {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(30000);
                c.setInstanceFollowRedirects(true);
                int codi = c.getResponseCode();
                if (codi != 200) {
                    c.disconnect();
                    error = "GitHub ha respost amb el codi " + codi;
                    continue;
                }
                long total = c.getContentLengthLong();
                long fet = 0, ultim = 0;
                try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(tmp)) {
                    byte[] b = new byte[65536];
                    int n;
                    while ((n = in.read(b)) > 0) {
                        out.write(b, 0, n);
                        fet += n;
                        if (fet - ultim > 512 * 1024) {
                            ultim = fet;
                            String t = total > 0
                                    ? String.format(Locale.ROOT, "Baixant el banc: %d de %d MB", fet >> 20, Math.max(1, total >> 20))
                                    : String.format(Locale.ROOT, "Baixant el banc: %d MB", fet >> 20);
                            runOnUiThread(() -> textEstat.setText(t));
                        }
                    }
                } finally {
                    c.disconnect();
                }
                if (total > 0 && fet < total) throw new IOException("la baixada s'ha tallat");
                runOnUiThread(() -> textEstat.setText("Comprovant el banc…"));
                installa(tmp, desti);
                Ui.historial(this, "Banc MIDI baixat (" + (fet >> 20) + " MB)");
                acaba("Banc a punt", "Ja pots crear pistes MIDI: Estudi → + Pista nova → MIDI.");
                return;
            } catch (java.net.UnknownHostException e) {
                tmp.delete();
                acaba("Sense connexió", "No s'ha pogut connectar amb GitHub. Comprova que tens internet (wifi o dades).");
                return;
            } catch (IOException | RuntimeException e) {
                tmp.delete();
                error = e.getMessage();
            }
        }
        acaba("No s'ha pogut baixar", "Error: " + error + ". Torna-ho a provar d'aquí a una estona.");
    }

    /** Comprova que és un banc que entenem i el posa al seu lloc. */
    private void installa(File tmp, File desti) throws IOException {
        Banc prova;
        try {
            prova = Banc.llegeix(tmp);
        } catch (IOException e) {
            tmp.delete();
            throw new IOException("el fitxer no és un banc SF2 que entenguem (" + e.getMessage() + ")");
        }
        if (prova.programes().isEmpty()) {
            tmp.delete();
            throw new IOException("el banc no té cap instrument");
        }
        Banc.oblida();
        desti.delete();
        if (!tmp.renameTo(desti)) throw new IOException("no s'ha pogut desar el banc");
        Banc.oblida();
    }

    // ---------- Importar ----------

    @SuppressWarnings("deprecation")
    private void triaFitxer() {
        if (ocupat) return;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, TRIA_SF2);
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != TRIA_SF2 || res != RESULT_OK || data == null || data.getData() == null) return;
        Uri u = data.getData();
        ocupa(true, "Copiant el banc…");
        new Thread(() -> {
            File desti = fitxer(this);
            desti.getParentFile().mkdirs();
            File tmp = new File(desti.getPath() + ".tmp");
            try (InputStream in = getContentResolver().openInputStream(u); OutputStream out = new FileOutputStream(tmp)) {
                if (in == null) throw new IOException("no s'ha pogut obrir");
                byte[] b = new byte[65536];
                int n;
                while ((n = in.read(b)) > 0) out.write(b, 0, n);
            } catch (IOException | RuntimeException e) {
                tmp.delete();
                acaba("No s'ha pogut importar", "Error: " + e.getMessage());
                return;
            }
            try {
                installa(tmp, desti);
            } catch (IOException e) {
                acaba("No s'ha pogut importar", e.getMessage());
                return;
            }
            Ui.historial(this, "Banc MIDI importat del mòbil");
            acaba("Banc a punt", "Ja pots crear pistes MIDI: Estudi → + Pista nova → MIDI.");
        }).start();
    }
}
