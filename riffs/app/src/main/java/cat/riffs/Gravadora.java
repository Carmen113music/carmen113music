package cat.riffs;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTimestamp;
import android.media.MediaRecorder;

import java.io.File;
import java.io.IOException;

/**
 * Grava del micròfon a un fitxer WAV (44.100 Hz, mono, 16 bits).
 * La gravació es fa en un fil a part; el nivell s'avisa a l'oient (des d'aquest fil).
 */
public final class Gravadora {

    public static final int MOSTREIG = 44100;

    /** Fonts de micròfon que es poden triar al diagnòstic. */
    public static final String AUTO = "auto";
    public static final String[] FONTS = {AUTO, "sense_processar", "mic", "reconeixement", "camera"};
    public static final String[] NOMS_FONTS = {
            "Automàtic (recomanat)",
            "Sense processar (so més natural)",
            "Micròfon normal",
            "Micròfon de dictat",
            "Micròfon de vídeo"
    };

    public interface Oient {
        /** Nivell de 0 a 1 (el pic del darrer tros). */
        void nivell(float pic);

        /** Hi ha hagut un error mentre gravava. */
        void error(String missatge);
    }

    private volatile boolean actiu = false;
    private Thread fil;
    private AudioRecord rec;
    private Wav.Escriptor escriptor;
    private volatile long duradaMs = 0;
    private volatile float picMaxim = 0;
    private double sumaQuadrats = 0;
    private long mostresTotals = 0;
    private String fontUsada = "";

    // Per saber quin tros de la gravació correspon a cada moment (sincronia amb el metrònom)
    private final AudioTimestamp marca = new AudioTimestamp();
    private volatile boolean teMarca = false;
    private volatile long nanoInici = 0;
    private volatile long mostresGravades = 0;

    public boolean grava() {
        return actiu;
    }

    public long duradaMs() {
        return duradaMs;
    }

    public String fontUsada() {
        return fontUsada;
    }

    /** Nivell màxim de tota la gravació, en dBFS (0 = el màxim possible). */
    public double picDb() {
        return picMaxim <= 0 ? -99 : 20 * Math.log10(picMaxim);
    }

    /** Nivell mitjà de tota la gravació, en dBFS. */
    public double mitjaDb() {
        if (mostresTotals == 0 || sumaQuadrats <= 0) return -99;
        return 20 * Math.log10(Math.sqrt(sumaQuadrats / mostresTotals));
    }

