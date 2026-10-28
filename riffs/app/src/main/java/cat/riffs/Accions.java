package cat.riffs;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;

import java.io.File;
import java.io.IOException;

/** Accions sobre una gravació que es fan des de la biblioteca i des de la fitxa. */
public final class Accions {

    private Accions() {}

    public interface AmbFitxer {
        void fet(File nou);
    }

    /** Pregunta a quina carpeta i hi mou la gravació. */
    public static void mou(Activity a, File wav, AmbFitxer fet) {
        Ui.triaCarpeta(a, "Mou a…", wav.getParentFile(), desti -> {
            try {
                Biblioteca b = Ui.biblioteca(a);
                String titol = b.gravacio(wav).fitxa.titol;
                File nou = b.mou(wav, desti);
                actualitzaUltima(a, wav, nou);
                if (!nou.equals(wav)) {
                    Ui.historial(a, "↪ Mogut «" + titol + "» a " + Ui.nomCarpeta(a, desti));
                    Ui.avis(a, "Mogut a " + Ui.nomCarpeta(a, desti));
                }
                fet.fet(nou);
            } catch (IOException e) {
                Ui.avis(a, e.getMessage());
            }
        });
    }

    /** Pregunta i esborra la gravació. */
    public static void esborra(Activity a, File wav, Runnable fet) {
        Biblioteca b = Ui.biblioteca(a);
        String titol = b.gravacio(wav).fitxa.titol;
        Ui.confirma(a, "Vols esborrar «" + titol + "»?", "S'esborrarà el so i la lletra. No es pot desfer.", "Esborra", () -> {
            b.esborra(wav);
            actualitzaUltima(a, wav, null);
            Ui.historial(a, "🗑 Esborrat «" + titol + "»");
            fet.run();
        });
    }

    /** Obre el menú de compartir d'Android (WhatsApp, correu, Drive…). */
    public static void comparteix(Activity a, File wav) {
        Biblioteca.Gravacio g = Ui.biblioteca(a).gravacio(wav);
        Uri uri = Proveidor.uri(a, wav, Biblioteca.nomPerCompartir(g.fitxa.titol));
        if (uri == null) {
            Ui.avis(a, "No puc compartir aquest fitxer.");
            return;
        }
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("audio/wav");
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.putExtra(Intent.EXTRA_SUBJECT, g.fitxa.titol);
        i.setClipData(ClipData.newRawUri(g.fitxa.titol, uri));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        a.startActivity(Intent.createChooser(i, "Comparteix «" + g.fitxa.titol + "»"));
        Ui.historial(a, "↗ Compartit «" + g.fitxa.titol + "»");
    }

    /** Si la gravació moguda o esborrada era «l'última», actualitza-ho. */
    private static void actualitzaUltima(Activity a, File vell, File nou) {
        String u = Ui.prefs(a).getString("ultima", null);
        if (u != null && u.equals(vell.getAbsolutePath())) {
            if (nou == null) Ui.prefs(a).edit().remove("ultima").apply();
            else Ui.prefs(a).edit().putString("ultima", nou.getAbsolutePath()).apply();
        }
    }
}
