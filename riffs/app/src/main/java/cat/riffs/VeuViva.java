package cat.riffs;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTimestamp;

import java.util.List;
import java.util.Locale;

/**
 * La veu en directe (2.44): llegeix el micròfon a trossets petits, en treu les notes amb {@link VeuNotes} i les passa
 * a l'oient amb el moment ({@code System.nanoTime}) en què es van cantar, fet amb les marques de temps del micròfon.
 * També mesura el retard d'entrada per a la pantalla i el diagnòstic.
 */
final class VeuViva {

    interface Oient {
        /** Comença, canvia o s'acaba una nota (en el fil del micròfon: res de pantalla aquí). */
        void event(VeuNotes.Event e, long nanos);
    }

    static volatile String info = "encara no s'ha cantat en directe";

    private static final int TROS = 192;

    final VeuNotes notes;
    private final Oient oient;
    private final int sr;
    private AudioRecord rec;
    private Thread fil;
    private volatile boolean viu;
    private String font = "";
    /** Retard d'entrada mesurat (ms; −1 = encara no). */
    volatile double retardEntradaMs = -1;
    private volatile long llegides = 0;

    VeuViva(int sr, Oient oient) {
        this.sr = sr;
        this.notes = new VeuNotes(sr);
        this.oient = oient;
    }

    /** La freqüència del micròfon: 48 kHz si el mòbil la té (la de la sortida, normalment), si no 44,1 kHz. */
    static int mostreig() {
        int m = AudioRecord.getMinBufferSize(48000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        return m > 0 ? 48000 : 44100;
    }

    /** Torna null si ha començat, o el missatge d'error. */
    String comenca(Context c) {
        int origen = Gravadora.font(c, Ui.prefs(c).getString("font", Gravadora.AUTO));
        font = Gravadora.nomFont(origen);
        int min = AudioRecord.getMinBufferSize(sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) return "Aquest mòbil no pot obrir el micròfon a " + sr + " Hz.";
        try {
            rec = new AudioRecord(origen, sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(min, TROS * 8));
        } catch (SecurityException e) {
            return "Falta el permís de micròfon.";
        } catch (IllegalArgumentException e) {
            return "No puc obrir el " + font + ".";
        }
        if (rec.getState() != AudioRecord.STATE_INITIALIZED) {
            rec.release();
            rec = null;
            return "No puc obrir el " + font + ".";
        }
        try {
            rec.startRecording();
        } catch (IllegalStateException e) {
            rec.release();
            rec = null;
            return "No s'ha pogut començar a escoltar el micròfon.";
        }
        viu = true;
        fil = new Thread(this::bucle, "veu-viva");
        fil.setPriority(Thread.MAX_PRIORITY);
        fil.start();
        return null;
    }

    private void bucle() {
        short[] buf = new short[TROS];
        AudioTimestamp ts = new AudioTimestamp();
        boolean teMarca = false;
        long marcaFrame = 0, marcaNano = 0;
        int compta = 0;
        while (viu) {
            int n = rec.read(buf, 0, TROS);
            if (n <= 0) {
                if (n < 0) break;
                continue;
            }
            long ara = System.nanoTime();
            long primera = llegides;
            llegides += n;
            // marca de temps: quan es va captar cada mostra (cada ~0,1 s)
            if (compta++ % 25 == 0) {
                try {
                    if (rec.getTimestamp(ts, AudioTimestamp.TIMEBASE_MONOTONIC) == AudioRecord.SUCCESS) {
                        teMarca = true;
                        marcaFrame = ts.framePosition;
                        marcaNano = ts.nanoTime;
                    }
                } catch (RuntimeException ignorat) {
                }
            }
            long nanoFi = teMarca ? marcaNano + Math.round((llegides - marcaFrame) * 1e9 / sr) : ara - 20_000_000L;
            double r = (ara - nanoFi) / 1e6;
            if (r > 0 && r < 1000) retardEntradaMs = retardEntradaMs < 0 ? r : retardEntradaMs * 0.9 + r * 0.1;
            List<VeuNotes.Event> ev = notes.afegeix(buf, n, primera);
            for (VeuNotes.Event e : ev) {
                long nano = nanoFi - Math.round((llegides - e.mostra) * 1e9 / sr);
                oient.event(e, nano);
            }
        }
    }

    /** Retard total aproximat (ms): micròfon + càlcul + sortida del sinte. −1 si encara no se sap. */
    double retardTotalMs() {
        double s = SortidaViva.darrerRetardMs;
        if (retardEntradaMs < 0) return -1;
        return retardEntradaMs + notes.retardCalculMs() + Math.max(0, s);
    }

    void atura() {
        viu = false;
        if (fil != null) {
            try {
                fil.join(500);
            } catch (InterruptedException ignorat) {
            }
            fil = null;
        }
        if (rec != null) {
            try {
                rec.stop();
            } catch (IllegalStateException ignorat) {
            }
            rec.release();
            rec = null;
        }
        info = String.format(Locale.ROOT, "%d Hz · %s · entrada %s · càlcul %.0f ms · sortida %s · total %s · desafinació apresa %+.0f cèntims",
                sr, font, retardEntradaMs < 0 ? "?" : String.format(Locale.ROOT, "%.0f ms", retardEntradaMs),
                notes.retardCalculMs(), SortidaViva.darrerRetardMs < 0 ? "?" : String.format(Locale.ROOT, "%.0f ms", SortidaViva.darrerRetardMs),
                retardTotalMs() < 0 ? "?" : String.format(Locale.ROOT, "%.0f ms", retardTotalMs()), notes.desviacio() * 100)
                .replace('.', ',');
    }

    long llegides() {
        return llegides;
    }
}