    /** Si el mòbil diu que admet el micròfon «sense processar». */
    public static boolean admetSenseProcessar(Context c) {
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        return am != null && "true".equals(am.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED));
    }

    /** Font d'Android que correspon a la triada. */
    public static int font(Context c, String triada) {
        if (triada == null) triada = AUTO;
        switch (triada) {
            case "sense_processar":
                return MediaRecorder.AudioSource.UNPROCESSED;
            case "mic":
                return MediaRecorder.AudioSource.MIC;
            case "reconeixement":
                return MediaRecorder.AudioSource.VOICE_RECOGNITION;
            case "camera":
                return MediaRecorder.AudioSource.CAMCORDER;
            default:
                return admetSenseProcessar(c) ? MediaRecorder.AudioSource.UNPROCESSED : MediaRecorder.AudioSource.MIC;
        }
    }

    public static String nomFont(int font) {
        switch (font) {
            case MediaRecorder.AudioSource.UNPROCESSED:
                return "sense processar";
            case MediaRecorder.AudioSource.MIC:
                return "micròfon normal";
            case MediaRecorder.AudioSource.VOICE_RECOGNITION:
                return "micròfon de dictat";
            case MediaRecorder.AudioSource.CAMCORDER:
                return "micròfon de vídeo";
            default:
                return "font " + font;
        }
    }

    /**
     * Comença a gravar a {@code fitxer}. Torna null si ha anat bé, o un missatge d'error en català.
     */
    public String comenca(Context c, File fitxer, String triada, Oient oient) {
        if (actiu) return "Ja està gravant.";
        int font = font(c, triada);
        fontUsada = nomFont(font);
        int minim = AudioRecord.getMinBufferSize(MOSTREIG, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minim <= 0) return "Aquest mòbil no pot gravar a 44.100 Hz (codi " + minim + ").";
        int mida = Math.max(minim * 2, MOSTREIG / 5 * 2); // com a mínim 0,2 s
        try {
            rec = new AudioRecord(font, MOSTREIG, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, mida);
        } catch (SecurityException e) {
            return "Falta el permís de micròfon.";
        } catch (IllegalArgumentException e) {
            return "No puc fer servir el " + fontUsada + ". Prova una altra font al diagnòstic 🔧.";
        }
        if (rec.getState() != AudioRecord.STATE_INITIALIZED) {
            rec.release();
            rec = null;
            return "No puc obrir el " + fontUsada + ". Prova una altra font al diagnòstic 🔧.";
        }
        try {
            escriptor = new Wav.Escriptor(fitxer, MOSTREIG, 1);
        } catch (IOException e) {
            rec.release();
            rec = null;
            return "No puc crear el fitxer de so.";
        }
        try {
            rec.startRecording();
        } catch (IllegalStateException e) {
            tancaTot();
            return "No s'ha pogut començar a gravar.";
        }
        if (rec.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
            tancaTot();
            return "El micròfon està ocupat (potser per una altra app o una trucada).";
        }
        duradaMs = 0;
        teMarca = false;
        mostresGravades = 0;
        nanoInici = System.nanoTime();
        picMaxim = 0;
        sumaQuadrats = 0;
        mostresTotals = 0;
        actiu = true;
        fil = new Thread(() -> bucle(oient), "gravadora");
        fil.start();
        return null;
    }

    private void bucle(Oient oient) {
        short[] tros = new short[2048];
        long darreraCapcalera = System.currentTimeMillis();
        while (actiu) {
            int n = rec.read(tros, 0, tros.length);
            if (n < 0) {
                actiu = false;
                if (oient != null) oient.error("El micròfon ha deixat de respondre (codi " + n + ").");
                break;
            }
            if (n == 0) continue;
            int pic = 0;
            for (int i = 0; i < n; i++) {
                int v = Math.abs(tros[i]);
                if (v > pic) pic = v;
                sumaQuadrats += (tros[i] / 32768.0) * (tros[i] / 32768.0);
            }
            mostresTotals += n;
            mostresGravades += n;
            if (!teMarca || mostresGravades % (MOSTREIG / 2) < tros.length) actualitzaMarca();
            float p = Math.min(1f, pic / 32768f);
            if (p > picMaxim) picMaxim = p;
            try {
                escriptor.escriu(tros, n);
                duradaMs = escriptor.duradaMs();
                long ara = System.currentTimeMillis();
                if (ara - darreraCapcalera > 2000) {
                    escriptor.actualitzaCapcalera();
                    darreraCapcalera = ara;
                }
            } catch (IOException e) {
                actiu = false;
                if (oient != null) oient.error("No s'ha pogut desar el so (potser el mòbil està ple).");
                break;
            }
            if (oient != null) oient.nivell(p);
        }
    }

    private synchronized void actualitzaMarca() {
        try {
            if (rec != null && rec.getTimestamp(marca, AudioTimestamp.TIMEBASE_MONOTONIC) == AudioRecord.SUCCESS
                    && marca.nanoTime > 0) {
                teMarca = true;
            }
        } catch (IllegalStateException ignored) {
        }
    }

    /**
     * Posició dins la gravació (en ms) del so que va entrar pel micròfon en el moment {@code nano}
     * (System.nanoTime). Té en compte el retard del micròfon si el mòbil l'informa.
     */
    public synchronized long msDelMoment(long nano) {
        double mostra;
        if (teMarca) {
            mostra = marca.framePosition + (nano - marca.nanoTime) * MOSTREIG / 1e9;
        } else {
            mostra = (nano - nanoInici) * MOSTREIG / 1e9 - MOSTREIG * 0.03; // retard típic d'entrada
        }
        return Math.max(0, Math.round(mostra * 1000 / MOSTREIG));
    }

    /** Si el mòbil dona marques de temps precises del micròfon (per al diagnòstic). */
    public boolean teMarcaDeTemps() {
        return teMarca;
    }

    /** Atura la gravació i tanca el fitxer. Torna la durada en mil·lisegons. */
    public long atura() {
        actiu = false;
        if (fil != null) {
            try {
                fil.join(1500);
            } catch (InterruptedException ignored) {
            }
            fil = null;
        }
        tancaTot();
        return duradaMs;
    }

    private void tancaTot() {
        if (rec != null) {
            try {
                rec.stop();
            } catch (IllegalStateException ignored) {
            }
            rec.release();
            rec = null;
        }
        if (escriptor != null) {
            try {
                escriptor.tanca();
            } catch (IOException ignored) {
            }
            escriptor = null;
        }
    }
}
