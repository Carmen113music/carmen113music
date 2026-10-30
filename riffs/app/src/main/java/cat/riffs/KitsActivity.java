package cat.riffs;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipFile;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Kits de bateria: importar-ne des d'una carpeta del mòbil (es converteixen i es classifiquen sols),
 * escoltar-ne els sons, canviar el tipus o el nom d'un so, i esborrar-los.
 */
public class KitsActivity extends Activity {

    private static final int TRIA_CARPETA = 41;
    /** Si és true, en obrir la pantalla s'obre directament el selector de carpetes. */
    public static final String EXTRA_IMPORTA = "importa";
    /** Si és true, en obrir la pantalla es baixen els kits de GitHub. */
    public static final String EXTRA_BAIXA = "baixa";
    /** El zip dels kits, adjunt a la release «kits» del repositori. */
    public static final String URL_KITS =
            "https://github.com/Carmen113music/carmen113music/releases/download/kits/kits-bateria.zip";

    private LinearLayout llista;
    private LinearLayout estat;
    private TextView textEstat;
    private FrameLayout botoImporta;
    private final Set<String> oberts = new HashSet<>();
    private volatile boolean important = false;
    private AudioTrack so;

    public static File arrel(android.content.Context c) {
        return new File(c.getFilesDir(), "kits");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setNavigationBarColor(Ui.FONS);
        LinearLayout arrelV = Ui.pantalla(this);
        botoImporta = Ui.botoIcona(this, Icona.MES, Ui.TEXT, Ui.SUPERFICIE2, 44, 22, "Importa kit");
        botoImporta.setOnClickListener(v -> triaCarpeta());
        arrelV.addView(Ui.barraEnrere(this, "Kits de bateria", botoImporta));

        estat = Ui.fila(this);
        estat.setBackground(Ui.rodo(Ui.alfa(Ui.GUITARRA, 30), Ui.dp(this, 14)));
        estat.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        textEstat = Ui.text(this, "", 14, Ui.GUITARRA);
        estat.addView(textEstat, Ui.pes(1));
        estat.setVisibility(View.GONE);
        LinearLayout.LayoutParams le = Ui.ample(this, 0);
        le.leftMargin = le.rightMargin = Ui.dp(this, 16);
        arrelV.addView(estat, le);

        ScrollView scroll = new ScrollView(this);
        llista = Ui.columna(this);
        llista.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 24));
        scroll.addView(llista);
        arrelV.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(arrelV);
        omple();
        if (savedInstanceState == null && getIntent().getBooleanExtra(EXTRA_IMPORTA, false)) triaCarpeta();
        if (savedInstanceState == null && getIntent().getBooleanExtra(EXTRA_BAIXA, false)) comencaBaixada();
    }

    @Override
    protected void onPause() {
        super.onPause();
        atura();
    }

    // ---------- Llista ----------

    private void omple() {
        llista.removeAllViews();
        List<Kits.Kit> kits = Kits.llista(arrel(this));
        if (kits.isEmpty()) {
            LinearLayout buit = Ui.columna(this);
            buit.setGravity(Gravity.CENTER_HORIZONTAL);
            buit.addView(Ui.rajola(this, Icona.BATERIA, Ui.SUAU, Ui.SUPERFICIE, 72));
            TextView t = Ui.negreta(this, "Encara no hi ha cap kit", 17, Ui.TEXT);
            t.setGravity(Gravity.CENTER);
            buit.addView(t, Ui.ample(this, 16));
            TextView s = Ui.text(this, "Posa la carpeta dels teus kits al mòbil (per exemple a Baixades) i toca «Importa un kit».", 14, Ui.SUAU);
            s.setGravity(Gravity.CENTER);
            s.setLineSpacing(0, 1.2f);
            buit.addView(s, Ui.ample(this, 8));
            LinearLayout b = Ui.pastilla(this, Icona.MES, "Importa un kit", Ui.TEXT, Ui.SOBRE_COLOR);
            b.setOnClickListener(v -> triaCarpeta());
            LinearLayout.LayoutParams lb = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lb.topMargin = Ui.dp(this, 20);
            buit.addView(b, lb);
            LinearLayout g = Ui.pastilla(this, Icona.BAIXA, "Baixa els kits de GitHub", Ui.SUPERFICIE2, Ui.TEXT);
            g.setOnClickListener(v -> comencaBaixada());
            LinearLayout.LayoutParams lg = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lg.topMargin = Ui.dp(this, 10);
            buit.addView(g, lg);
            llista.addView(buit, Ui.ample(this, 32));
        } else {
            llista.addView(Ui.etiqueta(this, kits.size() == 1 ? "1 kit" : kits.size() + " kits"), Ui.ample(this, 8));
            for (Kits.Kit k : kits) {
                llista.addView(filaKit(k), Ui.ample(this, 8));
                if (oberts.contains(k.dir.getName())) afegeixSons(k);
            }
        }

        LinearLayout ajuda = Ui.columna(this);
        ajuda.setBackground(Ui.rodoVora(Ui.SUPERFICIE, Ui.LINIA, Ui.dp(this, 18), 1));
        ajuda.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 16));
        ajuda.addView(Ui.etiqueta(this, "Com funciona"));
        TextView t = Ui.text(this, "Amb el + tria la carpeta d'un kit, o la carpeta que en conté uns quants: cada carpeta amb WAV es converteix en un kit. "
                + "L'app converteix cada so (mono, 44.100 Hz, 16 bits), en treu el silenci i l'endreça pel nom del fitxer (Kick, Snare, Hihat…). "
                + "Els kits queden dins l'app: no cal tornar-los a importar quan l'actualitzes. Els teus fitxers originals no es toquen.", 13, Ui.SUAU);
        t.setLineSpacing(0, 1.25f);
        ajuda.addView(t, Ui.ample(this, 10));
        llista.addView(ajuda, Ui.ample(this, 24));
    }

    private LinearLayout filaKit(Kits.Kit k) {
        LinearLayout r = Ui.targeta(this);
        r.addView(Ui.rajola(this, Icona.BATERIA, Ui.GUITARRA, Ui.alfa(Ui.GUITARRA, 36), 44));
        LinearLayout tx = Ui.columna(this);
        tx.addView(Ui.unaLinia(Ui.negreta(this, k.nom, 16, Ui.TEXT)));
        int n = k.sons.size();
        String info = (n == 1 ? "1 so" : n + " sons");
        List<String> tipus = k.tipus();
        StringBuilder sb = new StringBuilder();
        for (String ti : tipus) {
            if (ti.equals("altres")) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(Kits.nomTipus(ti));
        }
        if (sb.length() > 0) info += "  ·  " + sb;
        tx.addView(Ui.unaLinia(Ui.text(this, info, 13, Ui.SUAU)), Ui.ample(this, 4));
        LinearLayout.LayoutParams lp = Ui.pes(1);
        lp.leftMargin = Ui.dp(this, 12);
        r.addView(tx, lp);
        FrameLayout mes = Ui.botoIcona(this, Icona.PUNTS, Ui.SUAU, 0x00000000, 40, 22, "Opcions del kit");
        mes.setOnClickListener(v -> opcionsKit(k));
        r.addView(mes);
        String id = k.dir.getName();
        boolean obert = oberts.contains(id);
        if (obert) r.setBackground(Ui.tocable(Ui.SUPERFICIE2, Ui.dp(this, 16)));
        r.setOnClickListener(v -> {
            if (!oberts.remove(id)) oberts.add(id);
            omple();
        });
        r.setOnLongClickListener(v -> {
            opcionsKit(k);
            return true;
        });
        return r;
    }

    private void afegeixSons(Kits.Kit k) {
        LinearLayout caixa = Ui.columna(this);
        caixa.setPadding(Ui.dp(this, 8), 0, 0, Ui.dp(this, 4));
        for (String ti : k.tipus()) {
            TextView et = Ui.etiqueta(this, Kits.nomTipus(ti));
            caixa.addView(et, Ui.ample(this, 12));
            for (Kits.So s : k.delTipus(ti)) caixa.addView(filaSo(k, s), Ui.ample(this, 6));
        }
        llista.addView(caixa);
    }

    private LinearLayout filaSo(Kits.Kit k, Kits.So s) {
        LinearLayout r = Ui.targeta(this);
        r.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8));
        r.addView(Ui.rajola(this, Icona.PLAY, Ui.GUITARRA, Ui.SUPERFICIE2, 36));
        LinearLayout tx = Ui.columna(this);
        tx.addView(Ui.unaLinia(Ui.negreta(this, s.nom, 15, Ui.TEXT)));
        String info = String.format(Locale.ROOT, "%.1f s", s.ms / 1000.0).replace('.', ',');
        if (s.original != null && !s.original.isEmpty()) info += "  ·  " + s.original;
        tx.addView(Ui.unaLinia(Ui.text(this, info, 12, Ui.SUAU)), Ui.ample(this, 2));
        LinearLayout.LayoutParams lp = Ui.pes(1);
        lp.leftMargin = Ui.dp(this, 12);
        r.addView(tx, lp);
        FrameLayout mes = Ui.botoIcona(this, Icona.PUNTS, Ui.SUAU, 0x00000000, 40, 20, "Opcions del so");
        mes.setOnClickListener(v -> opcionsSo(k, s));
        r.addView(mes);
        r.setOnClickListener(v -> toca(k.fitxer(s)));
        r.setOnLongClickListener(v -> {
            opcionsSo(k, s);
            return true;
        });
        return r;
    }

    // ---------- Opcions ----------

    private void opcionsKit(Kits.Kit k) {
        List<Full.Accio> ac = new ArrayList<>();
        ac.add(new Full.Accio(Icona.LLAPIS, "Canvia el nom", false, () ->
                Ui.demanaText(this, "Nom del kit", k.nom, "Desa", nou -> {
                    nou = nou.trim();
                    if (nou.isEmpty() || nou.equals(k.nom)) return;
                    String vell = k.nom;
                    k.nom = Kits.nomLliure(arrel(this), nou);
                    if (desa(k)) Ui.historial(this, "El kit «" + vell + "» ara es diu «" + k.nom + "»");
                    omple();
                })));
        ac.add(new Full.Accio(Icona.PAPERERA, "Esborra", true, () ->
                Ui.confirma(this, "Esborrar el kit?", "S'esborrarà «" + k.nom + "» amb els seus " + k.sons.size()
                        + " sons de dins l'app. Els fitxers originals que vas importar no es toquen.", "Esborra", () -> {
                    atura();
                    Kits.esborra(k);
                    Ui.historial(this, "Kit esborrat: " + k.nom);
                    omple();
                })));
        int n = k.sons.size();
        Full.accions(this, Icona.BATERIA, Ui.GUITARRA, k.nom, n == 1 ? "1 so" : n + " sons", ac);
    }

    private void opcionsSo(Kits.Kit k, Kits.So s) {
        List<Full.Accio> ac = new ArrayList<>();
        ac.add(new Full.Accio(Icona.BATERIA, "Canvia el tipus", false, () -> triaTipus(k, s)));
        ac.add(new Full.Accio(Icona.LLAPIS, "Canvia el nom", false, () ->
                Ui.demanaText(this, "Nom del so", s.nom, "Desa", nou -> {
                    nou = nou.trim();
                    if (nou.isEmpty()) return;
                    s.nom = nou;
                    k.ordena();
                    desa(k);
                    omple();
                })));
        ac.add(new Full.Accio(Icona.PAPERERA, "Treu del kit", true, () ->
                Ui.confirma(this, "Treure aquest so?", "«" + s.nom + "» s'esborrarà del kit «" + k.nom + "».", "Treu", () -> {
                    atura();
                    k.sons.remove(s);
                    k.fitxer(s).delete();
                    desa(k);
                    omple();
                })));
        Full.accions(this, Icona.BATERIA, Ui.GUITARRA, s.nom, Kits.nomTipus(s.tipus) + "  ·  " + k.nom, ac);
    }

    private void triaTipus(Kits.Kit k, Kits.So s) {
        List<Full.Accio> ops = new ArrayList<>();
        for (String t : Kits.TIPUS) {
            boolean actual = t.equals(s.tipus);
            ops.add(new Full.Accio(actual ? Icona.BATERIA : Icona.MENYS, Kits.nomTipus(t) + (actual ? "  (ara)" : ""), false, () -> {
                if (t.equals(s.tipus)) return;
                s.tipus = t;
                k.ordena();
                desa(k);
                omple();
            }));
        }
        Full.llista(this, "Quin so és «" + s.nom + "»?", "Decideix a quina fila del drumpad surt.", ops);
    }

    private boolean desa(Kits.Kit k) {
        try {
            Kits.desa(k);
            return true;
        } catch (IOException e) {
            Ui.avis(this, "No s'ha pogut desar el kit");
            return false;
        }
    }

    // ---------- Escoltar ----------

    private void toca(File f) {
        short[] m = Wav.llegeixMostres(f);
        if (m == null || m.length == 0) {
            Ui.avis(this, "No es pot llegir aquest so");
            return;
        }
        atura();
        try {
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
                    .setBufferSizeInBytes(m.length * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build();
            t.write(m, 0, m.length);
            t.play();
            so = t;
        } catch (RuntimeException e) {
            Ui.avis(this, "No s'ha pogut fer sonar");
        }
    }

    private void atura() {
        if (so != null) {
            try {
                so.stop();
            } catch (RuntimeException ignorat) {
            }
            so.release();
            so = null;
        }
    }

    // ---------- Importar ----------

    private void triaCarpeta() {
        if (important) return;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        try {
            startActivityForResult(i, TRIA_CARPETA);
        } catch (RuntimeException e) {
            Ui.avis(this, "Aquest mòbil no deixa triar carpetes");
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != TRIA_CARPETA || res != RESULT_OK || data == null || data.getData() == null) return;
        Uri arbre = data.getData();
        important = true;
        botoImporta.setAlpha(0.4f);
        mostraEstat("Buscant els WAV…");
        new Thread(() -> importa(arbre), "importa-kits").start();
    }

    /** Busca totes les carpetes amb WAV (fins a 4 nivells) i n'importa cadascuna com a kit. */
    private void importa(Uri arbre) {
        ContentResolver cr = getContentResolver();
        List<Kits.Carpeta> carpetes = new ArrayList<>();
        try {
            String arrelId = DocumentsContract.getTreeDocumentId(arbre);
            busca(cr, arbre, arrelId, nomDocument(cr, arbre, arrelId), 0, carpetes);
        } catch (RuntimeException e) {
            acaba("No s'ha pogut llegir la carpeta", "Error: " + e.getMessage());
            return;
        }
        importaCarpetes(carpetes, false);
    }

    /** Importa les carpetes i en mostra el resum. Si {@code saltaRepetits}, no torna a importar els kits que ja hi són. */
    private void importaCarpetes(List<Kits.Carpeta> carpetes, boolean saltaRepetits) {
        StringBuilder resum = new StringBuilder();
        int kits = 0, sons = 0, errors = 0, altres = 0;
        List<String> formats = new ArrayList<>();
        List<String> repetits = new ArrayList<>();
        try {
            int total = 0;
            for (Kits.Carpeta c : carpetes) total += c.wavs.size();
            if (total == 0) {
                acaba("No hi ha cap WAV", "No s'ha trobat cap fitxer .wav (tampoc dins les carpetes).");
                return;
            }
            Set<String> existents = new HashSet<>();
            for (Kits.Kit k : Kits.llista(arrel(this))) existents.add(k.nom.toLowerCase(Locale.ROOT));
            for (int i = 0; i < carpetes.size(); i++) {
                Kits.Carpeta c = carpetes.get(i);
                if (saltaRepetits && existents.contains(c.nom.toLowerCase(Locale.ROOT))) {
                    repetits.add(c.nom);
                    continue;
                }
                String cap = carpetes.size() > 1 ? "Kit " + (i + 1) + " de " + carpetes.size() + " · " : "";
                Kits.Resultat r = Kits.importa(arrel(this), c.nom, c.wavs, (fet, tot, nom) ->
                        runOnUiThread(() -> mostraEstat(cap + "Important «" + c.nom + "»: " + Math.min(fet + 1, tot) + " de " + tot)));
                errors += r.errors.size();
                for (String f : r.formats) if (!formats.contains(f)) formats.add(f);
                if (r.kit == null) continue;
                kits++;
                sons += r.kit.sons.size();
                altres += r.altres;
                resum.append("\n• ").append(r.kit.nom).append(": ").append(r.kit.sons.size()).append(" sons");
                if (!r.errors.isEmpty()) resum.append(" (").append(r.errors.size()).append(" no s'han pogut llegir)");
                Ui.historial(this, "Kit importat: " + r.kit.nom + " (" + r.kit.sons.size() + " sons)");
                String id = r.kit.dir.getName();
                runOnUiThread(() -> oberts.add(id));
            }
        } catch (IOException | RuntimeException e) {
            acaba("No s'ha pogut importar", "Error: " + e.getMessage());
            return;
        }
        String titol = kits == 0 ? (repetits.isEmpty() ? "No s'ha importat cap kit" : "Ja tens tots aquests kits")
                : kits == 1 ? "Kit importat" : kits + " kits importats";
        String msg = kits == 0 ? "" : sons + " sons en total." + resum;
        if (!repetits.isEmpty()) msg += (msg.isEmpty() ? "" : "\n\n") + "Ja els tenies (no s'han tornat a importar): " + String.join(", ", repetits)
                + ". Si en vols una còpia nova, esborra'ls primer.";
        if (altres > 0) msg += "\n\n" + altres + (altres == 1 ? " so no s'ha pogut classificar i és" : " sons no s'han pogut classificar i són")
                + " a «Altres»: toca'ls els tres punts per dir quin so són.";
        if (errors > 0) msg += "\n\n" + errors + (errors == 1 ? " fitxer no s'ha pogut llegir" : " fitxers no s'han pogut llegir") + " (no és un WAV que entenguem o és tot silenci).";
        if (!formats.isEmpty()) msg += "\n\nFormat original: " + String.join("; ", formats) + ".";
        acaba(titol, msg);
    }

    // ---------- Baixar de GitHub ----------

    private void comencaBaixada() {
        if (important) return;
        important = true;
        botoImporta.setAlpha(0.4f);
        mostraEstat("Connectant amb GitHub…");
        new Thread(this::baixa, "baixa-kits").start();
    }

    private void baixa() {
        File zip = new File(getCacheDir(), "kits-bateria.zip");
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(URL_KITS).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setInstanceFollowRedirects(true);
            int codi = c.getResponseCode();
            if (codi == 404) {
                acaba("Encara no hi ha kits a GitHub", "No s'ha trobat el fitxer kits-bateria.zip a la release «kits» del repositori.");
                return;
            }
            if (codi != 200) {
                acaba("No s'han pogut baixar", "GitHub ha respost amb el codi " + codi + ". Torna-ho a provar d'aquí a una estona.");
                return;
            }
            long total = c.getContentLengthLong();
            long fet = 0, ultim = 0;
            try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(zip)) {
                byte[] b = new byte[65536];
                int n;
                while ((n = in.read(b)) > 0) {
                    out.write(b, 0, n);
                    fet += n;
                    if (fet - ultim > 512 * 1024) {
                        ultim = fet;
                        String t = total > 0
                                ? String.format(Locale.ROOT, "Baixant els kits: %d de %d MB", fet >> 20, Math.max(1, total >> 20))
                                : String.format(Locale.ROOT, "Baixant els kits: %d MB", fet >> 20);
                        runOnUiThread(() -> mostraEstat(t));
                    }
                }
            } finally {
                c.disconnect();
            }
            if (total > 0 && fet < total) throw new IOException("la baixada s'ha tallat");
            Ui.historial(this, "Kits baixats de GitHub (" + (fet >> 20) + " MB)");
            runOnUiThread(() -> mostraEstat("Obrint el zip…"));
            try (ZipFile z = Kits.obreZip(zip)) {
                importaCarpetes(Kits.carpetesDeZip(z, "Kit"), true);
            }
        } catch (java.net.UnknownHostException e) {
            acaba("Sense connexió", "No s'ha pogut connectar amb GitHub. Comprova que tens internet (wifi o dades).");
        } catch (IOException | RuntimeException e) {
            acaba("No s'han pogut baixar", "Error: " + e.getMessage());
        } finally {
            zip.delete();
        }
    }

    private void busca(ContentResolver cr, Uri arbre, String docId, String nom, int nivell, List<Kits.Carpeta> sortida) {
        Uri fills = DocumentsContract.buildChildDocumentsUriUsingTree(arbre, docId);
        Kits.Carpeta aquesta = new Kits.Carpeta();
        aquesta.nom = nom == null || nom.isEmpty() ? "Kit" : nom;
        List<String[]> subcarpetes = new ArrayList<>();
        try (Cursor c = cr.query(fills, new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
            if (c == null) return;
            while (c.moveToNext()) {
                String id = c.getString(0);
                String n = c.getString(1);
                String mime = c.getString(2);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    subcarpetes.add(new String[]{id, n});
                } else if (n != null && n.toLowerCase(Locale.ROOT).endsWith(".wav") && !n.startsWith(".")) {
                    Uri u = DocumentsContract.buildDocumentUriUsingTree(arbre, id);
                    aquesta.wavs.add(new Kits.Font() {
                        @Override
                        public String nom() {
                            return n;
                        }

                        @Override
                        public InputStream obre() throws IOException {
                            InputStream in = cr.openInputStream(u);
                            if (in == null) throw new IOException("no s'ha pogut obrir");
                            return in;
                        }
                    });
                }
            }
        }
        if (!aquesta.wavs.isEmpty()) sortida.add(aquesta);
        if (nivell >= 4) return;
        subcarpetes.sort((x, y) -> Kits.compara(x[1] == null ? "" : x[1], y[1] == null ? "" : y[1]));
        for (String[] s : subcarpetes) busca(cr, arbre, s[0], s[1], nivell + 1, sortida);
    }

    private String nomDocument(ContentResolver cr, Uri arbre, String docId) {
        Uri u = DocumentsContract.buildDocumentUriUsingTree(arbre, docId);
        try (Cursor c = cr.query(u, new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (RuntimeException ignorat) {
        }
        return "Kit";
    }

    private void mostraEstat(String s) {
        textEstat.setText(s);
        estat.setVisibility(View.VISIBLE);
    }

    private void acaba(String titol, String missatge) {
        runOnUiThread(() -> {
            important = false;
            botoImporta.setAlpha(1f);
            estat.setVisibility(View.GONE);
            if (isFinishing() || isDestroyed()) return;
            omple();
            Full.avis(this, titol, missatge, "D'acord", () -> {});
        });
    }
}
