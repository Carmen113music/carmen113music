package cat.riffs;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Historial del que s'ha fet (el més recent a dalt). Es desa en un fitxer de text.
 * No depèn d'Android: es pot provar amb Java d'escriptori.
 */
public final class Historial {

    private static final int MAXIM = 300;

    public static final class Entrada {
        public final long quan;
        public final String text;

        Entrada(long quan, String text) {
            this.quan = quan;
            this.text = text;
        }
    }

    private Historial() {}

    public static synchronized void afegeix(File fitxer, long quan, String text) {
        List<String> linies = new ArrayList<>();
        linies.add(quan + "\t" + text.replace('\n', ' ').replace('\t', ' '));
        try {
            if (fitxer.exists()) {
                for (String l : Files.readAllLines(fitxer.toPath(), StandardCharsets.UTF_8)) {
                    if (linies.size() >= MAXIM) break;
                    if (!l.isEmpty()) linies.add(l);
                }
            }
            Files.write(fitxer.toPath(), linies, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // l'historial no és imprescindible
        }
    }

    public static synchronized List<Entrada> llegeix(File fitxer) {
        List<Entrada> r = new ArrayList<>();
        if (!fitxer.exists()) return r;
        try {
            for (String l : Files.readAllLines(fitxer.toPath(), StandardCharsets.UTF_8)) {
                int t = l.indexOf('\t');
                if (t <= 0) continue;
                try {
                    r.add(new Entrada(Long.parseLong(l.substring(0, t)), l.substring(t + 1)));
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
        return r;
    }

    public static synchronized void esborra(File fitxer) {
        fitxer.delete();
    }
}
